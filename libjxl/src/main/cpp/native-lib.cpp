#include <jni.h>

#include "InputSource.h"
#include "JniInputStream.h"
#include "FileDescriptorInputSource.h"
#include "Decoder.h"
#include "Options.h"


jlong JNICALL
decoderAlloc(JNIEnv *env, jclass /* clazz */) {
    auto *ptr = new Decoder(env);
    return reinterpret_cast<jlong >(ptr);
}


void JNICALL
decoderFree(JNIEnv * /* env */, jclass /* clazz */, jlong native_decoder_ptr) {
    auto *decoder = reinterpret_cast<Decoder *>(native_decoder_ptr);
    delete decoder;
}


jint JNICALL
decoderFromInputStream(JNIEnv *env, jclass /* clazz */, jlong native_decoder_ptr,
                       jobject input_stream, jlong options_ptr, jobject callback) {
    auto *decoder = reinterpret_cast<Decoder *>(native_decoder_ptr);
    auto *options = reinterpret_cast<Options *>(options_ptr);
    auto jniInputStream = JniInputStream(env, input_stream);

    return decoder->DecodeJxl(env, jniInputStream, options, callback);
}

// TODO: test mmap
jint JNICALL
decoderFromFd(JNIEnv *env, jclass /* clazz */, jlong native_decoder_ptr, jint fd, jlong options_ptr,
              jobject callback) {
    auto *decoder = reinterpret_cast<Decoder *>(native_decoder_ptr);
    auto *options = reinterpret_cast<Options *>(options_ptr);
    auto jniInputStream = FileDescriptorInputSource(env, fd);

    return decoder->DecodeJxl(env, jniInputStream, options, callback);
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
decoderOptionsGetDecodeProgressive(JNIEnv * /* env */, jclass /* clazz */, jlong ptr) {
    auto options = reinterpret_cast<Options *>(ptr);
    return (options->decodeProgressive) ? JNI_TRUE : JNI_FALSE;
}


void JNICALL
decoderOptionsSetDecodeProgressive(JNIEnv * /* env */, jclass /* clazz */, jlong ptr,
                                   jboolean decode_progressive) {
    auto options = reinterpret_cast<Options *>(ptr);
    options->decodeProgressive = decode_progressive == JNI_TRUE;
}

jboolean JNICALL
decoderOptionsGetDecodeFrames(JNIEnv * /* env */, jclass /* clazz */, jlong ptr) {
    auto options = reinterpret_cast<Options *>(ptr);
    return (options->decodeFrames) ? JNI_TRUE : JNI_FALSE;
}


void JNICALL
decoderOptionsSetDecodeFrames(JNIEnv * /* env */, jclass /* clazz */, jlong ptr,
                              jboolean decode_frames) {
    auto options = reinterpret_cast<Options *>(ptr);
    options->decodeFrames = decode_frames == JNI_TRUE;
}


jint registerDecoder(JNIEnv *env) noexcept {
    jclass classOptions = env->FindClass("fr/oupson/libjxl/JxlDecoder");
    if (classOptions == nullptr) {
        return JNI_ERR;
    }

    static const JNINativeMethod methods[] = {{"getNativeDecoderPtr",    "()J",                                                              reinterpret_cast<void *>(decoderAlloc)},
                                              {"freeNativeDecoderPtr",   "(J)V",                                                             reinterpret_cast<void *>(decoderFree)},
                                              {"loadJxlFromInputStream", "(JLjava/io/InputStream;JLfr/oupson/libjxl/JxlDecoder$Callback;)I", reinterpret_cast<void *>(decoderFromInputStream)},
                                              {"loadJxlFromFd",          "(JIJLfr/oupson/libjxl/JxlDecoder$Callback;)I",                     reinterpret_cast<void *>(decoderFromFd)},

    };

    return env->RegisterNatives(classOptions, methods, sizeof(methods) / sizeof(JNINativeMethod));
}


jint registerDecoderOptions(JNIEnv *env) noexcept {
    jclass classOptions = env->FindClass("fr/oupson/libjxl/JxlDecoder$Options");
    if (classOptions == nullptr) {
        return JNI_ERR;
    }

    static const JNINativeMethod methods[] = {{"alloc",                "()J",   reinterpret_cast<void *>(decoderOptionsAlloc)},
                                              {"free",                 "(J)V",  reinterpret_cast<void *>(decoderOptionsFree)},
                                              {"setBitmapConfig",      "(JI)V", reinterpret_cast<void *>(decoderOptionsSetBitmapConfig)},
                                              {"getBitmapConfig",      "(J)I",  reinterpret_cast<void *>(decoderOptionsGetBitmapConfig)},
                                              {"getDecodeProgressive", "(J)Z",  reinterpret_cast<void *>(decoderOptionsGetDecodeProgressive)},
                                              {"setDecodeProgressive", "(JZ)V", reinterpret_cast<void *>(decoderOptionsSetDecodeProgressive)},
                                              {"getDecodeFrames",      "(J)Z",  reinterpret_cast<void *>(decoderOptionsGetDecodeFrames)},
                                              {"setDecodeFrames",      "(JZ)V", reinterpret_cast<void *>(decoderOptionsSetDecodeFrames)},};

    return env->RegisterNatives(classOptions, methods, sizeof(methods) / sizeof(JNINativeMethod));
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void * /* reserved */) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    if (registerDecoder(env) != JNI_OK) {
        return JNI_ERR;
    }

    if (registerDecoderOptions(env) != JNI_OK) {
        return JNI_ERR;
    }


    return JNI_VERSION_1_6;
}
