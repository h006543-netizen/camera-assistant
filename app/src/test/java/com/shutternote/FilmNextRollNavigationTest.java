package com.shutternote;

import java.util.Arrays;
import java.util.HashSet;
import org.junit.Test;
import static org.junit.Assert.*;

public class FilmNextRollNavigationTest {
    private FilmRollRecord roll(String id, int... cuts) {
        FilmRollRecord roll = new FilmRollRecord(); roll.id = id;
        for (int cut : cuts) {
            FilmFrameRecord frame = new FilmFrameRecord(); frame.number = cut;
            frame.photo = id + cut; roll.frames.add(frame);
        }
        return roll;
    }

    @Test public void walksBackwardFromCurrentAndStartsAtLowestActualCut() {
        FilmRollRecord current = roll("a", 36), next = roll("b", 18, 3, 12);
        FilmNextRollNavigation.Target target = FilmNextRollNavigation.find(
                Arrays.asList(roll("c", 1), next, current), new FilmRollFolders(), "a", frame -> true);
        assertEquals("b", target.rollId); assertEquals(3, target.number);
    }

    @Test public void skipsEmptyRollsAndMissingPhotoFiles() {
        FilmNextRollNavigation.Target target = FilmNextRollNavigation.find(
                Arrays.asList(roll("b", 2, 8), roll("missing", 1), roll("empty"), roll("a", 1)),
                new FilmRollFolders(), "a", frame -> frame.photo.equals("b8"));
        assertEquals("b", target.rollId); assertEquals(8, target.number);
    }

    @Test public void respectsFolderMembershipIncludingRoot() {
        FilmRollFolders folders = new FilmRollFolders(); folders.create("folder", "Folder");
        folders.move(new HashSet<>(Arrays.asList("a", "c")), "folder",
                new HashSet<>(Arrays.asList("a", "b", "c", "d")));
        java.util.List<FilmRollRecord> rolls = Arrays.asList(roll("a", 1), roll("b", 1), roll("c", 4), roll("d", 2));
        assertEquals("a", FilmNextRollNavigation.find(rolls, folders, "c", frame -> true).rollId);
        assertEquals("b", FilmNextRollNavigation.find(rolls, folders, "d", frame -> true).rollId);
    }

    @Test public void doesNotWrapOrChooseARollWhenCurrentWasDeleted() {
        java.util.List<FilmRollRecord> rolls = Arrays.asList(roll("a", 1), roll("b", 1));
        assertNull(FilmNextRollNavigation.find(rolls, new FilmRollFolders(), "a", frame -> true));
        assertNull(FilmNextRollNavigation.find(rolls, new FilmRollFolders(), "deleted", frame -> true));
    }

    @Test public void ignoresInvalidCutNumbersAndHasNoTargetWithoutPhotos() {
        java.util.List<FilmRollRecord> rolls = Arrays.asList(roll("c", 2), roll("b", 0, 37), roll("a", 1));
        assertEquals("c", FilmNextRollNavigation.find(rolls, new FilmRollFolders(), "a", frame -> true).rollId);
        assertNull(FilmNextRollNavigation.find(rolls, new FilmRollFolders(), "a", frame -> false));
    }

    @Test public void reorderingChangesNextFilmWithoutChangingItsRecords() {
        FilmRollRecord a = roll("a", 1), b = roll("b", 7), c = roll("c", 9);
        java.util.List<FilmRollRecord> reordered = FilmRollOrder.apply(Arrays.asList(c, b, a), Arrays.asList("b", "c", "a"));
        assertEquals("c", FilmNextRollNavigation.find(reordered, new FilmRollFolders(), "a", frame -> true).rollId);
        assertEquals(7, b.frames.get(0).number); assertEquals("b7", b.frames.get(0).photo);
    }

    @Test public void repeatedNextVisitsPreviousNeighborsAndStopsAtTop() {
        java.util.List<FilmRollRecord> rolls = Arrays.asList(roll("c", 1), roll("b", 7), roll("a", 13));
        FilmRollFolders folders = new FilmRollFolders();
        assertEquals("b", FilmNextRollNavigation.find(rolls, folders, "a", frame -> true).rollId);
        assertEquals("c", FilmNextRollNavigation.find(rolls, folders, "b", frame -> true).rollId);
        assertNull(FilmNextRollNavigation.find(rolls, folders, "c", frame -> true));
    }
}
