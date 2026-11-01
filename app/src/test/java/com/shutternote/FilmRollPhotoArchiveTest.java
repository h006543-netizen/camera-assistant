package com.shutternote;

import org.junit.Test;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.ZipInputStream;
import static org.junit.Assert.*;

public class FilmRollPhotoArchiveTest {
    private FilmFrameRecord frame(File photos, int number, int rotation) throws Exception {
        FilmFrameRecord frame=new FilmFrameRecord(); frame.number=number; frame.rotation=rotation;
        frame.photo="photo_"+number+".jpg"; frame.capturedAt=1700000000000L;
        Files.write(new File(photos,frame.photo).toPath(),new byte[]{10,(byte)number,30});
        return frame;
    }
    private Map<String,byte[]> read(byte[] bytes) throws Exception {
        Map<String,byte[]> entries=new LinkedHashMap<>();
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[1024]; int count;
                while((count=zip.read(buffer))!=-1)out.write(buffer,0,count);
                entries.put(entry.getName(),out.toByteArray());
            }
        }
        return entries;
    }
    private void clean(File directory) throws Exception {
        File[] files=directory.listFiles(); if(files!=null)for(File file:files)clean(file);
        Files.deleteIfExists(directory.toPath());
    }
    @Test public void preservesOriginalBytesAndPhysicalNumbersInSortedUnicodeFolder() throws Exception {
        File dir=Files.createTempDirectory("roll-export-").toFile();
        try {
            FilmRollRecord roll=new FilmRollRecord(); roll.name="가을 산책";
            roll.frames.add(frame(dir,12,0)); roll.frames.add(frame(dir,1,0));
            ByteArrayOutputStream out=new ByteArrayOutputStream(); List<Integer> progress=new ArrayList<>();
            assertEquals(2,FilmRollPhotoArchive.write(dir,roll,dir,out,(f,r)->fail(),(n,t)->progress.add(n)));
            Map<String,byte[]> entries=read(out.toByteArray());
            assertEquals(Arrays.asList("가을 산책/frame_001.jpg","가을 산책/frame_012.jpg"),new ArrayList<>(entries.keySet()));
            assertEquals(Arrays.asList(0,1,2),progress);
            assertArrayEquals(Files.readAllBytes(new File(dir,"photo_12.jpg").toPath()),entries.get("가을 산책/frame_012.jpg"));
            assertEquals(2,roll.frames.size());
        } finally { clean(dir); }
    }
    @Test public void rotationWritesOnlyATemporaryCopyAndCleansIt() throws Exception {
        File dir=Files.createTempDirectory("roll-export-").toFile();
        try {
            FilmRollRecord roll=new FilmRollRecord();roll.name="test";roll.frames.add(frame(dir,4,270));
            File original=new File(dir,"photo_4.jpg"); byte[] before=Files.readAllBytes(original.toPath());
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            FilmRollPhotoArchive.write(dir,roll,dir,out,(copy,degrees)-> {
                assertEquals(270,degrees); assertNotEquals(original,copy);
                Files.write(copy.toPath(),new byte[]{9,8,7});
            },(n,t)->{});
            assertArrayEquals(before,Files.readAllBytes(original.toPath()));
            assertArrayEquals(new byte[]{9,8,7},read(out.toByteArray()).get("test/frame_004.jpg"));
            assertEquals(1,Objects.requireNonNull(dir.listFiles()).length);
        } finally { clean(dir); }
    }
    @Test public void missingPhotoAndEmptyRollFailBeforeWritingAnything() throws Exception {
        File dir=Files.createTempDirectory("roll-export-").toFile();
        try {
            FilmRollRecord roll=new FilmRollRecord(); ByteArrayOutputStream out=new ByteArrayOutputStream();
            try { FilmRollPhotoArchive.write(dir,roll,dir,out,(f,r)->{},(n,t)->{}); fail(); } catch(IOException expected) { }
            FilmFrameRecord frame=frame(dir,1,0); roll.frames.add(frame); new File(dir,frame.photo).delete();
            try { FilmRollPhotoArchive.write(dir,roll,dir,out,(f,r)->{},(n,t)->{}); fail(); } catch(IOException expected) { }
            assertEquals(0,out.size());
        } finally { clean(dir); }
    }
    @Test public void traversalDuplicateCutsAndEmptyFilesAreRejected() throws Exception {
        File dir=Files.createTempDirectory("roll-export-").toFile();
        try {
            FilmRollRecord roll=new FilmRollRecord();FilmFrameRecord frame=frame(dir,1,0);roll.frames.add(frame);
            for(String name:new String[]{"../outside.jpg","/outside.jpg","C:\\outside.jpg"}) {
                frame.photo=name;
                try { FilmRollPhotoArchive.write(dir,roll,dir,new ByteArrayOutputStream(),(f,r)->{},(n,t)->{}); fail(); }
                catch(IOException expected) { }
            }
            frame.photo="photo_1.jpg";roll.frames.add(frame);
            try { FilmRollPhotoArchive.write(dir,roll,dir,new ByteArrayOutputStream(),(f,r)->{},(n,t)->{}); fail(); } catch(IOException expected) { }
            roll.frames.remove(1); Files.write(new File(dir,frame.photo).toPath(),new byte[0]);
            try { FilmRollPhotoArchive.write(dir,roll,dir,new ByteArrayOutputStream(),(f,r)->{},(n,t)->{}); fail(); } catch(IOException expected) { }
        } finally { clean(dir); }
    }
    @Test public void cancellationAndDestinationFailureKeepTheSourceAndClearPreparedCopy() throws Exception {
        File dir=Files.createTempDirectory("roll-export-").toFile();
        try {
            FilmRollRecord roll=new FilmRollRecord();roll.frames.add(frame(dir,1,90));
            byte[] before=Files.readAllBytes(new File(dir,"photo_1.jpg").toPath());
            try {
                FilmRollPhotoArchive.write(dir,roll,dir,new ByteArrayOutputStream(),(f,r)->Thread.currentThread().interrupt(),(n,t)->{});
                fail();
            } catch(InterruptedIOException expected) { } finally { Thread.interrupted(); }
            OutputStream broken=new OutputStream(){ @Override public void write(int b) throws IOException { throw new IOException("Storage full"); } };
            try { FilmRollPhotoArchive.write(dir,roll,dir,broken,(f,r)->{},(n,t)->{}); fail(); } catch(IOException expected) { }
            assertArrayEquals(before,Files.readAllBytes(new File(dir,"photo_1.jpg").toPath()));
            assertEquals(1,Objects.requireNonNull(dir.listFiles()).length);
        } finally { clean(dir); }
    }
    @Test public void filenamesAreSafeOnWindowsAndRetainUsefulRollNames() {
        assertEquals("trip_2026",FilmRollPhotoArchive.folderName("trip/2026"));
        assertEquals("roll_CON",FilmRollPhotoArchive.folderName("CON"));
        assertEquals("ShutterNote_roll",FilmRollPhotoArchive.folderName(" . "));
        assertEquals("여행",FilmRollPhotoArchive.folderName("여행."));
        assertTrue(FilmRollPhotoArchive.folderName(String.join("",Collections.nCopies(100,"a"))).length()<=64);
    }

    @Test public void rendersSelectedRecipeOnlyDuringExportAndCleansItsTemporaryCopy() throws Exception {
        File dir=Files.createTempDirectory("correction-export").toFile();
        try {
            FilmRollRecord roll=new FilmRollRecord(); roll.name="roll";
            FilmFrameRecord frame=frame(dir,7,0); roll.frames.add(frame);
            byte[] original=Files.readAllBytes(new File(dir,frame.photo).toPath()), correction={42,43,44};
            frame.correctionProfile=FilmColorCorrection.E100D; frame.correctionEnabled=true;
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            FilmRollPhotoArchive.write(dir,roll,dir,out,(f,r)->{},(n,t)->{},(source,selected,output)-> {
                assertEquals(frame,selected); assertArrayEquals(original,Files.readAllBytes(source.toPath()));
                Files.write(output.toPath(),correction);
            });
            assertArrayEquals(correction,read(out.toByteArray()).get("roll/frame_007.jpg"));
            frame.correctionEnabled=false; out=new ByteArrayOutputStream();
            FilmRollPhotoArchive.write(dir,roll,dir,out,(f,r)->{},(n,t)->{});
            assertArrayEquals(original,read(out.toByteArray()).get("roll/frame_007.jpg"));
            assertArrayEquals(original,Files.readAllBytes(new File(dir,frame.photo).toPath()));
            assertEquals(1,Objects.requireNonNull(dir.list()).length);
        } finally { clean(dir); }
    }
    @Test public void failedRecipeRenderDeletesTemporaryFileAndPreservesOriginal() throws Exception {
        File dir=Files.createTempDirectory("failed-recipe-export").toFile();
        try {
            FilmRollRecord roll=new FilmRollRecord(); FilmFrameRecord frame=frame(dir,1,0); roll.frames.add(frame);
            frame.correctionEnabled=true; frame.correctionProfile=FilmColorCorrection.E100D;
            byte[] before=Files.readAllBytes(new File(dir,frame.photo).toPath());
            assertThrows(IOException.class,()->FilmRollPhotoArchive.write(dir,roll,dir,new ByteArrayOutputStream(),(f,r)->{},(n,t)->{},
                    (source,selected,output)->{ Files.write(output.toPath(),new byte[]{1,2,3}); throw new IOException("fixture failure"); }));
            assertEquals(1,Objects.requireNonNull(dir.list()).length);
            assertArrayEquals(before,Files.readAllBytes(new File(dir,frame.photo).toPath()));
        } finally { clean(dir); }
    }

    @Test public void missingSelectedCorrectionFailsWithoutSilentlyExportingOriginal() throws Exception {
        File dir=Files.createTempDirectory("missing-correction").toFile();
        try {
            FilmRollRecord roll=new FilmRollRecord(); FilmFrameRecord frame=frame(dir,1,0); roll.frames.add(frame);
            frame.correctionPhoto="missing.jpg"; frame.correctionEnabled=true;
            assertThrows(IOException.class,()->FilmRollPhotoArchive.write(dir,roll,dir,new ByteArrayOutputStream(),(f,r)->{},(n,t)->{}));
            assertTrue(new File(dir,frame.photo).isFile());
        } finally { clean(dir); }
    }
}
