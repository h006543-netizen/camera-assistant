package com.shutternote;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class FilmNoteIsoStoreTest {
    /** In-memory preference backend, retaining the actual store read/write behavior. */
    private SharedPreferences preferences() {
        Map<String, String> data = new HashMap<>();
        return (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getString")) return data.getOrDefault((String) args[0], (String) args[1]);
                    if (method.getName().equals("edit")) {
                        Map<String, String> changes = new HashMap<>();
                        return Proxy.newProxyInstance(getClass().getClassLoader(),
                                new Class<?>[]{SharedPreferences.Editor.class}, (editor, action, params) -> {
                                    switch (action.getName()) {
                                        case "putString": changes.put((String) params[0], (String) params[1]); return editor;
                                        case "remove": changes.put((String) params[0], null); return editor;
                                        case "apply": case "commit":
                                            changes.forEach((key, value) -> { if (value == null) data.remove(key); else data.put(key, value); });
                                            return action.getName().equals("commit") ? true : null;
                                        default: throw new UnsupportedOperationException(action.getName());
                                    }
                                });
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test public void untouchedNoteStartsAtFilmSpeedDespiteGeneralIsoChanges() {
        ExposureSettingsStore general = new ExposureSettingsStore(preferences());
        FilmNoteIsoStore note = new FilmNoteIsoStore(preferences());
        general.save("800", "f/4", "1/250");
        assertEquals("100", note.load("roll-a", 100));
        general.save("1600", "f/4", "1/250");
        assertEquals("100", note.load("roll-a", 100));
    }

    @Test public void noteChoiceSurvivesReopeningAndLaterGeneralChangesWithoutCapture() {
        SharedPreferences notePreferences = preferences();
        FilmNoteIsoStore note = new FilmNoteIsoStore(notePreferences);
        ExposureSettingsStore general = new ExposureSettingsStore(preferences());
        note.save("roll-a", "200");
        general.save("800", "f/4", "1/250");
        assertEquals("200", new FilmNoteIsoStore(notePreferences).load("roll-a", 100));
    }

    @Test public void noteControlsCannotOverwriteGeneralIso() {
        ExposureSettingsStore general = new ExposureSettingsStore(preferences());
        FilmNoteIsoStore note = new FilmNoteIsoStore(preferences());
        general.save("800", "f/4", "1/250");
        note.save("roll-a", "200");
        new FilmNoteExposureStore(preferences()).save("roll-a", "f/8", "1/60");
        assertEquals("800", general.load().getIso());
        assertEquals("f/4", general.load().getAperture());
        assertEquals("1/250", general.load().getShutter());
        assertEquals("200", note.load("roll-a", 100));
    }

    @Test public void rollsRememberTheirOwnIsoAndUserChoiceWinsOverFilmEdit() {
        FilmNoteIsoStore note = new FilmNoteIsoStore(preferences());
        note.save("roll-a", "200");
        assertEquals("200", note.load("roll-a", 800));
        assertEquals("400", note.load("roll-b", 400));
        note.save("roll-b", "1600");
        assertEquals("200", note.load("roll-a", 100));
        assertEquals("1600", note.load("roll-b", 400));
    }

    @Test public void unknownSpeedAndInvalidSavedIsoNeverUseGeneralIso() {
        SharedPreferences prefs = preferences();
        FilmNoteIsoStore note = new FilmNoteIsoStore(prefs);
        prefs.edit().putString("roll-a", "999").apply();
        assertEquals("100", note.load("roll-a", 100));
        assertEquals("200", note.load("roll-b", 0));
        assertEquals("200", note.load("roll-c", 64));
        note.save("roll-a", "400"); note.save("roll-a", "999");
        assertEquals("400", note.load("roll-a", 100));
    }

    @Test public void deletingOneRollDoesNotRemoveAnotherRollIso() {
        FilmNoteIsoStore note = new FilmNoteIsoStore(preferences());
        note.save("roll-a", "200"); note.save("roll-b", "800");
        note.remove(Collections.singleton("roll-a"));
        assertEquals("100", note.load("roll-a", 100));
        assertEquals("800", note.load("roll-b", 400));
    }
}
