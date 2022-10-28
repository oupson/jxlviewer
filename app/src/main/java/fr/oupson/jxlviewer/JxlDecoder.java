package fr.oupson.jxlviewer;

import android.graphics.drawable.AnimationDrawable;

public class JxlDecoder {
    static {
        System.loadLibrary("jxlreader");
    }

    public native AnimationDrawable loadJxl(byte[] data);
}
