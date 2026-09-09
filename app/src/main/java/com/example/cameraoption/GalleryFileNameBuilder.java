package com.example.cameraoption;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 촬영 설정과 필름 정보를 갤러리에서 확인할 수 있는 JPEG 파일명으로 만든다. */
final class GalleryFileNameBuilder {

    private GalleryFileNameBuilder() {
    }

    static String build(
            String iso,
            String aperture,
            String shutter,
            String fieldOfView,
            String filmDisplayName,
            Date capturedAt
    ) {
        String timestamp = new SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
        ).format(capturedAt);
        return buildWithTimestamp(
                iso,
                aperture,
                shutter,
                fieldOfView,
                filmDisplayName,
                timestamp
        );
    }

    static String buildWithTimestamp(
            String iso,
            String aperture,
            String shutter,
            String fieldOfView,
            String filmDisplayName,
            String timestamp
    ) {
        return "ISO" + compactToken(iso, "Unknown")
                + "_" + apertureToken(aperture)
                + "_" + shutterToken(shutter)
                + "_" + fieldOfViewToken(fieldOfView)
                + "_" + filmToken(filmDisplayName)
                + "_" + compactToken(timestamp, "UnknownTime")
                + ".jpg";
    }

    private static String apertureToken(String aperture) {
        String value = aperture == null ? "" : aperture.trim();
        if (value.startsWith("f/")) {
            value = value.substring(2);
        } else if (value.startsWith("f")) {
            value = value.substring(1);
        }
        return "f" + compactToken(value, "Unknown");
    }

    private static String shutterToken(String shutter) {
        String value = shutter == null ? "" : shutter.trim().replace('/', '-');
        return compactToken(value, "UnknownShutter");
    }

    private static String fieldOfViewToken(String fieldOfView) {
        if (fieldOfView == null
                || ExposureSettingsStore.FIELD_OF_VIEW_DEFAULT.equals(fieldOfView)) {
            return "Default";
        }
        return compactToken(fieldOfView, "Default");
    }

    private static String filmToken(String filmDisplayName) {
        if (filmDisplayName == null
                || filmDisplayName.trim().isEmpty()
                || FilmProcessor.Preset.NONE.getDisplayName().equals(filmDisplayName)) {
            return "NoFilm";
        }

        String value = filmDisplayName.trim();
        if (value.startsWith("Kodak ")) {
            value = value.substring("Kodak ".length());
        }
        return compactToken(value, "NoFilm");
    }

    private static String compactToken(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        StringBuilder token = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isLetterOrDigit(character)
                    || character == '.'
                    || character == '-'
                    || character == '_') {
                token.append(character);
            }
        }
        return token.length() == 0 ? fallback : token.toString();
    }
}
