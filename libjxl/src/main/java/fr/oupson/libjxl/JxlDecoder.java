package fr.oupson.libjxl;

import android.graphics.drawable.AnimationDrawable;

public class JxlDecoder {
    public static native AnimationDrawable loadJxl( byte[] data);

    static {
        System.loadLibrary("jxlreader");
    }
}
