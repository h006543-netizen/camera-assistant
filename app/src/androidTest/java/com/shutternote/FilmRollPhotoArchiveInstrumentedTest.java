package com.shutternote;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.exifinterface.media.ExifInterface;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.*;
import java.nio.file.Files;
import java.util.zip.ZipInputStream;
import static org.junit.Assert.*;

public class FilmRollPhotoArchiveInstrumentedTest {
    @Test public void exportedJpegHasDisplayRotationWithoutRecompressionOrSourceChanges() throws Exception {
        File directory=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),
                "archive-test-"+java.util.UUID.randomUUID());
        assertTrue(directory.mkdirs());
        Bitmap image=Bitmap.createBitmap(40,20,Bitmap.Config.ARGB_8888);
        image.eraseColor(android.graphics.Color.RED);
        File original=new File(directory,"source.jpg"), extracted=new File(directory,"extracted.jpg");
        try {
            try(OutputStream stream=new FileOutputStream(original)) { assertTrue(image.compress(Bitmap.CompressFormat.JPEG,95,stream)); }
            byte[] before=Files.readAllBytes(original.toPath());
            int sourceOrientation=new ExifInterface(original).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
            for(int degrees:new int[]{0,90,180,270}) {
                FilmRollRecord roll=new FilmRollRecord();roll.name="photos";
                FilmFrameRecord frame=new FilmFrameRecord();frame.number=12;frame.photo="source.jpg";frame.rotation=degrees;roll.frames.add(frame);
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                FilmRollPhotoArchive.write(directory,roll,directory,bytes,FilmRollExportViewModel::orientCopy,(done,total)->{});
                try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                    assertEquals("photos/frame_012.jpg",zip.getNextEntry().getName());
                    try(OutputStream out=new FileOutputStream(extracted)) { FilmRollPhotoArchive.copy(zip,out); }
                    assertNull(zip.getNextEntry());
                }
                assertArrayEquals(before,Files.readAllBytes(original.toPath()));
                if(degrees==0) assertArrayEquals(before,Files.readAllBytes(extracted.toPath()));
                int expected=degrees==0?sourceOrientation:degrees==90?6:degrees==180?3:8;
                assertEquals(expected,new ExifInterface(extracted).getAttributeInt(ExifInterface.TAG_ORIENTATION,1));
                Bitmap decoded=BitmapFactory.decodeFile(original.getAbsolutePath());
                Bitmap exported=BitmapFactory.decodeFile(extracted.getAbsolutePath());
                Bitmap displayed=FilmScanImages.read(extracted,0,true);
                try {
                    assertTrue(decoded.sameAs(exported));
                    assertEquals(degrees%180==0?40:20,displayed.getWidth());
                    assertEquals(degrees%180==0?20:40,displayed.getHeight());
                } finally { decoded.recycle();exported.recycle();displayed.recycle(); }
            }
        } finally {
            image.recycle();
            assertTrue(directory.getCanonicalPath().startsWith(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir().getCanonicalPath()+File.separator+"archive-test-"));
            File[] files=directory.listFiles();if(files!=null)for(File file:files)file.delete();directory.delete();
        }
    }
}
