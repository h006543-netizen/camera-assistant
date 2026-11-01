package com.shutternote;

import android.content.Intent;
import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Compile on the host; execution requires an Android device. */
@RunWith(AndroidJUnit4.class)
public class FilmFrameCaptureIntentTest {
    @Test public void shotPositionAndTimeSurviveResultIntentParceling() {
        Intent camera = new Intent().putExtra(FilmFrameCapture.ROLL, "roll").putExtra(FilmFrameCapture.NUMBER, 7);
        Intent result = new Intent(); FilmFrameCapture.forward(camera, result);
        FilmFrameCapture.setShot(result, 123456789L, new FilmFrameLocation(-33.8688, 151.2093, 3000));
        FilmFrameCapture.requestLocationAfterPhoto(result, 987654321L);
        Parcel parcel = Parcel.obtain();
        try {
            result.writeToParcel(parcel, 0); parcel.setDataPosition(0);
            Intent restored = Intent.CREATOR.createFromParcel(parcel);
            assertTrue(FilmFrameCapture.isNote(restored));
            assertEquals(7, restored.getIntExtra(FilmFrameCapture.NUMBER, 0));
            assertEquals(123456789L, FilmFrameCapture.capturedAt(restored, 99));
            assertEquals(FilmFrameCapture.captureHour(result),FilmFrameCapture.captureHour(restored),0);
            assertEquals(987654321L, FilmFrameCapture.photoReadyNanos(restored));
            FilmFrameLocation position = FilmFrameCapture.location(restored);
            assertEquals(-33.8688, position.latitude, 0); assertEquals(151.2093, position.longitude, 0);
            assertEquals(3000, position.accuracyMeters, 0);
        } finally { parcel.recycle(); }
    }

    @Test public void deniedOrMissingFixDoesNotReusePreviousPositionAndOldTimeUsesFile() {
        Intent result = new Intent();
        assertNull(FilmFrameCapture.location(result));
        assertEquals(0, FilmFrameCapture.photoReadyNanos(result));
        assertEquals(99, FilmFrameCapture.capturedAt(result, 99));
        assertTrue(Double.isNaN(FilmFrameCapture.captureHour(result)));
        FilmFrameCapture.setShot(result, 123, new FilmFrameLocation(0, 0, 2));
        assertNotNull(FilmFrameCapture.location(result));
        FilmFrameCapture.setShot(result, 456, null);
        assertNull(FilmFrameCapture.location(result));
        assertEquals(456, FilmFrameCapture.capturedAt(result, 99));
    }
    @Test public void captureHourUsesShotZoneAfterDeviceTimezoneChanges() {
        java.util.TimeZone previous=java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Tokyo"));
            Intent shot=new Intent();FilmFrameCapture.setShot(shot,1791948060000L,null);
            double captured=FilmFrameCapture.captureHour(shot);
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"));
            assertEquals(captured,FilmFrameCapture.captureHour(shot),0);
        }finally{java.util.TimeZone.setDefault(previous);}
    }
}
