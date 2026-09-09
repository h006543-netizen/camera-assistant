package com.example.cameraoption;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ExposureBrightnessTest {
    @Test public void previewScaleMatchesLinearExposureAcrossChannelsAndStops() {
        for (double multiplier : new double[]{0.001, 0.25, 0.98, 1, 1.02, 2, 16, 1000}) {
            int[] saved = FilmProcessor.buildExposureLookupTable(multiplier);
            float previewScale = (float) ExposureBrightness.encodedScale(multiplier);
            for (int channel = 0; channel < 256; channel++) {
                int expected = (int) Math.round(255 * Math.pow(Math.min(1,
                        Math.pow(channel / 255.0, 2.2) * multiplier), 1.0 / 2.2));
                assertEquals(expected, saved[channel]);
                assertEquals(saved[channel], ExposureBrightness.applyChannel(channel, previewScale), 1);
            }
        }
    }
}
