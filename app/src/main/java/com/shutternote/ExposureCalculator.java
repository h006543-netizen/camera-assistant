package com.shutternote;

/**
 * 필름카메라 설정을 스마트폰의 고정 조리개 센서 노출로 환산한다.
 *
 * <p>노출량은 ISO × 셔터시간 ÷ 조리개²에 비례한다. 스마트폰 조리개는 고정되어
 * 있으므로 선택된 필름카메라 조리개의 효과를 ISO와 셔터시간의 조합으로 바꿔
 * 동일한 상대 노출량을 만든다.</p>
 */
public final class ExposureCalculator {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private ExposureCalculator() {
    }

    public static Result calculate(
            int selectedIso,
            double selectedShutterSeconds,
            double selectedAperture,
            double phoneAperture,
            int minimumSensorIso,
            int maximumSensorIso,
            long minimumExposureNanos,
            long maximumExposureNanos
    ) {
        return calculate(selectedIso, selectedShutterSeconds, selectedAperture,
                phoneAperture, minimumSensorIso, maximumSensorIso,
                minimumExposureNanos, maximumExposureNanos, maximumExposureNanos);
    }

    public static Result calculate(
            int selectedIso, double selectedShutterSeconds, double selectedAperture,
            double phoneAperture, int minimumSensorIso, int maximumSensorIso,
            long minimumExposureNanos, long maximumExposureNanos,
            long maximumFrameDurationNanos
    ) {
        // Respect both advertised bounds before choosing ISO/time and residual gain.
        maximumExposureNanos = Math.min(maximumExposureNanos, maximumFrameDurationNanos);
        if (selectedIso <= 0
                || selectedShutterSeconds <= 0.0
                || selectedAperture <= 0.0
                || phoneAperture <= 0.0
                || minimumSensorIso <= 0
                || maximumSensorIso < minimumSensorIso
                || minimumExposureNanos <= 0
                || maximumExposureNanos < minimumExposureNanos) {
            throw new IllegalArgumentException("노출 계산 값은 유효한 양수 범위여야 합니다.");
        }

        /*
         * phoneIso × phoneTime / phoneAperture²
         *     = selectedIso × selectedTime / selectedAperture²
         *
         * 따라서 스마트폰 센서가 만들어야 하는 ISO×시간의 목표값은 아래와 같다.
         */
        double apertureScale = square(phoneAperture / selectedAperture);
        double targetIsoSeconds = selectedIso * selectedShutterSeconds * apertureScale;

        long preferredExposureNanos = clamp(
                Math.round(selectedShutterSeconds * NANOS_PER_SECOND),
                minimumExposureNanos,
                maximumExposureNanos
        );

        double preferredExposureSeconds = preferredExposureNanos / NANOS_PER_SECOND;
        double requiredIso = targetIsoSeconds / preferredExposureSeconds;

        int sensorIso;
        long sensorExposureNanos;

        if (requiredIso >= minimumSensorIso && requiredIso <= maximumSensorIso) {
            sensorIso = clamp(
                    (int) Math.round(requiredIso),
                    minimumSensorIso,
                    maximumSensorIso
            );
            sensorExposureNanos = preferredExposureNanos;
        } else {
            sensorIso = requiredIso < minimumSensorIso
                    ? minimumSensorIso
                    : maximumSensorIso;

            sensorExposureNanos = clamp(
                    Math.round((targetIsoSeconds / sensorIso) * NANOS_PER_SECOND),
                    minimumExposureNanos,
                    maximumExposureNanos
            );
        }

        double actualIsoSeconds =
                sensorIso * (sensorExposureNanos / NANOS_PER_SECOND);
        double residualMultiplier = targetIsoSeconds / actualIsoSeconds;

        return new Result(
                sensorIso,
                sensorExposureNanos,
                Math.min(maximumFrameDurationNanos,
                        Math.max(33_333_333L, sensorExposureNanos)),
                residualMultiplier,
                apertureScale
        );
    }

    public static double parseAperture(String value) {
        String normalized = value.trim().toLowerCase();
        if (normalized.startsWith("f/")) {
            normalized = normalized.substring(2);
        } else if (normalized.startsWith("f")) {
            normalized = normalized.substring(1);
        }
        return Double.parseDouble(normalized);
    }

    public static double parseShutterSeconds(String value) {
        String normalized = value.trim().toLowerCase()
                .replace("초", "")
                .replace("s", "")
                .trim();

        if (normalized.contains("/")) {
            String[] parts = normalized.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("지원하지 않는 셔터속도 형식입니다: " + value);
            }
            return Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
        }

        return Double.parseDouble(normalized);
    }

    public static double calculateRelativeEv(
            int iso,
            double shutterSeconds,
            double aperture,
            int referenceIso,
            double referenceShutterSeconds,
            double referenceAperture
    ) {
        double ratio =
                ((double) iso / referenceIso)
                        * (shutterSeconds / referenceShutterSeconds)
                        * square(referenceAperture / aperture);
        return Math.log(ratio) / Math.log(2.0);
    }

    private static double square(double value) {
        return value * value;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static final class Result {
        private final int sensorIso;
        private final long sensorExposureNanos;
        private final long sensorFrameDurationNanos;
        private final double residualBrightnessMultiplier;
        private final double apertureScale;

        Result(
                int sensorIso,
                long sensorExposureNanos,
                long sensorFrameDurationNanos,
                double residualBrightnessMultiplier,
                double apertureScale
        ) {
            this.sensorIso = sensorIso;
            this.sensorExposureNanos = sensorExposureNanos;
            this.sensorFrameDurationNanos = sensorFrameDurationNanos;
            this.residualBrightnessMultiplier = residualBrightnessMultiplier;
            this.apertureScale = apertureScale;
        }

        public int getSensorIso() {
            return sensorIso;
        }

        public long getSensorExposureNanos() {
            return sensorExposureNanos;
        }

        public double getResidualBrightnessMultiplier() {
            return residualBrightnessMultiplier;
        }

        public long getSensorFrameDurationNanos() {
            return sensorFrameDurationNanos;
        }

        public double getApertureScale() {
            return apertureScale;
        }

        public boolean isHardwareRangeLimited() {
            return residualBrightnessMultiplier < 0.97
                    || residualBrightnessMultiplier > 1.03;
        }
    }
}
