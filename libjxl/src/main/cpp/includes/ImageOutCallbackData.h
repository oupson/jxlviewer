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

    // HDR passthrough: when the decoder outputs BT.2020 + PQ F16 code values,
    // copy them verbatim into the RGBA_F16 bitmap instead of running
    // skcms_Transform, which would re-encode the values.
    bool hdr_passthrough = false;
    int  src_channels = 3;           // decoder output channels (RGB=3, RGBA=4)
    int  src_bytes_per_channel = 2;  // F16 = 2
    int  dst_channels = 4;          // RGBA_F16 bitmap channels
    int  dst_bytes_per_channel = 2;

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
        this->dst_channels = 4;
        this->dst_bytes_per_channel = (format == BitmapConfig::RGBA_8888) ? 1 : 2;
    }

    // Derives the decoder output channel count and bytes per channel from the
    // source pixel format (RGB_hhh = 3ch, RGBA_hhhh = 4ch, F16 = 2B).
    void setSourcePixelFormat(skcms_PixelFormat pixelFormat) {
        this->sourcePixelFormat = pixelFormat;
        switch (pixelFormat) {
            case skcms_PixelFormat_RGB_888:  this->src_channels = 3; this->src_bytes_per_channel = 1; break;
            case skcms_PixelFormat_RGBA_8888: this->src_channels = 4; this->src_bytes_per_channel = 1; break;
            case skcms_PixelFormat_RGB_hhh:   this->src_channels = 3; this->src_bytes_per_channel = 2; break;
            case skcms_PixelFormat_RGBA_hhhh: this->src_channels = 4; this->src_bytes_per_channel = 2; break;
            default: break;
        }
    }

    void setHdrPassthrough(bool b) { this->hdr_passthrough = b; }
    bool isHdrPassthrough() const { return this->hdr_passthrough; }

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
        // HDR passthrough: the decoder already emitted BT.2020 + PQ F16 code
        // values, so copy them verbatim into the RGBA_F16 bitmap.
        if (this->hdr_passthrough) {
            uint8_t *dst = this->image_buffer + ((y * this->width + x) * this->dst_channels * this->dst_bytes_per_channel);
            const uint8_t *src = (const uint8_t *) pixels;
            const int sc = this->src_channels;
            const int sbc = this->src_bytes_per_channel;
            const int dbc = this->dst_channels;
            const int dsc = this->dst_bytes_per_channel;
            for (size_t p = 0; p < num_pixels; p++) {
                uint8_t *d = dst + p * dbc * dsc;
                const uint8_t *s = src + p * sc * sbc;
                for (int c = 0; c < sc; c++) {
                    for (int b = 0; b < sbc; b++) d[c * dsc + b] = s[c * sbc + b];
                }
                // Pad alpha = 1.0f (F16 half 0x3C00, little-endian 00 3C)
                if (sc < dbc) {
                    for (int c = sc; c < dbc; c++) {
                        d[c * dsc + 0] = 0x00;
                        d[c * dsc + 1] = 0x3C;
                    }
                }
            }
            return;
        }
        skcms_Transform(pixels, this->sourcePixelFormat,
                        this->is_alpha_premultiplied ? skcms_AlphaFormat_PremulAsEncoded
                                                     : skcms_AlphaFormat_Unpremul, &this->icc,
                        this->image_buffer + ((y * this->width + x) * (this->sampleSize)),
                        this->outputPixelFormat,
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
