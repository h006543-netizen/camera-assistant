package com.shutternote;

import org.junit.Test;
import static org.junit.Assert.*;

public class FilmNoteCaptureSettingsTest {
    @Test public void rollChoicesMapToPreviewValues() {
        assertEquals("28mm", FilmNoteCaptureSettings.fieldOfView("28", null));
        assertEquals("32mm", FilmNoteCaptureSettings.fieldOfView("32", null));
        assertEquals("35mm", FilmNoteCaptureSettings.fieldOfView("35", null));
        assertEquals("40mm", FilmNoteCaptureSettings.fieldOfView("40", null));
        assertEquals("50mm", FilmNoteCaptureSettings.fieldOfView("50", null));
        assertEquals("50mm", FilmNoteCaptureSettings.fieldOfView("50.0", null));
    }
    @Test public void manualSessionChoiceSurvivesRecreationButNewEntryUsesRoll() {
        assertEquals("40mm", FilmNoteCaptureSettings.fieldOfView("50", "40mm"));
        assertEquals("28mm", FilmNoteCaptureSettings.fieldOfView("32", "28mm"));
        assertEquals("32mm", FilmNoteCaptureSettings.fieldOfView("28", "32mm"));
        assertEquals(ExposureSettingsStore.FIELD_OF_VIEW_DEFAULT,
                FilmNoteCaptureSettings.fieldOfView("50", ExposureSettingsStore.FIELD_OF_VIEW_DEFAULT));
        assertEquals("50mm", FilmNoteCaptureSettings.fieldOfView("50", null));
    }
    @Test public void unknownRecordsAreNotGuessedAndInvalidRestorationIsIgnored() {
        for (String focal : new String[]{null, "", "30", "85", "bad", "NaN"}) {
            assertEquals(ExposureSettingsStore.FIELD_OF_VIEW_DEFAULT, FilmNoteCaptureSettings.fieldOfView(focal, null));
        }
        assertEquals("50mm", FilmNoteCaptureSettings.fieldOfView("50", "bad"));
    }
}
