package com.example.cameraoption;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

import java.util.Locale;

/**
 * 결과 화면용 필름 예상 보정 엔진.
 *
 * <p>항상 촬영 원본을 입력으로 받아 색상, 톤, 필름별 그레인을 한 번만 적용한다.
 * 실제 현상 및 스캔 결과를 복제하는 용도가 아니라 각 필름의 대표적인 방향을
 * 비교하기 위한 프리뷰다.</p>
 */
public final class FilmProcessor {

    private static final int TONE_LOOKUP_TABLE_SIZE = 4096;

    private FilmProcessor() {
    }

    public enum Preset {
        NONE("선택 없음", Profile.none()),
        GOLD_200("Kodak Gold 200", new Profile(
                1.08f, 1.02f, 0.92f,
                1.12f, 1.10f, 1.00f,
                0.005f, 0.04f,
                0.010f, 0.000f, -0.008f,
                0.008f, 0.003f, -0.004f,
                0.026f, 2, 0.16f, 1.20f, 0.85f,
                0.0f
        )),
        PORTRA_400("Kodak Portra 400", new Profile(
                1.045f, 1.012f, 0.985f,
                0.94f, 0.90f, 0.98f,
                0.025f, 0.10f,
                0.008f, 0.002f, -0.004f,
                0.010f, 0.005f, 0.002f,
                0.018f, 1, 0.08f, 1.08f, 0.72f,
                0.0f
        )),
        CINESTILL_800T("CineStill 800T", new Profile(
                1.035f, 0.985f, 1.075f,
                1.06f, 1.06f, 1.02f,
                0.012f, 0.08f,
                -0.006f, 0.002f, 0.025f,
                0.035f, 0.003f, -0.010f,
                0.046f, 2, 0.22f, 1.30f, 0.82f,
                0.82f
        )),
        VISION3_250D("Kodak Vision3 250D", new Profile(
                1.025f, 1.005f, 0.985f,
                0.96f, 0.84f, 0.98f,
                0.032f, 0.15f,
                0.002f, 0.005f, 0.012f,
                0.008f, 0.004f, -0.002f,
                0.014f, 1, 0.07f, 0.90f, 0.60f,
                0.0f
        ));

        private final String displayName;
        private final Profile profile;

        Preset(String displayName, Profile profile) {
            this.displayName = displayName;
            this.profile = profile;
        }

        public String getDisplayName() {
            return displayName;
        }

        Profile getProfile() {
            return profile;
        }

        public static Preset fromDisplayName(String name) {
            for (Preset preset : values()) {
                if (preset.displayName.equals(name)) {
                    return preset;
                }
            }
            return NONE;
        }
    }

    public static Bitmap applyExposure(Bitmap source, double multiplier) {
        return applyExposureAndPreset(source, multiplier, Preset.NONE);
    }

    public static Bitmap applyPreset(Bitmap original, Preset preset) {
        return applyPresetInternal(original, preset, null);
    }

    public static Bitmap applyExposureAndPreset(
            Bitmap original,
            double exposureMultiplier,
            Preset preset
    ) {
        int[] exposureLookupTable =
                exposureMultiplier == 1.0
                        ? null
                        : buildExposureLookupTable(exposureMultiplier);
        return applyPresetInternal(original, preset, exposureLookupTable);
    }

    private static Bitmap applyPresetInternal(
            Bitmap original,
            Preset preset,
            int[] exposureLookupTable
    ) {
        return applyPresetInternal(original, preset, exposureLookupTable, () -> false);
    }

    public static Bitmap applyPreset(Bitmap original, Preset preset,
            java.util.function.BooleanSupplier cancelled) {
        return applyPresetInternal(original, preset, null, cancelled);
    }

    public static Bitmap applyExposure(Bitmap original, double multiplier,
            java.util.function.BooleanSupplier cancelled) {
        return applyPresetInternal(original, Preset.NONE,
                multiplier == 1.0 ? null : buildExposureLookupTable(multiplier), cancelled);
    }

    private static Bitmap applyPresetInternal(Bitmap original, Preset preset,
            int[] exposureLookupTable, java.util.function.BooleanSupplier cancelled) {
        if (preset == Preset.NONE && exposureLookupTable == null) {
            return original;
        }

        Profile profile = preset.getProfile();
        int width = original.getWidth();
        int height = original.getHeight();
        int[] pixels = new int[width];
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        int seed = preset.ordinal() * 10_007 + 97;

        try {
        for (int y = 0; y < height; y++) {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                throw new java.util.concurrent.CancellationException();
            }
            original.getPixels(pixels, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x++) {
                int index = x;
                int color = pixels[index];

                int redChannel = (color >> 16) & 0xFF;
                int greenChannel = (color >> 8) & 0xFF;
                int blueChannel = color & 0xFF;

                if (exposureLookupTable != null) {
                    redChannel = exposureLookupTable[redChannel];
                    greenChannel = exposureLookupTable[greenChannel];
                    blueChannel = exposureLookupTable[blueChannel];
                }

                if (preset == Preset.NONE) {
                    pixels[index] = 0xFF000000
                            | (redChannel << 16)
                            | (greenChannel << 8)
                            | blueChannel;
                    continue;
                }

                float red = redChannel / 255f;
                float green = greenChannel / 255f;
                float blue = blueChannel / 255f;

                float luminance = clamp01(
                        red * 0.2126f + green * 0.7152f + blue * 0.0722f
                );
                float shadowWeight = clamp01(1f - luminance * 2f);
                float highlightWeight = clamp01((luminance - 0.55f) / 0.45f);

                red = red * profile.redGain
                        + profile.shadowRed * shadowWeight
                        + profile.highlightRed * highlightWeight;
                green = green * profile.greenGain
                        + profile.shadowGreen * shadowWeight
                        + profile.highlightGreen * highlightWeight;
                blue = blue * profile.blueGain
                        + profile.shadowBlue * shadowWeight
                        + profile.highlightBlue * highlightWeight;

                float adjustedLuminance = clamp01(
                        red * 0.2126f + green * 0.7152f + blue * 0.0722f
                );
                red = adjustedLuminance + (red - adjustedLuminance) * profile.saturation;
                green = adjustedLuminance + (green - adjustedLuminance) * profile.saturation;
                blue = adjustedLuminance + (blue - adjustedLuminance) * profile.saturation;

                red = applyTone(red, profile);
                green = applyTone(green, profile);
                blue = applyTone(blue, profile);

                float grainWeight = mix(
                        profile.highlightGrain,
                        profile.shadowGrain,
                        1f - luminance
                );
                float grain = signedNoise(
                        x / profile.grainSize,
                        y / profile.grainSize,
                        seed
                ) * profile.grainAmount * grainWeight;
                float chromaNoise = signedNoise(
                        x / profile.grainSize,
                        y / profile.grainSize,
                        seed + 131
                ) * profile.grainAmount * profile.chromaGrain;

                red = clamp01(red + grain + chromaNoise);
                green = clamp01(green + grain - chromaNoise * 0.35f);
                blue = clamp01(blue + grain + chromaNoise * 0.55f);

                pixels[index] = 0xFF000000
                        | (Math.round(red * 255f) << 16)
                        | (Math.round(green * 255f) << 8)
                        | Math.round(blue * 255f);
            }
            output.setPixels(pixels, 0, width, 0, y, width, 1);
        }

        if (preset != Preset.NONE && profile.halationAmount > 0f) {
            if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException();
            applyHalation(output, profile.halationAmount);
        }

        return output;
        } catch (RuntimeException | OutOfMemoryError error) {
            output.recycle();
            throw error;
        }
    }

    private static float applyTone(float value, Profile profile) {
        float clamped = clamp01(value);
        int index = Math.min(
                TONE_LOOKUP_TABLE_SIZE - 1,
                Math.round(clamped * (TONE_LOOKUP_TABLE_SIZE - 1))
        );
        return profile.toneLookupTable[index];
    }

    private static float[] buildToneLookupTable(Profile profile) {
        float[] lookupTable = new float[TONE_LOOKUP_TABLE_SIZE];
        for (int index = 0; index < lookupTable.length; index++) {
            float value = index / (float) (lookupTable.length - 1);
            lookupTable[index] = calculateTone(value, profile);
        }
        return lookupTable;
    }

    private static float calculateTone(float clamped, Profile profile) {
        float contrasted = (clamped - 0.5f) * profile.contrast + 0.5f;
        float lifted = profile.blackLift + contrasted * (1f - profile.blackLift);
        float gammaAdjusted = (float) Math.pow(clamp01(lifted), profile.gamma);

        /*
         * 가장 밝은 영역만 부드럽게 눌러 네거티브 필름의 하이라이트 관용도를
         * 단순 클리핑보다 자연스럽게 표현한다.
         */
        float highlight = clamp01((gammaAdjusted - 0.55f) / 0.45f);
        float compressed = gammaAdjusted
                - profile.highlightCompression
                * highlight
                * highlight
                * (1f - gammaAdjusted);
        return clamp01(compressed);
    }

    private static void applyHalation(Bitmap output, float amount) {
        int width = output.getWidth();
        int height = output.getHeight();
        int scale = 8;
        int maskWidth = Math.max(1, width / scale);
        int maskHeight = Math.max(1, height / scale);
        Bitmap sampled = Bitmap.createScaledBitmap(
                output,
                maskWidth,
                maskHeight,
                true
        );
        int[] source = new int[maskWidth * maskHeight];
        int[] maskAlpha = new int[maskWidth * maskHeight];
        sampled.getPixels(source, 0, maskWidth, 0, 0, maskWidth, maskHeight);

        for (int y = 0; y < maskHeight; y++) {
            for (int x = 0; x < maskWidth; x++) {
                int color = source[y * maskWidth + x];
                float red = ((color >> 16) & 0xFF) / 255f;
                float green = ((color >> 8) & 0xFF) / 255f;
                float blue = (color & 0xFF) / 255f;
                float luminance = red * 0.2126f + green * 0.7152f + blue * 0.0722f;
                float highlight = clamp01((luminance - 0.78f) / 0.22f);
                maskAlpha[y * maskWidth + x] = Math.round(highlight * amount * 190f);
            }
        }
        sampled.recycle();

        for (int pass = 0; pass < 3; pass++) {
            maskAlpha = boxBlur(maskAlpha, maskWidth, maskHeight, 3);
        }

        int[] maskPixels = new int[maskAlpha.length];
        for (int index = 0; index < maskPixels.length; index++) {
            int alpha = clamp255(maskAlpha[index]);
            maskPixels[index] = (alpha << 24) | 0x00FF2418;
        }

        Bitmap mask = Bitmap.createBitmap(
                maskPixels,
                maskWidth,
                maskHeight,
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        canvas.drawBitmap(mask, null, new android.graphics.Rect(0, 0, width, height), paint);
        paint.setXfermode(null);
        mask.recycle();
    }

    private static int[] boxBlur(int[] source, int width, int height, int radius) {
        int[] horizontal = new int[source.length];
        int[] output = new int[source.length];

        for (int y = 0; y < height; y++) {
            int sum = 0;
            int row = y * width;
            for (int x = -radius; x <= radius; x++) {
                sum += source[row + clamp(x, 0, width - 1)];
            }
            for (int x = 0; x < width; x++) {
                horizontal[row + x] = sum / (radius * 2 + 1);
                sum -= source[row + clamp(x - radius, 0, width - 1)];
                sum += source[row + clamp(x + radius + 1, 0, width - 1)];
            }
        }

        for (int x = 0; x < width; x++) {
            int sum = 0;
            for (int y = -radius; y <= radius; y++) {
                sum += horizontal[clamp(y, 0, height - 1) * width + x];
            }
            for (int y = 0; y < height; y++) {
                output[y * width + x] = sum / (radius * 2 + 1);
                sum -= horizontal[clamp(y - radius, 0, height - 1) * width + x];
                sum += horizontal[clamp(y + radius + 1, 0, height - 1) * width + x];
            }
        }

        return output;
    }

    static int[] buildExposureLookupTable(double multiplier) {
        double scale = ExposureBrightness.encodedScale(multiplier);
        int[] lookupTable = new int[256];
        for (int channel = 0; channel < lookupTable.length; channel++) {
            lookupTable[channel] = ExposureBrightness.applyChannel(channel, scale);
        }
        return lookupTable;
    }

    private static float signedNoise(int x, int y, int seed) {
        int hash = x * 374_761_393 + y * 668_265_263 + seed * 1_274_126_177;
        hash = (hash ^ (hash >> 13)) * 1_274_126_177;
        hash ^= hash >> 16;
        return ((hash & 0x7FFFFFFF) / (float) 0x3FFFFFFF) - 1f;
    }

    private static float mix(float start, float end, float amount) {
        return start + (end - start) * clamp01(amount);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Profile {
        final float redGain;
        final float greenGain;
        final float blueGain;
        final float saturation;
        final float contrast;
        final float gamma;
        final float blackLift;
        final float highlightCompression;
        final float shadowRed;
        final float shadowGreen;
        final float shadowBlue;
        final float highlightRed;
        final float highlightGreen;
        final float highlightBlue;
        final float grainAmount;
        final int grainSize;
        final float chromaGrain;
        final float shadowGrain;
        final float highlightGrain;
        final float halationAmount;
        final float[] toneLookupTable;

        Profile(
                float redGain,
                float greenGain,
                float blueGain,
                float saturation,
                float contrast,
                float gamma,
                float blackLift,
                float highlightCompression,
                float shadowRed,
                float shadowGreen,
                float shadowBlue,
                float highlightRed,
                float highlightGreen,
                float highlightBlue,
                float grainAmount,
                int grainSize,
                float chromaGrain,
                float shadowGrain,
                float highlightGrain,
                float halationAmount
        ) {
            this.redGain = redGain;
            this.greenGain = greenGain;
            this.blueGain = blueGain;
            this.saturation = saturation;
            this.contrast = contrast;
            this.gamma = gamma;
            this.blackLift = blackLift;
            this.highlightCompression = highlightCompression;
            this.shadowRed = shadowRed;
            this.shadowGreen = shadowGreen;
            this.shadowBlue = shadowBlue;
            this.highlightRed = highlightRed;
            this.highlightGreen = highlightGreen;
            this.highlightBlue = highlightBlue;
            this.grainAmount = grainAmount;
            this.grainSize = grainSize;
            this.chromaGrain = chromaGrain;
            this.shadowGrain = shadowGrain;
            this.highlightGrain = highlightGrain;
            this.halationAmount = halationAmount;
            this.toneLookupTable = FilmProcessor.buildToneLookupTable(this);
        }

        static Profile none() {
            return new Profile(
                    1f, 1f, 1f,
                    1f, 1f, 1f,
                    0f, 0f,
                    0f, 0f, 0f,
                    0f, 0f, 0f,
                    0f, 1, 0f, 0f, 0f,
                    0f
            );
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "FilmProfile(grain=%.3f)", grainAmount);
        }
    }
}
