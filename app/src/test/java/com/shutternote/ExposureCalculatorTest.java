package com.shutternote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExposureCalculatorTest {
    @Test
    public void calculate_capsExposureAtFrameLimitAndPreservesTargetBrightness() {
        ExposureCalculator.Result result = ExposureCalculator.calculate(
                3200, 1.0, 1.8, 1.8, 50, 800,
                100_000L, 1_000_000_000L, 100_000_000L);
        assertEquals(100_000_000L, result.getSensorExposureNanos());
        assertEquals(100_000_000L, result.getSensorFrameDurationNanos());
        assertEquals(3200.0, result.getSensorIso()
                * result.getSensorExposureNanos() / 1e9
                * result.getResidualBrightnessMultiplier(), 0.0001);
    }

    @Test
    public void calculate_frameDurationContainsExposureAcrossShutterRange() {
        for (double shutter : new double[]{1.0 / 8000, 1.0 / 125, 0.1, 1, 30}) {
            ExposureCalculator.Result result = ExposureCalculator.calculate(
                    200, shutter, 2.8, 1.8, 50, 3200,
                    100_000L, 1_000_000_000L, 500_000_000L);
            assertTrue(result.getSensorExposureNanos() <= result.getSensorFrameDurationNanos());
            assertTrue(result.getSensorFrameDurationNanos() <= 500_000_000L);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void calculate_rejectsFrameLimitBelowMinimumExposure() {
        ExposureCalculator.calculate(200, 0.01, 2.8, 1.8, 50, 800,
                100_000L, 1_000_000L, 50_000L);
    }

    @Test
    public void parseShutterSeconds_supportsFractionsAndWholeSeconds() {
        assertEquals(1.0 / 125.0, ExposureCalculator.parseShutterSeconds("1/125"), 0.000001);
        assertEquals(1.0, ExposureCalculator.parseShutterSeconds("1"), 0.000001);
    }

    @Test
    public void relativeEv_combinesIsoShutterAndApertureChanges() {
        double ev = ExposureCalculator.calculateRelativeEv(
                400,
                1.0 / 60.0,
                4.0,
                200,
                1.0 / 125.0,
                2.8
        );

        assertEquals(1.03, ev, 0.05);
    }

    @Test
    public void calculate_apertureDifferenceIsConvertedIntoSensorIso() {
        ExposureCalculator.Result result = ExposureCalculator.calculate(
                200,
                1.0 / 125.0,
                2.8,
                1.8,
                50,
                3200,
                100_000L,
                1_000_000_000L
        );

        assertEquals(83, result.getSensorIso());
        assertEquals(8_000_000L, result.getSensorExposureNanos());
        assertFalse(result.isHardwareRangeLimited());
    }

    @Test
    public void calculate_usesShutterWhenRequiredIsoIsBelowSensorRange() {
        ExposureCalculator.Result result = ExposureCalculator.calculate(
                100,
                1.0 / 125.0,
                8.0,
                1.8,
                50,
                3200,
                100_000L,
                1_000_000_000L
        );

        assertEquals(50, result.getSensorIso());
        assertEquals(810_000L, result.getSensorExposureNanos(), 2_000L);
        assertFalse(result.isHardwareRangeLimited());
    }

    @Test
    public void calculate_reportsWhenHardwareRangeCannotReachTarget() {
        ExposureCalculator.Result result = ExposureCalculator.calculate(
                3200,
                1.0,
                1.4,
                1.8,
                50,
                800,
                100_000L,
                100_000_000L
        );

        assertTrue(result.isHardwareRangeLimited());
        assertTrue(result.getResidualBrightnessMultiplier() > 1.0);
    }
}
