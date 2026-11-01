package com.shutternote;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.Assert.*;

/** All persistence is UUID-isolated; existing notes and gallery files are never touched. */
@RunWith(AndroidJUnit4.class)
public class LatestFilmNotesTest {
    private Isolated context;private FilmRollStore store;private Bitmap original;
    @Before public void setup() throws Exception {
        context=new Isolated(ApplicationProvider.getApplicationContext());store=new FilmRollStore(context);
        FilmRollRecord roll=new FilmRollRecord();roll.id="roll";assertTrue(store.save(roll));
        original=Bitmap.createBitmap(128,96,Bitmap.Config.ARGB_8888);
        int[] pixels=new int[128*96];
        for(int y=0;y<96;y++)for(int x=0;x<128;x++)pixels[y*128+x]=0xff000000|((x*255/127)<<16)|((y*255/95)<<8)|70;
        original.setPixels(pixels,0,128,0,0,128,96);
        FilmFrameRecord frame=new FilmFrameRecord();frame.number=1;frame.captureId="shot";assertTrue(store.saveFrame("roll",frame,original));
    }
    private FilmFrameRecord frame() throws Exception {return new FilmRollStore(context).find("roll").frame(1);}
    private static byte[] bytes(File file) throws Exception {
        try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();
        }
    }
    @Test public void allFiveRenderReplaceOneCopyAndReuseItForNoteAndExport() throws Exception {
        File raw=store.photo(frame());byte[] before=bytes(raw);
        int[] originalPixels=new int[128*96];original.getPixels(originalPixels,0,128,0,0,128,96);
        File previous=null;
        for(String id:FilmCorrectionProfiles.IDS) {
            Bitmap result=FilmColorCorrection.apply(context,original,id,()->false);
            try {assertTrue(store.saveCorrection("roll",1,"shot",frame().correctionRevision,true,id,result));}
            finally {result.recycle();}
            FilmFrameRecord current=frame();assertEquals(id,current.correctionProfile);assertTrue(current.correctionEnabled);
            File active=store.correction(current);assertTrue(active.isFile());if(previous!=null)assertFalse(previous.exists());
            byte[] saved=bytes(active);long modified=active.lastModified();
            assertEquals(active,FilmNoteCorrectionCache.source(context,store,current));
            assertEquals(modified,active.lastModified());assertArrayEquals(saved,bytes(active));
            assertArrayEquals(before,bytes(raw));previous=active;
            assertEquals(2,new File(context.getFilesDir(),"film_notes_photos").listFiles().length);
        }
        int[] after=new int[originalPixels.length];original.getPixels(after,0,128,0,0,128,96);assertArrayEquals(originalPixels,after);
        File zip=new File(context.getFilesDir(),"notes.zip");
        try(OutputStream destination=new FileOutputStream(zip)) {
            FilmRollPhotoArchive.write(raw.getParentFile(),store.find("roll"),new File(context.getFilesDir(),"export-work"),destination,
                    (file,degrees)->{},(done,total)->{},(file,frame,output)->{throw new IOException("Cached correction should be reused");});
        }
        try(java.util.zip.ZipInputStream input=new java.util.zip.ZipInputStream(new FileInputStream(zip))) {
            java.util.zip.ZipEntry entry;boolean found=false;
            while((entry=input.getNextEntry())!=null)if(entry.getName().endsWith(".jpg")) {
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
                while((n=input.read(buffer))!=-1)out.write(buffer,0,n);assertArrayEquals(bytes(previous),out.toByteArray());found=true;
            }
            assertTrue(found);
        }
    }
    @Test public void failedFilmChangeAndUnsupportedVersionsPreservePreviousSelectionAndFiles() throws Exception {
        String first=FilmCorrectionProfiles.IDS[0],second=FilmCorrectionProfiles.IDS[1];
        Bitmap image=FilmColorCorrection.apply(context,original,first,()->false);
        try {assertTrue(store.saveCorrection("roll",1,"shot",0,true,first,image));}finally {image.recycle();}
        FilmFrameRecord before=frame();File cached=store.correction(before),raw=store.photo(before);
        byte[] copy=bytes(cached),source=bytes(raw);
        image=FilmColorCorrection.apply(context,original,second,()->false);
        try {context.failCommit=true;assertFalse(store.saveCorrection("roll",1,"shot",before.correctionRevision,true,second,image));}
        finally {image.recycle();}
        assertEquals(first,frame().correctionProfile);assertEquals(before.correctionRevision,frame().correctionRevision);
        assertEquals(cached,store.correction(frame()));assertArrayEquals(copy,bytes(cached));assertArrayEquals(source,bytes(raw));
        assertFalse(store.saveCorrection("roll",1,"shot",before.correctionRevision,true,"unknown-version",null));
        assertEquals(2,new File(context.getFilesDir(),"film_notes_photos").listFiles().length);
    }
    @Test public void newProfileCanBeSavedWithCaptureAndMissingDerivedCopyIsRegeneratedOnce() throws Exception {
        FilmFrameRecord fresh=new FilmFrameRecord();fresh.number=2;fresh.captureId="shot2";
        fresh.correctionEnabled=true;fresh.correctionProfile=FilmCorrectionProfiles.IDS[3];
        assertTrue(store.saveFrame("roll",fresh,original));
        FilmFrameRecord current=store.find("roll").frame(2);File derived=store.correction(current),raw=store.photo(current);
        byte[] source=bytes(raw);assertTrue(derived.delete());
        File restored=FilmNoteCorrectionCache.source(context,store,current);assertTrue(restored.isFile());
        assertArrayEquals(source,bytes(raw));long modified=restored.lastModified();
        assertEquals(restored,FilmNoteCorrectionCache.source(context,store,store.find("roll").frame(2)));
        assertEquals(modified,restored.lastModified());
    }
    @After public void cleanup() throws Exception {
        if(original!=null)original.recycle();if(context==null)return;
        for(String key:context.preferences)context.getBaseContext().getSharedPreferences(key,0).edit().clear().commit();
        File root=context.getFilesDir();assertTrue(root.getCanonicalPath().startsWith(context.getCacheDir().getCanonicalPath()+File.separator+"latest-films-"));remove(root);
    }
    private static void remove(File file) {File[] children=file.listFiles();if(children!=null)for(File child:children)remove(child);file.delete();}
    private static final class Isolated extends ContextWrapper {
        final String prefix="latest-films-"+UUID.randomUUID()+"-";final File directory;final Set<String> preferences=new HashSet<>();boolean failCommit;
        Isolated(Context base) {super(base);directory=new File(base.getCacheDir(),prefix);assertTrue(directory.mkdirs());}
        @Override public Context getApplicationContext(){return this;}
        @Override public File getFilesDir(){return directory;}
        @Override public SharedPreferences getSharedPreferences(String name,int mode) {
            String key=prefix+name;preferences.add(key);SharedPreferences delegate=super.getSharedPreferences(key,mode);
            return (SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{
                if(!method.getName().equals("edit"))return method.invoke(delegate,args);
                SharedPreferences.Editor editor=delegate.edit();
                return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(proxy2,m,a)->{
                    if(m.getName().equals("commit")&&failCommit){failCommit=false;return false;}
                    Object result=m.invoke(editor,a);return result instanceof SharedPreferences.Editor?proxy2:result;
                });
            });
        }
    }
}
