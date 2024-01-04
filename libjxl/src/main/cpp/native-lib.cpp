#include "ImageOutCallbackData.h"

#include <cinttypes>


#include <vector>
#include <jni.h>
#include <android/log.h>

#include <jxl/decode.h>
#include <jxl/decode_cxx.h>
#include <jxl/resizable_parallel_runner.h>
#include <jxl/resizable_parallel_runner_cxx.h>

#include <skcms.h>

#include <android/bitmap.h>

#include "jxlviewer_consts.h"
#include "Exception.h"

#include "InputSource.h"
#include "JniInputStream.h"


jobject DecodeJpegXlOneShot(JNIEnv *env, InputSource &source) {
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
    if (JXL_DEC_SUCCESS != JxlDecoderSubscribeEvents(dec.get(),
                                                     JXL_DEC_BASIC_INFO | JXL_DEC_FULL_IMAGE |
                                                     JXL_DEC_FRAME | JXL_DEC_COLOR_ENCODING)) {
        jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR, "JxlDecoderSubscribeEvents");
        return nullptr;
    }

    if (JXL_DEC_SUCCESS !=
        JxlDecoderSetParallelRunner(dec.get(), JxlResizableParallelRunner, runner.get())) {
        jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR, "JxlDecoderSetParallelRunner");
        return nullptr;
    }

    JxlBasicInfo info;
    JxlFrameHeader frameHeader;
    JxlPixelFormat format = {4, JXL_TYPE_UINT8, JXL_NATIVE_ENDIAN, 0};

    uint8_t buffer[BUFFER_SIZE];
    auto readSize = source.read(buffer, sizeof(buffer));
    if (readSize == -1) {
        jxlviewer::throwNewError(env, NEED_MORE_INPUT_ERROR);
        return nullptr;
    } else if (readSize == INT32_MIN) {
        return nullptr;
    } else {
        if (JXL_DEC_SUCCESS != JxlDecoderSetInput(dec.get(), buffer, readSize)) {
            jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR, "JxlDecoderSetInput");
            return nullptr;
        }
    }

    jobject btm = nullptr;

    ImageOutCallbackData out_data;

    for (;;) {
        JxlDecoderStatus status = JxlDecoderProcessInput(dec.get());

        if (status == JXL_DEC_ERROR) {
            jxlviewer::throwNewError(env, DECODER_FAILED_ERROR);
            return nullptr;
        } else if (status == JXL_DEC_NEED_MORE_INPUT) {
            auto remaining = JxlDecoderReleaseInput(dec.get()); // TODO REMAINING TEST
            readSize = source.read(buffer + remaining, sizeof(buffer) - remaining);
            if (readSize == -1) {
                jxlviewer::throwNewError(env, NEED_MORE_INPUT_ERROR);
                return nullptr;
            } else if (readSize == INT32_MIN) {
                return nullptr;
            } else {
                if (JXL_DEC_SUCCESS != JxlDecoderSetInput(dec.get(), buffer, readSize)) {
                    jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR, "JxlDecoderSetInput");
                    return nullptr;
                }
            }
        } else if (status == JXL_DEC_BASIC_INFO) {
            if (JXL_DEC_SUCCESS != JxlDecoderGetBasicInfo(dec.get(), &info)) {
                jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR, "JxlDecoderGetBasicInfo");
                return nullptr;
            }
            out_data.setSize(info.xsize, info.ysize);
            out_data.setIsAlphaPremultiplied(info.alpha_premultiplied);
            JxlResizableParallelRunnerSetThreads(runner.get(),
                                                 JxlResizableParallelRunnerSuggestThreads(
                                                         info.xsize, info.ysize));
        } else if (status == JXL_DEC_COLOR_ENCODING) {
            if (!out_data.parseICCProfile(env, dec.get())) {
                return nullptr;
            }
        } else if (status == JXL_DEC_NEED_IMAGE_OUT_BUFFER) {
        } else if (status == JXL_DEC_FULL_IMAGE) {
            AndroidBitmap_unlockPixels(env, btm);

            auto btmDrawable = env->NewObject(bitmapDrawableClass, bitmapDrawableMethodID, btm);
            uint32_t num = (info.animation.tps_numerator == 0) ? 1 : info.animation.tps_numerator;
            env->CallVoidMethod(drawable, addDrawableMethodID, btmDrawable,
                                (int) (frameHeader.duration * 1000 *
                                       info.animation.tps_denominator / num));
        } else if (status == JXL_DEC_SUCCESS) {
            return drawable;
        } else if (status == JXL_DEC_FRAME) {
            if (JXL_DEC_SUCCESS != JxlDecoderGetFrameHeader(dec.get(), &frameHeader)) {
                jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR, "JxlDecoderGetFrameHeader");
                return nullptr;
            }

            btm = env->CallStaticObjectMethod(bitmapClass, createBitmapMethodId,
                                              (int) out_data.getWidth(),
                                              (int) out_data.getHeight(), bitmapConfig);

            AndroidBitmap_lockPixels(env, btm,
                                     reinterpret_cast<void **>(out_data.getImageBufferPtr()));

            if (JXL_DEC_SUCCESS !=
                JxlDecoderSetImageOutCallback(dec.get(), &format,
                                              jxl_viewer_image_out_callback, &out_data)) {
                jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR,
                                         "JxlDecoderSetImageOutCallback");
                return nullptr;
            }
        } else {
            jxlviewer::throwNewError(env, OTHER_ERROR_TYPE, "Unknown decoder status");
            return nullptr;
        }
    }

}

extern "C"
JNIEXPORT jobject JNICALL
Java_fr_oupson_libjxl_JxlDecoder_loadJxlFromInputStream(JNIEnv *env, jclass /* clazz */,
                                                        jobject input_stream) {
    auto jniInputStream = JniInputStream(env, input_stream);

    return DecodeJpegXlOneShot(env, jniInputStream);
}

