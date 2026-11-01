package com.shutternote;

import org.json.JSONObject;
import org.junit.Test;
import java.util.Locale;
import static org.junit.Assert.*;

public class FilmFrameLocationTest {
    @Test public void oldFrameHasNoLocationAndRetainsCaptureData() throws Exception {
        FilmFrameRecord frame = FilmFrameRecord.fromJson(new JSONObject("{\"number\":10,\"captureId\":\"old\",\"memo\":\"keep\",\"rotation\":270}"));
        assertNull(frame.location); assertEquals("keep", frame.memo); assertEquals(270, frame.rotation);
    }
    @Test public void savedLocationRoundTripsWithoutSwappingCoordinates() throws Exception {
        FilmFrameRecord frame = new FilmFrameRecord(); frame.number = 10;
        frame.location = new FilmFrameLocation(37.566535, 126.9779692, 123.5);
        FilmFrameRecord loaded = FilmFrameRecord.fromJson(frame.toJson());
        assertEquals(37.566535, loaded.location.latitude, 0); assertEquals(126.9779692, loaded.location.longitude, 0);
        assertEquals(123.5, loaded.location.accuracyMeters, 0);
    }
    @Test public void malformedLocationDoesNotDiscardAnExistingFrame() throws Exception {
        FilmFrameRecord frame = FilmFrameRecord.fromJson(new JSONObject("{\"number\":1,\"photo\":\"keep.jpg\",\"location\":{\"latitude\":91,\"longitude\":10}}"));
        assertNull(frame.location); assertEquals("keep.jpg", frame.photo);
        assertNull(FilmFrameLocation.fromJson(new JSONObject("{\"longitude\":12}")));
    }
    @Test public void acceptsBoundariesAndZeroButRejectsInvalidOrNonFinitePositions() {
        assertTrue(FilmFrameLocation.valid(0, 0)); assertTrue(FilmFrameLocation.valid(-90, -180)); assertTrue(FilmFrameLocation.valid(90, 180));
        assertFalse(FilmFrameLocation.valid(90.01, 10)); assertFalse(FilmFrameLocation.valid(10, -180.01));
        assertFalse(FilmFrameLocation.valid(Double.NaN, 10)); assertFalse(FilmFrameLocation.valid(10, Double.POSITIVE_INFINITY));
        assertNull(FilmFrameLocation.parse("", "126")); assertNull(FilmFrameLocation.parse("NaN", "10"));
    }
    @Test public void linksExactSignedCoordinatesRegardlessOfDeviceLocale() {
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            assertEquals("https://www.google.com/maps/search/?api=1&query=-33.8688%2C151.2093", new FilmFrameLocation(-33.8688, 151.2093, -1).googleMapsUrl());
            assertEquals("https://www.google.com/maps/search/?api=1&query=40.7128%2C-74.006", new FilmFrameLocation(40.7128, -74.006, -1).googleMapsUrl());
            assertEquals("37.566500, 126.978000", new FilmFrameLocation(37.5665, 126.978, -1).coordinates());
        } finally { Locale.setDefault(old); }
    }
    @Test public void editingLocationRequiresMatchingCaptureAndPreservesOtherMetadata() {
        FilmRollRecord roll = new FilmRollRecord(); FilmFrameRecord first = new FilmFrameRecord(), other = new FilmFrameRecord();
        first.number=1; first.captureId="new"; first.memo="memo"; first.photo="photo.jpg"; first.rotation=270; first.iso="100";
        other.number=2; other.captureId="other"; roll.name="roll"; roll.frames.add(first); roll.frames.add(other);
        FilmFrameLocation point = new FilmFrameLocation(0, 0, -1);
        assertFalse(FilmFrameLocation.apply(roll, 1, "old", point)); assertFalse(FilmFrameLocation.apply(roll, 3, "new", point));
        assertNull(first.location); assertTrue(FilmFrameLocation.apply(roll, 1, "new", point));
        assertSame(point, first.location); assertNull(other.location); assertEquals("memo", first.memo); assertEquals("photo.jpg", first.photo);
        assertEquals(270, first.rotation); assertEquals("100", first.iso); assertEquals("roll", roll.name);
    }
    @Test public void removingAndSerializingLocationDoesNotCreateZeroCoordinate() throws Exception {
        FilmRollRecord roll = new FilmRollRecord(); FilmFrameRecord frame = new FilmFrameRecord(); frame.number=1; frame.captureId="shot";
        frame.location=new FilmFrameLocation(37, 127, -1); roll.frames.add(frame);
        assertTrue(FilmFrameLocation.apply(roll, 1, "shot", null));
        assertNull(FilmFrameRecord.fromJson(frame.toJson()).location);
    }
    @Test public void manualInputSupportsDecimalCommaAndDoesNotClaimGpsAccuracy() {
        FilmFrameLocation point = FilmFrameLocation.parse(" 37,5665 ", "126,978");
        assertNotNull(point); assertEquals(37.5665, point.latitude, 0); assertEquals(-1, point.accuracyMeters, 0);
        assertNull(FilmFrameLocation.parse("37,5,4", "126"));
    }

    @Test public void lateAutomaticLocationOnlyFillsTheMatchingExistingPhoto() {
        FilmRollRecord roll = new FilmRollRecord(); FilmFrameRecord frame = new FilmFrameRecord();
        frame.number=1; frame.captureId="new"; frame.memo="edited"; frame.rotation=270;
        roll.frames.add(frame);
        FilmFrameLocation point = new FilmFrameLocation(37, 127, 8);
        assertFalse(FilmFrameLocation.applyCaptured(roll, 1, "old", point));
        assertFalse(FilmFrameLocation.applyCaptured(roll, 2, "new", point));
        assertFalse(FilmFrameLocation.applyCaptured(roll, 1, "new", null));
        assertTrue(FilmFrameLocation.applyCaptured(roll, 1, "new", point));
        assertSame(point, frame.location); assertEquals("edited", frame.memo); assertEquals(270, frame.rotation);
        assertFalse(FilmFrameLocation.applyCaptured(roll, 1, "new", new FilmFrameLocation(38, 128, 10)));
        roll.frames.clear(); assertFalse(FilmFrameLocation.applyCaptured(roll, 1, "new", point));
    }

    @Test public void manualEditThenRemovalSurvivesReloadAndBlocksLateGps() throws Exception {
        FilmRollRecord roll = new FilmRollRecord(); FilmFrameRecord frame = new FilmFrameRecord();
        frame.number=1; frame.captureId="shot"; roll.frames.add(frame);
        FilmFrameLocation point = new FilmFrameLocation(37, 127, -1);
        assertTrue(FilmFrameLocation.apply(roll, 1, "shot", point));
        assertFalse(FilmFrameLocation.applyCaptured(roll, 1, "shot", new FilmFrameLocation(38, 128, 8)));
        assertTrue(FilmFrameLocation.apply(roll, 1, "shot", null));
        FilmFrameRecord loaded = FilmFrameRecord.fromJson(frame.toJson());
        assertEquals(2, loaded.locationRevision); assertNull(loaded.location);
        roll.frames.clear(); roll.frames.add(loaded);
        assertFalse(FilmFrameLocation.applyCaptured(roll, 1, "shot", point));
        assertNull(loaded.location);
        assertEquals(0, FilmFrameRecord.fromJson(new JSONObject("{\"number\":1,\"captureId\":\"old\"}")).locationRevision);
    }
}
