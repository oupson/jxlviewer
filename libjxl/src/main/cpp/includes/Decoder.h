#ifndef JXLVIEWER_DECODER_H
#define JXLVIEWER_DECODER_H

#include <jni.h>

#include "InputSource.h"
#include "Options.h"

class Decoder {
public:
    explicit Decoder(JNIEnv *env);

    ~Decoder();

    int DecodeJxl(JNIEnv *env, InputSource &source, Options *options, jobject callback);

private:
    JavaVM *vm;

    jclass bitmapClass;
    jmethodID createBitmapMethodId;

    jobject bitmapConfigRgbaU8;
    jobject bitmapConfigRgbaF16;

    jclass callbackClass;
    jmethodID callbackOnHeaderDecoded;
    jmethodID callbackOnProgressiveFrame;
    jmethodID callbackOnFrameDecoded;
};

#endif //JXLVIEWER_DECODER_H
