#define LOG_TAG "native-jxl"

#include <cinttypes>

#include <vector>
#include <jni.h>
#include <android/log.h>

#include "jxl/decode.h"
#include "jxl/decode_cxx.h"
#include "jxl/resizable_parallel_runner.h"
#include "jxl/resizable_parallel_runner_cxx.h"

jobject DecodeJpegXlOneShot(JNIEnv *env, const uint8_t *jxl, size_t size) {
    size_t xsize;
    size_t ysize;
    std::vector<uint8_t> pixels{};

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
    auto copyPixelsFromBufferMethodId = env->GetMethodID(bitmapClass, "copyPixelsFromBuffer",
                                                         "(Ljava/nio/Buffer;)V");

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
            pixels.resize(xsize * ysize * 4);
            void *pixels_buffer = (void *) pixels.data();
            size_t pixels_buffer_size = pixels.size() * sizeof(uint8_t);
            if (JXL_DEC_SUCCESS != JxlDecoderSetImageOutBuffer(dec.get(), &format,
                                                               pixels_buffer,
                                                               pixels_buffer_size)) {
                __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                                    "JxlDecoderSetImageOutBuffer failed");
                return nullptr;
            }
        } else if (status == JXL_DEC_FULL_IMAGE) {
            void *pixels_buffer = (void *) pixels.data();

            // Nothing to do. Do not yet return. If the image is an animation, more
            // full frames may be decoded. This example only keeps the last one.
            auto buffer = env->NewDirectByteBuffer(pixels_buffer, xsize * ysize * 4);

            auto btm = env->CallStaticObjectMethod(bitmapClass, createBitmapMethodId, (int) xsize,
                                                   (int) ysize, bitmapConfig);

            env->CallVoidMethod(btm, copyPixelsFromBufferMethodId, buffer);

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
Java_fr_oupson_jxlviewer_JxlDecoder_loadJxl(
        JNIEnv *env,
        jobject /* this */,
        jbyteArray data) {
    auto size = env->GetArrayLength(data);
    auto dataPtr = env->GetByteArrayElements(data, nullptr);
    auto result = DecodeJpegXlOneShot(env, (uint8_t *) dataPtr, size);

    env->ReleaseByteArrayElements(data, dataPtr, 0);
    return result;
}