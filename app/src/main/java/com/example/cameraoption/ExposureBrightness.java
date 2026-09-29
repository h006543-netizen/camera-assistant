package com.example.cameraoption;

/** Shared gamma-2.2 exposure transform for preview and encoded image channels. */
public final class ExposureBrightness {
    private ExposureBrightness() { }

    /** Two percent dimmer encoded RGB, applied before the shared gamma transform. */
    public static double adjustForDisplay(double multiplier) {
        return multiplier * Math.pow(0.98, 2.2);
    }

    public static double encodedScale(double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier <= 0.0) {
            throw new IllegalArgumentException("Brightness multiplier must be finite and positive");
        }
        return Math.pow(multiplier, 1.0 / 2.2);
    }

    public static int applyChannel(int channel, double scale) {
        return (int) Math.round(Math.max(0.0, Math.min(255.0, channel * scale)));
    }
}
