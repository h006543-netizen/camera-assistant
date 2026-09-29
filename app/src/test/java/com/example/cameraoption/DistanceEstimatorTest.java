package com.example.cameraoption;

import org.junit.Test;
import java.nio.ByteBuffer;
import static org.junit.Assert.*;

public class DistanceEstimatorTest {
    private static final long FRAME = 150_000_000L;

    @Test public void backgroundCannotReplaceMissingCenter() {
        assertTrue(Float.isNaN(DistanceEstimator.centralMedian(
                new float[]{Float.NaN, 9, 9, 9, 9})));
        assertTrue(Float.isNaN(DistanceEstimator.centralMedian(
                new float[]{1, 9, 9, 9, 9})));
    }

    @Test public void centralSurfaceWinsOverBackgroundNeighbors() {
        assertEquals(1f, DistanceEstimator.centralMedian(
                new float[]{1, 1.02f, .98f, 9, 9}), .001f);
        assertTrue(Float.isNaN(DistanceEstimator.centralMedian(
                new float[]{9, 1, 1, 1, 1})));
    }

    @Test public void onePixelAndInvalidDepthAreNotMeasurements() {
        assertTrue(Float.isNaN(DistanceEstimator.centralMedian(
                new float[]{1, 0, Float.NaN, Float.POSITIVE_INFINITY, 0})));
    }

    @Test public void newTargetRequiresTwoFramesButNoLongAverage() {
        DistanceEstimator filter = new DistanceEstimator();
        assertTrue(Float.isNaN(filter.update(1, FRAME)));
        assertEquals(1, filter.update(1, FRAME * 2), .001f);
        assertTrue(Float.isNaN(filter.update(4, FRAME * 3)));
        assertEquals(4, filter.update(4, FRAME * 4), .001f);
    }

    @Test public void isolatedOutOfRangeSpikeIsNeverDisplayed() {
        DistanceEstimator filter = new DistanceEstimator();
        filter.update(1, FRAME);
        filter.update(1, FRAME * 2);
        assertTrue(Float.isNaN(filter.update(12, FRAME * 3)));
        assertEquals(1, filter.update(1, FRAME * 4), .001f);
    }

    @Test public void duplicateFrameCannotConfirmNewReading() {
        DistanceEstimator filter = new DistanceEstimator();
        filter.update(2, FRAME);
        assertTrue(Float.isNaN(filter.update(2, FRAME)));
        assertEquals(2, filter.update(2, FRAME * 2), .001f);
    }

    @Test public void lostTrackingAndLongGapsRequireFreshConfirmation() {
        DistanceEstimator filter = new DistanceEstimator();
        filter.update(2, FRAME);
        filter.update(2, FRAME * 2);
        filter.reset();
        assertTrue(Float.isNaN(filter.update(2, FRAME * 3)));
        filter.update(2, FRAME * 4);
        assertTrue(Float.isNaN(filter.update(2, FRAME * 10)));
    }

    @Test public void tenMeterBoundaryDoesNotBlendWithOutOfRange() {
        DistanceEstimator filter = new DistanceEstimator();
        filter.update(10, FRAME);
        assertEquals(10, filter.update(10, FRAME * 2), .001f);
        assertTrue(Float.isNaN(filter.update(12, FRAME * 3)));
        assertTrue(filter.update(12, FRAME * 4) > 10);
        assertTrue(Float.isNaN(filter.update(9.9f, FRAME * 5)));
        assertEquals(9.9f, filter.update(9.9f, FRAME * 6), .001f);
    }

    @Test public void depthReaderHandlesStrideUnsigned16BitsAndEdges() {
        ByteBuffer bytes = ByteBuffer.allocate(24);
        bytes.position(2);
        // Pixel (1, 1), row stride 10, pixel stride 4; 40000mm = 0x9c40.
        bytes.put(16, (byte) 0x40);
        bytes.put(17, (byte) 0x9c);
        assertEquals(40, DistanceEstimator.readDepthMeters(bytes, 2, 2, 10, 4, 1, 1), 0);
        assertTrue(Float.isNaN(DistanceEstimator.readDepthMeters(bytes, 2, 2, 10, 4, -1, 1)));
        assertTrue(Float.isNaN(DistanceEstimator.readDepthMeters(bytes, 2, 2, 10, 4, 2, 1)));
        assertTrue(Float.isNaN(DistanceEstimator.readDepthMeters(bytes, 2, 2, 10, 4, 0, 0)));
    }
}
