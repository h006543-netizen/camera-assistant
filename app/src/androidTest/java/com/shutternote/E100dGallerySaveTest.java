package com.shutternote;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.media.MediaScannerConnection;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Explicit generated gallery fixture. Cleanup targets only new rows matching its unique name. */
@RunWith(AndroidJUnit4.class)
public class E100dGallerySaveTest {
    private Map<String,String> rows(Context context,String prefix) {
        Map<String,String> result=new LinkedHashMap<>();
        if(Build.VERSION.SDK_INT<29) {
            File directory=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),"Camera");
            File[] files=directory.listFiles((dir,name)->name.startsWith(prefix));
            if(files!=null)for(File file:files)result.put(file.getAbsolutePath(),file.getName());
            return result;
        }
        try(Cursor cursor=context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID,MediaStore.Images.Media.DISPLAY_NAME},
                MediaStore.Images.Media.DISPLAY_NAME+" LIKE ?",new String[]{prefix+"%"},null)) {
            assertNotNull(cursor);
            while(cursor.moveToNext())result.put(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cursor.getLong(0)).toString(),cursor.getString(1));
        }
        return result;
    }
    @Test public void savesOneOriginalWithRecipeAndChangesSettingsWithoutMorePhotos() throws Exception {
        Context context=ApplicationProvider.getApplicationContext();
        assumeTrue(Build.VERSION.SDK_INT>=29||context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED);
        File source=new File(context.getCacheDir(),"e100d-gallery-"+UUID.randomUUID()+".png");
        Bitmap image=Bitmap.createBitmap(192,128,Bitmap.Config.ARGB_8888); image.eraseColor(Color.rgb(80,130,190));
        try(OutputStream out=new FileOutputStream(source)){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}finally{image.recycle();}
        assertTrue(source.setLastModified(946684800000L+new Random().nextInt(10000000)*60000L));
        String name=GalleryFileNameBuilder.build("100","f/2.8","1/125",ExposureSettingsStore.FIELD_OF_VIEW_DEFAULT,new Date(source.lastModified()));
        String prefix=name.substring(0,name.length()-4);
        Set<String> before=rows(context,prefix).keySet();
        Intent intent=new Intent(context,FilmResultActivity.class).putExtra(FilmResultActivity.EXTRA_IMAGE_PATH,source.getAbsolutePath())
                .putExtra(FilmResultActivity.EXTRA_EXPOSURE_MULTIPLIER,1.0).putExtra(FilmResultActivity.EXTRA_ISO,"100")
                .putExtra(FilmResultActivity.EXTRA_APERTURE,"f/2.8").putExtra(FilmResultActivity.EXTRA_SHUTTER,"1/125");
        try(ActivityScenario<FilmResultActivity> scenario=ActivityScenario.launch(intent)) {
            AtomicBoolean ready=new AtomicBoolean(); long deadline=SystemClock.uptimeMillis()+15000;
            while(!ready.get()&&SystemClock.uptimeMillis()<deadline) {
                scenario.onActivity(activity->ready.set(activity.findViewById(R.id.noteCorrectionCategory).isEnabled())); SystemClock.sleep(50);
            }
            assertTrue(ready.get());
            scenario.onActivity(activity->E100dResultUiTest.assertChoice(activity,R.string.correction_original));
            E100dResultUiTest.selectCorrection(scenario,R.string.correction_e100d);
            scenario.onActivity(activity->activity.findViewById(R.id.btnFilmResultSave).performClick());
            ready.set(false); deadline=SystemClock.uptimeMillis()+15000;
            while(!ready.get()&&SystemClock.uptimeMillis()<deadline) {
                scenario.onActivity(activity->ready.set(((TextView)activity.findViewById(R.id.btnFilmResultSave)).getText().toString().equals(activity.getString(R.string.saved))));
                SystemClock.sleep(50);
            }
            assertTrue("Gallery save did not complete",ready.get());
            Map<String,String> added=rows(context,prefix); added.keySet().removeAll(before); deadline=SystemClock.uptimeMillis()+10000;
            while(added.size()<1&&SystemClock.uptimeMillis()<deadline) {
                SystemClock.sleep(100); added=rows(context,prefix); added.keySet().removeAll(before);
            }
            assertEquals(1,added.size()); assertTrue(FilmGalleryRecipeStore.corrected(context,source.getAbsolutePath()));
            File cached=FilmGalleryRecipeStore.correction(context,source.getAbsolutePath()); assertTrue(cached.isFile());
            long modified=cached.lastModified();
            E100dResultUiTest.selectCorrection(scenario,R.string.correction_e100d);
            assertEquals(cached,FilmGalleryRecipeStore.correction(context,source.getAbsolutePath())); assertEquals(modified,cached.lastModified());
            assertTrue(added.values().stream().noneMatch(value->value.contains("_E100D")));
            Set<Integer> colors=new HashSet<>();
            for(String path:added.keySet()) {
                try(InputStream in=path.startsWith("content:")?context.getContentResolver().openInputStream(Uri.parse(path)):new FileInputStream(path)) {
                    Bitmap bitmap=android.graphics.BitmapFactory.decodeStream(in); assertNotNull(bitmap);
                    int pixel=bitmap.getPixel(0,0);
                    for(int shift:new int[]{0,8,16}) assertTrue(Math.abs(((pixel>>>shift)&255)-((Color.rgb(80,130,190)>>>shift)&255))<=3);
                    colors.add(pixel); bitmap.recycle();
                }
            }
            assertEquals(1,colors.size());
            E100dResultUiTest.selectCorrection(scenario,R.string.correction_original);
            assertFalse(FilmGalleryRecipeStore.corrected(context,source.getAbsolutePath()));assertFalse(cached.exists());
            scenario.recreate();E100dResultUiTest.awaitReady(scenario);
            scenario.onActivity(activity->E100dResultUiTest.assertChoice(activity,R.string.correction_original));
            assertFalse(FilmGalleryRecipeStore.corrected(context,source.getAbsolutePath()));
            E100dResultUiTest.selectCorrection(scenario,R.string.correction_e100d);
            File restored=FilmGalleryRecipeStore.correction(context,source.getAbsolutePath());assertNotNull(restored);assertTrue(restored.isFile());
            E100dResultUiTest.selectCorrection(scenario,R.string.correction_original);
            assertFalse(FilmGalleryRecipeStore.corrected(context,source.getAbsolutePath()));assertFalse(restored.exists());
            scenario.recreate();E100dResultUiTest.awaitReady(scenario);
            scenario.onActivity(activity->E100dResultUiTest.assertChoice(activity,R.string.correction_original));
            Map<String,String> after=rows(context,prefix);after.keySet().removeAll(before);assertEquals(1,after.size());

        } finally {
            Map<String,String> added=rows(context,prefix); added.keySet().removeAll(before);
            for(String path:added.keySet()) {
                if(path.startsWith("content:"))context.getContentResolver().delete(Uri.parse(path),null,null);
                else {
                    assertTrue(new File(path).delete());
                    MediaScannerConnection.scanFile(context,new String[]{path},new String[]{"image/jpeg"},null);
                }
            }
            android.content.SharedPreferences preferences=context.getSharedPreferences("capture_saves",0);
            File cached=FilmGalleryRecipeStore.correction(context,source.getAbsolutePath()); if(cached!=null)assertTrue(cached.delete());
            String output=preferences.getString("output:"+source.getAbsolutePath(),null);
            preferences.edit().remove(source.getAbsolutePath()).remove("output:"+source.getAbsolutePath()).remove("recipe:"+output).commit();
            assertTrue(source.delete());
        }
    }
}
