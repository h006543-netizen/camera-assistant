package com.shutternote;

import org.junit.Test;
import static org.junit.Assert.*;

public class ImageMemoryBudgetTest {
    @Test public void keepsOriginalWhenMemoryAllows() {
        assertEquals(1, ImageMemoryBudget.sampleSize(4000, 3000, 512L * 1024 * 1024));
    }
    @Test public void largePhotoFitsConservativeBudget() {
        long available = 64L * 1024 * 1024;
        int sample = ImageMemoryBudget.sampleSize(8000, 6000, available);
        assertTrue(sample > 1);
        assertTrue(((8000L + sample - 1) / sample)
                * ((6000L + sample - 1) / sample) * 16 <= available / 2);
    }
    @Test public void tinyBudgetStillTerminates() {
        assertTrue(ImageMemoryBudget.sampleSize(1, 1, 0) >= 1);
    }
}
