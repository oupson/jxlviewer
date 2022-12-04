#define LOG_TAG "native-jxl"

#include <cinttypes>

#include <vector>
#include <jni.h>
#include <android/log.h>

#include <jxl/decode.h>
#include <jxl/decode_cxx.h>
#include <jxl/resizable_parallel_runner.h>
#include <jxl/resizable_parallel_runner_cxx.h>

#include <android/bitmap.h>

inline uint32_t min(uint32_t a, uint32_t b) {
    return (b < a) ? b : a;
}

/**
* Multiplies a single channel value with passed alpha. Values are already shifted
* and can be directly ORed back into uint32_t structure.
*/
inline uint32_t premultiply_channel_value(const uint32_t pixel, const uint8_t offset,
                                          const uint32_t alpha) {
    uint32_t multipliedValue = (((pixel >> offset) & 0xFF) * alpha) / 255;
    return min(multipliedValue, 255) << offset;
}

/**
*   This premultiplies alpha value in the bitmap. Android expects its bitmaps to have alpha premultiplied for optimization -
*   this means that instead of ARGB values of (128, 255, 255, 255) the bitmap needs to store (128, 128, 128, 128). Color channels
*   are multiplied with alpha value (0.0 .. 1.0).
*/
inline void premultiply_bitmap_alpha(const uint32_t bitmapHeight, const uint32_t bitmapWidth,
                                     uint32_t *bitmapBuffer) {
    const uint32_t pixels = bitmapHeight * bitmapWidth;
    for (uint32_t i = 0; i < pixels; i++) {
        const auto alpha = (uint32_t) ((bitmapBuffer[i] >> 24) & 0xFF);

        bitmapBuffer[i] = (bitmapBuffer[i] & 0xFF000000) |
                          premultiply_channel_value(bitmapBuffer[i], 16, alpha) |
                          premultiply_channel_value(bitmapBuffer[i], 8, alpha) |
                          premultiply_channel_value(bitmapBuffer[i], 0, alpha);
    }
}

jobject DecodeJpegXlOneShot(JNIEnv *env, const uint8_t *jxl, size_t size) {
    size_t xsize;
    size_t ysize;

    auto drawableClass = env->FindClass("android/graphics/drawable/AnimationDrawable");
    jmethodID drawableMethodID = env->GetMethodID(drawableClass, "<init>", "()V");
    jmethodID addDrawableMethodID = env->GetMethodID(drawableClass, "addFrame",
                                                     "(Landroid/graphics/drawable/Drawable;I)V");
    jobject drawable = env->NewObject(drawableClass, drawableMethodID);

    auto bitmapDrawableClass = env->FindClass("android/graphics/drawable/BitmapDrawable");
    jmethodID bitmapDrawableMethodID = env->GetMethodID(bitmapDrawableClass, "<init>",
                                                        "(Landroid/graphics/Bitmap;)V");

    auto bitmapClass = env->FindClass("android/graphics/Bitmap");
    auto createBitmapMethodId = env->GetStaticMethodID(bitmapClass, "createBitmap",
                                                       "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");

    jstring configName = env->NewStringUTF("ARGB_8888");
    jclass bitmapConfigClass = env->FindClass("android/graphics/Bitmap$Config");
    jmethodID valueOfBitmapConfigFunction = env->GetStaticMethodID(bitmapConfigClass, "valueOf",
                                                                   "(Ljava/lang/String;)Landroid/graphics/Bitmap$Config;");
    jobject bitmapConfig = env->CallStaticObjectMethod(bitmapConfigClass,
                                                       valueOfBitmapConfigFunction, configName);

    // Multi-threaded parallel runner.
    auto runner = JxlResizableParallelRunnerMake(nullptr);

    auto dec = JxlDecoderMake(nullptr);
    if (JXL_DEC_SUCCESS !=
        JxlDecoderSubscribeEvents(dec.get(), JXL_DEC_BASIC_INFO |
                                             JXL_DEC_FULL_IMAGE | JXL_DEC_FRAME)) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "JxlDecoderSubscribeEvents failed");
        return nullptr;
    }

    if (JXL_DEC_SUCCESS != JxlDecoderSetParallelRunner(dec.get(),
                                                       JxlResizableParallelRunner,
                                                       runner.get())) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "JxlDecoderSetParallelRunner failed");
        return nullptr;
    }

    JxlBasicInfo info;
    JxlFrameHeader frameHeader;
    JxlPixelFormat format = {4, JXL_TYPE_UINT8, JXL_NATIVE_ENDIAN, 0};

    JxlDecoderSetInput(dec.get(), jxl, size);
    JxlDecoderCloseInput(dec.get());

    jobject btm = nullptr;

    uint8_t *bitmap_buffer;

    for (;;) {
        JxlDecoderStatus status = JxlDecoderProcessInput(dec.get());

        if (status == JXL_DEC_ERROR) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Decoder error");
            return nullptr;
        } else if (status == JXL_DEC_NEED_MORE_INPUT) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Error, already provided all input");
            return nullptr;
        } else if (status == JXL_DEC_BASIC_INFO) {
            if (JXL_DEC_SUCCESS != JxlDecoderGetBasicInfo(dec.get(), &info)) {
                __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "JxlDecoderGetBasicInfo");
                return nullptr;
            }
            xsize = info.xsize;
            ysize = info.ysize;
            JxlResizableParallelRunnerSetThreads(
                    runner.get(),
                    JxlResizableParallelRunnerSuggestThreads(info.xsize, info.ysize));
        } else if (status == JXL_DEC_NEED_IMAGE_OUT_BUFFER) {
            size_t buffer_size;
            if (JXL_DEC_SUCCESS !=
                JxlDecoderImageOutBufferSize(dec.get(), &format, &buffer_size)) {
                __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                                    "JxlDecoderImageOutBufferSize failed");
                return nullptr;
            }

            if (buffer_size != xsize * ysize * 4) {
                __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                                    "Invalid out buffer size %" PRIu64 " %" PRIu64,
                                    static_cast<uint64_t>(buffer_size),
                                    static_cast<uint64_t>(xsize * ysize * 4));
                return nullptr;
            }

            btm = env->CallStaticObjectMethod(bitmapClass, createBitmapMethodId, (int) xsize,
                                              (int) ysize, bitmapConfig);


            AndroidBitmap_lockPixels(env, btm, reinterpret_cast<void **>(&bitmap_buffer));

            size_t pixels_buffer_size = buffer_size * sizeof(uint8_t);

            if (JXL_DEC_SUCCESS != JxlDecoderSetImageOutBuffer(dec.get(), &format,
                                                               bitmap_buffer,
                                                               pixels_buffer_size)) {
                __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                                    "JxlDecoderSetImageOutBuffer failed");
                return nullptr;
            }
        } else if (status == JXL_DEC_FULL_IMAGE) {
            // Android need images with alpha to be premultiplied, otherwise it produce strange results.
            if (info.alpha_bits && !info.alpha_premultiplied) {
                premultiply_bitmap_alpha(info.ysize, info.xsize, (uint32_t *) bitmap_buffer);
            }

            AndroidBitmap_unlockPixels(env, btm);

            auto btmDrawable = env->NewObject(bitmapDrawableClass, bitmapDrawableMethodID, btm);
            uint32_t num = (info.animation.tps_numerator == 0) ? 1 : info.animation.tps_numerator;
            env->CallVoidMethod(drawable, addDrawableMethodID, btmDrawable,
                                (int) (frameHeader.duration * 1000 *
                                       info.animation.tps_denominator /
                                       num));
        } else if (status == JXL_DEC_SUCCESS) {
            return drawable;
        } else if (status == JXL_DEC_FRAME) {
            if (JXL_DEC_SUCCESS != JxlDecoderGetFrameHeader(dec.get(), &frameHeader)) {
                __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "JxlDecoderGetFrameHeader failed");
                return nullptr;
            }
        } else {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Unknown decoder status");
            return nullptr;
        }
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_fr_oupson_libjxl_JxlDecoder_loadJxl(
        JNIEnv *env,
        jclass /* clazz */,
        jbyteArray data) {
    auto size = env->GetArrayLength(data);
    auto dataPtr = env->GetByteArrayElements(data, nullptr);
    auto result = DecodeJpegXlOneShot(env, (uint8_t *) dataPtr, size);

    env->ReleaseByteArrayElements(data, dataPtr, 0);
    return result;
}