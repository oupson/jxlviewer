//
// Created by oupson on 15/01/2023.
//

#include "Exception.h"

namespace jxlviewer {
    jint throwNewError(JNIEnv *env, int errorType) {
        jclass exClass = env->FindClass("fr/oupson/libjxl/exceptions/DecodeError");
        if (env->ExceptionCheck()) {
            return -1;
        }

        jmethodID createClassMethod = env->GetMethodID(exClass, "<init>", "(I)V");

        jobject obj = env->NewObject(exClass, createClassMethod, errorType);
        return env->Throw((jthrowable) obj);
    }

    jint throwNewError(JNIEnv *env, int errorType, const char *message) {
        jclass exClass = env->FindClass("fr/oupson/libjxl/exceptions/DecodeError");
        if (env->ExceptionCheck()) {
            return -1;
        }

        jmethodID createClassMethod = env->GetMethodID(exClass, "<init>", "(ILjava/lang/String;)V");

        jstring javaMessage = env->NewStringUTF(message);

        jobject obj = env->NewObject(exClass, createClassMethod, errorType, javaMessage);
        return env->Throw((jthrowable) obj);
    }
}
