package com.shutternote;

import org.junit.Test;
import static org.junit.Assert.*;

public class FilmCaptureLocationSampleTest {
    private static final long PHOTO_READY = 100_000_000_000L;
    private final FilmFrameLocation bridge = new FilmFrameLocation(37.514, 126.996, 8);

    @Test public void recentPositionIsFrozenWhileTheUserWaitsOnResultScreen() throws Exception {
        FilmCaptureLocationSample sample = new FilmCaptureLocationSample(bridge, PHOTO_READY - 1_000_000_000L);
        FilmFrameRecord frame = new FilmFrameRecord();
        frame.capturedAt = 123456789L; frame.location = sample.forPhoto(PHOTO_READY, PHOTO_READY);
        // A later fix at another building cannot mutate the position passed to the result.
        sample = new FilmCaptureLocationSample(new FilmFrameLocation(37.6, 127.1, 10), PHOTO_READY + 60_000_000_000L);
        FilmFrameRecord stored = FilmFrameRecord.fromJson(frame.toJson());
        assertEquals(bridge.latitude, stored.location.latitude, 0);
        assertEquals(bridge.longitude, stored.location.longitude, 0);
        assertEquals(123456789L, stored.capturedAt);
        assertNull(sample.forPhoto(PHOTO_READY, PHOTO_READY + 60_000_000_000L));
    }

    @Test public void rejectsAnOldFixInsteadOfAssigningAnotherPlaceToThePhoto() {
        assertNull(new FilmCaptureLocationSample(bridge, PHOTO_READY - 15_000_000_001L).forPhoto(PHOTO_READY, PHOTO_READY));
        assertSame(bridge, new FilmCaptureLocationSample(bridge, PHOTO_READY - 15_000_000_000L).forPhoto(PHOTO_READY, PHOTO_READY));
    }

    @Test public void firstFixAfterPhotoIsAcceptedButLaterFixDoesNotFillTheShot() {
        assertSame(bridge, new FilmCaptureLocationSample(bridge, PHOTO_READY + 9_000_000_000L).forPhoto(PHOTO_READY, PHOTO_READY + 9_000_000_000L));
        assertSame(bridge, new FilmCaptureLocationSample(bridge, PHOTO_READY + 10_000_000_000L).forPhoto(PHOTO_READY, PHOTO_READY + 10_000_000_000L));
        assertNull(new FilmCaptureLocationSample(bridge, PHOTO_READY + 10_000_000_001L).forPhoto(PHOTO_READY, PHOTO_READY + 11_000_000_000L));
        assertNull(new FilmCaptureLocationSample(bridge, PHOTO_READY + 1).forPhoto(PHOTO_READY, PHOTO_READY));
        assertNull(new FilmCaptureLocationSample(bridge, 0).forPhoto(PHOTO_READY, PHOTO_READY));
    }

    @Test public void simultaneousNetworkFixDoesNotReplaceMoreAccurateGpsButMovementDoes() {
        FilmCaptureLocationSample gps = new FilmCaptureLocationSample(bridge, PHOTO_READY);
        FilmFrameLocation approximate = new FilmFrameLocation(37.51, 127, 2000);
        assertFalse(new FilmCaptureLocationSample(approximate, PHOTO_READY + 1_000_000_000L).betterThan(gps));
        assertTrue(new FilmCaptureLocationSample(approximate, PHOTO_READY + 10_000_000_000L).betterThan(gps));
        assertTrue(gps.betterThan(new FilmCaptureLocationSample(approximate, PHOTO_READY)));
    }

    @Test public void approximatePermissionRemainsUsableAndMissingFixNeverCreatesZeroCoordinate() {
        FilmFrameLocation approximate = new FilmFrameLocation(37.5, 127, 3000);
        assertSame(approximate, new FilmCaptureLocationSample(approximate, PHOTO_READY).forPhoto(PHOTO_READY, PHOTO_READY));
        assertNull(new FilmCaptureLocationSample(null, PHOTO_READY).forPhoto(PHOTO_READY, PHOTO_READY));
    }
}
