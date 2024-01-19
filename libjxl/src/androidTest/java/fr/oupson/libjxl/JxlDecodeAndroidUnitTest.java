package fr.oupson.libjxl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;

import fr.oupson.libjxl.exceptions.DecodeError;

public class JxlDecodeAndroidUnitTest {
    @Test
    public void decode_LogoShouldNotFail() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        InputStream input = context.getResources().getAssets().open("logo.jxl");

        try {
            AnimationDrawable result = JxlDecoder.loadJxl(input);
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

        try {
            AnimationDrawable result = JxlDecoder.loadJxl(input);
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

        try {
            AnimationDrawable result = JxlDecoder.loadJxl(input);
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
    public void decode_FerrisWithParcelFileDescriptorShouldNotFail() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        Path testFile = new File(context.getCacheDir(), "ferris.jxl").toPath();
        if (!Files.exists(testFile)) {
            try (InputStream assetInputStream = context.getResources().getAssets().open("ferris.jxl")) {
                Files.copy(assetInputStream, testFile);
            }
        }

        Uri androidUri = Uri.fromFile(testFile.toFile());

        try (ParcelFileDescriptor input = context.getContentResolver().openFileDescriptor(androidUri, "r")) {
            AnimationDrawable result = JxlDecoder.loadJxl(Objects.requireNonNull(input));
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

        DecodeError error = Assert.assertThrows(DecodeError.class, () -> {
            AnimationDrawable result = JxlDecoder.loadJxl(new ByteArrayInputStream(content));
        });

        Assert.assertEquals(DecodeError.DecodeErrorType.NEED_MORE_INPUT_ERROR, error.getErrorType());
    }

    @Test
    public void decode_PngShouldFail() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        InputStream input = context.getResources().getAssets().open("android.png");
        DecodeError error = Assert.assertThrows(DecodeError.class, () -> {
            AnimationDrawable result = JxlDecoder.loadJxl(input);
        });
        Assert.assertEquals(DecodeError.DecodeErrorType.DECODER_FAILED_ERROR, error.getErrorType());
    }

    @Test
    public void decode_WithFailingStreamShouldFail() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();

        class FailingStream extends InputStream {
            private final InputStream wrapped;
            private int count = 0;

            public FailingStream(InputStream stream) {
                this.wrapped = stream;
            }

            @Override
            public int read() {
                return -1;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (count == 0) {
                    count += 1;
                    return wrapped.read(b, off, len);
                } else {
                    throw new NoSuchFileException("foo.jxl");
                }
            }

            @Override
            public void close() throws IOException {
                this.wrapped.close();
                super.close();
            }
        }

        InputStream input = new FailingStream(context.getResources().getAssets().open("ferris.jxl"));

        // As input is marked finished, it is a decoder error and not an NeedMoreInputException
        Assert.assertThrows(NoSuchFileException.class, () -> {
            AnimationDrawable result = JxlDecoder.loadJxl(input);
        });
    }

    @Test
    public void decodeThumbnails_shouldNotFail() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();

        String[] assetList = new String[]{"didi.jxl", "ferris.jxl", "logo.jxl"};
        for (String asset : assetList) {
            InputStream inputStream = context.getAssets().open(asset);
            try {
                Bitmap result = JxlDecoder.loadThumbnail(inputStream);
                Assert.assertNotNull(result);
            } catch (Exception e) {
                Assert.fail("Failed to read image : " + e.getMessage());
            } finally {
                inputStream.close();
            }
        }
    }

    // TODO: find a way to test icc profile errors
}
