package com.shutternote;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class FilmFrameCorrectionRecordTest {
    @Test public void legacyCutsOpenAsOriginalWithoutChangingTheirPhoto() throws Exception {
        FilmFrameRecord frame = FilmFrameRecord.fromJson(new JSONObject().put("number", 7).put("photo", "old.jpg"));
        assertFalse(frame.correctionEnabled); assertEquals("old.jpg", frame.displayPhotoName());
        assertEquals("", frame.correctionPhoto); assertEquals(0, frame.correctionRevision);
    }
    @Test public void selectedCorrectionRoundTripsAndCanReturnToTheSameOriginal() throws Exception {
        FilmFrameRecord frame = new FilmFrameRecord(); frame.number = 13; frame.photo = "original.jpg";
        frame.correctionProfile = FilmColorCorrection.E100D;
        frame.correctionEnabled = true; frame.correctionRevision = 4; frame.memo = "keep";
        FilmFrameRecord restored = FilmFrameRecord.fromJson(frame.toJson());
        assertEquals("original.jpg", restored.displayPhotoName()); assertEquals(FilmColorCorrection.E100D, restored.correctionProfile);
        assertTrue(restored.correctionEnabled); assertEquals("",restored.correctionPhoto);
        assertEquals(4, restored.correctionRevision); restored.correctionEnabled = false;
        assertEquals("original.jpg", restored.displayPhotoName()); assertEquals("", restored.correctionPhoto);
        assertEquals("keep", restored.memo);
    }
    @Test public void unsafeCorrectionPathsCannotBecomePhotoSources() throws Exception {
        for (String name : new String[]{"../other.jpg", "C:\\other.jpg", "/other.jpg", "other.png", ""}) {
            FilmFrameRecord frame = FilmFrameRecord.fromJson(new JSONObject().put("number", 1).put("photo", "original.jpg")
                    .put("correctionPhoto", name).put("correctionEnabled", true));
            assertFalse(frame.correctionEnabled); assertEquals("original.jpg", frame.displayPhotoName());
        }
    }
    @Test public void rotationAndRecordEditsPreserveCorrectionAndOriginalMetadata() {
        FilmRollRecord roll = new FilmRollRecord(); FilmFrameRecord frame = new FilmFrameRecord();
        frame.number = 1; frame.captureId = "shot"; frame.photo = "original.jpg";
        frame.correctionPhoto = "corrected.jpg"; frame.correctionEnabled = true; roll.frames.add(frame);
        assertTrue(FilmFrameRecord.rotate(roll, 1, "shot"));
        assertEquals("original.jpg", frame.photo); assertEquals("corrected.jpg", frame.displayPhotoName());
        assertEquals(270, frame.rotation); assertTrue(frame.correctionEnabled);
    }

    @Test public void aCorrectionReferenceCannotAliasTheOriginal() throws Exception {
        FilmFrameRecord frame = FilmFrameRecord.fromJson(new JSONObject().put("number", 1).put("photo", "original.jpg")
                .put("correctionPhoto", "original.jpg").put("correctionEnabled", true));
        assertFalse(frame.correctionEnabled); assertEquals("", frame.correctionPhoto);
        assertEquals("original.jpg", frame.displayPhotoName());
    }
}
