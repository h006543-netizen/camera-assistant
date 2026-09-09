package com.example.cameraoption;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.List;

/**
 * 사용자가 마지막으로 선택한 노출 설정을 앱 내부 저장소에 보관한다.
 */
public final class ExposureSettingsStore {

    public static final String DEFAULT_ISO = "200";
    public static final String DEFAULT_APERTURE = "f/2.8";
    public static final String DEFAULT_SHUTTER = "1/125";
    public static final String FIELD_OF_VIEW_DEFAULT = "기본";
    public static final String FIELD_OF_VIEW_35_MM = "35mm";
    public static final String FIELD_OF_VIEW_40_MM = "40mm";
    public static final String FIELD_OF_VIEW_50_MM = "50mm";

    private static final String PREFERENCES_NAME = "exposure_settings";
    private static final String KEY_ISO = "iso";
    private static final String KEY_APERTURE = "aperture";
    private static final String KEY_SHUTTER = "shutter";
    private static final String KEY_FIELD_OF_VIEW = "field_of_view";

    private static final String[] ISO_VALUES = {
            "50", "64", "80", "100", "125", "160", "200", "250", "320",
            "400", "500", "640", "800", "1000", "1250", "1600", "3200"
    };

    private static final String[] APERTURE_VALUES = {
            "f/1.4", "f/1.8", "f/2", "f/2.8", "f/4",
            "f/5.6", "f/8", "f/11", "f/16", "f/22"
    };

    private static final String[] SHUTTER_VALUES = {
            "1", "1/2", "1/4", "1/8", "1/15", "1/30",
            "1/60", "1/125", "1/250", "1/500", "1/1000", "1/2000"
    };

    private static final String[] FIELD_OF_VIEW_VALUES = {
            FIELD_OF_VIEW_DEFAULT,
            FIELD_OF_VIEW_35_MM,
            FIELD_OF_VIEW_40_MM,
            FIELD_OF_VIEW_50_MM
    };

    private static final List<String> ISO_OPTIONS = Arrays.asList(ISO_VALUES);
    private static final List<String> APERTURE_OPTIONS = Arrays.asList(APERTURE_VALUES);
    private static final List<String> SHUTTER_OPTIONS = Arrays.asList(SHUTTER_VALUES);
    private static final List<String> FIELD_OF_VIEW_OPTIONS =
            Arrays.asList(FIELD_OF_VIEW_VALUES);

    private final SharedPreferences preferences;

    public ExposureSettingsStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
        );
    }

    public Values load() {
        return new Values(
                normalizeIso(preferences.getString(KEY_ISO, DEFAULT_ISO)),
                normalizeAperture(preferences.getString(KEY_APERTURE, DEFAULT_APERTURE)),
                normalizeShutter(preferences.getString(KEY_SHUTTER, DEFAULT_SHUTTER)),
                normalizeFieldOfView(
                        preferences.getString(KEY_FIELD_OF_VIEW, FIELD_OF_VIEW_DEFAULT)
                )
        );
    }

    public void save(String iso, String aperture, String shutter) {
        preferences.edit()
                .putString(KEY_ISO, normalizeIso(iso))
                .putString(KEY_APERTURE, normalizeAperture(aperture))
                .putString(KEY_SHUTTER, normalizeShutter(shutter))
                .apply();
    }

    public void save(String iso, String aperture, String shutter, String fieldOfView) {
        preferences.edit()
                .putString(KEY_ISO, normalizeIso(iso))
                .putString(KEY_APERTURE, normalizeAperture(aperture))
                .putString(KEY_SHUTTER, normalizeShutter(shutter))
                .putString(KEY_FIELD_OF_VIEW, normalizeFieldOfView(fieldOfView))
                .apply();
    }

    public static String[] isoValues() {
        return ISO_VALUES.clone();
    }

    public static String[] apertureValues() {
        return APERTURE_VALUES.clone();
    }

    public static String[] shutterValues() {
        return SHUTTER_VALUES.clone();
    }

    public static String[] fieldOfViewValues() {
        return FIELD_OF_VIEW_VALUES.clone();
    }

    static boolean isValidIso(String value) {
        return ISO_OPTIONS.contains(value);
    }

    static boolean isValidAperture(String value) {
        return APERTURE_OPTIONS.contains(value);
    }

    static boolean isValidShutter(String value) {
        return SHUTTER_OPTIONS.contains(value);
    }

    static boolean isValidFieldOfView(String value) {
        return FIELD_OF_VIEW_OPTIONS.contains(value);
    }

    static String normalizeIso(String value) {
        return isValidIso(value) ? value : DEFAULT_ISO;
    }

    static String normalizeAperture(String value) {
        return isValidAperture(value) ? value : DEFAULT_APERTURE;
    }

    static String normalizeShutter(String value) {
        return isValidShutter(value) ? value : DEFAULT_SHUTTER;
    }

    static String normalizeFieldOfView(String value) {
        return isValidFieldOfView(value) ? value : FIELD_OF_VIEW_DEFAULT;
    }

    public static final class Values {

        private final String iso;
        private final String aperture;
        private final String shutter;
        private final String fieldOfView;

        private Values(String iso, String aperture, String shutter, String fieldOfView) {
            this.iso = iso;
            this.aperture = aperture;
            this.shutter = shutter;
            this.fieldOfView = fieldOfView;
        }

        public String getIso() {
            return iso;
        }

        public String getAperture() {
            return aperture;
        }

        public String getShutter() {
            return shutter;
        }

        public String getFieldOfView() {
            return fieldOfView;
        }
    }
}
