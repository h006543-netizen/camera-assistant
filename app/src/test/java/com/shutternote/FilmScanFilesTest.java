package com.shutternote;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class FilmScanFilesTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private FilmScanFiles files() { return new FilmScanFiles(folder.getRoot()); }
    @Test public void copiesOriginalBytesAndKeepsDraftUntilConfirmed() throws Exception {
        FilmScanFiles files=files(); byte[] data={1,2,3,4,5};
        File draft=files.copyDraft(new ByteArrayInputStream(data)); File stored=files.copyPermanent(draft);
        assertArrayEquals(data,Files.readAllBytes(stored.toPath())); assertTrue(draft.isFile());
        assertEquals(stored,files.scan(stored.getName())); assertNull(files.scan(draft.getName()));
        assertEquals(draft.getName().substring(8),stored.getName());
    }
    @Test public void emptyOrBrokenImportsRemovePartialFiles() throws Exception {
        FilmScanFiles files=files();
        try { files.copyDraft(new ByteArrayInputStream(new byte[0])); fail(); } catch(IOException expected) { }
        InputStream broken=new InputStream() { int count; @Override public int read() throws IOException { if(count++ > 3) throw new IOException("provider failed"); return 42; } };
        try { files.copyDraft(broken); fail(); } catch(IOException expected) { }
        assertEquals(0,new File(folder.getRoot(),"film_notes_scans").list().length);
    }
    @Test public void byteLimitRejectsLargeInputWithoutChangingAnExistingScan() throws Exception {
        FilmScanFiles files=files(); File draft=files.copyDraft(new ByteArrayInputStream(new byte[]{9})); File stored=files.copyPermanent(draft);
        InputStream huge=new InputStream() { long remaining=FilmScanFiles.MAX_BYTES+1;
            @Override public int read() { return remaining-- > 0 ? 1 : -1; }
            @Override public int read(byte[] buffer,int offset,int length) { if(remaining<=0)return -1; int count=(int)Math.min(length,remaining); java.util.Arrays.fill(buffer,offset,offset+count,(byte)1); remaining-=count; return count; }
        };
        try { files.copyDraft(huge); fail(); } catch(IOException expected) { }
        assertArrayEquals(new byte[]{9},Files.readAllBytes(stored.toPath()));
        assertEquals(2,new File(folder.getRoot(),"film_notes_scans").list().length);
    }
    @Test public void cleanupKeepsLinkedFilesAndCurrentDraftButRemovesOldOrphans() throws Exception {
        FilmScanFiles files=files(); File active=files.copyDraft(new ByteArrayInputStream(new byte[]{1}));
        File abandoned=files.copyDraft(new ByteArrayInputStream(new byte[]{2})); File linked=files.copyPermanent(active), orphan=files.copyPermanent(abandoned);
        long old=System.currentTimeMillis()-25L*60*60*1000;
        for(File file:new File[]{active,abandoned,linked,orphan}) assertTrue(file.setLastModified(old));
        files.cleanOrphans(Collections.singleton(linked.getName()),active.getName(),System.currentTimeMillis());
        assertTrue(active.exists()); assertTrue(linked.exists()); assertFalse(abandoned.exists()); assertFalse(orphan.exists());
    }
    @Test public void arbitraryPathsCannotResolveOrBecomeAPermanentCopy() throws Exception {
        FilmScanFiles files=files(); assertNull(files.draft("../../note.jpg")); assertNull(files.scan("../note.jpg"));
        File external=folder.newFile("pending-12345678-1234-1234-1234-123456789abc.img");
        try { files.copyPermanent(external); fail(); } catch(IOException expected) { }
    }
}
