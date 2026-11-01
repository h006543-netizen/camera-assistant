package com.shutternote;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;

public class FilmRollFoldersTest {
    private Set<String> ids(String... ids) { return new HashSet<>(Arrays.asList(ids)); }

    @Test public void oldInstallationsStartOutsideFoldersWithoutChangingRollData() throws Exception {
        FilmRollFolders folders=FilmRollFolders.read("{}");
        assertTrue(folders.names.isEmpty()); assertEquals("",folders.folderOf("old-roll"));
        assertEquals("",FilmRollFolders.read(folders.json()).folderOf("old-roll"));
    }

    @Test public void unicodeNamesAndMultipleRollMovesSurviveReloadWithoutDuplicateMembership() throws Exception {
        FilmRollFolders folders=new FilmRollFolders();
        assertTrue(folders.create("tokyo"," 26년 도쿄 여행 ")); assertTrue(folders.create("other","다른 여행"));
        assertTrue(folders.move(ids("a","b"),"tokyo",ids("a","b","c")));
        FilmRollFolders restored=FilmRollFolders.read(folders.json());
        assertEquals("26년 도쿄 여행",restored.names.get("tokyo"));
        assertEquals("tokyo",restored.folderOf("a")); assertEquals("tokyo",restored.folderOf("b")); assertEquals("",restored.folderOf("c"));
        assertTrue(restored.move(ids("b","c"),"other",ids("a","b","c")));
        assertEquals("tokyo",restored.folderOf("a")); assertEquals("other",restored.folderOf("b")); assertEquals("other",restored.folderOf("c"));
        assertTrue(restored.move(ids("a"),"",ids("a","b","c"))); assertEquals("",restored.folderOf("a"));
    }

    @Test public void renamingAndRemovingFoldersKeepRollsAndOtherFolderMembership() throws Exception {
        FilmRollFolders folders=new FilmRollFolders(); folders.create("a","A"); folders.create("b","B");
        folders.move(ids("roll-a"),"a",ids("roll-a","roll-b")); folders.move(ids("roll-b"),"b",ids("roll-a","roll-b"));
        assertTrue(folders.rename("a","東京 2026")); assertEquals("a",folders.folderOf("roll-a"));
        assertTrue(folders.remove("a")); assertEquals("",folders.folderOf("roll-a")); assertEquals("b",folders.folderOf("roll-b"));
        FilmRollFolders restored=FilmRollFolders.read(folders.json()); assertEquals(1,restored.names.size()); assertEquals("B",restored.names.get("b"));
    }

    @Test public void invalidNamesAndDeletedDestinationNeverChangeMembership() throws Exception {
        FilmRollFolders folders=new FilmRollFolders(); folders.create("a","A"); folders.move(ids("roll"),"a",ids("roll"));
        String before=folders.json();
        assertFalse(folders.create("a","Duplicate ID")); assertFalse(folders.create("b","   "));
        assertFalse(folders.rename("a",String.join("",Collections.nCopies(81,"x"))));
        assertFalse(folders.move(ids("roll"),"deleted",ids("roll")));
        assertEquals(before,folders.json());
        assertFalse(folders.remove("missing")); assertFalse(folders.rename("missing","Name"));
    }

    @Test public void deletedRollsAreNotResurrectedAndNewRollCreationKeepsOtherFolders() throws Exception {
        FilmRollFolders folders=new FilmRollFolders(); folders.create("a","A"); folders.create("b","B");
        folders.move(ids("old","deleted"),"a",ids("old","deleted"));
        folders.move(ids("new","deleted"),"b",ids("old","new"));
        assertEquals("a",folders.folderOf("old")); assertEquals("b",folders.folderOf("new")); assertEquals("",folders.folderOf("deleted"));
    }

    @Test public void unreadableFolderDataIsRejectedRatherThanReplaced() throws Exception {
        for(String json:new String[]{"broken", "{\"folders\":\"bad\"}", "{\"members\":[]}",
                "{\"folders\":[{\"id\":\"x\",\"name\":\"\"}]}"}) {
            try { FilmRollFolders.read(json); fail("Accepted damaged folder data"); }
            catch(org.json.JSONException expected) { }
        }
    }

    /** Simulates the memory update that SharedPreferences performs even for a failed disk commit. */
    private SharedPreferences preferences(Map<String,String> data,boolean fail) {
        return (SharedPreferences)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{
            if(method.getName().equals("getString")) return data.getOrDefault((String)args[0],(String)args[1]);
            if(method.getName().equals("edit")) {
                Map<String,String> pending=new HashMap<>();
                return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(editor,action,params)->{
                    if(action.getName().equals("putString")) { pending.put((String)params[0],(String)params[1]); return editor; }
                    if(action.getName().equals("commit")) { data.putAll(pending); return !fail; }
                    throw new UnsupportedOperationException(action.getName());
                });
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    @Test public void folderWriteFailureRestoresOriginalAndNeverTouchesRolls() throws Exception {
        Map<String,String> data=new HashMap<>(); data.put("rolls","original-rolls"); data.put("folders","{}");
        FilmRollFolders folders=new FilmRollFolders(); folders.create("a","A");
        assertFalse(FilmFolderStore.commit(preferences(data,true),folders.json()));
        assertEquals("{}",data.get("folders")); assertEquals("original-rolls",data.get("rolls"));
    }

    @Test public void creationCommitsRollAndMembershipTogetherAndRollEditsDoNotUndoMembership() throws Exception {
        FilmRollRecord roll=new FilmRollRecord(); roll.id="roll"; roll.memo="Latest memo";
        FilmRollFolders folders=new FilmRollFolders(); folders.create("tokyo","Tokyo"); folders.move(ids("roll"),"tokyo",ids("roll"));
        Map<String,String> data=new HashMap<>(); SharedPreferences prefs=preferences(data,false);
        assertTrue(FilmRollStore.commit(prefs,Collections.singletonList(roll),folders.json()));
        roll.memo="Edited after move";
        assertTrue(FilmRollStore.commit(prefs,Collections.singletonList(roll)));
        assertEquals("tokyo",FilmRollFolders.read(data.get("folders")).folderOf("roll"));
        assertEquals("Edited after move",new JSONArray(data.get("rolls")).getJSONObject(0).getString("memo"));
    }

    @Test public void failedNewRollCommitRestoresBothRollsAndMembershipExactly() throws Exception {
        Map<String,String> data=new HashMap<>(); data.put("rolls","[]"); data.put("folders","{}");
        FilmRollRecord roll=new FilmRollRecord(); roll.id="new";
        FilmRollFolders folders=new FilmRollFolders(); folders.create("a","A"); folders.move(ids("new"),"a",ids("new"));
        assertFalse(FilmRollStore.commit(preferences(data,true),Collections.singletonList(roll),folders.json()));
        assertEquals("[]",data.get("rolls")); assertEquals("{}",data.get("folders"));
    }

    @Test public void rangeSelectionExtendsRetractsAndPreservesEarlierTappedChoices() {
        FilmRollSelection selection=new FilmRollSelection(); selection.begin("e"); Set<String> baseline=selection.snapshot();
        java.util.List<String> order=Arrays.asList("a","b","c","d","e");
        selection.range(order,"b","d",baseline); assertEquals(ids("b","c","d","e"),selection.snapshot());
        selection.range(order,"b","c",baseline); assertEquals(ids("b","c","e"),selection.snapshot());
        selection.range(order,"b","a",baseline); assertEquals(ids("a","b","e"),selection.snapshot());
        selection.toggle("d"); selection.toggle("a"); assertEquals(ids("b","d","e"),selection.snapshot());
        selection.range(order,"missing","d",baseline); assertEquals(ids("b","d","e"),selection.snapshot());
    }
}
