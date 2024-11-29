//
// Created by oupson on 07/02/2023.
//

#ifndef JXLVIEWER_IMAGEOUTCALLBACKDATA_H
#define JXLVIEWER_IMAGEOUTCALLBACKDATA_H

#include <cinttypes>
#include <cstdlib>

#include <skcms.h>
#include <jni.h>
#include "jxl/decode.h"
#include <fstream>
#include "Exception.h"

class ImageOutCallbackData {
private:
    size_t width;
    size_t height;

    bool is_alpha_premultiplied;

    uint8_t *image_buffer;

    uint8_t *icc_buffer;
    skcms_ICCProfile icc = {};

    skcms_PixelFormat sourcePixelFormat;
    skcms_PixelFormat outputPixelFormat;
    uint8_t sampleSize;

public:
    explicit ImageOutCallbackData(BitmapConfig format) : ImageOutCallbackData(format,
                                                                              skcms_PixelFormat_RGBA_hhhh) {
    }

    ImageOutCallbackData(BitmapConfig format, skcms_PixelFormat sourcePixelFormat) : width(0),
                                                                                     height(0),
                                                                                     is_alpha_premultiplied(
                                                                                             false),
                                                                                     image_buffer(
                                                                                             nullptr),
                                                                                     icc_buffer(
                                                                                             nullptr),
                                                                                     sourcePixelFormat(
                                                                                             sourcePixelFormat) {
        this->outputPixelFormat = (format == BitmapConfig::RGBA_8888) ? skcms_PixelFormat_RGBA_8888
                                                                      : skcms_PixelFormat_RGBA_hhhh;
        this->sampleSize = (format == BitmapConfig::RGBA_8888) ? 4 : 8;
    }

    ~ImageOutCallbackData() {
        if (icc_buffer != nullptr) {
            free(icc_buffer);
            icc_buffer = nullptr;
        }
    }

    size_t getWidth() const {
        return width;
    }

    size_t getHeight() const {
        return height;
    }

    void setSize(size_t image_width, size_t image_height) {
        this->width = image_width;
        this->height = image_height;
    }

    void setIsAlphaPremultiplied(bool alpha_premultiplied) {
        this->is_alpha_premultiplied = alpha_premultiplied;
    }

    uint8_t **getImageBufferPtr() {
        return &this->image_buffer;
    }

    bool parseICCProfile(JNIEnv *env, JxlDecoder *dec) noexcept {
        size_t icc_size;
        if (JXL_DEC_SUCCESS !=
            JxlDecoderGetICCProfileSize(dec, JXL_COLOR_PROFILE_TARGET_DATA, &icc_size)) {
            jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR, "JxlDecoderGetICCProfileSize");
            return false;
        }

        this->icc_buffer = (uint8_t *) malloc(icc_size * sizeof(uint8_t));
        if (this->icc_buffer == nullptr && icc_size != 0) {
            jxlviewer::throwNewError(env, "java/lang/OutOfMemoryError",
                                     "Failed to allocate memory for icc profile");
            return false;
        }

        if (JXL_DEC_SUCCESS !=
            JxlDecoderGetColorAsICCProfile(dec, JXL_COLOR_PROFILE_TARGET_DATA, this->icc_buffer,
                                           icc_size)) {
            jxlviewer::throwNewError(env, METHOD_CALL_FAILED_ERROR,
                                     "JxlDecoderGetColorAsICCProfile");
            return false;
        }

        if (!skcms_Parse(this->icc_buffer, icc_size, &icc)) {
            jxlviewer::throwNewError(env, ICC_PROFILE_ERROR,
                                     "Invalid ICC profile from JXL image decoder");
            return false;
        }

        return true;
    }

    void imageDataFromCallback(const void *pixels, size_t x, size_t y, size_t num_pixels) {
        skcms_Transform(pixels, this->sourcePixelFormat,
                        this->is_alpha_premultiplied ? skcms_AlphaFormat_PremulAsEncoded
                                                     : skcms_AlphaFormat_Unpremul, &this->icc,
                        this->image_buffer + ((y * this->width + x) * (this->sampleSize)), this->outputPixelFormat,
                        skcms_AlphaFormat_PremulAsEncoded,// Android need images with alpha to be premultiplied, otherwise it produce strange results.
                        skcms_sRGB_profile(), num_pixels);
    }
};

void jxl_viewer_image_out_callback(void *opaque_data, size_t x, size_t y, size_t num_pixels,
                                   const void *pixels) {
    auto *data = (ImageOutCallbackData *) opaque_data;
    data->imageDataFromCallback(pixels, x, y, num_pixels);
}


#endif //JXLVIEWER_IMAGEOUTCALLBACKDATA_H
