#include <jni.h>

#include "InputSource.h"
#include "JniInputStream.h"
#include "FileDescriptorInputSource.h"
#include "Decoder.h"
#include "Options.h"

extern "C" JNIEXPORT jobject JNICALL
Java_fr_oupson_libjxl_JxlDecoder_loadJxlFromInputStream(JNIEnv *env, jclass /* clazz */,
                                                        jlong native_decoder_ptr,
                                                        jobject input_stream, jlong options_ptr) {
    auto *decoder = reinterpret_cast<Decoder *>(native_decoder_ptr);
    auto *options = reinterpret_cast<Options *>(options_ptr);
    auto jniInputStream = JniInputStream(env, input_stream);

    return decoder->DecodeJxl(env, jniInputStream, options);
}

extern "C" JNIEXPORT jobject JNICALL
Java_fr_oupson_libjxl_JxlDecoder_loadJxlFromFd(JNIEnv *env, jclass /* clazz */,
                                               jlong native_decoder_ptr, jint fd,
                                               jlong options_ptr) {
    auto *decoder = reinterpret_cast<Decoder *>(native_decoder_ptr);
    auto *options = reinterpret_cast<Options *>(options_ptr);
    auto jniInputStream = FileDescriptorInputSource(env, fd);

    return decoder->DecodeJxl(env, jniInputStream, options);
}

extern "C" JNIEXPORT jlong JNICALL
Java_fr_oupson_libjxl_JxlDecoder_getNativeDecoderPtr(JNIEnv *env, jclass /* clazz */) {
    auto *ptr = new Decoder(env);
    return reinterpret_cast<jlong >(ptr);
}

extern "C" JNIEXPORT void JNICALL
Java_fr_oupson_libjxl_JxlDecoder_freeNativeDecoderPtr(JNIEnv * /* env */, jclass /* clazz */,
                                                      jlong native_decoder_ptr) {
    auto *decoder = reinterpret_cast<Decoder *>(native_decoder_ptr);
    delete decoder;
}

extern "C" JNIEXPORT jobject JNICALL
Java_fr_oupson_libjxl_JxlDecoder_loadThumbnailFromFd(JNIEnv *env, jclass /* clazz */,
                                                     jlong native_decoder_ptr, jint fd) {
    auto *decoder = reinterpret_cast<Decoder *>(native_decoder_ptr);
    auto jniInputStream = FileDescriptorInputSource(env, fd);

    return decoder->DecodeJxlThumbnail(env, jniInputStream);

}

extern "C" JNIEXPORT jobject JNICALL
Java_fr_oupson_libjxl_JxlDecoder_loadThumbnailFromInputStream(JNIEnv *env, jclass /* clazz */,
                                                              jlong native_decoder_ptr,
                                                              jobject input_stream) {
    auto *decoder = reinterpret_cast<Decoder *>(native_decoder_ptr);
    auto jniInputStream = JniInputStream(env, input_stream);

    return decoder->DecodeJxlThumbnail(env, jniInputStream);
}

jlong JNICALL
decoderOptionsAlloc(JNIEnv * /* env */, jclass /* clazz */) {
    auto options = new Options();
    return reinterpret_cast<jlong>(options);
}

void JNICALL
decoderOptionsFree(JNIEnv * /* env */, jclass /* clazz */, jlong ptr) {
    auto options = reinterpret_cast<Options *>(ptr);
    delete options;
}

jint JNICALL
decoderOptionsGetBitmapConfig(JNIEnv * /* env */, jclass /* clazz */, jlong ptr) {
    auto options = reinterpret_cast<Options *>(ptr);
    return options->rgbaConfig;
}

void JNICALL
decoderOptionsSetBitmapConfig(JNIEnv * /* env */, jclass /* clazz */, jlong ptr, jint format) {
    auto options = reinterpret_cast<Options *>(ptr);
    options->rgbaConfig = static_cast<BitmapConfig>(format);
}


jboolean JNICALL
decoderOptionsGetDecodeMultipleFrames(JNIEnv * /* env */, jclass /* clazz */, jlong ptr) {
    auto options = reinterpret_cast<Options *>(ptr);
    return (options->decodeMultipleFrames) ? JNI_TRUE : JNI_FALSE;
}


void JNICALL
decoderOptionsSetDecodeMultipleFrames(JNIEnv * /* env */, jclass /* clazz */, jlong ptr,
                                      jboolean decodeMultipleFrames) {
    auto options = reinterpret_cast<Options *>(ptr);
    options->decodeMultipleFrames = decodeMultipleFrames == JNI_TRUE;
}


jint registerDecoderOptions(JNIEnv *env) noexcept {
    jclass classOptions = env->FindClass("fr/oupson/libjxl/JxlDecoder$Options");
    if (classOptions == nullptr) {
        return JNI_ERR;
    }

    static const JNINativeMethod methods[] = {
            {"alloc",                   "()J",   reinterpret_cast<void *>(decoderOptionsAlloc)},
            {"free",                    "(J)V",  reinterpret_cast<void *>(decoderOptionsFree)},
            {"setBitmapConfig",         "(JI)V", reinterpret_cast<void *>(decoderOptionsSetBitmapConfig)},
            {"getBitmapConfig",         "(J)I",  reinterpret_cast<void *>(decoderOptionsGetBitmapConfig)},
            {"getDecodeMultipleFrames", "(J)Z",  reinterpret_cast<void *>(decoderOptionsGetDecodeMultipleFrames)},
            {"setDecodeMultipleFrames", "(JZ)V", reinterpret_cast<void *>(decoderOptionsSetDecodeMultipleFrames)},
    };

    return env->RegisterNatives(classOptions, methods, sizeof(methods) / sizeof(JNINativeMethod));
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void * /* reserved */) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    if (registerDecoderOptions(env) != JNI_OK) {
        return JNI_ERR;
    }


    return JNI_VERSION_1_6;
}
