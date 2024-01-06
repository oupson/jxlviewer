//
// Created by oupso on 04/01/2024.
//

#ifndef JXLVIEWER_INPUTSOURCE_H
#define JXLVIEWER_INPUTSOURCE_H

#include <cinttypes>

/**
 * @class InputSource
 * @brief Abstraction over an input source.
 *
 * This represent an abstraction over a source, for example an Java InputStream or a file descriptor.
 */
class InputSource {
public:
    /**
     * @brief Reads data from the source into the provided buffer.
     * @param buffer The buffer to read data into.
     * @param size The number of bytes to read.
     * @return The total number of bytes read, -1 if EOF or INT32_MIN if error with JNI error set correctly.
     *
     * This method reads data from the source into the specified buffer.
     */
    virtual int32_t read(uint8_t *buffer, size_t size) = 0;
};

#endif //JXLVIEWER_INPUTSOURCE_H
