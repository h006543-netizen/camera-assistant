package com.shutternote;

import java.util.Collection;

/** Pure rules shared by the two-row list and its scrubber. */
final class FilmFrameNavigation {
    static int pairCount(int total) { return (Math.max(1, total) + 1) / 2; }
    static int clampPair(int pair, int total) { return Math.max(0, Math.min(pair, pairCount(total) - 1)); }
    static int firstFrame(int pair, int total) { return clampPair(pair, total) * 2 + 1; }
    static int lastFrame(int pair, int total) { return Math.min(firstFrame(pair, total) + 1, total); }
    static int nextFrame(int total, Collection<Integer> recorded) {
        for (int number = 1; number <= total; number++) if (!recorded.contains(number)) return number;
        return total; // A completed roll opens its final pair.
    }

    static java.util.List<Integer> recordedNumbers(int total, Collection<Integer> recorded) {
        java.util.TreeSet<Integer> ordered = new java.util.TreeSet<>();
        for (Integer number : recorded) if (number != null && number >= 1 && number <= total) ordered.add(number);
        return new java.util.ArrayList<>(ordered);
    }
}
