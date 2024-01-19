package fr.oupson.libjxl;

import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.os.ParcelFileDescriptor;

import java.io.InputStream;

import fr.oupson.libjxl.exceptions.DecodeError;

/**
 * This class can be used to load JPEG XL files.
 * <p>
 * You can call static methods which will retrieve a global instance or you can call class methods on an already initialized instance.
 */
public class JxlDecoder {
    static {
        System.loadLibrary("jxlreader");
    }

    private static JxlDecoder decoder = null;

    private long nativeDecoderPtr;

    /**
     * Create a new instance of a decoder.
     *
     * @throws ClassNotFoundException If the native side failed to find the classes used to load the file.
     */
    JxlDecoder() throws ClassNotFoundException {
        this.nativeDecoderPtr = getNativeDecoderPtr();
    }

    @Override
    protected void finalize() throws Throwable {
        freeNativeDecoderPtr(this.nativeDecoderPtr);
        this.nativeDecoderPtr = 0L;
        super.finalize();
    }

    /**
     * Get an instance of the decoder, with native part initialized.
     *
     * @return [JxlDecoder] a initialized instance.
     * @throws ClassNotFoundException If the native side failed to find the classes used to load the file.
     */
    public static synchronized JxlDecoder getInstance() throws ClassNotFoundException {
        if (decoder == null) {
            decoder = new JxlDecoder();
        }

        return decoder;
    }

    /**
     * Decode a full image from an inputStream.
     * <p/>
     * Always return an AnimationDrawable, even if the image is not an animation.
     * This AnimationDrawable is made of {@link android.graphics.drawable.BitmapDrawable}.
     *
     * @param inputStream The InputStream to read the input from.
     * @return The decoded image. Don't forget to call start on it.
     * @throws OutOfMemoryError If there is not enough memory to read the image.
     * @throws DecodeError      If the image is somehow invalid.
     */
    public AnimationDrawable decodeImage(InputStream inputStream) throws OutOfMemoryError, DecodeError {
        return loadJxlFromInputStream(this.nativeDecoderPtr, inputStream);
    }

    /**
     * Decode a full image from a parcel file descriptor.
     * <p/>
     * Always return an AnimationDrawable, even if the image is not an animation.
     * This AnimationDrawable is made of {@link android.graphics.drawable.BitmapDrawable}.
     *
     * @param fileDescriptor The ParcelFileDescriptor to read the input from.
     * @return The decoded image. Don't forget to call start on it.
     * @throws OutOfMemoryError If there is not enough memory to read the image.
     * @throws DecodeError      If the image is somehow invalid.
     */
    public AnimationDrawable decodeImage(ParcelFileDescriptor fileDescriptor) throws OutOfMemoryError, DecodeError {
        return loadJxlFromFd(this.nativeDecoderPtr, fileDescriptor.getFd());
    }

    /**
     * Decode a thumbnail of an image from an inputStream.
     *
     * @param inputStream The InputStream to read the input from.
     * @return A partial image if possible, or the whole image as a fallback.
     * @throws OutOfMemoryError If there is not enough memory to read the thumbnail.
     * @throws DecodeError      If the image is somehow invalid.
     */
    public Bitmap decodeThumbnail(InputStream inputStream) throws OutOfMemoryError, DecodeError {
        return loadThumbnailFromInputStream(this.nativeDecoderPtr, inputStream);
    }

    /**
     * Decode a thumbnail of a parcel file descriptor.
     *
     * @param fileDescriptor The ParcelFileDescriptor to read the input from.
     * @return A partial image if possible, or the whole image as a fallback.
     * @throws OutOfMemoryError If there is not enough memory to read the thumbnail.
     * @throws DecodeError      If the image is somehow invalid.
     */
    public Bitmap decodeThumbnail(ParcelFileDescriptor fileDescriptor) throws OutOfMemoryError, DecodeError {
        return loadThumbnailFromFd(this.nativeDecoderPtr, fileDescriptor.getFd());
    }

    /**
     * Decode a full image from an inputStream.
     * <p/>
     * Always return an AnimationDrawable, even if the image is not an animation.
     * This AnimationDrawable is made of {@link android.graphics.drawable.BitmapDrawable}.
     *
     * @param inputStream The InputStream to read the input from.
     * @return The decoded image. Don't forget to call start on it.
     * @throws OutOfMemoryError       If there is not enough memory to read the image.
     * @throws DecodeError            If the image is somehow invalid.
     * @throws ClassNotFoundException If the native side failed to find the classes used to load the file.
     */
    public static AnimationDrawable loadJxl(InputStream inputStream) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return JxlDecoder.getInstance().decodeImage(inputStream);
    }

    /**
     * Decode a full image from an inputStream.
     * <p/>
     * Always return an AnimationDrawable, even if the image is not an animation.
     * This AnimationDrawable is made of {@link android.graphics.drawable.BitmapDrawable}.
     *
     * @param fileDescriptor The ParcelFileDescriptor to read the input from.
     * @return The decoded image. Don't forget to call start on it.
     * @throws OutOfMemoryError       If there is not enough memory to read the image.
     * @throws DecodeError            If the image is somehow invalid.
     * @throws ClassNotFoundException If the native side failed to find the classes used to load the file.
     */
    public static AnimationDrawable loadJxl(ParcelFileDescriptor fileDescriptor) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return JxlDecoder.getInstance().decodeImage(fileDescriptor);
    }

    /**
     * Decode a thumbnail of a parcel file descriptor.
     *
     * @param inputStream The InputStream to read the input from.
     * @return A partial image if possible, or the whole image as a fallback.
     * @throws OutOfMemoryError       If there is not enough memory to read the thumbnail.
     * @throws DecodeError            If the image is somehow invalid.
     * @throws ClassNotFoundException If the native side failed to find the classes used to load the file.
     */
    public static Bitmap loadThumbnail(InputStream inputStream) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return JxlDecoder.getInstance().decodeThumbnail(inputStream);
    }

    /**
     * Decode a thumbnail of a parcel file descriptor.
     *
     * @param fileDescriptor The ParcelFileDescriptor to read the input from.
     * @return A partial image if possible, or the whole image as a fallback.
     * @throws OutOfMemoryError       If there is not enough memory to read the thumbnail.
     * @throws DecodeError            If the image is somehow invalid.
     * @throws ClassNotFoundException If the native side failed to find the classes used to load the file.
     */
    public static Bitmap loadThumbnail(ParcelFileDescriptor fileDescriptor) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return JxlDecoder.getInstance().decodeThumbnail(fileDescriptor);
    }

    private static native long getNativeDecoderPtr() throws ClassNotFoundException;

    private static native void freeNativeDecoderPtr(long nativeDecoderPtr);

    private static native AnimationDrawable loadJxlFromInputStream(long nativeDecoderPtr, InputStream inputStream) throws OutOfMemoryError, DecodeError;

    private static native AnimationDrawable loadJxlFromFd(long nativeDecoderPtr, int fd) throws OutOfMemoryError, DecodeError;

    private static native Bitmap loadThumbnailFromInputStream(long nativeDecoderPtr, InputStream inputStream) throws OutOfMemoryError, DecodeError;

    private static native Bitmap loadThumbnailFromFd(long nativeDecoderPtr, int fd) throws OutOfMemoryError, DecodeError;
}
