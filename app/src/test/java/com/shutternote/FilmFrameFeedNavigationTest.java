package com.shutternote;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class FilmFrameFeedNavigationTest {
    @Test public void sparseCutsKeepActualNumbersInPhysicalOrder() {
        assertEquals(Arrays.asList(1, 7, 36), FilmFrameNavigation.recordedNumbers(36, Arrays.asList(36, 1, 7)));
    }
    @Test public void invalidOrDuplicateRecordsCannotCreateExtraPages() {
        assertEquals(Arrays.asList(2, 24), FilmFrameNavigation.recordedNumbers(24, Arrays.asList(2, 24, 2, 0, 25, null)));
    }
    @Test public void aSingleRecordedCutHasNoArtificialNextPage() {
        assertEquals(Collections.singletonList(13), FilmFrameNavigation.recordedNumbers(36, Collections.singletonList(13)));
        assertTrue(FilmFrameNavigation.recordedNumbers(36, Collections.emptyList()).isEmpty());
    }
}
