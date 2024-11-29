#ifndef JXLVIEWER_DECODER_H
#define JXLVIEWER_DECODER_H

#include <jni.h>

#include "InputSource.h"
#include "Options.h"

class Decoder {
public:
    explicit Decoder(JNIEnv *env);

    ~Decoder();

    jobject DecodeJxl(JNIEnv *env, InputSource &source, Options* options);

    jobject DecodeJxlThumbnail(JNIEnv *env, InputSource &source);

private:
    JavaVM *vm;

    jclass drawableClass;
    jmethodID drawableMethodID;
    jmethodID addDrawableMethodID;

    jclass bitmapDrawableClass;
    jmethodID bitmapDrawableMethodID;

    jclass bitmapClass;
    jmethodID createBitmapMethodId;

    jobject bitmapConfigRgbaU8;
    jobject bitmapConfigRgbaF16;
};

#endif //JXLVIEWER_DECODER_H
