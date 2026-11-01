package com.shutternote;

import androidx.exifinterface.media.ExifInterface;
import java.io.File;

final class FilmSceneMetadata {
    static double captureHour(File original, double snapshot) {
        // A stored snapshot is stable even after the note JPEG loses its EXIF during encoding.
        if (Vision500SceneTime.validHour(snapshot)) return snapshot;
        try {
            ExifInterface exif = new ExifInterface(original);
            return Vision500SceneTime.fromExif(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
                    exif.getAttribute("OffsetTimeOriginal"));
        } catch (java.io.IOException | RuntimeException ignored) { return Double.NaN; }
    }
}
