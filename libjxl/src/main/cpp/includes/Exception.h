//
// Created by oupson on 15/01/2023.
//

#ifndef JXLVIEWER_EXCEPTION_H
#define JXLVIEWER_EXCEPTION_H

#include <jni.h>

#define DECODER_FAILED_ERROR (0)
#define ICC_PROFILE_ERROR (1)
#define METHOD_CALL_FAILED_ERROR (2)
#define NEED_MORE_INPUT_ERROR (3)

namespace jxlviewer {
    jint throwNewError(JNIEnv *env, int errorType);

    jint throwNewError(JNIEnv *env, int errorType, const char *message);

    inline jint throwNewError(JNIEnv *env, const char *className, const char *message) {
        jclass exClass = env->FindClass(className);
        return env->ThrowNew(exClass, message);
    }
}

#endif //JXLVIEWER_EXCEPTION_H
