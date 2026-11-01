package com.shutternote;

import java.util.List;
import java.util.function.Predicate;

/** Walks backward through the visible folder's saved order to the next readable roll. */
final class FilmNextRollNavigation {
    static final float REVEAL_FRACTION = .55f;

    static final class Target {
        final String rollId;
        final int number;
        Target(String rollId, int number) { this.rollId = rollId; this.number = number; }
    }

    static Target find(List<FilmRollRecord> rolls, FilmRollFolders folders, String current,
                       Predicate<FilmFrameRecord> hasPhoto) {
        int currentIndex = -1;
        for (int i = 0; i < rolls.size(); i++) if (rolls.get(i).id.equals(current)) { currentIndex = i; break; }
        String folder = folders.folderOf(current);
        for (int i = currentIndex - 1; i >= 0; i--) {
            FilmRollRecord roll = rolls.get(i);
            if (!folder.equals(folders.folderOf(roll.id))) continue;
            int first = Integer.MAX_VALUE;
            for (FilmFrameRecord frame : roll.frames) {
                if (frame.number >= 1 && frame.number <= roll.plannedFrames && hasPhoto.test(frame))
                    first = Math.min(first, frame.number);
            }
            if (first != Integer.MAX_VALUE) return new Target(roll.id, first);
        }
        return null;
    }
}
