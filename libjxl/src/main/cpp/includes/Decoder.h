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

    // createBitmap(int, int, Config, boolean, ColorSpace) - API 28+ overload, used
    // to create HDR bitmaps already tagged with their color space. nullptr below
    // API 28, in which case the HDR path degrades gracefully.
    jmethodID createBitmapWithColorSpaceId; // (IILandroid/graphics/Bitmap$Config;ZLandroid/graphics/ColorSpace;)Landroid/graphics/Bitmap;

    jobject bitmapConfigRgbaU8;
    jobject bitmapConfigRgbaF16;

    // ColorSpace.Named.BT2020_PQ (BT.2020 + PQ, 10000 nit reference, ICC parametric),
    // resolved once and held as a global ref. Bitmap.setColorSpace() is deliberately
    // avoided: the platform rejects re-tagging an sRGB bitmap with a different
    // transfer function, so the space is applied at creation time.
    jclass colorSpaceClass;           // android/graphics/ColorSpace
    jmethodID colorSpaceGetNamedId;   // get(Named):Landroid/graphics/ColorSpace;
    jobject bt2020NamedField;         // ColorSpace$Named.BT2020_PQ (global ref)
    bool hdrColorSpaceReady;          // false -> HDR degrades to the plain SDR route

    jclass callbackClass;
    jmethodID callbackOnHeaderDecoded;
    jmethodID callbackOnProgressiveFrame;
    jmethodID callbackOnFrameDecoded;
};

#endif //JXLVIEWER_DECODER_H
