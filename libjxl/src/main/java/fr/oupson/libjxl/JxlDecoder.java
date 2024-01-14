package fr.oupson.libjxl;

import android.graphics.drawable.AnimationDrawable;
import android.os.ParcelFileDescriptor;

import java.io.InputStream;

import fr.oupson.libjxl.exceptions.DecodeError;

public class JxlDecoder {
    static {
        System.loadLibrary("jxlreader");
    }

    private static JxlDecoder decoder = null;

    private long nativeDecoderPtr = 0L;

    JxlDecoder() {
        this.nativeDecoderPtr = getNativeDecoderPtr();
    }

    @Override
    protected void finalize() throws Throwable {
        freeNativeDecoderPtr(this.nativeDecoderPtr);
        this.nativeDecoderPtr = 0L;
        super.finalize();
    }

    public static synchronized JxlDecoder getInstance() {
        if (decoder == null) {
            decoder = new JxlDecoder();
        }

        return decoder;
    }

    public AnimationDrawable decodeImage(InputStream inputStream) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return loadJxlFromInputStream(this.nativeDecoderPtr, inputStream, false);
    }

    public AnimationDrawable decodeImage(ParcelFileDescriptor fileDescriptor) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return loadJxlFromFd(this.nativeDecoderPtr, fileDescriptor.getFd(), false);
    }

    public AnimationDrawable decodeThumbnail(InputStream inputStream) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return loadJxlFromInputStream(this.nativeDecoderPtr, inputStream, true);
    }

    public AnimationDrawable decodeThumbnail(ParcelFileDescriptor fileDescriptor) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return loadJxlFromFd(this.nativeDecoderPtr, fileDescriptor.getFd(), true);
    }

    public static AnimationDrawable loadJxl(InputStream inputStream) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return JxlDecoder.getInstance().decodeImage(inputStream);
    }

    public static AnimationDrawable loadJxl(ParcelFileDescriptor fileDescriptor) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return JxlDecoder.getInstance().decodeImage(fileDescriptor);
    }

    public static AnimationDrawable loadThumbnail(InputStream inputStream) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return JxlDecoder.getInstance().decodeThumbnail(inputStream);
    }

    public static AnimationDrawable loadThumbnail(ParcelFileDescriptor fileDescriptor) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return JxlDecoder.getInstance().decodeThumbnail(fileDescriptor);
    }

    private static native long getNativeDecoderPtr();

    private static native void freeNativeDecoderPtr(long nativeDecoderPtr);

    private static native AnimationDrawable loadJxlFromInputStream(long nativeDecoderPtr, InputStream inputStream, boolean decodeAsThumbnail) throws OutOfMemoryError, DecodeError, ClassNotFoundException;

    private static native AnimationDrawable loadJxlFromFd(long nativeDecoderPtr, int fd, boolean decodeAsThumbnail) throws OutOfMemoryError, DecodeError, ClassNotFoundException;
}
