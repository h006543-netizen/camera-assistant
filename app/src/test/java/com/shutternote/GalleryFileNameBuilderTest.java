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

    @Test public void noteNameUsesCustomRollAndSelectedFilmIncludingPendingRequest() {
        FilmFrameRecord frame = noteFrame();
        assertEquals("ISO400_f8_1-250_40mm_261011_1304__원본.jpg",
                GalleryFileNameBuilder.buildNote(frame, "  "));
        frame.correctionEnabled = true;
        frame.correctionProfile = FilmCorrectionProfiles.latest("cine800t");
        frame.correctionRequest = "pending";
        frame.correctionRequestedProfile = FilmCorrectionProfiles.latest("vision3-250d");
        assertEquals("ISO400_f8_1-250_40mm_261011_1304_제주 여행_비전3_250D.jpg",
                GalleryFileNameBuilder.buildNote(frame, "제주 여행"));
        frame.correctionEnabled = false; // Original comparison retains the cached/pending film.
        assertEquals("ISO400_f8_1-250_40mm_261011_1304_제주 여행_원본.jpg",
                GalleryFileNameBuilder.buildNote(frame, "제주 여행"));
    }

    @Test public void noteNameKeepsUnicodeAndSanitizesLongUnsafeRollNames() {
        FilmFrameRecord frame = noteFrame();
        String name = GalleryFileNameBuilder.buildNote(frame, "여행/서울:밤?");
        assertEquals("ISO400_f8_1-250_40mm_261011_1304_여행_서울_밤__원본.jpg", name);
        String longName = GalleryFileNameBuilder.buildNote(frame, "여행📷".repeat(40));
        org.junit.Assert.assertTrue(longName.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 255);
        org.junit.Assert.assertFalse(longName.contains("\uFFFD"));
    }

    private FilmFrameRecord noteFrame() {
        FilmFrameRecord frame = new FilmFrameRecord();
        frame.iso = "400"; frame.aperture = "f/8"; frame.shutter = "1/250"; frame.fieldOfView = "40mm";
        java.util.Calendar time = java.util.Calendar.getInstance();
        time.set(2026, java.util.Calendar.OCTOBER, 11, 13, 4, 0);
        frame.capturedAt = time.getTimeInMillis();
        return frame;
    }
}
