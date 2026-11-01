package com.shutternote;

import org.junit.Test;
import static org.junit.Assert.*;

public class FilmRollFrameEditTest {
    private FilmFrameRecord cut(int number) {
        FilmFrameRecord frame=new FilmFrameRecord();frame.number=number;frame.memo="captured after editor opened";return frame;
    }
    @Test public void staleRollEditorPreservesCutCapturedAfterItWasOpened() {
        FilmRollRecord current=new FilmRollRecord(),edited=new FilmRollRecord();
        edited.name="Updated roll name";current.frames.add(cut(13));
        assertTrue(FilmRollStore.mergeRollEdit(current,edited));
        assertEquals("Updated roll name",edited.name);
        assertEquals("captured after editor opened",edited.frame(13).memo);
    }
    @Test public void reducingPlannedCountCannotHideExistingPhysicalFrames() {
        FilmRollRecord current=new FilmRollRecord(),edited=new FilmRollRecord();current.frames.add(cut(36));
        edited.plannedFrames=24;assertFalse(FilmRollStore.mergeRollEdit(current,edited));
        edited.plannedFrames=36;assertTrue(FilmRollStore.mergeRollEdit(current,edited));
    }
    @Test public void olderCopyOfCutMemoDoesNotOverwriteLatestMemo() {
        FilmRollRecord current=new FilmRollRecord(),edited=new FilmRollRecord();
        FilmFrameRecord old=cut(1);old.memo="old";edited.frames.add(old);
        FilmFrameRecord recent=cut(1);recent.memo="latest";current.frames.add(recent);
        assertTrue(FilmRollStore.mergeRollEdit(current,edited));assertEquals("latest",edited.frame(1).memo);
    }
    @Test public void invalidPlannedCountsAreRejectedAtTheStoreBoundary() {
        FilmRollRecord current=new FilmRollRecord(),edited=new FilmRollRecord();
        edited.plannedFrames=0;assertFalse(FilmRollStore.mergeRollEdit(current,edited));
        edited.plannedFrames=1000;assertFalse(FilmRollStore.mergeRollEdit(current,edited));
    }
}
