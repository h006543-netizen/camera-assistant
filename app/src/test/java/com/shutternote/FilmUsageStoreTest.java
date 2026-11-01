package com.shutternote;
import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class FilmUsageStoreTest {
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
    @Test public void frequencyThenRecencyWithMaximumFive() {
        FilmUsageStore store=new FilmUsageStore(preferences());store.record("portra","PORTRA 160");store.record("portra","PORTRA 160");
        for(int i=0;i<6;i++)store.record("film-"+i,"Film "+i);
        assertEquals(5,store.top().size());assertEquals("portra",store.top().get(0).id);assertEquals("film-5",store.top().get(1).id);
    }
    @Test public void reopenAndSeedOnlyOnceWithManufacturerFreeName() {
        SharedPreferences prefs=preferences();FilmUsageStore store=new FilmUsageStore(prefs);
        FilmRollRecord recent=new FilmRollRecord(),old=new FilmRollRecord();recent.filmId="kodak-portra-160";recent.filmName="KODAK PORTRA 160";
        old.filmId="kodak-gold-200";old.filmName="KODAK GOLD 200";store.seed(Arrays.asList(recent,old));store.seed(Arrays.asList(recent,old));
        FilmUsageStore reopened=new FilmUsageStore(prefs);assertEquals("PORTRA 160",reopened.top().get(0).name);assertEquals(1,reopened.top().get(0).count);
    }
    @Test public void customFilmAndBlankSelection() {
        FilmUsageStore store=new FilmUsageStore(preferences());store.record("","Custom film");store.record("","");
        assertEquals(1,store.top().size());assertEquals("Custom film",store.top().get(0).name);
    }
    @Test public void displayRenameDoesNotCountAsNewCatalogUse() {
        assertFalse(FilmUsageStore.changed("kodak-portra-160","KODAK PORTRA 160","kodak-portra-160","PORTRA 160"));
        assertTrue(FilmUsageStore.changed("kodak-portra-160","PORTRA 160","kodak-gold-200","GOLD 200"));
        assertFalse(FilmUsageStore.changed(""," Custom ","","Custom"));assertTrue(FilmUsageStore.changed("","A","","B"));
    }

    @Test public void removePersistsDoesNotReseedOrClearLastAndReusedStartsAtOne() {
        SharedPreferences prefs=preferences();FilmUsageStore store=new FilmUsageStore(prefs);
        FilmRollRecord roll=new FilmRollRecord();roll.filmId="a";roll.filmName="A";roll.cameraId="a";roll.cameraName="A";
        store.seed(Collections.singletonList(roll));store.record("a","A");store.record("b","B");
        assertTrue(store.remove("b","B"));assertEquals("b",store.last().id);
        assertTrue(store.remove("a","A"));
        FilmUsageStore reopened=new FilmUsageStore(prefs);reopened.seed(Collections.singletonList(roll));
        assertTrue(reopened.top().isEmpty());reopened.record("a","A");assertEquals(1,reopened.top().get(0).count);
    }
    @Test public void removingCustomOnlyRemovesThatNameAndPromotesNextEntry() {
        FilmUsageStore store=new FilmUsageStore(preferences());
        store.record("","Custom A");store.record("","Custom B");store.record("c","C");
        assertTrue(store.remove("","Custom B"));assertEquals(2,store.top().size());
        assertEquals("Custom A",store.top().get(1).name);
    }

    @Test public void failedCommitRestoresHistoryInMemory() {
        FilmUsageStore store=new FilmUsageStore(preferences(true));store.record("a","A");
        assertFalse(store.remove("a","A"));assertEquals("a",store.top().get(0).id);assertEquals(1,store.top().get(0).count);
    }
    @Test public void nextRankedItemFillsDeletedSlot() {
        FilmUsageStore store=new FilmUsageStore(preferences());
        for(int i=0;i<6;i++)store.record("item-"+i,"Item "+i);
        assertEquals(5,store.top().size());assertTrue(store.remove("item-5","Item 5"));
        assertEquals(5,store.top().size());assertEquals("item-0",store.top().get(4).id);
    }

    @Test public void retiredVision3ChoiceDoesNotDisplaceValidFavoritesOrRewriteOldRecords() {
        SharedPreferences prefs=preferences();FilmUsageStore store=new FilmUsageStore(prefs);
        FilmRollRecord old=new FilmRollRecord();old.filmId="user-vision3-800t";old.filmName="VISION3 800T";
        store.seed(Collections.singletonList(old));
        for(int i=0;i<6;i++) store.record("valid-"+i,"Film "+i);
        for(int i=0;i<10;i++) store.record(old.filmId,old.filmName);
        List<FilmUsageStore.Entry> favorites=new FilmUsageStore(prefs).top();
        assertEquals(5,favorites.size());assertEquals("valid-5",favorites.get(0).id);
        assertEquals("valid-1",favorites.get(4).id);
        assertEquals("user-vision3-800t",old.filmId);assertEquals("VISION3 800T",old.filmName);
        assertEquals(11,prefs.getInt("count/user-vision3-800t",0));
    }
}
