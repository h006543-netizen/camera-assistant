package com.shutternote;
import org.junit.Test;
import static org.junit.Assert.*;

public class FilmFrameDeleteTest {
    private FilmFrameRecord frame(int number,String id){FilmFrameRecord frame=new FilmFrameRecord();frame.number=number;frame.captureId=id;return frame;}
    @Test public void deletingOneCutKeepsOtherNumbersAndRollInformation(){
        FilmRollRecord roll=new FilmRollRecord();roll.memo="roll note";
        roll.frames.add(frame(1,"one"));roll.frames.add(frame(3,"three"));roll.frames.add(frame(36,"last"));
        assertNotNull(FilmRollStore.removeFrame(roll,3,"three"));
        assertNull(roll.frame(3));assertNotNull(roll.frame(1));assertNotNull(roll.frame(36));assertEquals("roll note",roll.memo);
        assertEquals(36,roll.plannedFrames);
    }
    @Test public void staleConfirmationCannotDeleteReplacementPhoto(){
        FilmRollRecord roll=new FilmRollRecord();roll.frames.add(frame(1,"new capture"));
        assertNull(FilmRollStore.removeFrame(roll,1,"old capture"));assertEquals("new capture",roll.frame(1).captureId);
    }
    @Test public void lastCutDeletionLeavesAnEmptyRoll(){
        FilmRollRecord roll=new FilmRollRecord();roll.frames.add(frame(13,"capture"));
        assertNotNull(FilmRollStore.removeFrame(roll,13,"capture"));assertTrue(roll.frames.isEmpty());assertEquals(36,roll.plannedFrames);
    }
    @Test public void staleRollEditCannotResurrectDeletedCut(){
        FilmRollRecord stored=new FilmRollRecord(),draft=new FilmRollRecord();
        stored.frames.add(frame(1,"capture"));draft.frames.add(frame(1,"capture"));
        FilmRollStore.removeFrame(stored,1,"capture");
        assertTrue(FilmRollStore.mergeRollEdit(stored,draft));assertTrue(draft.frames.isEmpty());
    }
}
