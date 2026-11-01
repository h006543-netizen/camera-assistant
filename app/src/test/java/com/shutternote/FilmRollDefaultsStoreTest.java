package com.shutternote;
import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class FilmRollDefaultsStoreTest {
    private SharedPreferences preferences(){
        Map<String,Object> data=new HashMap<>();
        return (SharedPreferences)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SharedPreferences.class},(p,m,a)->{
            switch(m.getName()){
                case "getString":case "getInt":case "getLong":case "getBoolean":return data.getOrDefault((String)a[0],a[1]);
                case "getAll":return new HashMap<>(data);
                case "edit":
                    Map<String,Object> changes=new HashMap<>();
                    return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(e,method,args)->{
                        switch(method.getName()){
                            case "putString":case "putInt":case "putLong":case "putBoolean":changes.put((String)args[0],args[1]);return e;
                            case "apply":case "commit":data.putAll(changes);return method.getName().equals("commit")?true:null;
                            default:throw new UnsupportedOperationException(method.getName());
                        }
                    });
                default:throw new UnsupportedOperationException(m.getName());
            }
        });
    }
    @Test public void noHistoryStartsAt35And36() {
        FilmRollRecord draft=new FilmRollRecord();new FilmRollDefaultsStore(preferences()).restore(draft);
        assertEquals("35",draft.focalLength);assertEquals(36,draft.plannedFrames);assertEquals(36,draft.countMode);
    }
    @Test public void customCountAndFocalPersistAcrossReopening() {
        SharedPreferences prefs=preferences();FilmRollDefaultsStore store=new FilmRollDefaultsStore(prefs);store.recordFocal("50");store.recordCount(72,0);
        FilmRollRecord draft=new FilmRollRecord();new FilmRollDefaultsStore(prefs).restore(draft);
        assertEquals("50",draft.focalLength);assertEquals(72,draft.plannedFrames);assertEquals(0,draft.countMode);
    }
    @Test public void independentFieldUpdatesAndInvalidValuesDoNotEraseDefaults() {
        FilmRollDefaultsStore store=new FilmRollDefaultsStore(preferences());store.recordFocal("50");store.recordCount(24,24);
        store.recordFocal("40");store.recordCount(1000,0);store.recordFocal("85");FilmRollRecord draft=new FilmRollRecord();store.restore(draft);
        assertEquals("40",draft.focalLength);assertEquals(24,draft.plannedFrames);assertEquals(24,draft.countMode);
        store.recordCount(36,0);store.restore(draft);assertEquals("40",draft.focalLength);assertEquals(36,draft.plannedFrames);assertEquals(0,draft.countMode);
    }
    @Test public void seedUsesMostRecentExistingRollOnce() {
        FilmRollDefaultsStore store=new FilmRollDefaultsStore(preferences());FilmRollRecord recent=new FilmRollRecord(),old=new FilmRollRecord();
        recent.focalLength="50";recent.plannedFrames=48;recent.countMode=0;old.focalLength="35";old.plannedFrames=24;old.countMode=24;
        store.seed(Arrays.asList(recent,old));store.recordFocal("40");store.seed(Arrays.asList(recent,old));FilmRollRecord draft=new FilmRollRecord();store.restore(draft);
        assertEquals("40",draft.focalLength);assertEquals(48,draft.plannedFrames);assertEquals(0,draft.countMode);
    }
}
