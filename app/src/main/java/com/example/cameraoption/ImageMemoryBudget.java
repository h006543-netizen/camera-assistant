package com.example.cameraoption;

/** Leave room for rotation, output bitmap, JPEG encoding and the visible preview. */
public final class ImageMemoryBudget {
    private ImageMemoryBudget() { }

    public static int sampleSize(int width, int height, long availableBytes) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException();
        long budget = Math.max(1L, availableBytes / 2);
        int sample = 1;
        while (((width + (long) sample - 1) / sample)
                * ((height + (long) sample - 1) / sample) > budget / 16
                && sample < Math.max(width, height)) {
            sample *= 2;
        }
        return sample;
    }
}
