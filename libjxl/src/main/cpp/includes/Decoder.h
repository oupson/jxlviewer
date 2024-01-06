#ifndef JXLVIEWER_DECODER_H
#define JXLVIEWER_DECODER_H

#include <jni.h>

#include "InputSource.h"

class Decoder {
public:
    Decoder(JNIEnv *env);

    ~Decoder();

    jobject DecodeJxl(JNIEnv *env, InputSource &source);

private:
    JavaVM *vm;

    jclass drawableClass;
    jmethodID drawableMethodID;
    jmethodID addDrawableMethodID;

    jclass bitmapDrawableClass;
    jmethodID bitmapDrawableMethodID;

    jclass bitmapClass;
    jmethodID createBitmapMethodId;

    jobject bitmapConfig;
};

#endif //JXLVIEWER_DECODER_H
