//
// Created by oupson on 21/12/2022.
//

#ifndef JXLVIEWER_ICCPROFILE_H
#define JXLVIEWER_ICCPROFILE_H

#include <jxl/decode.h>

#include <skcms.h>
#include <jni.h>

class ICCProfile {
private:
    uint8_t *icc_buf = nullptr;
    skcms_ICCProfile icc = {};

public:
    ICCProfile() noexcept;

    ~ICCProfile();

    bool parse(JNIEnv *env, JxlDecoder *dec) noexcept;

    bool transform(void *bitmap_buffer, size_t width, size_t height, bool alpha_premultiplied) const noexcept;
};

#endif //JXLVIEWER_ICCPROFILE_H
