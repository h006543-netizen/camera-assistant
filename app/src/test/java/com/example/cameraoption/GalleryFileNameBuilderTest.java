package com.example.cameraoption;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GalleryFileNameBuilderTest {

    @Test
    public void buildWithTimestamp_includesExposureFieldOfViewFilmAndTime() {
        assertEquals(
                "ISO400_f2.8_1-125_35mm_Portra400_20260805_234500.jpg",
                GalleryFileNameBuilder.buildWithTimestamp(
                        "400",
                        "f/2.8",
                        "1/125",
                        "35mm",
                        "Kodak Portra 400",
                        "20260805_234500"
                )
        );
    }

    @Test
    public void buildWithTimestamp_usesReadableDefaults() {
        assertEquals(
                "ISO200_f2.8_1-125_Default_NoFilm_20260805_234500.jpg",
                GalleryFileNameBuilder.buildWithTimestamp(
                        "200",
                        "f/2.8",
                        "1/125",
                        "기본",
                        "선택 없음",
                        "20260805_234500"
                )
        );
    }
}
