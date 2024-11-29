package fr.oupson.libjxl;

import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.InputStream;

import fr.oupson.libjxl.exceptions.ConfigException;
import fr.oupson.libjxl.exceptions.DecodeError;

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

    public static AnimationDrawable loadJxl(InputStream inputStream, Options options) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return JxlDecoder.getInstance().decodeImage(inputStream, options);
    }

    public static AnimationDrawable loadJxl(ParcelFileDescriptor fileDescriptor, Options options) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return JxlDecoder.getInstance().decodeImage(fileDescriptor, options);
    }

    public static Bitmap loadThumbnail(InputStream inputStream) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return JxlDecoder.getInstance().decodeThumbnail(inputStream);
    }

    public static Bitmap loadThumbnail(ParcelFileDescriptor fileDescriptor) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return JxlDecoder.getInstance().decodeThumbnail(fileDescriptor);
    }

    private static native long getNativeDecoderPtr();

    private static native void freeNativeDecoderPtr(long nativeDecoderPtr);

    private static native AnimationDrawable loadJxlFromInputStream(long nativeDecoderPtr, InputStream inputStream, long options) throws OutOfMemoryError, DecodeError, ClassNotFoundException;

    private static native AnimationDrawable loadJxlFromFd(long nativeDecoderPtr, int fd, long options) throws OutOfMemoryError, DecodeError, ClassNotFoundException;

    private static native Bitmap loadThumbnailFromInputStream(long nativeDecoderPtr, InputStream inputStream) throws OutOfMemoryError, DecodeError, ClassNotFoundException;

    private static native Bitmap loadThumbnailFromFd(long nativeDecoderPtr, int fd) throws OutOfMemoryError, DecodeError, ClassNotFoundException;

    @Override
    protected void finalize() throws Throwable {
        freeNativeDecoderPtr(this.nativeDecoderPtr);
        this.nativeDecoderPtr = 0L;
        super.finalize();
    }

    public AnimationDrawable decodeImage(InputStream inputStream, Options options) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return loadJxlFromInputStream(this.nativeDecoderPtr, inputStream, (options == null) ? 0 : options.ptr);
    }

    public AnimationDrawable decodeImage(ParcelFileDescriptor fileDescriptor, Options options) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return loadJxlFromFd(this.nativeDecoderPtr, fileDescriptor.getFd(), (options == null) ? 0 : options.ptr);
    }

    public Bitmap decodeThumbnail(InputStream inputStream) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return loadThumbnailFromInputStream(this.nativeDecoderPtr, inputStream);
    }

    public Bitmap decodeThumbnail(ParcelFileDescriptor fileDescriptor) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return loadThumbnailFromFd(this.nativeDecoderPtr, fileDescriptor.getFd());
    }

    public static class Options implements AutoCloseable {
        static {
            System.loadLibrary("jxlreader");
        }

        private long ptr = alloc();

        private static native long alloc();

        private static native void free(long ptr);

        private static native int getBitmapConfig(long ptr);

        private static native void setBitmapConfig(long ptr, int format);

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

        public void setFormat(Bitmap.Config config) throws ConfigException {
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
        }
    }
}
