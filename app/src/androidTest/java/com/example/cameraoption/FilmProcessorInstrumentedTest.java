package com.example.cameraoption;

import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FilmProcessorInstrumentedTest {
    @Test public void rowProcessingPreservesImageAndExposure() {
        int[] input = {0xff000000, 0xff204080, 0xffeeeeee,
                0xff123456, 0xff808080, 0xffffffff};
        Bitmap source = Bitmap.createBitmap(input, 3, 2, Bitmap.Config.ARGB_8888);
        Bitmap result = FilmProcessor.applyExposure(source, 2);
        try {
            int[] lut = FilmProcessor.buildExposureLookupTable(2);
            for (int i = 0; i < input.length; i++) {
                int color = input[i];
                int expected = 0xff000000 | lut[(color >> 16) & 255] << 16
                        | lut[(color >> 8) & 255] << 8 | lut[color & 255];
                assertEquals(expected, result.getPixel(i % 3, i / 3));
                assertEquals(color, source.getPixel(i % 3, i / 3));
            }
        } finally { result.recycle(); source.recycle(); }
    }

    @Test public void cancelledProcessingLeavesSourceUsable() {
        Bitmap source = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888);
        try {
            try {
                FilmProcessor.applyPreset(source, FilmProcessor.Preset.GOLD_200, () -> true);
                fail("Expected cancellation");
            } catch (java.util.concurrent.CancellationException expected) {
                assertFalse(source.isRecycled());
                assertEquals(20, source.getWidth());
            }
        } finally { source.recycle(); }
    }
}
