#include <jxl/decode.h>
#include <jxl/decode_cxx.h>
#include <jxl/resizable_parallel_runner.h>
#include <jxl/resizable_parallel_runner_cxx.h>

#include <android/bitmap.h>

#include "Decoder.h"
#include "Exception.h"
#include "JniInputStream.h"
#include "ImageOutCallbackData.h"
#include <android/log.h>

Decoder::Decoder(JNIEnv *env) : vm(nullptr) {
    env->GetJavaVM(&this->vm);

    this->bitmapClass = reinterpret_cast<jclass>(env->NewGlobalRef(
            env->FindClass("android/graphics/Bitmap")));
    this->createBitmapMethodId = env->GetStaticMethodID(this->bitmapClass, "createBitmap",
                                                        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");


    this->callbackClass = reinterpret_cast<jclass>(env->NewGlobalRef(
            env->FindClass("fr/oupson/libjxl/JxlDecoder$Callback")));

    this->callbackOnHeaderDecoded = env->GetMethodID(this->callbackClass, "onHeaderDecoded",
                                                     "(IIIIZI)Z");
    this->callbackOnProgressiveFrame = env->GetMethodID(this->callbackClass, "onProgressiveFrame",
                                                        "(Landroid/graphics/Bitmap;)Z");
    this->callbackOnFrameDecoded = env->GetMethodID(this->callbackClass, "onFrameDecoded",
                                                    "(ILandroid/graphics/Bitmap;)Z");

    // ARGB_8888 is stored as RGBA_8888 in memory
    jstring rgbaU8configName = env->NewStringUTF("ARGB_8888");
    jclass bitmapConfigClass = env->FindClass("android/graphics/Bitmap$Config");
    jmethodID valueOfBitmapConfigFunction = env->GetStaticMethodID(bitmapConfigClass, "valueOf",
                                                                   "(Ljava/lang/String;)Landroid/graphics/Bitmap$Config;");

    this->bitmapConfigRgbaU8 = reinterpret_cast<jclass>(env->NewGlobalRef(
            env->CallStaticObjectMethod(bitmapConfigClass, valueOfBitmapConfigFunction,
                                        rgbaU8configName)));

    if (android_get_device_api_level() >= 26) {
        jstring rgbaF16configName = env->NewStringUTF("RGBA_F16");
        this->bitmapConfigRgbaF16 = reinterpret_cast<jclass>(env->NewGlobalRef(
                env->CallStaticObjectMethod(bitmapConfigClass, valueOfBitmapConfigFunction,
                                            rgbaF16configName)));
    }
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

    env->DeleteGlobalRef(this->bitmapClass);
    env->DeleteGlobalRef(this->bitmapConfigRgbaU8);
    env->DeleteGlobalRef(this->bitmapConfigRgbaF16);

    env->DeleteGlobalRef(this->callbackClass);

    if (needToDetach) {
        this->vm->DetachCurrentThread();
    }
}

int Decoder::DecodeJxl(JNIEnv *env, InputSource &source, Options *options, jobject callback) {
    BitmapConfig btmConfigNative = (options != nullptr) ? options->rgbaConfig
                                                        : BitmapConfig::RGBA_8888;

    jobject bitmapConfig = (options != nullptr) ? ((options->rgbaConfig == 0)
                                                   ? this->bitmapConfigRgbaU8
                                                   : this->bitmapConfigRgbaF16)
                                                : this->bitmapConfigRgbaU8;



    // Multi-threaded parallel runner.
    auto runner = JxlResizableParallelRunnerMake(nullptr);

    auto events = JXL_DEC_BASIC_INFO | JXL_DEC_FRAME;

    if (options->decodeFrames) {
        events = events | JXL_DEC_COLOR_ENCODING | JXL_DEC_FULL_IMAGE;
    }

    if (options->decodeProgressive) {
        events = events | JXL_DEC_FRAME_PROGRESSION;
    }

    auto dec = JxlDecoderMake(nullptr);
    if (JXL_DEC_SUCCESS != JxlDecoderSubscribeEvents(dec.get(), events)) {
        jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR, "JxlDecoderSubscribeEvents");
        return -1;
    }

    if (JXL_DEC_SUCCESS !=
        JxlDecoderSetParallelRunner(dec.get(), JxlResizableParallelRunner, runner.get())) {
        jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR, "JxlDecoderSetParallelRunner");
        return -1;
    }

    JxlBasicInfo info;
    JxlFrameHeader frameHeader;
    JxlPixelFormat format = {4, JXL_TYPE_FLOAT16, JXL_NATIVE_ENDIAN, 0};

    uint8_t buffer[BUFFER_SIZE];

    jobject btm = nullptr;

    ImageOutCallbackData out_data(btmConfigNative);

    JxlDecoderStatus status = JXL_DEC_NEED_MORE_INPUT;

    int nbr_frames = 0;

    for (;;) {
        if (status == JXL_DEC_ERROR) {
            jxlviewer::throwNewError(env, DECODER_FAILED_ERROR);
            return -1;
        } else if (status == JXL_DEC_NEED_MORE_INPUT) {
            auto remaining = JxlDecoderReleaseInput(dec.get()); // TODO REMAINING TEST
            auto readSize = source.read(buffer + remaining, sizeof(buffer) - remaining);
            if (readSize == -1) {
                jxlviewer::throwNewError(env, NEED_MORE_INPUT_ERROR);
                return -1;
            } else if (readSize == INT32_MIN) {
                return nbr_frames;
            } else {
                if (JXL_DEC_SUCCESS != JxlDecoderSetInput(dec.get(), buffer, readSize)) {
                    jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR, "JxlDecoderSetInput");
                    return -1;
                }
            }
        } else if (status == JXL_DEC_BASIC_INFO) {
            if (JXL_DEC_SUCCESS != JxlDecoderGetBasicInfo(dec.get(), &info)) {
                jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR, "JxlDecoderGetBasicInfo");
                return -1;
            }

            if (info.alpha_bits == 0) {
                if (btmConfigNative == BitmapConfig::RGBA_8888) {
                    out_data.setSourcePixelFormat(skcms_PixelFormat_RGB_888);
                    format = {3, JXL_TYPE_UINT8, JXL_NATIVE_ENDIAN, 0};
                } else {
                    out_data.setSourcePixelFormat(skcms_PixelFormat_RGB_hhh);
                    format = {3, JXL_TYPE_FLOAT16, JXL_NATIVE_ENDIAN, 0};
                }
            } else {
                if (btmConfigNative == BitmapConfig::RGBA_8888) {
                    out_data.setSourcePixelFormat(skcms_PixelFormat_RGBA_8888);
                    format = {4, JXL_TYPE_UINT8, JXL_NATIVE_ENDIAN, 0};
                } else {
                    out_data.setSourcePixelFormat(skcms_PixelFormat_RGBA_hhhh);
                    format = {4, JXL_TYPE_FLOAT16, JXL_NATIVE_ENDIAN, 0};
                }
            }

            out_data.setSize(info.xsize, info.ysize);
            out_data.setIsAlphaPremultiplied(info.alpha_premultiplied);
            JxlResizableParallelRunnerSetThreads(runner.get(),
                                                 JxlResizableParallelRunnerSuggestThreads(
                                                         info.xsize, info.ysize));

            auto continueDecoding = env->CallBooleanMethod(callback, this->callbackOnHeaderDecoded,
                                                           info.xsize, info.ysize,
                                                           info.intrinsic_xsize,
                                                           info.intrinsic_ysize,
                                                           (info.have_animation) ? JNI_TRUE
                                                                                 : JNI_FALSE,
                                                           (int) info.orientation);
            if (env->ExceptionCheck() == JNI_TRUE) {
                return -1;
            }

            if (continueDecoding != JNI_TRUE) {
                return nbr_frames;
            }
        } else if (status == JXL_DEC_COLOR_ENCODING) {
            if (!out_data.parseICCProfile(env, dec.get())) {
                return -1;
            }
        } else if (status == JXL_DEC_NEED_IMAGE_OUT_BUFFER) {
            if (!options->decodeFrames) {
                if (JXL_DEC_SUCCESS != JxlDecoderSkipCurrentFrame(dec.get())) {
                    jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR,
                                             "JxlDecoderSkipCurrentFrame");
                    return -1;
                }
            }
        } else if (status == JXL_DEC_FULL_IMAGE) {
            AndroidBitmap_unlockPixels(env, btm);

            int delay = 0;
            if (info.have_animation) {
                uint32_t num = (info.animation.tps_numerator == 0) ? 1
                                                                   : info.animation.tps_numerator;
                delay = (int) (frameHeader.duration * 1000 * info.animation.tps_denominator / num);
            }

            const auto continueDecoding =
                    env->CallBooleanMethod(callback, this->callbackOnFrameDecoded, delay, btm) ==
                    JNI_TRUE;

            if (env->ExceptionCheck() == JNI_TRUE) {
                return -1;
            }

            nbr_frames += 1;

            if (!continueDecoding) {
                return nbr_frames;
            }
        } else if (status == JXL_DEC_SUCCESS) {
            return nbr_frames;
        } else if (status == JXL_DEC_FRAME) {
            if (options->decodeFrames) {
                if (JXL_DEC_SUCCESS != JxlDecoderGetFrameHeader(dec.get(), &frameHeader)) {
                    jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR,
                                             "JxlDecoderGetFrameHeader");
                    return -1;
                }

                if (btm == nullptr) {
                    btm = env->CallStaticObjectMethod(bitmapClass, createBitmapMethodId,
                                                      (int) out_data.getWidth(),
                                                      (int) out_data.getHeight(), bitmapConfig);
                    if (env->ExceptionCheck() == JNI_TRUE) {
                        return -1;
                    }
                }

                if (AndroidBitmap_lockPixels(env, btm,
                                             reinterpret_cast<void **>(out_data.getImageBufferPtr())) !=
                    ANDROID_BITMAP_RESULT_SUCCESS) {
                    jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR,
                                             "AndroidBitmap_lockPixels");
                    return -1;
                }

                if (JXL_DEC_SUCCESS !=
                    JxlDecoderSetImageOutCallback(dec.get(), &format, jxl_viewer_image_out_callback,
                                                  &out_data)) {
                    jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR,
                                             "JxlDecoderSetImageOutCallback");
                    return -1;
                }
            } else {
                nbr_frames += 1;
            }
        } else if (status == JXL_DEC_FRAME_PROGRESSION) {
            if (options->decodeProgressive) {
                if (JXL_DEC_SUCCESS == JxlDecoderFlushImage(dec.get())) {
                    AndroidBitmap_unlockPixels(env, btm);

                    auto continueDecoding = env->CallBooleanMethod(callback,
                                                                   this->callbackOnProgressiveFrame,
                                                                   btm);
                    if (env->ExceptionCheck() == JNI_TRUE) {
                        return -1;
                    }

                    if (continueDecoding != JNI_TRUE) {
                        return nbr_frames;
                    } else {
                        if (AndroidBitmap_lockPixels(env, btm,
                                                     reinterpret_cast<void **>(out_data.getImageBufferPtr())) !=
                            ANDROID_BITMAP_RESULT_SUCCESS) {
                            jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR,
                                                     "AndroidBitmap_lockPixels");
                            return -1;
                        }
                    }
                };
            }
        } else {
            jxlviewer::throwNewError(env, OTHER_ERROR_TYPE, "Unknown decoder status");
            return -1;
        }
        status = JxlDecoderProcessInput(dec.get());
    }
}
