package com.shutternote;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FieldOfViewCalculatorTest {

    @Test
    public void equivalentFocalLength_usesThreeByTwoCropOfFourByThreeSensor() {
        double equivalent = FieldOfViewCalculator.calculateEquivalentFocalLength(
                5.0,
                7.2,
                5.4
        );

        assertEquals(25.0, equivalent, 0.01);
    }

    @Test
    public void chooseBaseEquivalent_prefersTypicalMainCamera() {
        double equivalent = FieldOfViewCalculator.chooseBaseEquivalentFocalLength(
                new float[]{2.0f, 5.0f, 10.0f},
                7.2,
                5.4
        );

        assertEquals(25.0, equivalent, 0.01);
    }

    @Test
    public void equivalentFocalLength_handlesWideAndRotatedSensors() {
        assertEquals(30.0, FieldOfViewCalculator.calculateEquivalentFocalLength(
                5.0, 8.0, 4.0), 0.0001);
        assertEquals(25.0, FieldOfViewCalculator.calculateEquivalentFocalLength(
                5.0, 5.4, 7.2), 0.0001);
        assertEquals(40.0, FieldOfViewCalculator.calculateEquivalentFocalLength(
                40.0, 36.0, 24.0), 0.0001);
    }

    @Test
    public void zoomRatio_matchesCorrectedFilmWidthAndHeightForAllFocalLengths() {
        double base = FieldOfViewCalculator.calculateEquivalentFocalLength(5.0, 7.2, 5.4);
        for (String target : new String[]{"35mm", "40mm", "50mm"}) {
            double focal = Double.parseDouble(target.replace("mm", ""));
            double zoom = FieldOfViewCalculator.calculateZoomRatio(target, base);
            // Both scene dimensions shrink by the same requested calibration.
            assertEquals(36.0 / focal / 1.03, 7.2 / zoom / 5.0, 0.0001);
            assertEquals(24.0 / focal / 1.03, 4.8 / zoom / 5.0, 0.0001);
        }
    }

    @Test
    public void chooseBaseEquivalent_uses24mmWhenMetadataIsUnavailable() {
        assertEquals(
                24.0,
                FieldOfViewCalculator.chooseBaseEquivalentFocalLength(null, 0.0, 0.0),
                0.0
        );
    }

    @Test
    public void zoomRatio_converts35mmEquivalentFrom24mmBase() {
        float zoomRatio = FieldOfViewCalculator.calculateZoomRatio(
                ExposureSettingsStore.FIELD_OF_VIEW_35_MM,
                24.0
        );

        assertEquals(1.5020833f, zoomRatio, 0.0001f);
    }

    @Test
    public void zoomRatio_correctsDefaultFieldOfView() {
        assertEquals(
                1.03f,
                FieldOfViewCalculator.calculateZoomRatio(
                        ExposureSettingsStore.FIELD_OF_VIEW_DEFAULT,
                        24.0
                ),
                0.0f
        );
    }

    @Test
    public void zoomRatio_correctsEverySelectionForBothSensorOrientationsWithoutAccumulating() {
        String[] selections = {"기본", "35mm", "40mm", "50mm"};
        float[] expected = {1.03f, 1.442f, 1.648f, 2.06f};
        for (double[] sensor : new double[][]{{7.2, 5.4}, {5.4, 7.2}}) {
            double base = FieldOfViewCalculator.calculateEquivalentFocalLength(
                    5.0, sensor[0], sensor[1]);
            for (int i = 0; i < selections.length; i++) {
                for (int repeat = 0; repeat < 3; repeat++) {
                    assertEquals(expected[i], FieldOfViewCalculator.calculateZoomRatio(
                            selections[i], base), 0.0001f);
                }
            }
        }
    }

    @Test
    public void zoomRatio_preservesCorrectedFallbackForInvalidMetadata() {
        for (double base : new double[]{0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(1.03f, FieldOfViewCalculator.calculateZoomRatio("50mm", base), 0.0f);
        }
        assertEquals(1.03f, FieldOfViewCalculator.calculateZoomRatio("invalid", 24.0), 0.0f);
    }

    @Test
    public void clampZoomRatio_respectsCameraRange() {
        assertEquals(
                2.0f,
                FieldOfViewCalculator.clampZoomRatio(2.5f, 1.0f, 2.0f),
                0.0f
        );
        float corrected = FieldOfViewCalculator.calculateZoomRatio("50mm", 25.0);
        assertEquals(2.06f, corrected, 0.0001f);
        assertEquals(2.0f, FieldOfViewCalculator.clampZoomRatio(corrected, 1.0f, 2.0f), 0.0f);
        assertEquals(2.06f, FieldOfViewCalculator.clampZoomRatio(corrected, 1.0f, 3.0f), 0.0001f);
        assertEquals(1.0f, FieldOfViewCalculator.clampZoomRatio(
                FieldOfViewCalculator.calculateZoomRatio("35mm", 40.0), 1.0f, 3.0f), 0.0f);
    }
}
