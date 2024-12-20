package fr.oupson.libjxl_microbenchmark;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.BlackHole;
import androidx.benchmark.junit4.BenchmarkRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import fr.oupson.libjxl.JxlDecoder;

@RunWith(AndroidJUnit4.class)
public class JxlDecoderBenchmark {
    @Rule
    public BenchmarkRule benchmarkRule = new BenchmarkRule();

    private void loadImage(byte[] content) {
        try (JxlDecoder.Options options = new JxlDecoder.Options().setDecodeMultipleFrames(true).setFormat(Bitmap.Config.ARGB_8888)) {
            Drawable animationDrawable = JxlDecoder.loadJxl(new ByteArrayInputStream(content), options);
            BlackHole.consume(animationDrawable);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void benchmarkDecodeLogo() throws IOException {
        final BenchmarkState state = benchmarkRule.getState();

        state.pauseTiming();
        Context context = ApplicationProvider.getApplicationContext();
        InputStream input = context.getResources().getAssets().open("logo.jxl");

        byte[] content = new byte[input.available()];

        int size = input.read(content);
        input.close();

        Assert.assertEquals("Failed to read test file, invalid size", 117, size);
        state.resumeTiming();

        while (state.keepRunning()) {
            loadImage(content);
        }
    }

    @Test
    public void benchmarkDecodeDidi() throws IOException {
        final BenchmarkState state = benchmarkRule.getState();

        state.pauseTiming();
        Context context = ApplicationProvider.getApplicationContext();
        InputStream input = context.getResources().getAssets().open("didi.jxl");

        byte[] content = new byte[input.available()];

        int size = input.read(content);
        input.close();

        Assert.assertEquals("Failed to read test file, invalid size", 529406, size);
        state.resumeTiming();

        while (state.keepRunning()) {
            loadImage(content);
        }
    }

    @Test
    public void benchmarkDecodeFerris() throws IOException {
        final BenchmarkState state = benchmarkRule.getState();

        state.pauseTiming();
        Context context = ApplicationProvider.getApplicationContext();
        InputStream input = context.getResources().getAssets().open("ferris.jxl");

        byte[] content = new byte[input.available()];

        int size = input.read(content);
        input.close();

        Assert.assertEquals("Failed to read test file, invalid size", 404955, size);
        state.resumeTiming();

        while (state.keepRunning()) {
            loadImage(content);
        }
    }
}
