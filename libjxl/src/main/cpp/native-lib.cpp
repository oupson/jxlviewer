#include <jni.h>

#include "InputSource.h"
#include "JniInputStream.h"
#include "FileDescriptorInputSource.h"
#include "Decoder.h"

extern "C" JNIEXPORT jobject JNICALL
Java_fr_oupson_libjxl_JxlDecoder_loadJxlFromInputStream(JNIEnv *env, jclass /* clazz */,
                                                        jlong native_decoder_ptr,
                                                        jobject input_stream) {
    Decoder *decoder = reinterpret_cast<Decoder *>(native_decoder_ptr);
    auto jniInputStream = JniInputStream(env, input_stream);

    return decoder->DecodeJxl(env, jniInputStream);
}

extern "C" JNIEXPORT jobject JNICALL
Java_fr_oupson_libjxl_JxlDecoder_loadJxlFromFd(JNIEnv *env, jclass /* clazz */,
                                               jlong native_decoder_ptr, jint fd) {
    Decoder *decoder = reinterpret_cast<Decoder *>(native_decoder_ptr);
    auto jniInputStream = FileDescriptorInputSource(env, fd);

    return decoder->DecodeJxl(env, jniInputStream);
}

extern "C" JNIEXPORT jlong JNICALL
Java_fr_oupson_libjxl_JxlDecoder_getNativeDecoderPtr(JNIEnv *env, jclass /* clazz */) {
    Decoder *ptr = new Decoder(env);
    return reinterpret_cast<jlong >(ptr);
}

extern "C" JNIEXPORT void JNICALL
Java_fr_oupson_libjxl_JxlDecoder_freeNativeDecoderPtr(JNIEnv *env, jclass /* clazz */,
                                                      jlong native_decoder_ptr) {
    Decoder *decoder = reinterpret_cast<Decoder *>(native_decoder_ptr);
    delete decoder;
}

extern "C" JNIEXPORT jobject JNICALL
Java_fr_oupson_libjxl_JxlDecoder_loadThumbnailFromFd(JNIEnv *env, jclass /* clazz */,
                                                     jlong native_decoder_ptr, jint fd) {
    Decoder *decoder = reinterpret_cast<Decoder *>(native_decoder_ptr);
    auto jniInputStream = FileDescriptorInputSource(env, fd);

    return decoder->DecodeJxlThumbnail(env, jniInputStream);

}

extern "C" JNIEXPORT jobject JNICALL
Java_fr_oupson_libjxl_JxlDecoder_loadThumbnailFromInputStream(JNIEnv *env, jclass /* clazz */,
                                                              jlong native_decoder_ptr,
                                                              jobject input_stream) {
    Decoder *decoder = reinterpret_cast<Decoder *>(native_decoder_ptr);
    auto jniInputStream = JniInputStream(env, input_stream);

    return decoder->DecodeJxlThumbnail(env, jniInputStream);
}