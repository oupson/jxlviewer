package fr.oupson.libjxl;

import android.graphics.Bitmap;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.InputStream;

import fr.oupson.libjxl.exceptions.ConfigException;
import fr.oupson.libjxl.exceptions.DecodeError;


public class JxlDecoder {
    static {
        System.loadLibrary("jxlreader");
    }

    private long nativeDecoderPtr;

    /**
     * Construct a new decoder.
     * This is a costly operation, you should reuse it.
     * <p>The decoder support parallel decoding.</p>
     */
    public JxlDecoder() {
        this.nativeDecoderPtr = getNativeDecoderPtr();
    }

    private static native long getNativeDecoderPtr();

    private static native void freeNativeDecoderPtr(long nativeDecoderPtr);

    private static native int loadJxlFromInputStream(long nativeDecoderPtr, InputStream inputStream, long options, Callback callback) throws OutOfMemoryError, DecodeError, ClassNotFoundException;

    private static native int loadJxlFromFd(long nativeDecoderPtr, int fd, long options, Callback callback) throws OutOfMemoryError, DecodeError, ClassNotFoundException;

    @Override
    protected void finalize() throws Throwable {
        freeNativeDecoderPtr(this.nativeDecoderPtr);
        this.nativeDecoderPtr = 0L;
        super.finalize();
    }

    /**
     * Decode an image from an input stream.
     * <p>For better performance you should probably use {@link JxlDecoder#decodeImage(ParcelFileDescriptor, Options, Callback)}.</p>
     *
     * @param inputStream file data.
     * @param options to customise the decoding.
     * @param callback to receive frames and other events.
     * @return number of frame decoded.
     * @throws OutOfMemoryError not enough memory for decoding.
     * @throws DecodeError file is not a valid jpeg xl file.
     * @throws ClassNotFoundException a class was not found, this should not happen.
     */
    public int decodeImage(@NonNull InputStream inputStream, @Nullable Options options, @NonNull Callback callback) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return loadJxlFromInputStream(this.nativeDecoderPtr, inputStream, (options == null) ? 0 : options.ptr, callback);
    }

    /**
     * Decode an image from an input stream.
     *
     * @param fileDescriptor file data.
     * @param options to customise the decoding.
     * @param callback to receive frames and other events.
     * @return number of frame decoded.
     * @throws OutOfMemoryError not enough memory for decoding.
     * @throws DecodeError file is not a valid jpeg xl file.
     * @throws ClassNotFoundException a class was not found, this should not happen.
     */
    public int decodeImage(@NonNull ParcelFileDescriptor fileDescriptor, @Nullable Options options, @NonNull Callback callback) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return loadJxlFromFd(this.nativeDecoderPtr, fileDescriptor.getFd(), (options == null) ? 0 : options.ptr, callback);
    }

    public interface Callback {
        /**
         * Called when frame information are obtained.
         *
         * @param width Width of the image in pixel, without applying orientation.
         * @param height Width of the image in pixel, without applying orientation.
         * @param intrinsicWidth Recommended width for displaying the image.
         * @param intrinsicHeight Recommended height for displaying the image.
         * @param isAnimated true if the image is composed of multiple visible frames.
         * @param orientation Same as {@link android.media.ExifInterface} orientation values.
         * @return true to continue decoding.
         */
        boolean onHeaderDecoded(int width, int height, int intrinsicWidth, int intrinsicHeight, boolean isAnimated, int orientation);

        /**
         * Called if progressive data are available.
         *
         * @param btm progressive data, owned by the decoder, copy if you continue decoding.
         * @return true to continue decoding.
         * @see Options#setDecodeProgressive(boolean)
         */
        boolean onProgressiveFrame(@NonNull Bitmap btm);

        /**
         * Called when frame is available.
         *
         * @param duration if animated, duration of the frame in milliseconds.
         * @param btm progressive data, owned by the decoder, copy if you continue decoding.
         * @return true to continue decoding.
         * @see Options#setDecodeFrames(boolean)
         */
        boolean onFrameDecoded(int duration, @NonNull Bitmap btm);
    }

    /**
     * Options used by {@link JxlDecoder}.
     */
    public static class Options implements AutoCloseable {
        static {
            System.loadLibrary("jxlreader");
        }

        private long ptr = alloc();

        private static native long alloc();

        private static native void free(long ptr);

        private static native int getBitmapConfig(long ptr);

        private static native void setBitmapConfig(long ptr, int format);

        private static native boolean getDecodeProgressive(long ptr);

        private static native void setDecodeProgressive(long ptr, boolean decodeProgressive);

        private static native boolean getDecodeFrames(long ptr);

        private static native void setDecodeFrames(long ptr, boolean decodeFrames);

        @Override
        public void close() throws Exception {
            if (ptr != 0L) {
                free(this.ptr);
                ptr = 0L;
            }
        }

        @Override
        protected void finalize() throws Throwable {
            this.close();
            super.finalize();
        }

        /**
         * Get the bitmap config used by the decoder.
         *
         * @return The {@link Bitmap.Config} used by the decoder.
         * @throws ConfigException If the config return unsupported value (this should not happen).
         */
        public Bitmap.Config getFormat() throws ConfigException {
            int config = getBitmapConfig(this.ptr);
            switch (config) {
                case 0:
                    return Bitmap.Config.ARGB_8888;
                case 1:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        return Bitmap.Config.RGBA_F16;
                    } else {
                        throw new ConfigException("RGBA_F16 can't be used on Android < 8");
                    }
                default:
                    throw new ConfigException(String.format("Unknown bitmap format : %d", config));
            }
        }

        /**
         * Set the bitmap config for the output bitmaps.
         * Please note that only ARGB_8888 and RGBA_F16 are supported.
         * ARGB_8888 by default.
         *
         * @param config An ARGB_8888 or RGBA_F16 {@link Bitmap.Config}.
         * @throws ConfigException When the config is not supported.
         */
        public Options setFormat(@NonNull Bitmap.Config config) throws ConfigException {
            int format = 0;
            switch (config) {
                case ARGB_8888:
                    break;
                case RGBA_F16:
                    format = 1;
                    break;
                default:
                    throw new ConfigException("Only ARGB_8888 and RGBA_F16 are supported");
            }
            setBitmapConfig(this.ptr, format);
            return this;
        }

        /**
         * Should the decoder decode progressive data and call {@link Callback#onProgressiveFrame(Bitmap)}.
         *
         * @return true then the decoder will call {@link Callback#onProgressiveFrame(Bitmap)} if progressive data are available.
         */
        public boolean getDecodeProgressive() {
            return getDecodeProgressive(this.ptr);
        }

        /**
         * Should the decoder decode progressive data and call {@link Callback#onProgressiveFrame(Bitmap)}.
         *
         * @param decodeProgressive if true then the decoder will call {@link Callback#onProgressiveFrame(Bitmap)} if progressive data are available.
         */
        public Options setDecodeProgressive(boolean decodeProgressive) {
            setDecodeProgressive(this.ptr, decodeProgressive);
            return this;
        }

        /**
         * Should the decoder decode frames and call {@link Callback#onFrameDecoded(int, Bitmap)}.
         * This can be used to quickly count number of frames or only decode progressive data.
         *
         * @return true then the decoder will call  {@link Callback#onFrameDecoded(int, Bitmap).
         */
        public boolean getDecodeFrames() {
            return getDecodeFrames(this.ptr);
        }

        /**
         * Should the decoder decode frames and call {@link Callback#onFrameDecoded(int, Bitmap)}.
         * This can be used to quickly count number of frames or only decode progressive data.
         *
         * @param decodeFrames if true then the decoder will call {@link Callback#onFrameDecoded(int, Bitmap).
         */
        public Options setDecodeFrames(boolean decodeFrames) {
            setDecodeFrames(this.ptr, decodeFrames);
            return this;
        }

    }
}
