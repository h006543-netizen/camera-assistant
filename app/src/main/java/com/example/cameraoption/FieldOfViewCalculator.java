package com.example.cameraoption;

/**
 * 스마트폰 카메라 메타데이터를 35mm 필름 환산 화각용 줌 배율로 변환한다.
 */
public final class FieldOfViewCalculator {

    static final double DEFAULT_PHONE_EQUIVALENT_MM = 24.0;

    private static final double FULL_FRAME_WIDTH_MM = 36.0;
    private static final double FULL_FRAME_HEIGHT_MM = 24.0;
    private static final double FULL_FRAME_DIAGONAL_MM = Math.hypot(
            FULL_FRAME_WIDTH_MM,
            FULL_FRAME_HEIGHT_MM
    );
    private static final double TYPICAL_MAIN_CAMERA_MIN_MM = 18.0;
    private static final double TYPICAL_MAIN_CAMERA_MAX_MM = 32.0;

    private FieldOfViewCalculator() {
    }

    static double calculateEquivalentFocalLength(
            double actualFocalLengthMm,
            double sensorWidthMm,
            double sensorHeightMm
    ) {
        if (!Double.isFinite(actualFocalLengthMm)
                || !Double.isFinite(sensorWidthMm)
                || !Double.isFinite(sensorHeightMm)
                || actualFocalLengthMm <= 0.0
                || sensorWidthMm <= 0.0
                || sensorHeightMm <= 0.0) {
            return Double.NaN;
        }

        double sensorDiagonalMm = Math.hypot(sensorWidthMm, sensorHeightMm);
        return actualFocalLengthMm * FULL_FRAME_DIAGONAL_MM / sensorDiagonalMm;
    }

    static double chooseBaseEquivalentFocalLength(
            float[] availableFocalLengthsMm,
            double sensorWidthMm,
            double sensorHeightMm
    ) {
        if (availableFocalLengthsMm == null || availableFocalLengthsMm.length == 0) {
            return DEFAULT_PHONE_EQUIVALENT_MM;
        }

        double firstValidEquivalent = Double.NaN;
        double closestMainEquivalent = Double.NaN;
        double closestMainDistance = Double.MAX_VALUE;

        for (float focalLengthMm : availableFocalLengthsMm) {
            double equivalent = calculateEquivalentFocalLength(
                    focalLengthMm,
                    sensorWidthMm,
                    sensorHeightMm
            );
            if (!Double.isFinite(equivalent) || equivalent <= 0.0) {
                continue;
            }

            if (!Double.isFinite(firstValidEquivalent)) {
                firstValidEquivalent = equivalent;
            }

            if (equivalent >= TYPICAL_MAIN_CAMERA_MIN_MM
                    && equivalent <= TYPICAL_MAIN_CAMERA_MAX_MM) {
                double distance = Math.abs(equivalent - DEFAULT_PHONE_EQUIVALENT_MM);
                if (distance < closestMainDistance) {
                    closestMainEquivalent = equivalent;
                    closestMainDistance = distance;
                }
            }
        }

        if (Double.isFinite(closestMainEquivalent)) {
            return closestMainEquivalent;
        }
        if (Double.isFinite(firstValidEquivalent)) {
            return firstValidEquivalent;
        }
        return DEFAULT_PHONE_EQUIVALENT_MM;
    }

    static float calculateZoomRatio(
            String selectedFieldOfView,
            double baseEquivalentFocalLengthMm
    ) {
        if (!Double.isFinite(baseEquivalentFocalLengthMm)
                || baseEquivalentFocalLengthMm <= 0.0
                || ExposureSettingsStore.FIELD_OF_VIEW_DEFAULT.equals(selectedFieldOfView)) {
            return 1.0f;
        }

        double targetEquivalentFocalLengthMm;
        switch (selectedFieldOfView) {
            case ExposureSettingsStore.FIELD_OF_VIEW_35_MM:
                targetEquivalentFocalLengthMm = 35.0;
                break;
            case ExposureSettingsStore.FIELD_OF_VIEW_40_MM:
                targetEquivalentFocalLengthMm = 40.0;
                break;
            case ExposureSettingsStore.FIELD_OF_VIEW_50_MM:
                targetEquivalentFocalLengthMm = 50.0;
                break;
            default:
                return 1.0f;
        }

        return (float) (targetEquivalentFocalLengthMm / baseEquivalentFocalLengthMm);
    }

    static float clampZoomRatio(float requestedZoomRatio, float minZoomRatio, float maxZoomRatio) {
        return Math.max(minZoomRatio, Math.min(requestedZoomRatio, maxZoomRatio));
    }
}
