package com.shutternote;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class FilmPostPhotoLocationTest {
    private static final long PHOTO_READY = 100_000_000_000L;
    private final FilmFrameLocation bridge = new FilmFrameLocation(37.514, 126.996, 8);

    @Test public void longExposureDoesNotConsumeTheTenSecondPostPhotoWindow() {
        long shutter = PHOTO_READY - 30_000_000_000L;
        FilmPostPhotoLocation request = new FilmPostPhotoLocation(PHOTO_READY, null, false);
        assertEquals(10_000_000_000L, request.remainingNanos(PHOTO_READY));
        FilmCaptureLocationSample sample = new FilmCaptureLocationSample(bridge, PHOTO_READY + 9_000_000_000L);
        assertNull(sample.forPhoto(shutter, PHOTO_READY + 9_000_000_000L));
        request.complete(sample.forPhoto(PHOTO_READY, PHOTO_READY + 9_000_000_000L), PHOTO_READY + 9_000_000_000L);
        assertSame(bridge, request.position());
    }

    @Test public void photoCanFinishAndLeaveResultBeforeLocationArrives() {
        FilmPostPhotoLocation request = new FilmPostPhotoLocation(PHOTO_READY, null, false);
        request.saving();
        FilmFrameRecord frame = new FilmFrameRecord(); frame.capturedAt = 123;
        frame.location = request.position(); // Immediate snapshot; unresolved GPS cannot block saving.
        request.saved(); request.release();
        assertFalse(request.isComplete()); assertFalse(request.abandoned()); assertFalse(request.removable());
        assertNull(frame.location); assertNull(request.claimAttachment());
        request.complete(bridge, PHOTO_READY + 8_000_000_000L);
        assertSame(bridge, request.claimAttachment()); assertNull(request.claimAttachment());
        assertTrue(request.removable()); assertEquals(123, frame.capturedAt);
    }

    @Test public void locationArrivingBeforePhotoIsAvailableToTheSaveSnapshot() {
        FilmPostPhotoLocation request = new FilmPostPhotoLocation(PHOTO_READY, null, false);
        request.saving(); request.complete(bridge, PHOTO_READY + 1);
        assertSame(bridge, request.position()); assertNull(request.claimAttachment());
        request.saved(); assertSame(bridge, request.claimAttachment()); assertNull(request.claimAttachment());
    }

    @Test public void concurrentPhotoAndLocationCompletionClaimOnlyOneAttachment() throws Exception {
        FilmPostPhotoLocation request = new FilmPostPhotoLocation(PHOTO_READY, null, false);
        request.saving();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger attachments = new AtomicInteger();
        Thread saving = new Thread(() -> {
            await(start); request.saved();
            if (request.claimAttachment() != null) attachments.incrementAndGet();
        });
        Thread locating = new Thread(() -> {
            await(start); request.complete(bridge, PHOTO_READY + 1);
            if (request.claimAttachment() != null) attachments.incrementAndGet();
        });
        saving.start(); locating.start(); start.countDown(); saving.join(1000); locating.join(1000);
        assertFalse(saving.isAlive()); assertFalse(locating.isAlive());
        assertEquals(1, attachments.get()); assertNull(request.claimAttachment());
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException error) { throw new AssertionError(error); }
    }

    @Test public void releasedSavingPhotoKeepsItsLocationRequestUntilPhotoCommit() {
        FilmPostPhotoLocation request = new FilmPostPhotoLocation(PHOTO_READY, null, false);
        request.saving(); request.release();
        assertFalse(request.abandoned());
        request.complete(bridge, PHOTO_READY + 1); assertNull(request.claimAttachment());
        request.saved(); assertSame(bridge, request.claimAttachment());
    }

    @Test public void discardedPhotoAndLateCallbackCannotAttachLocation() {
        FilmPostPhotoLocation request = new FilmPostPhotoLocation(PHOTO_READY, null, false);
        request.release(); assertTrue(request.abandoned()); assertTrue(request.removable());
        request.complete(null, PHOTO_READY + 1); request.complete(bridge, PHOTO_READY + 2);
        assertTrue(request.isComplete()); assertNull(request.position()); assertNull(request.claimAttachment());
    }

    @Test public void failedPhotoCanRetryButReleasedFailedSaveIsAbandoned() {
        FilmPostPhotoLocation retry = new FilmPostPhotoLocation(PHOTO_READY, null, false);
        retry.saving(); retry.complete(bridge, PHOTO_READY + 1); retry.failed();
        assertFalse(retry.abandoned()); assertNull(retry.claimAttachment());
        retry.saving(); retry.saved(); assertSame(bridge, retry.claimAttachment());
        FilmPostPhotoLocation abandoned = new FilmPostPhotoLocation(PHOTO_READY, null, false);
        abandoned.saving(); abandoned.release(); abandoned.failed();
        assertTrue(abandoned.abandoned()); assertTrue(abandoned.removable());
    }

    @Test public void timeoutOrForegroundExitKeepsTheSavedPhotoWithoutLocation() {
        FilmPostPhotoLocation request = new FilmPostPhotoLocation(PHOTO_READY, null, false);
        request.saving(); request.saved(); request.complete(null, PHOTO_READY + 2);
        request.complete(bridge, PHOTO_READY + 3);
        assertTrue(request.removable()); assertNull(request.position()); assertNull(request.claimAttachment());
    }

    @Test public void differentPhotosRetainTheirOwnFixesInReverseCompletionOrder() {
        FilmPostPhotoLocation first = new FilmPostPhotoLocation(PHOTO_READY, null, false);
        FilmPostPhotoLocation second = new FilmPostPhotoLocation(PHOTO_READY + 1, null, false);
        FilmFrameLocation building = new FilmFrameLocation(37.6, 127.1, 20);
        first.saved(); second.saved();
        second.complete(building, PHOTO_READY + 2); first.complete(bridge, PHOTO_READY + 3);
        assertSame(bridge, first.claimAttachment()); assertSame(building, second.claimAttachment());
    }

    @Test public void expiredOrRebootedResultDoesNotRecordCurrentPosition() {
        FilmPostPhotoLocation expired = new FilmPostPhotoLocation(PHOTO_READY, null, false);
        assertEquals(0, expired.remainingNanos(PHOTO_READY + 10_000_000_000L));
        expired.complete(bridge, PHOTO_READY + 10_000_000_000L); assertNull(expired.position());
        FilmPostPhotoLocation rebooted = new FilmPostPhotoLocation(PHOTO_READY, null, false);
        assertEquals(0, rebooted.remainingNanos(1)); rebooted.complete(bridge, 1); assertNull(rebooted.position());
    }

    @Test public void resolvedPositionAndAbsenceSurviveRecreationWithoutASecondLookup() {
        FilmPostPhotoLocation restored = new FilmPostPhotoLocation(PHOTO_READY, bridge, true);
        restored.complete(new FilmFrameLocation(37.6, 127.1, 20), PHOTO_READY + 1);
        assertTrue(restored.isComplete()); assertSame(bridge, restored.position());
        FilmPostPhotoLocation missing = new FilmPostPhotoLocation(PHOTO_READY, null, true);
        missing.complete(bridge, PHOTO_READY + 1); assertTrue(missing.isComplete()); assertNull(missing.position());
        assertTrue(new FilmPostPhotoLocation(0, null, false).isComplete());
    }
}
