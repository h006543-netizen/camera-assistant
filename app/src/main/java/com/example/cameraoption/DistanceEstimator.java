package com.example.cameraoption;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Pure measurement policy, independent of ARCore and the Android UI. */
final class DistanceEstimator {
    static final float MAX_METERS = 6f;
    private static final float OUT_OF_RANGE = 6.01f;
    private float stable = Float.NaN;
    private float pending = Float.NaN;
    private long lastTimestamp;

    static float readDepthMeters(ByteBuffer buffer, int width, int height,
                                 int rowStride, int pixelStride, int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return Float.NaN;
        int index = buffer.position() + y * rowStride + x * pixelStride;
        if (index < 0 || index + 1 >= buffer.limit()) return Float.NaN;
        // D_16 uses all 16 bits, little endian, in millimeters (not DEPTH16's 13 bits).
        int mm = (buffer.get(index) & 0xff) | ((buffer.get(index + 1) & 0xff) << 8);
        return mm == 0 ? Float.NaN : mm / 1000f;
    }

    static boolean valid(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value) && value >= 0.15f;
    }

    static float centralMedian(float[] samples) {
        if (samples.length != 5 || !valid(samples[0])) return Float.NaN;
        float center = samples[0];
        float tolerance = Math.max(0.08f, center * 0.12f);
        float[] agreeing = new float[5];
        int count = 0;
        for (float sample : samples) {
            if (valid(sample) && Math.abs(sample - center) <= tolerance) {
                agreeing[count++] = sample;
            }
        }
        if (count < 3) return Float.NaN;
        Arrays.sort(agreeing, 0, count);
        return count % 2 == 0
                ? (agreeing[count / 2 - 1] + agreeing[count / 2]) / 2f
                : agreeing[count / 2];
    }

    float update(float measurement, long timestamp) {
        if (!valid(measurement) || timestamp <= 0) {
            reset();
            return Float.NaN;
        }
        if (timestamp <= lastTimestamp) return Float.NaN;
        if (lastTimestamp != 0 && timestamp - lastTimestamp > 500_000_000L) reset();
        lastTimestamp = timestamp;
        float value = measurement > MAX_METERS ? OUT_OF_RANGE : measurement;
        if (valid(stable) && sameRange(value, stable)
                && Math.abs(value - stable) <= Math.max(0.15f, stable * 0.15f)) {
            stable = stable * 0.4f + value * 0.6f;
            pending = Float.NaN;
            return stable;
        }
        // Do not display the old target while confirming a different target.
        if (valid(pending) && sameRange(value, pending)
                && Math.abs(value - pending) <= Math.max(0.1f, value * 0.1f)) {
            stable = (pending + value) / 2f;
            pending = Float.NaN;
            return stable;
        }
        pending = value;
        return Float.NaN;
    }

    private static boolean sameRange(float a, float b) {
        return (a > MAX_METERS) == (b > MAX_METERS);
    }

    void reset() {
        stable = Float.NaN;
        pending = Float.NaN;
        lastTimestamp = 0;
    }
}
