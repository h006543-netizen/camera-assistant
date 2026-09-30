package com.shutternote;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ExposureBrightnessTest {
    @Test public void fixedAdjustmentDimsPreviewAndSavedChannelsByTwoPercent() {
        for (double original : new double[]{0.1, 1.0, 4.0}) {
            double adjusted = ExposureBrightness.adjustForDisplay(original);
            double scale = ExposureBrightness.encodedScale(adjusted);
            assertEquals(ExposureBrightness.encodedScale(original) * 0.98, scale, 0.000001);
            int[] saved = FilmProcessor.buildExposureLookupTable(adjusted);
            for (int channel = 0; channel < 256; channel++) {
                assertEquals(ExposureBrightness.applyChannel(channel, scale), saved[channel]);
            }
        }
        assertEquals(98, FilmProcessor.buildExposureLookupTable(
                ExposureBrightness.adjustForDisplay(1.0))[100]);
    }

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
