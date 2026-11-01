package com.shutternote;

/** Nonblocking per-photo state. Photo and location completion may occur in either order. */
final class FilmPostPhotoLocation {
    private final long photoReadyNanos;
    private boolean complete, saved, saving, released, attachmentClaimed;
    private FilmFrameLocation position;

    FilmPostPhotoLocation(long photoReadyNanos, FilmFrameLocation initial, boolean restoredComplete) {
        this.photoReadyNanos = photoReadyNanos;
        if (restoredComplete || initial != null || photoReadyNanos <= 0) {
            position = initial;
            complete = true;
        }
    }

    long remainingNanos(long nowNanos) {
        if (photoReadyNanos <= 0 || nowNanos < photoReadyNanos) return 0;
        return Math.max(0, FilmCaptureLocationSample.WAIT_NANOS - (nowNanos - photoReadyNanos));
    }

    synchronized void complete(FilmFrameLocation next, long nowNanos) {
        if (complete) return;
        position = remainingNanos(nowNanos) > 0 ? next : null;
        complete = true;
    }

    synchronized boolean isComplete() { return complete; }
    synchronized FilmFrameLocation position() { return position; }
    synchronized void saving() { saving = true; }
    synchronized void saved() { saved = true; saving = false; }
    synchronized void failed() { saving = false; }
    synchronized void release() { released = true; }
    synchronized boolean abandoned() { return released && !saving && !saved; }
    synchronized boolean removable() { return abandoned() || (complete && saved); }
    synchronized FilmFrameLocation claimAttachment() {
        if (!saved || !complete || position == null || attachmentClaimed) return null;
        attachmentClaimed = true;
        return position;
    }
}
