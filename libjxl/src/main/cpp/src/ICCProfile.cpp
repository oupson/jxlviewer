//
// Created by oupson on 21/12/2022.
//

#include "ICCProfile.h"
#include "jxlviewer_consts.h"

#include <jxl/decode.h>
#include <cstdlib>
#include <android/log.h>


ICCProfile::ICCProfile() noexcept = default;

ICCProfile::~ICCProfile() {
    if (this->icc_buf != nullptr) {
        free(this->icc_buf);
        this->icc_buf = nullptr;
    }
}

bool ICCProfile::parse(JxlDecoder *dec) noexcept {
    size_t icc_size;
    if (JXL_DEC_SUCCESS != JxlDecoderGetICCProfileSize(
            dec,
            nullptr, // UNUSED
            JXL_COLOR_PROFILE_TARGET_DATA, &icc_size)) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "JxlDecoderGetICCProfileSize failed");
        return false;
    }

    this->icc_buf = (uint8_t *) malloc(icc_size * sizeof(uint8_t));
    if (this->icc_buf == nullptr && icc_size != 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "Failed to allocate memory for icc profile");
        return false;
    }

    if (JXL_DEC_SUCCESS !=
        JxlDecoderGetColorAsICCProfile(dec,
                                       nullptr, // UNUSED
                                       JXL_COLOR_PROFILE_TARGET_DATA,
                                       icc_buf, icc_size)) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "JxlDecoderGetColorAsICCProfile failed");

        return false;
    }

    if (!skcms_Parse(icc_buf, icc_size,
                     &icc)) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "Invalid ICC profile from JXL image decoder");
        return false;
    }

    return true;
}

bool ICCProfile::transform(void *bitmap_buffer, size_t width, size_t height,
                           bool alpha_premultiplied) const noexcept {
    return skcms_Transform(
            (void *) bitmap_buffer,
            skcms_PixelFormat_RGBA_8888,
            alpha_premultiplied ? skcms_AlphaFormat_PremulAsEncoded
                                : skcms_AlphaFormat_Unpremul,
            &this->icc,
            (void *) bitmap_buffer,
            skcms_PixelFormat_RGBA_8888,
            skcms_AlphaFormat_PremulAsEncoded,// Android need images with alpha to be premultiplied, otherwise it produce strange results.
            skcms_sRGB_profile(),
            width * height
    );
}