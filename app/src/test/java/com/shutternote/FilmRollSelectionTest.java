package com.shutternote;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class FilmRollSelectionTest {
    @Test public void longPressSelectsFirstRollAndTapsToggleAdditionalRolls() {
        FilmRollSelection selection = new FilmRollSelection();
        selection.toggle("a");
        assertFalse(selection.isActive()); assertEquals(0, selection.count());
        selection.begin("a");
        assertTrue(selection.isActive()); assertTrue(selection.contains("a"));
        selection.begin("a"); // Repeated long press does not duplicate the choice.
        assertEquals(1, selection.count());
        selection.toggle("b"); assertEquals(2, selection.count());
        selection.toggle("a"); assertFalse(selection.contains("a")); assertTrue(selection.contains("b"));
        selection.toggle("b"); assertEquals(0, selection.count()); assertTrue(selection.isActive());
        selection.clear(); assertFalse(selection.isActive());
    }

    @Test public void recreationKeepsIdsAndReorderingDoesNotChangeSelection() {
        FilmRollSelection old = new FilmRollSelection(); old.begin("a"); old.toggle("c");
        FilmRollSelection restored = new FilmRollSelection(); restored.restore(old.isActive(), old.snapshot());
        restored.retainAvailable(Arrays.asList("new", "c", "b", "a"));
        assertEquals(new HashSet<>(Arrays.asList("a", "c")), restored.snapshot());
        restored.retainAvailable(Arrays.asList("new", "b", "c"));
        assertEquals(Collections.singleton("c"), restored.snapshot());
        restored.retainAvailable(Collections.emptyList());
        assertFalse(restored.isActive()); assertEquals(0, restored.count());
    }

    @Test public void deletionSnapshotIsUnaffectedByLaterTapsOrCancellation() {
        FilmRollSelection selection = new FilmRollSelection(); selection.begin("a");
        Set<String> submitted = selection.snapshot();
        selection.toggle("b"); selection.clear();
        assertEquals(Collections.singleton("a"), submitted);
    }

    @Test public void batchDeletionMatchesIdsNotNamesAndPreservesUnselectedNewOrEditedRolls() {
        FilmRollRecord a = record("a", "Same name"), b = record("b", "Same name");
        FilmRollRecord c = record("c", "Updated"), added = record("new", "New roll");
        List<FilmRollRecord> current = Arrays.asList(added, c, b, a);
        List<FilmRollRecord> remaining = FilmRollStore.remainingAfterDelete(current, new HashSet<>(Arrays.asList("a", "c", "missing")));
        assertEquals(Arrays.asList(added, b), remaining);
        assertEquals(4, current.size());
        assertSame(b, remaining.get(1));
        assertTrue(FilmRollStore.remainingAfterDelete(Collections.singletonList(a), Collections.singleton("a")).isEmpty());
        assertEquals(current, FilmRollStore.remainingAfterDelete(current, Collections.emptySet()));
    }

    private static FilmRollRecord record(String id, String name) {
        FilmRollRecord record = new FilmRollRecord(); record.id = id; record.name = name;
        return record;
    }
}
