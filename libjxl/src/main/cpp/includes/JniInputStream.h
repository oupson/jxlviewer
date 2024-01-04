//
// Created by oupso on 04/01/2024.
//

#ifndef JXLVIEWER_JNIINPUTSTREAM_H
#define JXLVIEWER_JNIINPUTSTREAM_H

#include "InputSource.h"
#include <jni.h>

#define BUFFER_SIZE (4096)

class JniInputStream : public InputSource {
public:
    JniInputStream(JNIEnv *env, jobject inputStream) : env(env), inputStream(inputStream),
                                                       sizeRead(0) {
        jclass inputStream_Clazz = env->FindClass("java/io/InputStream");
        this->readMethodId = env->GetMethodID(inputStream_Clazz, "read", "([BII)I");
        this->javaByteArray = env->NewByteArray(BUFFER_SIZE);
    }

    ~JniInputStream() {
        env->DeleteLocalRef(this->javaByteArray);
    }

    // TODO: Improve buffering
    size_t read(uint8_t *buffer, size_t size) override {
        if (this->sizeRead > 0) {
            return readFromBuffer(buffer, size);
        } else {
            this->sizeRead = env->CallIntMethod(this->inputStream, this->readMethodId,
                                                javaByteArray, 0, BUFFER_SIZE);
            this->offset = 0;
            return readFromBuffer(buffer, size);
        }
    }

private:
    JNIEnv *env;
    jobject inputStream;
    jmethodID readMethodId;
    jbyteArray javaByteArray;
    size_t sizeRead;
    size_t offset;

    size_t readFromBuffer(const uint8_t *buffer, size_t size) {
        auto res = std::min(size, sizeRead - offset);
        env->GetByteArrayRegion(javaByteArray, offset,
                                res,
                                (jbyte *) buffer);
        if (res + offset == sizeRead) {
            sizeRead = 0;
            offset = 0;
        } else {
            offset += res;
        }
        return res;
    }
};

#endif //JXLVIEWER_JNIINPUTSTREAM_H