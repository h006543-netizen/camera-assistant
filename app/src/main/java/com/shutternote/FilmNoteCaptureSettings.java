package com.shutternote;

/** Maps record-only millimetres to the supported preview choices on note entry. */
final class FilmNoteCaptureSettings {
    static String fieldOfView(String focalLength, String restored) {
        if (ExposureSettingsStore.isValidFieldOfView(restored)) return restored;
        if (focalLength != null) {
            try {
                double mm = Double.parseDouble(focalLength.trim().replace(',', '.'));
                if (mm == 35) return ExposureSettingsStore.FIELD_OF_VIEW_35_MM;
                if (mm == 40) return ExposureSettingsStore.FIELD_OF_VIEW_40_MM;
                if (mm == 50) return ExposureSettingsStore.FIELD_OF_VIEW_50_MM;
            } catch (NumberFormatException ignored) { }
        }
        return ExposureSettingsStore.FIELD_OF_VIEW_DEFAULT;
    }
}
