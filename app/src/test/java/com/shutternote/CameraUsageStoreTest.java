package com.shutternote;
import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CameraUsageStoreTest {
    private SharedPreferences preferences(){ return preferences(false); }
    private SharedPreferences preferences(boolean failRemoval){
        Map<String,Object> data=new HashMap<>();
        return (SharedPreferences)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SharedPreferences.class},(p,m,a)->{
            switch(m.getName()){
                case "getString":case "getInt":case "getLong":case "getBoolean":return data.getOrDefault((String)a[0],a[1]);
                case "getAll":return new HashMap<>(data);
                case "edit":
                    Map<String,Object> changes=new HashMap<>();
                    Set<String> removals=new HashSet<>();
                    return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(e,method,args)->{
                        switch(method.getName()){
                            case "putString":case "putInt":case "putLong":case "putBoolean":changes.put((String)args[0],args[1]);return e;
                            case "remove":removals.add((String)args[0]);return e;
                            case "apply":case "commit":removals.forEach(data::remove);data.putAll(changes);return method.getName().equals("commit") ? !(failRemoval && !removals.isEmpty()) : null;
                            default:throw new UnsupportedOperationException(method.getName());
                        }
                    });
                default:throw new UnsupportedOperationException(m.getName());
            }
        });
    }
    @Test public void frequencyWinsOverRecentUseAndOnlyThreeAreShown(){
        CameraUsageStore store=new CameraUsageStore(preferences());
        store.record("fm2","Nikon FM2");store.record("fm2","Nikon FM2");
        store.record("m6","Leica M6");store.record("t2","Contax T2");store.record("ae1","Canon AE-1");
        assertEquals(3,store.top().size());assertEquals("fm2",store.top().get(0).id);assertEquals("ae1",store.top().get(1).id);
    }
    @Test public void tiedCountsPreferMostRecentAndReopeningKeepsHistory(){
        SharedPreferences prefs=preferences();CameraUsageStore store=new CameraUsageStore(prefs);
        store.record("fm2","Nikon FM2");store.record("m6","Leica M6");
        CameraUsageStore reopened=new CameraUsageStore(prefs);
        assertEquals("m6",reopened.last().id);assertEquals("m6",reopened.top().get(0).id);
    }
    @Test public void existingRollsSeedOnceInRecentOrder(){
        CameraUsageStore store=new CameraUsageStore(preferences());
        FilmRollRecord recent=new FilmRollRecord(),old=new FilmRollRecord();
        recent.cameraId="m6";recent.cameraName="Leica M6";old.cameraId="fm2";old.cameraName="Nikon FM2";
        store.seed(Arrays.asList(recent,old));store.seed(Arrays.asList(recent,old));
        assertEquals("m6",store.last().id);assertEquals(1,store.top().get(0).count);
    }
    @Test public void customCameraNameIsRememberedAndCanBeFrequent(){
        CameraUsageStore store=new CameraUsageStore(preferences());store.record("","My camera");
        assertEquals("",store.last().id);assertEquals("My camera",store.last().name);assertEquals("My camera",store.top().get(0).name);
    }
    @Test public void EmptySelectionDoesNotReplaceLastUsedCamera(){
        CameraUsageStore store=new CameraUsageStore(preferences());store.record("fm2","Nikon FM2");store.record("","");
        assertEquals("fm2",store.last().id);assertEquals(1,store.top().size());
    }

    @Test public void removePersistsDoesNotReseedOrClearLastAndReusedStartsAtOne() {
        SharedPreferences prefs=preferences();CameraUsageStore store=new CameraUsageStore(prefs);
        FilmRollRecord roll=new FilmRollRecord();roll.filmId="a";roll.filmName="A";roll.cameraId="a";roll.cameraName="A";
        store.seed(Collections.singletonList(roll));store.record("a","A");store.record("b","B");
        assertTrue(store.remove("b","B"));assertEquals("b",store.last().id);
        assertTrue(store.remove("a","A"));
        CameraUsageStore reopened=new CameraUsageStore(prefs);reopened.seed(Collections.singletonList(roll));
        assertTrue(reopened.top().isEmpty());reopened.record("a","A");assertEquals(1,reopened.top().get(0).count);
    }
    @Test public void removingCustomOnlyRemovesThatNameAndPromotesNextEntry() {
        CameraUsageStore store=new CameraUsageStore(preferences());
        store.record("","Custom A");store.record("","Custom B");store.record("c","C");
        assertTrue(store.remove("","Custom B"));assertEquals(2,store.top().size());
        assertEquals("Custom A",store.top().get(1).name);
    }

    @Test public void failedCommitRestoresHistoryInMemory() {
        CameraUsageStore store=new CameraUsageStore(preferences(true));store.record("a","A");
        assertFalse(store.remove("a","A"));assertEquals("a",store.top().get(0).id);assertEquals(1,store.top().get(0).count);
    }
    @Test public void nextRankedItemFillsDeletedSlot() {
        CameraUsageStore store=new CameraUsageStore(preferences());
        for(int i=0;i<4;i++)store.record("item-"+i,"Item "+i);
        assertEquals(3,store.top().size());assertTrue(store.remove("item-3","Item 3"));
        assertEquals(3,store.top().size());assertEquals("item-0",store.top().get(2).id);
    }
}
