//
// Created by oupso on 04/01/2024.
//

#ifndef JXLVIEWER_FILEDESCRIPTORINPUTSOURCE_H
#define JXLVIEWER_FILEDESCRIPTORINPUTSOURCE_H

#include <unistd.h>

#include "InputSource.h"
#include "Exception.h"

inline int32_t readWithErrorHandling(JNIEnv *env, int fd, uint8_t *buffer, size_t size);

class FileDescriptorInputSource : public InputSource {
public:
    FileDescriptorInputSource(JNIEnv *env, int fd) : env(env), fd(fd) {

    }

    int32_t read(uint8_t *buffer, size_t size) override {
        return readWithErrorHandling(this->env, this->fd, buffer, size);
    }

private:
    JNIEnv *env;
    int fd;
};

inline int32_t readWithErrorHandling(JNIEnv *env, int fd, uint8_t *buffer, size_t size) {
    auto n = read(fd, buffer, size);
    if (n > 0) {
        return n;
    } else if (n == 0) {
        return -1;
    } else {
        auto error = strerror(errno);
        jxlviewer::throwNewError(env, "java/io/IOException", error);
        return INT32_MIN;
    }
}

#endif //JXLVIEWER_FILEDESCRIPTORINPUTSOURCE_H
