package fr.oupson.libjxl;

import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import fr.oupson.libjxl.exceptions.DecodeError;

public class JxlDecodeAndroidUnitTest {
    @Test
    public void decode_LogoShouldNotFail() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        InputStream input = context.getResources().getAssets().open("logo.jxl");

        byte[] content = new byte[input.available()];

        int size = input.read(content);
        input.close();

        Assert.assertEquals("Failed to read test file, invalid size", 117, size);

        try {
            AnimationDrawable result = JxlDecoder.loadJxl(content);
            Assert.assertNotNull(result);
            Assert.assertEquals("Invalid number of frames", 1, result.getNumberOfFrames());
            BitmapDrawable frame = (BitmapDrawable) result.getFrame(0);
            Assert.assertEquals("Invalid image width", 1000, frame.getBitmap().getWidth());
            Assert.assertEquals("Invalid image height", 1000, frame.getBitmap().getHeight());
        } catch (Exception e) {
            Assert.fail("Failed to read image : " + e.getMessage());
        }
    }

    @Test
    public void decode_DidiShouldNotFail() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        InputStream input = context.getResources().getAssets().open("didi.jxl");

        byte[] content = new byte[input.available()];

        int size = input.read(content);
        input.close();

        Assert.assertEquals("Failed to read test file, invalid size", 529406, size);

        try {
            AnimationDrawable result = JxlDecoder.loadJxl(content);
            Assert.assertNotNull(result);
            Assert.assertEquals("Invalid number of frames", 1, result.getNumberOfFrames());
            BitmapDrawable frame = (BitmapDrawable) result.getFrame(0);
            Assert.assertEquals("Invalid image width", 2048, frame.getBitmap().getWidth());
            Assert.assertEquals("Invalid image height", 1536, frame.getBitmap().getHeight());
        } catch (Exception e) {
            Assert.fail("Failed to read image : " + e.getMessage());
        }
    }

    @Test
    public void decode_FerrisShouldNotFail() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        InputStream input = context.getResources().getAssets().open("ferris.jxl");

        byte[] content = new byte[input.available()];

        int size = input.read(content);
        input.close();

        Assert.assertEquals("Failed to read test file, invalid size", 404955, size);

        try {
            AnimationDrawable result = JxlDecoder.loadJxl(content);
            Assert.assertNotNull(result);
            Assert.assertEquals("Invalid number of frames", 27, result.getNumberOfFrames());

            for (int i = 0; i < 27; i++) {
                BitmapDrawable frame = (BitmapDrawable) result.getFrame(i);
                Assert.assertEquals("Invalid frame width", 378, frame.getBitmap().getWidth());
                Assert.assertEquals("Invalid frame height", 300, frame.getBitmap().getHeight());
            }
        } catch (Exception e) {
            Assert.fail("Failed to read image : " + e.getMessage());
        }
    }

    @Test
    public void decode_WithoutEnoughInputShouldFail() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        InputStream input = context.getResources().getAssets().open("ferris.jxl");

        byte[] content = new byte[100];

        int size = input.read(content);
        input.close();

        // As input is marked finished, it is a decoder error and not an NeedMoreInputException
        // TODO: fixme when implementing stream loading
        DecodeError error = Assert.assertThrows(DecodeError.class, () -> {
            AnimationDrawable result = JxlDecoder.loadJxl(content);
        });

        Assert.assertEquals(DecodeError.DecodeErrorType.DecoderFailedError, error.getErrorType());
    }

    @Test
    public void decode_PngShouldFail() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        InputStream input = context.getResources().getAssets().open("android.png");

        byte[] content = new byte[input.available()];

        int size = input.read(content);
        input.close();

        DecodeError error =  Assert.assertThrows(DecodeError.class, () -> {
            AnimationDrawable result = JxlDecoder.loadJxl(content);
        });
        Assert.assertEquals(DecodeError.DecodeErrorType.DecoderFailedError, error.getErrorType());
    }

    // TODO: find a way to test icc profile errors
}
