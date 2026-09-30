package com.shutternote;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GalleryFileNameBuilderTest {

    @Test
    public void buildWithTimestamp_includesExposureFieldOfViewAndTime() {
        assertEquals(
                "ISO400_f2.8_1-125_35mm_260805_2345.jpg",
                GalleryFileNameBuilder.buildWithTimestamp(
                        "400",
                        "f/2.8",
                        "1/125",
                        "35mm",
                        "260805_2345"
                )
        );
    }

    @Test
    public void buildWithTimestamp_usesReadableDefaults() {
        assertEquals(
                "ISO200_f2.8_1-125_Default_260805_2345.jpg",
                GalleryFileNameBuilder.buildWithTimestamp(
                        "200",
                        "f/2.8",
                        "1/125",
                        "기본",
                        "260805_2345"
                )
        );
    }

    @Test public void collisionUsesParenthesizedNumbers() {
        String base = "ISO400_f2.8_1-125_35mm_261001_1530.jpg";
        java.util.Set<String> existing = new java.util.HashSet<>();
        assertEquals(base, GalleryFileNameBuilder.availableName(base, existing));
        existing.add(base);
        assertEquals("ISO400_f2.8_1-125_35mm_261001_1530(1).jpg",
                GalleryFileNameBuilder.availableName(base, existing));
        existing.add(GalleryFileNameBuilder.numberedName(base, 1));
        assertEquals("ISO400_f2.8_1-125_35mm_261001_1530(2).jpg",
                GalleryFileNameBuilder.availableName(base, existing));
    }

    @Test public void buildUsesMinutePrecision() {
        java.util.Calendar time = java.util.Calendar.getInstance();
        time.set(2026, java.util.Calendar.OCTOBER, 1, 15, 30, 45);
        assertEquals("ISO400_f2.8_1-125_35mm_261001_1530.jpg",
                GalleryFileNameBuilder.build("400", "f/2.8", "1/125", "35mm", time.getTime()));
    }
}
