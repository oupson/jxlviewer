#include <jxl/decode.h>
#include <jxl/decode_cxx.h>
#include <jxl/resizable_parallel_runner.h>
#include <jxl/resizable_parallel_runner_cxx.h>

#include <android/bitmap.h>

#include "Decoder.h"
#include "Exception.h"
#include "JniInputStream.h"
#include "ImageOutCallbackData.h"

Decoder::Decoder(JNIEnv *env) {
    env->GetJavaVM(&this->vm);
    this->drawableClass = reinterpret_cast<jclass>(env->NewGlobalRef(
            env->FindClass("android/graphics/drawable/AnimationDrawable")));
    this->drawableMethodID = env->GetMethodID(this->drawableClass, "<init>", "()V");
    this->addDrawableMethodID = env->GetMethodID(this->drawableClass, "addFrame",
                                                 "(Landroid/graphics/drawable/Drawable;I)V");

    this->bitmapDrawableClass = reinterpret_cast<jclass>(env->NewGlobalRef(
            env->FindClass("android/graphics/drawable/BitmapDrawable")));
    this->bitmapDrawableMethodID = env->GetMethodID(this->bitmapDrawableClass, "<init>",
                                                    "(Landroid/graphics/Bitmap;)V");

    this->bitmapClass = reinterpret_cast<jclass>(env->NewGlobalRef(
            env->FindClass("android/graphics/Bitmap")));
    this->createBitmapMethodId = env->GetStaticMethodID(this->bitmapClass, "createBitmap",
                                                        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");

    jstring configName = env->NewStringUTF("ARGB_8888");
    jclass bitmapConfigClass = env->FindClass("android/graphics/Bitmap$Config");
    jmethodID valueOfBitmapConfigFunction = env->GetStaticMethodID(bitmapConfigClass, "valueOf",
                                                                   "(Ljava/lang/String;)Landroid/graphics/Bitmap$Config;");
    this->bitmapConfig = reinterpret_cast<jclass>(env->NewGlobalRef(
            env->CallStaticObjectMethod(bitmapConfigClass, valueOfBitmapConfigFunction,
                                        configName)));
}

Decoder::~Decoder() {
    JNIEnv *env;

    bool needToDetach = false;
    jint res = this->vm->GetEnv((void **) &env, JNI_VERSION_1_6);
    if (JNI_EDETACHED == res) {
        res = this->vm->AttachCurrentThread(&env, nullptr);
        if (JNI_OK == res) {
            needToDetach = true;
        } else {
            return;
        }
    } else if (JNI_OK != res) {
        return;
    }

    env->DeleteGlobalRef(this->drawableClass);
    env->DeleteGlobalRef(this->bitmapDrawableClass);
    env->DeleteGlobalRef(this->bitmapClass);
    env->DeleteGlobalRef(this->bitmapConfig);

    if (needToDetach) {
        this->vm->DetachCurrentThread();
    }
}

jobject Decoder::DecodeJxl(JNIEnv *env, InputSource &source) {
    jobject drawable = env->NewObject(drawableClass, drawableMethodID);

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
                                              (int) out_data.getWidth(), (int) out_data.getHeight(),
                                              bitmapConfig);

            AndroidBitmap_lockPixels(env, btm,
                                     reinterpret_cast<void **>(out_data.getImageBufferPtr()));

            if (JXL_DEC_SUCCESS !=
                JxlDecoderSetImageOutCallback(dec.get(), &format, jxl_viewer_image_out_callback,
                                              &out_data)) {
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

// TODO: image preview
jobject Decoder::DecodeJxlThumbnail(JNIEnv *env, InputSource &source) {
    // Multi-threaded parallel runner.
    auto runner = JxlResizableParallelRunnerMake(nullptr);

    auto dec = JxlDecoderMake(nullptr);
    if (JXL_DEC_SUCCESS != JxlDecoderSubscribeEvents(dec.get(),
                                                     JXL_DEC_BASIC_INFO | JXL_DEC_FULL_IMAGE |
                                                     JXL_DEC_FRAME | JXL_DEC_COLOR_ENCODING |
                                                     JXL_DEC_FRAME_PROGRESSION)) {
        jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR, "JxlDecoderSubscribeEvents");
        return nullptr;
    }

    if (JXL_DEC_SUCCESS !=
        JxlDecoderSetParallelRunner(dec.get(), JxlResizableParallelRunner, runner.get())) {
        jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR, "JxlDecoderSetParallelRunner");
        return nullptr;
    }

    if (JXL_DEC_SUCCESS !=
        JxlDecoderSetProgressiveDetail(dec.get(), JxlProgressiveDetail::kLastPasses)) {
        jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR, "JxlDecoderSetProgressiveDetail");
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
            if (info.alpha_bits == 0) {
                out_data = ImageOutCallbackData(skcms_PixelFormat_RGB_888);
                format = JxlPixelFormat{3, JXL_TYPE_UINT8, JXL_NATIVE_ENDIAN, 0};
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

            JxlDecoderCloseInput(dec.get());
            return btm;
        } else if (status == JXL_DEC_SUCCESS) {
            AndroidBitmap_unlockPixels(env, btm);
            return btm;
        } else if (status == JXL_DEC_FRAME) {
            if (JXL_DEC_SUCCESS != JxlDecoderGetFrameHeader(dec.get(), &frameHeader)) {
                jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR, "JxlDecoderGetFrameHeader");
                return nullptr;
            }

            btm = env->CallStaticObjectMethod(bitmapClass, createBitmapMethodId,
                                              (int) out_data.getWidth(), (int) out_data.getHeight(),
                                              bitmapConfig);

            AndroidBitmap_lockPixels(env, btm,
                                     reinterpret_cast<void **>(out_data.getImageBufferPtr()));

            if (JXL_DEC_SUCCESS !=
                JxlDecoderSetImageOutCallback(dec.get(), &format, jxl_viewer_image_out_callback,
                                              &out_data)) {
                jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR,
                                         "JxlDecoderSetImageOutCallback");
                return nullptr;
            }
            JxlDecoderFlushImage(dec.get());
        } else if (status == JXL_DEC_FRAME_PROGRESSION) {
            JxlDecoderFlushImage(dec.get());
            JxlDecoderSkipCurrentFrame(dec.get());
        } else {
            jxlviewer::throwNewError(env, OTHER_ERROR_TYPE, "Unknown decoder status");
            return nullptr;
        }
    }
}