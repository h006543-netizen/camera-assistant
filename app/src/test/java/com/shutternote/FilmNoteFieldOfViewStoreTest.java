package com.shutternote;
import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;
public class FilmNoteFieldOfViewStoreTest {
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

    @Test public void firstEntryUsesRollDespiteGeneralChanges() {
        ExposureSettingsStore general=new ExposureSettingsStore(preferences());
        FilmNoteFieldOfViewStore note=new FilmNoteFieldOfViewStore(preferences());
        general.save("800","f/4","1/250","35mm");assertEquals("50mm",note.load("a","50"));
        general.save("800","f/4","1/250","40mm");assertEquals("50mm",note.load("a","50"));
    }
    @Test public void noteChoicePersistsWithoutCaptureAndWinsOverRollEdits() {
        SharedPreferences prefs=preferences();FilmNoteFieldOfViewStore note=new FilmNoteFieldOfViewStore(prefs);
        note.save("a","40mm");assertEquals("40mm",new FilmNoteFieldOfViewStore(prefs).load("a","50"));
        assertEquals("40mm",note.load("a","35"));
        note.save("a",ExposureSettingsStore.FIELD_OF_VIEW_DEFAULT);assertEquals(ExposureSettingsStore.FIELD_OF_VIEW_DEFAULT,note.load("a","50"));
    }
    @Test public void noteExposurePreservesGeneralIsoAndFieldOfView() {
        ExposureSettingsStore general=new ExposureSettingsStore(preferences());general.save("800","f/4","1/250","35mm");
        FilmNoteFieldOfViewStore note=new FilmNoteFieldOfViewStore(preferences());note.save("a","50mm");
        new FilmNoteExposureStore(preferences()).save("a","f/8","1/60");assertEquals("35mm",general.load().getFieldOfView());assertEquals("800",general.load().getIso());
        assertEquals("f/4",general.load().getAperture());assertEquals("1/250",general.load().getShutter());
        general.save("1600","f/8","1/60","40mm");assertEquals("50mm",note.load("a","35"));
    }
    @Test public void rollsAreIndependentAndInvalidStoredValuesUseRoll() {
        SharedPreferences prefs=preferences();FilmNoteFieldOfViewStore note=new FilmNoteFieldOfViewStore(prefs);
        note.save("a","50mm");note.save("b","35mm");note.save("a","bad");
        assertEquals("50mm",note.load("a","40"));assertEquals("35mm",note.load("b","50"));
        prefs.edit().putString("c","bad").apply();assertEquals("40mm",note.load("c","40"));
        assertEquals(ExposureSettingsStore.FIELD_OF_VIEW_DEFAULT,note.load("d","85"));
    }
    @Test public void deletingOneRollOnlyRemovesItsRememberedChoice() {
        FilmNoteFieldOfViewStore note=new FilmNoteFieldOfViewStore(preferences());note.save("a","40mm");note.save("b","50mm");
        note.remove(Collections.singleton("a"));assertEquals("35mm",note.load("a","35"));assertEquals("50mm",note.load("b","35"));
    }
}
