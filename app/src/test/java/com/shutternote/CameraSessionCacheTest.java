package com.shutternote;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class CameraSessionCacheTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void keepsExistingCaptureAndRemovesMissingCapture() throws Exception {
        File existing = temporary.newFile("recent.jpg");
        File missing = new File(temporary.getRoot(), "missing.jpg");
        assertEquals(Collections.singletonList(missing.getAbsolutePath()),
                CameraSessionCache.missingCapturePaths(Arrays.asList(
                        existing.getAbsolutePath(), missing.getAbsolutePath())));
    }

    @Test public void removesRecordAfterCaptureIsDeleted() throws Exception {
        File capture = temporary.newFile("expired.jpg");
        assertTrue(CameraSessionCache.missingCapturePaths(
                Collections.singletonList(capture.getAbsolutePath())).isEmpty());
        assertTrue(capture.delete());
        assertEquals(Collections.singletonList(capture.getAbsolutePath()),
                CameraSessionCache.missingCapturePaths(Collections.singletonList(capture.getAbsolutePath())));
    }
}
