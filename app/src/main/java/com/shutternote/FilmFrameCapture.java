package com.shutternote;

import android.content.Intent;

final class FilmFrameCapture {
    static final String ROLL = "note_capture_roll";
    static final String NUMBER = "note_capture_number";
    private static final String CAPTURED_AT = "note_capture_time";
    private static final String CAPTURE_OFFSET = "note_capture_offset_minutes";
    private static final String LATITUDE = "note_capture_latitude";
    private static final String LONGITUDE = "note_capture_longitude";
    private static final String ACCURACY = "note_capture_accuracy";
    private static final String PHOTO_READY_NANOS = "note_photo_ready_nanos";
    static boolean isNote(Intent intent) {
        String id = intent.getStringExtra(ROLL);
        return id != null && !id.isEmpty() && intent.getIntExtra(NUMBER, 0) > 0;
    }
    static void forward(Intent from, Intent to) {
        if (isNote(from)) to.putExtra(ROLL, from.getStringExtra(ROLL))
                .putExtra(NUMBER, from.getIntExtra(NUMBER, 0));
    }
    static void setShot(Intent intent, long capturedAt, FilmFrameLocation position) {
        intent.putExtra(CAPTURED_AT, capturedAt);
        intent.putExtra(CAPTURE_OFFSET, java.util.TimeZone.getDefault().getOffset(capturedAt)/60000);
        if (position != null) intent.putExtra(LATITUDE, position.latitude)
                .putExtra(LONGITUDE, position.longitude).putExtra(ACCURACY, position.accuracyMeters);
        else {
            intent.removeExtra(LATITUDE); intent.removeExtra(LONGITUDE); intent.removeExtra(ACCURACY);
        }
    }
    static FilmFrameLocation location(Intent intent) {
        double latitude = intent.getDoubleExtra(LATITUDE, Double.NaN);
        double longitude = intent.getDoubleExtra(LONGITUDE, Double.NaN);
        return FilmFrameLocation.valid(latitude, longitude)
                ? new FilmFrameLocation(latitude, longitude, intent.getDoubleExtra(ACCURACY, -1)) : null;
    }
    static long capturedAt(Intent intent, long fileTime) {
        long time = intent.getLongExtra(CAPTURED_AT, 0);
        return time > 0 ? time : fileTime;
    }
    static double captureHour(Intent intent) {
        return Vision500SceneTime.fromSnapshot(intent.getLongExtra(CAPTURED_AT,0),
                intent.getIntExtra(CAPTURE_OFFSET,Integer.MIN_VALUE));
    }
    static void requestLocationAfterPhoto(Intent intent, long photoReadyNanos) {
        intent.putExtra(PHOTO_READY_NANOS, photoReadyNanos);
    }
    static long photoReadyNanos(Intent intent) {
        return intent.getLongExtra(PHOTO_READY_NANOS, 0);
    }
}
