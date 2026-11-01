package com.shutternote;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;

public class FilmRollOrderTest {
    private FilmRollRecord roll(String id) {
        FilmRollRecord roll = new FilmRollRecord(); roll.id = id; roll.name = "Same title";
        return roll;
    }

    @Test public void newOrderUsesIdsAndPreservesLatestPhotoMemoAndRollChanges() throws Exception {
        FilmRollRecord a = roll("a"), b = roll("b"), c = roll("c");
        b.memo = "Edited while dragging"; b.filmName = "400D";
        FilmFrameRecord frame = new FilmFrameRecord(); frame.number = 7; frame.memo = "Latest cut memo";
        frame.photo = "saved.jpg"; frame.rotation = 270; b.frames.add(frame);
        List<FilmRollRecord> latest = Arrays.asList(a, b, c);
        List<FilmRollRecord> ordered = FilmRollOrder.apply(latest, Arrays.asList("c", "b", "a"));
        assertEquals(Arrays.asList(c, b, a), ordered);
        assertEquals(Arrays.asList(a, b, c), latest);
        assertSame(b, ordered.get(1)); assertSame(frame, ordered.get(1).frames.get(0));
        FilmRollRecord restored = FilmRollRecord.fromJson(ordered.get(1).toJson());
        assertEquals("Edited while dragging", restored.memo); assertEquals("400D", restored.filmName);
        assertEquals("saved.jpg", restored.frame(7).photo); assertEquals("Latest cut memo", restored.frame(7).memo);
        assertEquals(270, restored.frame(7).rotation);
    }

    @Test public void concurrentCreationsStayFirstAndDeletedRollsAreNeverResurrected() {
        FilmRollRecord first = roll("newer"), second = roll("new"), a = roll("a"), c = roll("c");
        List<FilmRollRecord> ordered = FilmRollOrder.apply(Arrays.asList(first, second, a, c), Arrays.asList("c", "deleted", "a"));
        assertEquals(Arrays.asList(first, second, c, a), ordered);
    }

    @Test public void duplicateAndUnknownIdsCannotDuplicateOrDropStoredRolls() {
        FilmRollRecord a = roll("a"), b = roll("b"), c = roll("c");
        assertEquals(Arrays.asList(b, c, a), FilmRollOrder.apply(Arrays.asList(a, b, c), Arrays.asList("c", "c", "missing", "a")));
        assertEquals(Arrays.asList(a, b, c), FilmRollOrder.apply(Arrays.asList(a, b, c), Collections.emptyList()));
        assertTrue(FilmRollOrder.apply(Collections.emptyList(), Arrays.asList("a", "b")).isEmpty());
    }

    /** Mimics SharedPreferences applying to memory even when the disk commit fails. */
    private SharedPreferences preferences(String[] data, boolean fail) {
        return (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
            if (method.getName().equals("getString")) return data[0];
            if (method.getName().equals("edit")) {
                String[] pending = {data[0]};
                return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SharedPreferences.Editor.class}, (editor, action, params) -> {
                    if (action.getName().equals("putString")) { pending[0] = (String) params[1]; return editor; }
                    if (action.getName().equals("commit")) { data[0] = pending[0]; return !fail; }
                    throw new UnsupportedOperationException(action.getName());
                });
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    @Test public void committedOrderSurvivesReloadAndLaterRollEditKeepsItsPosition() throws Exception {
        FilmRollRecord a = roll("a"), b = roll("b");
        String[] stored = {new JSONArray().put(a.toJson()).put(b.toJson()).toString()};
        assertTrue(FilmRollStore.commit(preferences(stored, false), FilmRollOrder.apply(Arrays.asList(a, b), Arrays.asList("b", "a"))));
        JSONArray restored = new JSONArray(stored[0]);
        assertEquals("b", restored.getJSONObject(0).getString("id"));
        FilmRollRecord edited = FilmRollRecord.fromJson(restored.getJSONObject(0)); edited.memo = "New memo";
        FilmRollRecord latest = FilmRollRecord.fromJson(restored.getJSONObject(0));
        assertTrue(FilmRollStore.mergeRollEdit(latest, edited));
        assertTrue(FilmRollStore.commit(preferences(stored, false), Arrays.asList(edited, a)));
        assertEquals("b", new JSONArray(stored[0]).getJSONObject(0).getString("id"));
    }

    @Test public void failedCommitRestoresExactOriginalJsonAndAllRecords() throws Exception {
        FilmRollRecord a = roll("a"), b = roll("b");
        a.memo = "Must survive";
        String original = new JSONArray().put(a.toJson()).put(b.toJson()).toString();
        String[] stored = {original};
        assertFalse(FilmRollStore.commit(preferences(stored, true), FilmRollOrder.apply(Arrays.asList(a, b), Arrays.asList("b", "a"))));
        assertEquals(original, stored[0]);
    }
}
