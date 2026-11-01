package com.shutternote;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class FilmScanRulesTest {
    private static final String FIRST = "12345678-1234-1234-1234-123456789abc.img";
    private static final String SECOND = "12345678-1234-1234-1234-123456789def.img";
    private FilmRollRecord roll() {
        FilmRollRecord roll = new FilmRollRecord(); roll.memo = "shared memo";
        FilmFrameRecord frame = new FilmFrameRecord(); frame.number = 12; frame.captureId = "original";
        frame.memo = "cut memo"; frame.photo = "note.jpg"; frame.rotation = 90; frame.iso = "400";
        frame.location = new FilmFrameLocation(37, 127, 10); roll.frames.add(frame);
        return roll;
    }
    @Test public void legacyRecordStartsWithoutAScan() throws Exception {
        FilmFrameRecord frame = FilmFrameRecord.fromJson(new JSONObject().put("number", 12).put("captureId", "capture"));
        assertEquals("", frame.scanPhoto); assertEquals(0, frame.scanRevision);
    }
    @Test public void associationRoundTripsWithItsRevision() throws Exception {
        FilmRollRecord roll = roll(); assertTrue(FilmScanRules.apply(roll,12,"original",0,FIRST));
        FilmFrameRecord result = FilmFrameRecord.fromJson(roll.frame(12).toJson());
        assertEquals(FIRST, result.scanPhoto); assertEquals(1, result.scanRevision);
    }
    @Test public void associatingAndUnlinkingPreserveAllOtherRecords() {
        FilmRollRecord roll = roll(); FilmFrameRecord frame = roll.frame(12);
        FilmFrameRecord other = new FilmFrameRecord(); other.number=36; other.captureId="other"; roll.frames.add(other);
        assertTrue(FilmScanRules.apply(roll,12,"original",0,FIRST));
        assertTrue(FilmScanRules.apply(roll,12,"original",1,""));
        assertEquals("",frame.scanPhoto); assertEquals(2,frame.scanRevision);
        assertEquals("shared memo",roll.memo); assertSame(other,roll.frame(36));
        assertEquals("cut memo",frame.memo); assertEquals("note.jpg",frame.photo); assertEquals(90,frame.rotation);
        assertEquals("400",frame.iso); assertEquals(37,frame.location.latitude,0);
    }
    @Test public void staleCaptureCannotAttachToARetakenCut() {
        FilmRollRecord roll = roll(); assertFalse(FilmScanRules.apply(roll,12,"old capture",0,FIRST));
        assertFalse(FilmScanRules.apply(roll,11,"original",0,FIRST)); assertEquals("",roll.frame(12).scanPhoto);
    }
    @Test public void staleReplacementAndUnlinkCannotOverwriteNewerSelection() {
        FilmRollRecord roll = roll(); assertTrue(FilmScanRules.apply(roll,12,"original",0,FIRST));
        assertTrue(FilmScanRules.apply(roll,12,"original",1,SECOND));
        assertFalse(FilmScanRules.apply(roll,12,"original",1,FIRST));
        assertFalse(FilmScanRules.apply(roll,12,"original",1,"")); assertEquals(SECOND,roll.frame(12).scanPhoto);
    }
    @Test public void unlinkRevisionCannotBeBypassedByReusingAnEarlierEmptyState() {
        FilmRollRecord roll = roll(); FilmScanRules.apply(roll,12,"original",0,FIRST); FilmScanRules.apply(roll,12,"original",1,"");
        assertFalse(FilmScanRules.apply(roll,12,"original",0,SECOND)); assertEquals(2,roll.frame(12).scanRevision);
    }
    @Test public void invalidPathsAndOverflowAreRejected() throws Exception {
        FilmRollRecord roll = roll();
        for(String invalid : new String[]{"../"+FIRST,"/"+FIRST,"pending-"+FIRST,"note.jpg"}) {
            assertFalse(FilmScanRules.apply(roll,12,"original",0,invalid));
            assertEquals("",FilmFrameRecord.fromJson(new JSONObject().put("number",12).put("scanPhoto",invalid)).scanPhoto);
        }
        roll.frame(12).scanRevision=Long.MAX_VALUE;
        assertFalse(FilmScanRules.apply(roll,12,"original",Long.MAX_VALUE,FIRST));
    }
    @Test public void onlyTwoPortraitPhotosUseSideBySidePlacement() {
        assertTrue(FilmScanRules.sideBySide(200,300,400,600));
        assertFalse(FilmScanRules.sideBySide(300,200,600,400));
        assertFalse(FilmScanRules.sideBySide(200,300,600,400));
        assertFalse(FilmScanRules.sideBySide(200,200,200,300));
        assertFalse(FilmScanRules.sideBySide(0,300,200,300));
    }
    @Test public void staleRollEditPreservesTheLatestScan() {
        FilmRollRecord latest=roll(), draft=roll(); FilmScanRules.apply(latest,12,"original",0,FIRST);
        assertTrue(FilmRollStore.mergeRollEdit(latest,draft)); assertEquals(FIRST,draft.frame(12).scanPhoto);
    }
    @Test public void restoringACompletedSaveRecognizesOnlyTheSameDraftIdentity() {
        FilmRollRecord roll=roll(); FilmScanRules.apply(roll,12,"original",0,FIRST);
        assertTrue(FilmScanRules.completedDraft(roll.frame(12),"pending-"+FIRST));
        assertFalse(FilmScanRules.completedDraft(roll.frame(12),"pending-"+SECOND));
        assertFalse(FilmScanRules.completedDraft(roll.frame(12),"../../"+FIRST));
        FilmScanRules.apply(roll,12,"original",1,"");
        assertFalse(FilmScanRules.completedDraft(roll.frame(12),"pending-"+FIRST));
    }
}
