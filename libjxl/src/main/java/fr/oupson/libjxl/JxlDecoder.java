package fr.oupson.libjxl;

import android.graphics.drawable.AnimationDrawable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import fr.oupson.libjxl.exceptions.DecodeError;

public class JxlDecoder {

    static {
        System.loadLibrary("jxlreader");
    }

    public static AnimationDrawable loadJxl(byte[] bytes) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        return loadJxl(inputStream);
    }

    public static AnimationDrawable loadJxl(InputStream inputStream) throws OutOfMemoryError, DecodeError, ClassNotFoundException {
        return loadJxlFromInputStream(inputStream);
    }

    private static native void startLogIntercept();

    private static native AnimationDrawable loadJxlFromInputStream(InputStream inputStream) throws OutOfMemoryError, DecodeError, ClassNotFoundException;
}
