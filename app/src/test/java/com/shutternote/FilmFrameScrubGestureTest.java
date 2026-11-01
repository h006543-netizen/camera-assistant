package com.shutternote;

import org.junit.Test;
import static org.junit.Assert.*;

public class FilmFrameScrubGestureTest {
    @Test public void equalDragsGiveEqualMotionRegardlessOfTouchDownLocation() {
        for(float down:new float[]{0,100,200,900}) {
            FilmFrameScrubGesture gesture=new FilmFrameScrubGesture(); gesture.begin(down,4000,17000,200);
            assertEquals(4000,gesture.progress(),.001f);
            assertTrue(gesture.move(down+20,8)); assertEquals(5700,gesture.progress(),.001f);
            assertTrue(gesture.move(down-20,8)); assertEquals(2300,gesture.progress(),.001f);
        }
    }
    @Test public void touchAndSmallFingerJitterKeepExactCurrentPhotoPosition() {
        FilmFrameScrubGesture gesture=new FilmFrameScrubGesture(); gesture.begin(900,4234.75f,17000,200);
        assertFalse(gesture.move(905,8)); assertFalse(gesture.move(897,8));
        assertFalse(gesture.moved()); assertEquals(4234.75f,gesture.progress(),.001f);
    }
    @Test public void firstDragIncludesTheMotionBeforeTouchSlopWithoutAnAbsoluteJump() {
        FilmFrameScrubGesture gesture=new FilmFrameScrubGesture(); gesture.begin(900,4000,17000,200);
        gesture.move(904,8); gesture.move(910,8);
        assertTrue(gesture.moved()); assertEquals(4850,gesture.progress(),.001f);
        gesture.move(920,8); assertEquals(5700,gesture.progress(),.001f);
    }
    @Test public void reversingImmediatelyAfterEitherEdgeHasNoDeadZone() {
        FilmFrameScrubGesture gesture=new FilmFrameScrubGesture(); gesture.begin(0,4000,17000,200);
        gesture.move(1000,8); assertEquals(17000,gesture.progress(),.001f);
        gesture.move(999,8); assertEquals(16915,gesture.progress(),.001f);
        gesture.move(-1000,8); assertEquals(0,gesture.progress(),.001f);
        gesture.move(-999,8); assertEquals(85,gesture.progress(),.001f);
    }
    @Test public void replacingTheFingerKeepsProgressAndStartsFromNewFingerPosition() {
        FilmFrameScrubGesture gesture=new FilmFrameScrubGesture(); gesture.begin(800,4000,17000,200);
        gesture.reanchor(10); assertFalse(gesture.move(10,8)); assertEquals(4000,gesture.progress(),.001f);
        gesture.move(30,8); assertEquals(5700,gesture.progress(),.001f);
        gesture.reanchor(900); gesture.move(900,8); assertEquals(5700,gesture.progress(),.001f);
        gesture.move(880,8); assertEquals(4000,gesture.progress(),.001f);
    }
    @Test public void allSupportedRollSizesStayWithinTheFirstAndLastPair() {
        for(int total=1;total<=999;total++) {
            int max=(FilmFrameNavigation.pairCount(total)-1)*1000;
            FilmFrameScrubGesture gesture=new FilmFrameScrubGesture(); gesture.begin(500,max/2f,max,200);
            gesture.move(-100000,8); assertEquals(0,gesture.progress(),.001f);
            gesture.move(100000,8); assertEquals(max,gesture.progress(),.001f);
        }
    }
}
