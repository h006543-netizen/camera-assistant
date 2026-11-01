package com.shutternote;

/** A bounded position sample around the photo-completion time, independent of shutter execution. */
final class FilmCaptureLocationSample {
    static final long MAX_AGE_NANOS = 15_000_000_000L;
    static final long WAIT_NANOS = 10_000_000_000L;
    final FilmFrameLocation position;
    final long measuredAtNanos;

    FilmCaptureLocationSample(FilmFrameLocation position, long measuredAtNanos) {
        this.position = position;
        this.measuredAtNanos = measuredAtNanos;
    }

    FilmFrameLocation forPhoto(long referenceNanos, long nowNanos) {
        if (position == null || measuredAtNanos <= 0 || measuredAtNanos > nowNanos) return null;
        long delta = measuredAtNanos - referenceNanos;
        return delta >= -MAX_AGE_NANOS && delta <= WAIT_NANOS ? position : null;
    }

    boolean betterThan(FilmCaptureLocationSample other) {
        if (other == null) return true;
        // A simultaneous coarse network fix must not replace a precise GPS fix.
        long delta = measuredAtNanos - other.measuredAtNanos;
        if (Math.abs(delta) <= 2_000_000_000L
                && position.accuracyMeters >= 0 && other.position.accuracyMeters >= 0
                && position.accuracyMeters != other.position.accuracyMeters) {
            return position.accuracyMeters < other.position.accuracyMeters;
        }
        return delta > 0;
    }
}
