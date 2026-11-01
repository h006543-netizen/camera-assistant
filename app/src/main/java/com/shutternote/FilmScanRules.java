package com.shutternote;

/** Capture identity and an optimistic revision protect edits from stale comparison screens. */
final class FilmScanRules {
    private FilmScanRules() { }

    static boolean validName(String name) {
        return name != null && name.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.img");
    }

    static boolean apply(FilmRollRecord roll, int cut, String capture, long revision, String name) {
        if (name == null || (!name.isEmpty() && !validName(name))) return false;
        FilmFrameRecord frame = roll.frame(cut);
        if (frame == null || !frame.captureId.equals(capture) || frame.scanRevision != revision
                || revision < 0 || revision == Long.MAX_VALUE) return false;
        frame.scanPhoto = name;
        frame.scanRevision++;
        return true;
    }

    static boolean sideBySide(int firstWidth, int firstHeight, int secondWidth, int secondHeight) {
        return firstWidth > 0 && secondWidth > 0 && firstHeight > firstWidth && secondHeight > secondWidth;
    }

    static boolean completedDraft(FilmFrameRecord frame, String pending) {
        return pending != null && pending.startsWith("pending-") && validName(pending.substring(8))
                && frame.scanPhoto.equals(pending.substring(8));
    }
}
