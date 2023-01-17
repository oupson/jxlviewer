package fr.oupson.libjxl;

import android.graphics.drawable.AnimationDrawable;

import fr.oupson.libjxl.exceptions.DecodeError;

public class JxlDecoder {
    public static native AnimationDrawable loadJxl(byte[] data) throws OutOfMemoryError, DecodeError, ClassNotFoundException;

    static {
        System.loadLibrary("jxlreader");
    }
}
