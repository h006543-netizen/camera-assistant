package com.shutternote;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.exifinterface.media.ExifInterface;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

/** Host build checks only; execution requires a device. */
@RunWith(AndroidJUnit4.class)
public class FilmSceneMetadataTest {
    @Test public void originalExifAndPinnedSnapshotWorkWithoutUsingFileTime() throws Exception {
        Context app=ApplicationProvider.getApplicationContext();
        File file=new File(app.getCacheDir(),"scene-time-"+java.util.UUID.randomUUID()+".jpg");
        Bitmap bitmap=Bitmap.createBitmap(32,24,Bitmap.Config.ARGB_8888);
        try {
            try(FileOutputStream out=new FileOutputStream(file)){assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG,95,out));}
            assertTrue(Double.isNaN(FilmSceneMetadata.captureHour(file,Double.NaN)));
            ExifInterface exif=new ExifInterface(file);
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,"2026:10:14 12:21:00");
            exif.setAttribute("OffsetTimeOriginal","+09:00");exif.saveAttributes();
            assertEquals(12.35,FilmSceneMetadata.captureHour(file,Double.NaN),1e-12);
            assertEquals(21.5,FilmSceneMetadata.captureHour(file,21.5),0);
            exif=new ExifInterface(file);exif.setAttribute("OffsetTimeOriginal",null);exif.saveAttributes();
            assertTrue(Double.isNaN(FilmSceneMetadata.captureHour(file,Double.NaN)));
            assertEquals(21.5,FilmSceneMetadata.captureHour(file,21.5),0);
        }finally{bitmap.recycle();file.delete();}
    }
}
