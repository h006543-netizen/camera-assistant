package com.shutternote;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** UUID-isolated files/preferences: no user photo, gallery output or user roll access. */
@RunWith(AndroidJUnit4.class)
public class E100dCorrectionStorageTest {
    private IsolatedContext context;
    private FilmRollStore store;
    private Bitmap original, corrected;
    private FilmFrameRecord frame(int cut) throws Exception { return store.find("roll").frame(cut); }

    @Before public void setup() throws Exception {
        context = new IsolatedContext(ApplicationProvider.getApplicationContext());
        store = new FilmRollStore(context);
        FilmRollRecord roll = new FilmRollRecord(); roll.id="roll"; assertTrue(store.save(roll));
        original = Bitmap.createBitmap(96,64,Bitmap.Config.ARGB_8888); original.eraseColor(Color.rgb(98,145,201));
        corrected = FilmColorCorrection.apply(context,original,()->false);
    }
    private void save(int cut) {
        FilmFrameRecord frame = new FilmFrameRecord(); frame.number=cut; frame.captureId="shot-"+cut; frame.correctionEnabled=true;
        frame.correctionProfile=FilmColorCorrection.E100D;
        assertTrue(store.saveFrame("roll",frame,original));
    }
    private byte[] bytes(File file) throws Exception {
        try(InputStream in=new FileInputStream(file); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int count; while((count=in.read(buffer))!=-1)out.write(buffer,0,count); return out.toByteArray();
        }
    }
    private int countPhotos() { return Objects.requireNonNull(new File(context.getFilesDir(),"film_notes_photos").list()).length; }

    @Test public void selectedCopyIsReusedAndClearingLeavesOnlyTheOriginal() throws Exception {
        save(7); FilmFrameRecord frame=frame(7); File raw=store.photo(frame);
        byte[] before=bytes(raw); File copy=store.correction(frame); assertTrue(copy.isFile()); assertEquals(2,countPhotos());
        byte[] cached=bytes(copy); long modified=copy.lastModified();
        assertEquals(copy,FilmNoteCorrectionCache.source(context,store,frame));
        assertEquals(copy,FilmNoteCorrectionCache.source(context,new FilmRollStore(context),frame));
        assertEquals(modified,copy.lastModified()); assertArrayEquals(cached,bytes(copy));
        assertTrue(store.saveFrameMemo("roll",7,"latest memo")); assertTrue(store.rotateFrame("roll",7,"shot-7"));
        assertTrue(store.saveCorrection("roll",7,"shot-7",0,false,null));
        store=new FilmRollStore(context); frame=frame(7);
        assertFalse(frame.correctionEnabled); assertArrayEquals(before,bytes(raw));
        assertFalse(copy.exists()); assertEquals(1,countPhotos());
        assertEquals("latest memo",frame.memo); assertEquals(270,frame.rotation);
        assertTrue(store.saveCorrection("roll",7,"shot-7",1,true,FilmColorCorrection.E100D,corrected));
        assertTrue(frame(7).correctionEnabled); assertArrayEquals(before,bytes(raw)); assertEquals(2,countPhotos());
    }
    @Test public void staleOrFailedRecipeChangesKeepMetadataAndOriginal() throws Exception {
        save(1); File raw=store.photo(frame(1)); byte[] before=bytes(raw);
        assertFalse(store.saveCorrection("roll",1,"old-shot",0,false,null));
        context.failNextCommit=true;
        assertFalse(store.saveCorrection("roll",1,"shot-1",0,false,null));
        assertEquals(0,frame(1).correctionRevision); assertTrue(frame(1).correctionEnabled);
        assertTrue(store.correction(frame(1)).isFile()); assertEquals(2,countPhotos()); assertArrayEquals(before,bytes(raw));
        assertTrue(store.saveCorrection("roll",1,"shot-1",0,false,null));
        assertFalse(store.saveCorrection("roll",1,"shot-1",0,true,FilmColorCorrection.E100D)); assertEquals(1,countPhotos());
    }
    @Test public void failedInitialSaveCleansOutputAndRetryDoesNotDuplicate() throws Exception {
        FilmFrameRecord frame=new FilmFrameRecord(); frame.number=1; frame.captureId="shot-1"; frame.correctionEnabled=true;
        frame.correctionProfile=FilmColorCorrection.E100D;
        context.failNextCommit=true;
        assertFalse(store.saveFrame("roll",frame,original)); assertNull(frame(1)); assertEquals(0,countPhotos());
        assertTrue(store.saveFrame("roll",frame,original)); assertEquals(2,countPhotos());
        assertTrue(store.saveFrame("roll",frame,original)); assertEquals(2,countPhotos());
    }
    @Test public void deletingCutsAndRollCleansOriginalsOnlyAfterCommit() throws Exception {
        save(1); save(2); File one=store.photo(frame(1));
        context.failNextCommit=true; assertFalse(store.deleteFrame("roll",1,"shot-1"));
        assertTrue(one.isFile());
        assertTrue(store.deleteFrame("roll",1,"shot-1")); assertFalse(one.exists());
        assertEquals(2,countPhotos()); assertTrue(store.delete(Collections.singleton("roll"))); assertEquals(0,countPhotos());
    }
    private File legacy(String profile) throws Exception {
        save(1); FilmRollRecord roll=store.find("roll"); FilmFrameRecord frame=roll.frame(1);
        assertTrue(store.correction(frame).delete());
        frame.correctionPhoto="legacy-copy.jpg"; frame.correctionProfile=profile;
        File file=new File(context.getFilesDir(),"film_notes_photos/legacy-copy.jpg");
        try(OutputStream out=new FileOutputStream(file)) { assertTrue(corrected.compress(Bitmap.CompressFormat.JPEG,95,out)); }
        assertTrue(context.getSharedPreferences("film_roll_records",0).edit()
                .putString("rolls",new org.json.JSONArray().put(roll.toJson()).toString()).commit());
        return file;
    }
    @Test public void activeLegacyCopyIsPreservedAndReused() throws Exception {
        File copy=legacy(FilmColorCorrection.E100D);
        org.json.JSONObject old=new org.json.JSONArray(context.getSharedPreferences("film_roll_records",0).getString("rolls","[]")).getJSONObject(0);
        File raw=new File(context.getFilesDir(),"film_notes_photos/"+old.getJSONArray("frames").getJSONObject(0).getString("photo"));
        byte[] before=bytes(raw);
        assertEquals("legacy-copy.jpg",frame(1).correctionPhoto); assertTrue(copy.isFile());
        store=new FilmRollStore(context); FilmFrameRecord migrated=frame(1);
        assertTrue(migrated.correctionEnabled); assertEquals(FilmColorCorrection.E100D,migrated.correctionProfile);
        assertEquals("legacy-copy.jpg",migrated.correctionPhoto); assertTrue(copy.exists()); assertEquals(2,countPhotos()); assertArrayEquals(before,bytes(raw));
        assertEquals(copy,FilmNoteCorrectionCache.source(context,store,migrated));
    }
    @Test public void changingProfileReplacesOnlyThePreviousCopyAndFailureKeepsIt() throws Exception {
        File old=legacy("older-profile-version"); byte[] before=bytes(store.photo(frame(1)));
        context.failNextCommit=true;
        assertFalse(store.saveCorrection("roll",1,"shot-1",0,true,FilmColorCorrection.E100D,corrected));
        assertTrue(old.isFile()); assertEquals("older-profile-version",frame(1).correctionProfile); assertEquals(2,countPhotos());
        assertTrue(store.saveCorrection("roll",1,"shot-1",0,true,FilmColorCorrection.E100D,corrected));
        assertFalse(old.exists()); assertTrue(store.correction(frame(1)).isFile()); assertEquals(2,countPhotos());
        assertArrayEquals(before,bytes(store.photo(frame(1))));
    }
    @Test public void unknownRecipeAndMissingOriginalDoNotLoseLegacyCopies() throws Exception {
        File copy=legacy("future-film-version"); assertEquals("legacy-copy.jpg",frame(1).correctionPhoto); assertTrue(copy.isFile());
        FilmRollRecord roll=store.find("roll"); FilmFrameRecord frame=roll.frame(1); frame.correctionProfile=FilmColorCorrection.E100D;
        assertTrue(store.photo(frame).delete());
        assertTrue(context.getSharedPreferences("film_roll_records",0).edit().putString("rolls",new org.json.JSONArray().put(roll.toJson()).toString()).commit());
        assertEquals("legacy-copy.jpg",frame(1).correctionPhoto); assertTrue(copy.isFile());
    }
    @Test public void missingCopyIsCreatedOnceForRecipeOnlyRecords() throws Exception {
        File copy=legacy(FilmColorCorrection.E100D); assertTrue(copy.delete());
        FilmFrameRecord restored=frame(1);
        assertTrue(restored.correctionEnabled); assertEquals(FilmColorCorrection.E100D,restored.correctionProfile);
        File repaired=FilmNoteCorrectionCache.source(context,store,restored);
        assertTrue(repaired.isFile()); assertTrue(store.photo(restored).isFile()); assertEquals(2,countPhotos());
        assertEquals(repaired,FilmNoteCorrectionCache.source(context,store,frame(1))); assertEquals(2,countPhotos());
    }
    @Test public void legacyReferenceToAnotherOriginalNeverDeletesThatPhoto() throws Exception {
        save(1); save(2); FilmRollRecord roll=store.find("roll");
        File other=store.photo(roll.frame(2)); byte[] before=bytes(other);
        roll.frame(1).correctionPhoto=roll.frame(2).photo;
        assertTrue(context.getSharedPreferences("film_roll_records",0).edit().putString("rolls",new org.json.JSONArray().put(roll.toJson()).toString()).commit());
        assertTrue(other.isFile()); assertTrue(store.deleteFrame("roll",1,"shot-1")); assertArrayEquals(before,bytes(other));
    }
    @Test public void exportUsesTheSavedCorrectionWithoutRenderingAgain() throws Exception {
        save(1); FilmRollRecord roll=store.find("roll"); File raw=store.photo(roll.frame(1)); byte[] before=bytes(raw);
        File work=new File(context.getCacheDir(),"recipe-export-"+UUID.randomUUID()); assertTrue(work.mkdirs());
        try {
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            FilmRollPhotoArchive.write(raw.getParentFile(),roll,work,out,(file,degrees)->{},(done,total)->{},
                    (source,frame,file)->{throw new AssertionError("Saved correction must be reused");});
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
                assertNotNull(zip.getNextEntry()); ByteArrayOutputStream jpeg=new ByteArrayOutputStream(); FilmRollPhotoArchive.copy(zip,jpeg);
                Bitmap exported=BitmapFactory.decodeByteArray(jpeg.toByteArray(),0,jpeg.size());
                Bitmap expected=BitmapFactory.decodeFile(store.correction(roll.frame(1)).getAbsolutePath());
                try { assertEquals(expected.getWidth(),exported.getWidth());
                    for(int shift:new int[]{0,8,16}) assertTrue(Math.abs(((expected.getPixel(0,0)>>>shift)&255)-((exported.getPixel(0,0)>>>shift)&255))<=3);
                } finally { exported.recycle(); expected.recycle(); }
            }
            assertEquals(0,Objects.requireNonNull(work.list()).length); assertEquals(2,countPhotos()); assertArrayEquals(before,bytes(raw));
        } finally { remove(work); }
    }
    @Test public void galleryRecipeFailuresRestorePreviousSettingsWithoutCreatingPhotos() {
        String capture="isolated-gallery-capture"; android.net.Uri output=android.net.Uri.parse("content://fixture/images/1");
        SharedPreferences preferences=context.getSharedPreferences("capture_saves",0);
        context.failNextCommit=true;
        assertFalse(FilmGalleryRecipeStore.save(context,capture,output,true,corrected));
        assertFalse(preferences.contains(capture)); assertFalse(preferences.contains("output:"+capture)); assertFalse(preferences.contains("recipe:"+output));
        assertTrue(FilmGalleryRecipeStore.save(context,capture,output,true,corrected)); assertTrue(FilmGalleryRecipeStore.corrected(context,capture));
        File cached=FilmGalleryRecipeStore.correction(context,capture); assertTrue(cached.isFile());
        context.failNextCommit=true; assertFalse(FilmGalleryRecipeStore.select(context,capture,false));
        assertTrue(FilmGalleryRecipeStore.corrected(context,capture));
        assertTrue(FilmGalleryRecipeStore.select(context,capture,false)); assertFalse(FilmGalleryRecipeStore.corrected(context,capture));
        assertFalse(cached.exists());
        assertFalse(new File(context.getFilesDir(),"film_notes_photos").exists());
    }
    @Test public void androidBitmapRendererMatchesSuppliedFixtureAndDoesNotChangeSourcePixels() throws Exception {
        byte[] source=null, expected=null;
        try(ZipInputStream zip=new ZipInputStream(context.getAssets().open("film_corrections/e100d/correction.snfilm"))) {
            java.util.zip.ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                if(!entry.getName().equals("reference_input.png")&&!entry.getName().equals("reference_output.png"))continue;
                ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int count;
                while((count=zip.read(buffer))!=-1)out.write(buffer,0,count);
                if(entry.getName().equals("reference_input.png"))source=out.toByteArray(); else expected=out.toByteArray();
            }
        }
        assertNotNull(source); assertNotNull(expected);
        Bitmap input=BitmapFactory.decodeByteArray(source,0,source.length), target=BitmapFactory.decodeByteArray(expected,0,expected.length);
        int size=input.getWidth()*input.getHeight(); int[] before=new int[size], after=new int[size], actual=new int[size], wanted=new int[size];
        input.getPixels(before,0,input.getWidth(),0,0,input.getWidth(),input.getHeight());
        Bitmap rendered=FilmColorCorrection.apply(context,input,()->false);
        try {
            input.getPixels(after,0,input.getWidth(),0,0,input.getWidth(),input.getHeight()); assertArrayEquals(before,after);
            rendered.getPixels(actual,0,input.getWidth(),0,0,input.getWidth(),input.getHeight());
            target.getPixels(wanted,0,input.getWidth(),0,0,input.getWidth(),input.getHeight());
            for(int i=0;i<size;i++)for(int shift:new int[]{0,8,16})assertTrue(Math.abs(((actual[i]>>>shift)&255)-((wanted[i]>>>shift)&255))<=1);
        } finally { input.recycle(); target.recycle(); rendered.recycle(); }
    }
    @After public void cleanup() throws Exception {
        if(original!=null)original.recycle(); if(corrected!=null)corrected.recycle();
        if(context==null)return;
        for(String name:context.preferences)context.getBaseContext().getSharedPreferences(name,0).edit().clear().commit();
        File directory=context.getFilesDir(); assertTrue(directory.getCanonicalPath().startsWith(context.getCacheDir().getCanonicalPath()+File.separator+"e100d-test-"));
        remove(directory);
    }
    private static void remove(File file) { File[] children=file.listFiles(); if(children!=null)for(File child:children)remove(child); file.delete(); }
    private static final class IsolatedContext extends ContextWrapper {
        private final String prefix="e100d-test-"+UUID.randomUUID()+"-";
        private final File directory;
        final Set<String> preferences=new HashSet<>(); boolean failNextCommit;
        IsolatedContext(Context base) { super(base); directory=new File(base.getCacheDir(),prefix); assertTrue(directory.mkdirs()); }
        @Override public Context getApplicationContext() { return this; }
        @Override public File getFilesDir() { return directory; }
        @Override public SharedPreferences getSharedPreferences(String name,int mode) {
            String key=prefix+name; preferences.add(key); SharedPreferences delegate=super.getSharedPreferences(key,mode);
            if(!name.equals("film_roll_records")&&!name.equals("capture_saves"))return delegate;
            return (SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{
                if(!method.getName().equals("edit"))return method.invoke(delegate,args);
                SharedPreferences.Editor editor=delegate.edit();
                return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(editProxy,editMethod,editArgs)->{
                    Object result=editMethod.invoke(editor,editArgs);
                    if(editMethod.getName().equals("commit")&&failNextCommit){failNextCommit=false;return false;}
                    return result instanceof SharedPreferences.Editor?editProxy:result;
                });
            });
        }
    }
}
