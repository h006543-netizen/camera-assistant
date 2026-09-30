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
    public void zoomRatio_matchesFilmWidthAndHeightFor40And50mm() {
        double base = FieldOfViewCalculator.calculateEquivalentFocalLength(5.0, 7.2, 5.4);
        for (String target : new String[]{"40mm", "50mm"}) {
            double focal = target.equals("40mm") ? 40.0 : 50.0;
            double zoom = FieldOfViewCalculator.calculateZoomRatio(target, base);
            // Scene width/distance and height/distance must equal the film camera.
            assertEquals(36.0 / focal, 7.2 / zoom / 5.0, 0.0001);
            assertEquals(24.0 / focal, 4.8 / zoom / 5.0, 0.0001);
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

        assertEquals(1.458f, zoomRatio, 0.001f);
    }

    @Test
    public void zoomRatio_usesOneForDefaultFieldOfView() {
        assertEquals(
                1.0f,
                FieldOfViewCalculator.calculateZoomRatio(
                        ExposureSettingsStore.FIELD_OF_VIEW_DEFAULT,
                        24.0
                ),
                0.0f
        );
    }

    @Test
    public void clampZoomRatio_respectsCameraRange() {
        assertEquals(
                2.0f,
                FieldOfViewCalculator.clampZoomRatio(2.5f, 1.0f, 2.0f),
                0.0f
        );
    }
}
