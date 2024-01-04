//
// Created by oupso on 04/01/2024.
//

#ifndef JXLVIEWER_JNIINPUTSTREAM_H
#define JXLVIEWER_JNIINPUTSTREAM_H

#include "InputSource.h"
#include <jni.h>

#define BUFFER_SIZE (4096)

/**
 * @class JniInputStream
 * @brief An implementation of InputSource using Java Native Interface (JNI) to read from a Java InputStream.
 *
 * This class extends the InputSource class and provides functionality to read data from a Java InputStream
 * using the Java Native Interface (JNI). It overrides the read method to efficiently read data from the
 * underlying Java InputStream into a provided buffer.
 */
class JniInputStream : public InputSource {
public:
    /**
     * @brief Constructor for JniInputStream.
     * @param env The JNI environment pointer.
     * @param inputStream The Java InputStream object.
     *
     * This constructor initializes the JniInputStream with the JNI environment pointer and the Java InputStream object.
     * It also sets up the JNI method ID for the read method and allocates a Java byte array for buffering.
     */
    JniInputStream(JNIEnv *env, jobject inputStream) : env(env), inputStream(inputStream),
                                                       sizeRead(0) {
        jclass inputStream_Clazz = env->FindClass("java/io/InputStream");
        this->readMethodId = env->GetMethodID(inputStream_Clazz, "read", "([BII)I");
        this->javaByteArray = env->NewByteArray(BUFFER_SIZE);
    }

    /**
    * @brief Destructor for JniInputStream.
    *
    * Cleans up resources, including deleting the allocated Java byte array.
    */
    ~JniInputStream() {
        env->DeleteLocalRef(this->javaByteArray);
    }

    /**
     * @brief Reads data from the Java InputStream into the provided buffer.
     * @param buffer The buffer to read data into.
     * @param size The number of bytes to read.
     * @return The total number of bytes read, -1 if EOF or INT32_MIN if error with JNI error set correctly.
     *
     * This method reads data from the Java InputStream into the specified buffer.
     * It efficiently manages the reading process, handling buffering and JNI calls.
     */
    int32_t read(uint8_t *buffer, size_t size) override {
        ssize_t totalRead = (this->sizeRead > 0) ? readFromBuffer(buffer, size) : this->sizeRead;

        while (totalRead < size) {
            this->sizeRead = env->CallIntMethod(this->inputStream, this->readMethodId,
                                                javaByteArray, 0, BUFFER_SIZE);
            if (env->ExceptionCheck()) {
                return INT32_MIN;
            } else {
                this->offset = 0;
                if (this->sizeRead >= 0) {
                    totalRead += readFromBuffer(buffer + totalRead, size - totalRead);
                } else {
                    break;
                }
            }
        }

        return totalRead;
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