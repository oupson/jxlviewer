package fr.oupson.libjxl;

import android.graphics.drawable.AnimationDrawable;
import android.os.ParcelFileDescriptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import fr.oupson.libjxl.exceptions.DecodeError;

public class JxlDecoder {

    static {
        System.loadLibrary("jxlreader");
    }

    public static AnimationDrawable loadJxl(InputStream inputStream) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return loadJxlFromInputStream(inputStream);
    }

    public static AnimationDrawable loadJxl(ParcelFileDescriptor fileDescriptor) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return loadJxlFromFd(fileDescriptor.getFd());
    }

    private static native AnimationDrawable loadJxlFromInputStream(InputStream inputStream) throws OutOfMemoryError, DecodeError, ClassNotFoundException;

    private static native AnimationDrawable loadJxlFromFd(int fd) throws OutOfMemoryError, DecodeError, ClassNotFoundException;
}
