package com.shutternote;
import org.junit.Test;
import static org.junit.Assert.*;
public class FilmFrameRotationTest {
    @Test public void fourCounterclockwiseTurnsReturnToOriginalOrientation() {
        FilmRollRecord roll=new FilmRollRecord();FilmFrameRecord frame=new FilmFrameRecord();frame.number=1;frame.captureId="shot";roll.frames.add(frame);
        for(int expected:new int[]{270,180,90,0}) { assertTrue(FilmFrameRecord.rotate(roll,1,"shot"));assertEquals(expected,frame.rotation); }
    }
    @Test public void onlyMatchingCaptureTurnsAndMetadataStaysIntact() {
        FilmRollRecord roll=new FilmRollRecord();FilmFrameRecord first=new FilmFrameRecord(),second=new FilmFrameRecord();
        first.number=1;first.captureId="first";first.photo="original.jpg";first.memo="keep";first.fieldOfView="50mm";first.iso="100";
        second.number=2;second.captureId="second";roll.frames.add(first);roll.frames.add(second);
        assertFalse(FilmFrameRecord.rotate(roll,1,"old"));assertFalse(FilmFrameRecord.rotate(roll,3,"missing"));
        assertTrue(FilmFrameRecord.rotate(roll,1,"first"));assertEquals(0,second.rotation);
        assertEquals("original.jpg",first.photo);assertEquals("keep",first.memo);assertEquals("50mm",first.fieldOfView);assertEquals("100",first.iso);
    }
    @Test public void invalidRotationDefaultsToZeroAndWholeTurnsNormalize() {
        assertEquals(0,FilmFrameRecord.normalizedRotation(45));assertEquals(0,FilmFrameRecord.normalizedRotation(360));
        assertEquals(270,FilmFrameRecord.normalizedRotation(-90));assertEquals(90,FilmFrameRecord.normalizedRotation(450));
    }
}
