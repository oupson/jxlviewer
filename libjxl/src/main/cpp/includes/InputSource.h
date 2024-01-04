//
// Created by oupso on 04/01/2024.
//

#ifndef JXLVIEWER_INPUTSOURCE_H
#define JXLVIEWER_INPUTSOURCE_H

class InputSource {
public:
    virtual size_t read(uint8_t *buffer, size_t size) = 0;
};

#endif //JXLVIEWER_INPUTSOURCE_H
