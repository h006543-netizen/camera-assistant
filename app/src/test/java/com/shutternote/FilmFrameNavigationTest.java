package com.shutternote;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import static org.junit.Assert.*;

public class FilmFrameNavigationTest {
    @Test public void thirtySixFramesOccupyEighteenColumnsInPhysicalOrder() {
        assertEquals(18,FilmFrameNavigation.pairCount(36));
        for(int pair=0;pair<18;pair++) {
            assertEquals(pair*2+1,FilmFrameNavigation.firstFrame(pair,36));
            assertEquals(pair*2+2,FilmFrameNavigation.lastFrame(pair,36));
        }
    }
    @Test public void oddRollHasNoInventedFinalFrame() {
        for(int total:new int[]{1,3,25,999}) {
            int pair=FilmFrameNavigation.pairCount(total)-1;
            assertEquals(total,FilmFrameNavigation.firstFrame(pair,total));
            assertEquals(total,FilmFrameNavigation.lastFrame(pair,total));
        }
    }
    @Test public void positionsSurviveRollCountChangesWithoutGoingOutOfRange() {
        assertEquals(0,FilmFrameNavigation.clampPair(-4,36));
        assertEquals(11,FilmFrameNavigation.clampPair(17,24));
        assertEquals(0,FilmFrameNavigation.clampPair(100,1));
    }
    @Test public void nextCaptureFillsNumberGapsRatherThanUsingRecordCount() {
        assertEquals(1,FilmFrameNavigation.nextFrame(36,Collections.emptyList()));
        assertEquals(2,FilmFrameNavigation.nextFrame(36,Arrays.asList(1,3,4,36)));
        assertEquals(5,FilmFrameNavigation.nextFrame(36,Arrays.asList(4,2,1,3)));
    }
    @Test public void completedRollOpensLastColumnAndDuplicatesDoNotAdvancePosition() {
        ArrayList<Integer> complete=new ArrayList<>();for(int i=1;i<=36;i++)complete.add(i);
        assertEquals(36,FilmFrameNavigation.nextFrame(36,complete));
        assertEquals(2,FilmFrameNavigation.nextFrame(36,Arrays.asList(1,1)));
    }
    @Test public void everySupportedCountProducesOnlyValidVisibleCutNumbers() {
        for(int total=1;total<=999;total++)for(int pair=0;pair<FilmFrameNavigation.pairCount(total);pair++){
            int first=FilmFrameNavigation.firstFrame(pair,total),last=FilmFrameNavigation.lastFrame(pair,total);
            assertTrue(first>=1&&first<=total);assertTrue(last>=first&&last<=total);
        }
    }
}
