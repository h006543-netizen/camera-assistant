package com.shutternote;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 촬영 설정과 시각을 갤러리에서 확인할 수 있는 JPEG 파일명으로 만든다. */
final class GalleryFileNameBuilder {

    private GalleryFileNameBuilder() {
    }

    static String build(
            String iso,
            String aperture,
            String shutter,
            String fieldOfView,
            Date capturedAt
    ) {
        String timestamp = new SimpleDateFormat(
                "yyMMdd_HHmm",
                Locale.US
        ).format(capturedAt);
        return buildWithTimestamp(
                iso,
                aperture,
                shutter,
                fieldOfView,
                timestamp
        );
    }

    static String buildWithTimestamp(
            String iso,
            String aperture,
            String shutter,
            String fieldOfView,
            String timestamp
    ) {
        return "ISO" + compactToken(iso, "Unknown")
                + "_" + apertureToken(aperture)
                + "_" + shutterToken(shutter)
                + "_" + fieldOfViewToken(fieldOfView)
                + "_" + compactToken(timestamp, "UnknownTime")
                + ".jpg";
    }

    static String numberedName(String fileName, int number) {
        if (number == 0) return fileName;
        return fileName.substring(0, fileName.length() - 4) + "(" + number + ").jpg";
    }

    static String buildNote(FilmFrameRecord frame, String rollName) {
        String base = build(frame.iso, frame.aperture, frame.shutter, frame.fieldOfView, new Date(frame.capturedAt));
        String film = "원본";
        if (frame.correctionEnabled) {
            switch (FilmCorrectionProfiles.key(frame.selectedProfile())) {
                case "vision3-50d": film = "비전3_50D"; break;
                case "e100d": film = "엑타크롬_E100D"; break;
                case "vision3-250d": film = "비전3_250D"; break;
                case "cine800t": film = "씨네스틸_800T"; break;
                case "vision3-500t": film = "비전3_500T"; break;
                case "portra400": film = "포트라_400"; break;
                default: film = "보정본";
            }
        }
        String name = rollName == null ? "" : rollName.replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_").trim();
        // Leave room for the exposure, film, extension and collision suffix on UTF-8 filesystems.
        StringBuilder safe = new StringBuilder();
        int bytes = 0;
        for (int offset = 0; offset < name.length();) {
            int point = name.codePointAt(offset);
            int length = point <= 0x7f ? 1 : point <= 0x7ff ? 2 : point <= 0xffff ? 3 : 4;
            if (bytes + length > 120) break;
            safe.appendCodePoint(point); bytes += length; offset += Character.charCount(point);
        }
        return base.substring(0, base.length() - 4) + "_" + safe + "_" + film + ".jpg";
    }

    static String availableName(String fileName, java.util.Set<String> existing) {
        int number = 0;
        while (existing.contains(numberedName(fileName, number))) number++;
        return numberedName(fileName, number);
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
