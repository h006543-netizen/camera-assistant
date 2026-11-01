package com.shutternote;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class FilmNoteExposureStoreTest {
    private SharedPreferences preferences() {
        Map<String,String> data=new HashMap<>();
        return (SharedPreferences)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{
            if (method.getName().equals("getString")) return data.getOrDefault((String)args[0],(String)args[1]);
            if (method.getName().equals("getAll")) return new HashMap<>(data);
            if (method.getName().equals("edit")) {
                Map<String,String> changes=new HashMap<>();
                return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(editor,action,params)->{
                    switch(action.getName()) {
                        case "putString": changes.put((String)params[0],(String)params[1]); return editor;
                        case "remove": changes.put((String)params[0],null); return editor;
                        case "apply": case "commit":
                            changes.forEach((key,value)->{ if(value==null) data.remove(key); else data.put(key,value); });
                            return action.getName().equals("commit")?true:null;
                        default: throw new UnsupportedOperationException(action.getName());
                    }
                });
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }
    private void assertExposure(FilmNoteExposureStore store,String roll,String aperture,String shutter) {
        FilmNoteExposureStore.Values values=store.load(roll);
        assertEquals(aperture,values.aperture); assertEquals(shutter,values.shutter);
    }

    @Test public void threeRollsRememberDifferentIsoApertureAndShutterAcrossReopening() {
        SharedPreferences settings=preferences(), isoSettings=preferences();
        FilmNoteExposureStore note=new FilmNoteExposureStore(settings); FilmNoteIsoStore iso=new FilmNoteIsoStore(isoSettings);
        iso.save("800-film","800"); note.save("800-film","f/1.8","1/60");
        iso.save("100-film","100"); note.save("100-film","f/8","1/500");
        iso.save("400-film","400"); note.save("400-film","f/4","1/125");
        FilmNoteExposureStore reopened=new FilmNoteExposureStore(settings);
        assertExposure(reopened,"800-film","f/1.8","1/60"); assertExposure(reopened,"100-film","f/8","1/500");
        assertExposure(reopened,"400-film","f/4","1/125");
        assertEquals("800",new FilmNoteIsoStore(isoSettings).load("800-film",100));
        assertEquals("100",new FilmNoteIsoStore(isoSettings).load("100-film",800));
        assertEquals("400",new FilmNoteIsoStore(isoSettings).load("400-film",200));
    }

    @Test public void unsavedRollStartsWithIndependentDefaultsDespiteGeneralOrOtherRollChanges() {
        ExposureSettingsStore general=new ExposureSettingsStore(preferences());
        FilmNoteExposureStore note=new FilmNoteExposureStore(preferences());
        general.save("800","f/1.4","1"); note.save("other","f/22","1/2000");
        assertExposure(note,"new","f/2.8","1/125");
        general.save("100","f/16","1/1000");
        assertExposure(note,"new","f/2.8","1/125");
    }

    @Test public void changingEitherControlOnlyAffectsThatRoll() {
        FilmNoteExposureStore note=new FilmNoteExposureStore(preferences());
        note.save("a","f/4","1/60"); note.save("b","f/11","1/500");
        note.save("a","f/5.6",note.load("a").shutter);
        assertExposure(note,"a","f/5.6","1/60"); assertExposure(note,"b","f/11","1/500");
        note.save("a",note.load("a").aperture,"1/250");
        assertExposure(note,"a","f/5.6","1/250"); assertExposure(note,"b","f/11","1/500");
    }

    @Test public void invalidStoredControlsFallBackIndividuallyAndBadChoicesCannotOverwriteValidPair() {
        SharedPreferences settings=preferences(); FilmNoteExposureStore note=new FilmNoteExposureStore(settings);
        settings.edit().putString("aperture/a","f/3.5").putString("shutter/a","1/250")
                .putString("aperture/b","f/8").putString("shutter/b","1/90").apply();
        assertExposure(note,"a","f/2.8","1/250"); assertExposure(note,"b","f/8","1/125");
        note.save("a","f/4","1/60"); note.save("a","bad","1/500"); note.save("a","f/22",null);
        assertExposure(note,"a","f/4","1/60");
    }

    @Test public void missingRollIdCannotCreateSharedFallbackKeys() {
        SharedPreferences settings=preferences(); FilmNoteExposureStore note=new FilmNoteExposureStore(settings);
        note.save(null,"f/8","1/500"); note.save("","f/16","1/1000");
        assertTrue(settings.getAll().isEmpty());
        assertExposure(note,null,"f/2.8","1/125"); assertExposure(note,"","f/2.8","1/125");
    }

    @Test public void deletingOneRollOnlyClearsItsPairAndNewInstanceKeepsRemainingRoll() {
        SharedPreferences settings=preferences(); FilmNoteExposureStore note=new FilmNoteExposureStore(settings);
        note.save("a","f/1.8","1/60"); note.save("b","f/8","1/500");
        note.remove(Collections.singleton("a"));
        FilmNoteExposureStore reopened=new FilmNoteExposureStore(settings);
        assertExposure(reopened,"a","f/2.8","1/125"); assertExposure(reopened,"b","f/8","1/500");
    }

    @Test public void noteAndGeneralExposureSettingsNeverOverwriteEachOther() {
        ExposureSettingsStore general=new ExposureSettingsStore(preferences()); general.save("200","f/4","1/250","35mm");
        FilmNoteExposureStore note=new FilmNoteExposureStore(preferences());
        note.save("a","f/1.8","1/60"); note.save("b","f/16","1/1000");
        assertEquals("200",general.load().getIso()); assertEquals("f/4",general.load().getAperture());
        assertEquals("1/250",general.load().getShutter()); assertEquals("35mm",general.load().getFieldOfView());
        general.save("1600","f/22","1","50mm");
        assertExposure(note,"a","f/1.8","1/60"); assertExposure(note,"b","f/16","1/1000");
    }
}
