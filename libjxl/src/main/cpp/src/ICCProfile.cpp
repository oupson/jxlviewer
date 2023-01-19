//
// Created by oupson on 21/12/2022.
//

#include "ICCProfile.h"
#include "jxlviewer_consts.h"
#include "Exception.h"

#include <jxl/decode.h>
#include <cstdlib>
#include <android/log.h>
#include <jni.h>


ICCProfile::ICCProfile() noexcept = default;

ICCProfile::~ICCProfile() {
    if (this->icc_buf != nullptr) {
        free(this->icc_buf);
        this->icc_buf = nullptr;
    }
}

bool ICCProfile::parse(JNIEnv *env, JxlDecoder *dec) noexcept {
    size_t icc_size;
    if (JXL_DEC_SUCCESS != JxlDecoderGetICCProfileSize(dec, nullptr, // UNUSED
                                                       JXL_COLOR_PROFILE_TARGET_DATA, &icc_size)) {
        jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR, "JxlDecoderGetICCProfileSize");
        return false;
    }

    this->icc_buf = (uint8_t *) malloc(icc_size * sizeof(uint8_t));
    if (this->icc_buf == nullptr && icc_size != 0) {
        jxlviewer::throwNewError(env, "java/lang/OutOfMemoryError",
                                 "Failed to allocate memory for icc profile");
        return false;
    }

    if (JXL_DEC_SUCCESS != JxlDecoderGetColorAsICCProfile(dec, nullptr, // UNUSED
                                                          JXL_COLOR_PROFILE_TARGET_DATA, icc_buf,
                                                          icc_size)) {
        jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR, "JxlDecoderGetColorAsICCProfile");
        return false;
    }

    if (!skcms_Parse(icc_buf, icc_size, &icc)) {
        jxlviewer::throwNewError(env, ICC_PROFILE_ERROR,
                                 "Invalid ICC profile from JXL image decoder");
        return false;
    }

    return true;
}

bool ICCProfile::transform(void *bitmap_buffer, size_t width, size_t height,
                           bool alpha_premultiplied) const noexcept {
    return skcms_Transform((void *) bitmap_buffer, skcms_PixelFormat_RGBA_8888,
                           alpha_premultiplied ? skcms_AlphaFormat_PremulAsEncoded
                                               : skcms_AlphaFormat_Unpremul, &this->icc,
                           (void *) bitmap_buffer, skcms_PixelFormat_RGBA_8888,
                           skcms_AlphaFormat_PremulAsEncoded,// Android need images with alpha to be premultiplied, otherwise it produce strange results.
                           skcms_sRGB_profile(), width * height);
}