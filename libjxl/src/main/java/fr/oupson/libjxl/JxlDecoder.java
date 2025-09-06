package fr.oupson.libjxl;

import android.graphics.Bitmap;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.InputStream;

import fr.oupson.libjxl.exceptions.ConfigException;
import fr.oupson.libjxl.exceptions.DecodeError;

// TODO: Nullable annotations
public class JxlDecoder {
    private static JxlDecoder decoder = null;

    static {
        System.loadLibrary("jxlreader");
    }

    private long nativeDecoderPtr;

    JxlDecoder() {
        this.nativeDecoderPtr = getNativeDecoderPtr();
    }

    public static synchronized JxlDecoder getInstance() {
        if (decoder == null) {
            decoder = new JxlDecoder();
        }

        return decoder;
    }

    public static int loadJxl(InputStream inputStream, Options options, Callback callback) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return JxlDecoder.getInstance().decodeImage(inputStream, options, callback);
    }

    public static int loadJxl(ParcelFileDescriptor fileDescriptor, Options options, Callback callback) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return JxlDecoder.getInstance().decodeImage(fileDescriptor, options, callback);
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

    public int decodeImage(InputStream inputStream, Options options, Callback callback) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return loadJxlFromInputStream(this.nativeDecoderPtr, inputStream, (options == null) ? 0 : options.ptr, callback);
    }

    public int decodeImage(ParcelFileDescriptor fileDescriptor, Options options, Callback callback) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
       return  loadJxlFromFd(this.nativeDecoderPtr, fileDescriptor.getFd(), (options == null) ? 0 : options.ptr, callback);
    }

    public interface Callback {
        boolean onHeaderDecoded(int width, int height, int intrinsicWidth, int intrinsicHeight, boolean isAnimated, int orientation);

        boolean onProgressiveFrame(Bitmap btm);

        boolean onFrameDecoded(int duration, Bitmap btm);
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
        public Options setFormat(Bitmap.Config config) throws ConfigException {
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

        // TODO: Document.
        public boolean getDecodeProgressive() {
            return getDecodeProgressive(this.ptr);
        }

        public Options setDecodeProgressive(boolean decodeProgressive) {
            setDecodeProgressive(this.ptr, decodeProgressive);
            return this;
        }

        public boolean getDecodeFrames() {
            return getDecodeFrames(this.ptr);
        }

        public Options setDecodeFrames(boolean decodeFrames) {
            setDecodeFrames(this.ptr, decodeFrames);
            return this;
        }

    }
}
