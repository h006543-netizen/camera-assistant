package com.shutternote;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import androidx.exifinterface.media.ExifInterface;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Uses UUID-isolated preferences and files; never reads or changes the user's rolls. */
@RunWith(AndroidJUnit4.class)
public class FilmScanStorageTest {
    private IsolatedContext context;
    private FilmRollStore store;
    private FilmScanFiles files;
    private String rollId;
    private File source;

    @Before public void prepare() throws Exception {
        context = new IsolatedContext(ApplicationProvider.getApplicationContext());
        store = new FilmRollStore(context); files = new FilmScanFiles(context.getFilesDir());
        FilmRollRecord roll = new FilmRollRecord(); roll.id = UUID.randomUUID().toString(); rollId = roll.id;
        assertTrue(store.save(roll));
        Bitmap image = Bitmap.createBitmap(240,160,Bitmap.Config.ARGB_8888); image.eraseColor(Color.GRAY);
        try {
            for (int cut : new int[]{12,36}) { FilmFrameRecord frame = new FilmFrameRecord(); frame.number=cut; frame.captureId="capture-"+cut; assertTrue(store.saveFrame(rollId,frame,image)); }
            source = new File(context.getFilesDir(),"gallery-original.png");
            try(FileOutputStream output = new FileOutputStream(source)) { assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,output)); }
        } finally { image.recycle(); }
    }

    private File draft() throws Exception { try(FileInputStream input = new FileInputStream(source)) { return files.copyDraft(input); } }
    private FilmFrameRecord frame(int cut) throws Exception { return store.find(rollId).frame(cut); }
    private byte[] bytes(File file) throws Exception {
        try(FileInputStream input = new FileInputStream(file); java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer=new byte[1024]; int count; while((count=input.read(buffer))!=-1)output.write(buffer,0,count); return output.toByteArray();
        }
    }

    @Test public void durableLinkSurvivesSourceDeletionAndPreservesLatestMemo() throws Exception {
        File pending=draft(); assertTrue(store.saveFrameMemo(rollId,12,"latest memo"));
        assertTrue(store.saveScan(rollId,12,"capture-12",0,pending));
        File linked=store.scan(frame(12)); assertArrayEquals(bytes(source),bytes(linked));
        assertTrue(source.delete()); assertTrue(linked.isFile());
        assertEquals("latest memo",frame(12).memo); assertTrue(store.photo(frame(12)).isFile());
        assertEquals(1,frame(12).scanRevision); assertNotNull(frame(36));
    }
    @Test public void staleReplacementAndUnlinkPreserveBothOriginalAndCurrentScan() throws Exception {
        assertTrue(store.saveScan(rollId,12,"capture-12",0,draft())); File first=store.scan(frame(12));
        File next=draft(); assertFalse(store.saveScan(rollId,12,"old-capture",1,next));
        assertFalse(store.saveScan(rollId,12,"capture-12",0,next));
        assertTrue(first.isFile()); assertTrue(store.saveScan(rollId,12,"capture-12",1,next));
        File second=store.scan(frame(12)); assertFalse(first.exists()); assertTrue(second.exists());
        assertFalse(store.saveScan(rollId,12,"capture-12",1,null)); assertTrue(second.exists());
        assertTrue(store.saveScan(rollId,12,"capture-12",2,null)); assertFalse(second.exists());
        assertTrue(source.isFile()); assertTrue(store.photo(frame(12)).isFile());
    }
    @Test public void failedPreferenceCommitRestoresMetadataAndRemovesOnlyNewCopy() throws Exception {
        assertTrue(store.saveScan(rollId,12,"capture-12",0,draft())); File first=store.scan(frame(12));
        File next=draft(); context.failNextCommit=true;
        assertFalse(store.saveScan(rollId,12,"capture-12",1,next));
        assertEquals(first,store.scan(frame(12))); assertEquals(1,frame(12).scanRevision);
        assertTrue(first.isFile()); assertTrue(next.isFile()); assertFalse(files.scan(next.getName().substring(8)).exists());
    }
    @Test public void deletingCutAndRollCleansTheirScansOnly() throws Exception {
        assertTrue(store.saveScan(rollId,12,"capture-12",0,draft())); assertTrue(store.saveScan(rollId,36,"capture-36",0,draft()));
        File first=store.scan(frame(12)), last=store.scan(frame(36));
        assertTrue(store.deleteFrame(rollId,12,"capture-12")); assertFalse(first.exists()); assertTrue(last.exists());
        assertTrue(store.delete(java.util.Collections.singleton(rollId))); assertFalse(last.exists()); assertTrue(source.exists());
    }
    @Test public void completedDraftIdentityMakesRepeatedConfirmationANoOp() throws Exception {
        File pending=draft(); assertTrue(store.saveScan(rollId,12,"capture-12",0,pending));
        assertTrue(FilmScanRules.completedDraft(frame(12),pending.getName()));
        assertTrue(store.saveScan(rollId,12,"capture-12",1,pending));
        assertEquals(1,frame(12).scanRevision); assertTrue(store.scan(frame(12)).isFile());
    }
    @Test public void exifAndNoteRotationDetermineTheDisplayedAspectRatio() throws Exception {
        File jpeg=new File(context.getFilesDir(),"orientation.jpg");
        Bitmap image=Bitmap.createBitmap(240,160,Bitmap.Config.ARGB_8888); image.eraseColor(Color.RED);
        try(FileOutputStream output=new FileOutputStream(jpeg)) { assertTrue(image.compress(Bitmap.CompressFormat.JPEG,100,output)); } finally { image.recycle(); }
        ExifInterface exif=new ExifInterface(jpeg); exif.setAttribute(ExifInterface.TAG_ORIENTATION,String.valueOf(ExifInterface.ORIENTATION_ROTATE_90)); exif.saveAttributes();
        Bitmap scan=FilmScanImages.read(jpeg,0,true), note=FilmScanImages.read(store.photo(frame(12)),90,false);
        try { assertEquals(160,scan.getWidth()); assertEquals(240,scan.getHeight()); assertTrue(FilmScanRules.sideBySide(note.getWidth(),note.getHeight(),scan.getWidth(),scan.getHeight())); }
        finally { scan.recycle(); note.recycle(); }
    }
    @Test public void invalidImageDoesNotReplaceStoredConnection() throws Exception {
        assertTrue(store.saveScan(rollId,12,"capture-12",0,draft())); File previous=store.scan(frame(12));
        File invalid=files.copyDraft(new ByteArrayInputStream(new byte[]{1,2,3}));
        try { FilmScanImages.read(invalid,0,true); fail(); } catch(java.io.IOException expected) { }
        assertEquals(previous,store.scan(frame(12))); assertTrue(previous.isFile()); invalid.delete();
    }

    @After public void cleanup() throws Exception {
        if(context==null)return;
        for(String name:context.preferences) context.getBaseContext().getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear().commit();
        File directory=context.getFilesDir();
        assertTrue(directory.getCanonicalPath().startsWith(context.getCacheDir().getCanonicalPath()+File.separator+"scan-test-"));
        remove(directory);
    }
    private static void remove(File file) { File[] children=file.listFiles(); if(children!=null)for(File child:children)remove(child); file.delete(); }

    private static final class IsolatedContext extends ContextWrapper {
        private final String prefix="scan-test-"+UUID.randomUUID()+"-";
        private final File directory;
        final Set<String> preferences=new HashSet<>();
        boolean failNextCommit;
        IsolatedContext(Context base) { super(base); directory=new File(base.getCacheDir(),prefix); assertTrue(directory.mkdirs()); }
        @Override public Context getApplicationContext() { return this; }
        @Override public File getFilesDir() { return directory; }
        @Override public SharedPreferences getSharedPreferences(String name,int mode) {
            String key=prefix+name; preferences.add(key);
            SharedPreferences delegate=super.getSharedPreferences(key,mode);
            if(!name.equals("film_roll_records"))return delegate;
            return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{
                if(!method.getName().equals("edit"))return method.invoke(delegate,args);
                SharedPreferences.Editor editor=delegate.edit();
                return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(editProxy,editMethod,editArgs)->{
                    Object result=editMethod.invoke(editor,editArgs);
                    if(editMethod.getName().equals("commit")&&failNextCommit) { failNextCommit=false; return false; }
                    return result instanceof SharedPreferences.Editor ? editProxy : result;
                });
            });
        }
    }
}
