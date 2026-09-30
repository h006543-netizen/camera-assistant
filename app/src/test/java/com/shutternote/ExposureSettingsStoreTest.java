package com.shutternote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExposureSettingsStoreTest {

    @Test
    public void validators_acceptOnlySupportedExposureValues() {
        assertTrue(ExposureSettingsStore.isValidIso("800"));
        assertTrue(ExposureSettingsStore.isValidIso("80"));
        assertTrue(ExposureSettingsStore.isValidAperture("f/5.6"));
        assertTrue(ExposureSettingsStore.isValidShutter("1/60"));
        assertTrue(ExposureSettingsStore.isValidFieldOfView("40mm"));

        assertFalse(ExposureSettingsStore.isValidIso("999"));
        assertFalse(ExposureSettingsStore.isValidAperture("f/3.5"));
        assertFalse(ExposureSettingsStore.isValidShutter("1/90"));
        assertFalse(ExposureSettingsStore.isValidFieldOfView("85mm"));
    }

    @Test
    public void normalizers_replaceInvalidValuesWithDefaults() {
        assertEquals("80", ExposureSettingsStore.normalizeIso("80"));
        assertEquals(
                ExposureSettingsStore.DEFAULT_ISO,
                ExposureSettingsStore.normalizeIso(null)
        );
        assertEquals(
                ExposureSettingsStore.DEFAULT_APERTURE,
                ExposureSettingsStore.normalizeAperture("f/3.5")
        );
        assertEquals(
                ExposureSettingsStore.DEFAULT_SHUTTER,
                ExposureSettingsStore.normalizeShutter("1/90")
        );
        assertEquals(
                ExposureSettingsStore.FIELD_OF_VIEW_DEFAULT,
                ExposureSettingsStore.normalizeFieldOfView("85mm")
        );
    }
}
