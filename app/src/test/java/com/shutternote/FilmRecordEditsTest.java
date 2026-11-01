package com.shutternote;

import org.junit.Test;
import java.util.TimeZone;
import static org.junit.Assert.*;
import static com.shutternote.FilmRecordEdits.Field;

public class FilmRecordEditsTest {
    @Test public void shotFocalUsesTheSameValueAsExposureAndKeepsRollDefault() {
        FilmRollRecord r=roll();r.focalLength="35";
        assertEquals(0,FilmRecordEdits.apply(r,1,"first",Field.EXPOSURE,new String[]{"100","f/2.8","1/125","50mm"}));
        assertEquals("50mm",FilmRecordEdits.values(r,r.frame(1),Field.SHOT_FOCAL)[0]);
        assertEquals(0,FilmRecordEdits.apply(r,1,"first",Field.SHOT_FOCAL,new String[]{"40mm"}));
        assertEquals("40mm",FilmRecordEdits.values(r,r.frame(1),Field.EXPOSURE)[3]);
        assertEquals("35",r.focalLength);assertEquals("original.jpg",r.frame(1).photo);
    }
    @Test public void invalidOrReplacedShotCannotChangeRecordedFocal() {
        FilmRollRecord r=roll();
        assertNotEquals(0,FilmRecordEdits.apply(r,1,"first",Field.SHOT_FOCAL,new String[]{"85mm"}));
        assertNotEquals(0,FilmRecordEdits.apply(r,1,"old",Field.SHOT_FOCAL,new String[]{"50mm"}));
        assertEquals("35mm",r.frame(1).fieldOfView);
    }
    private FilmRollRecord roll() {
        FilmRollRecord roll = new FilmRollRecord(); roll.id="roll"; roll.memo="latest roll memo"; roll.name="latest name";
        FilmFrameRecord first=new FilmFrameRecord();first.number=1;first.captureId="first";first.memo="latest cut memo";
        first.photo="original.jpg";first.iso="100";first.aperture="f/2.8";first.shutter="1/125";first.fieldOfView="35mm";
        FilmFrameRecord last=new FilmFrameRecord();last.number=36;last.captureId="last";last.memo="keep";
        roll.frames.add(first);roll.frames.add(last);return roll;
    }
    @Test public void singleRollFieldPreservesLatestOtherFieldsAndCuts() {
        FilmRollRecord r=roll();assertEquals(0,FilmRecordEdits.apply(r,1,"first",Field.NAME,new String[]{"new name"}));
        assertEquals("new name",r.name);assertEquals("latest roll memo",r.memo);assertEquals("latest cut memo",r.frame(1).memo);
        assertEquals(2,r.frames.size());assertEquals("keep",r.frame(36).memo);
    }
    @Test public void cutMemoOnlyChangesTheIntendedCapture() {
        FilmRollRecord r=roll();assertEquals(0,FilmRecordEdits.apply(r,1,"first",Field.CUT_MEMO,new String[]{"new cut memo"}));
        assertEquals("new cut memo",r.frame(1).memo);assertEquals("latest roll memo",r.memo);assertEquals("keep",r.frame(36).memo);
    }
    @Test public void deletedOrReplacedCaptureCannotBeEditedByAnOldDraft() {
        FilmRollRecord r=roll();r.frame(1).captureId="replacement";
        assertNotEquals(0,FilmRecordEdits.apply(r,1,"first",Field.CUT_MEMO,new String[]{"wrong"}));
        assertEquals("latest cut memo",r.frame(1).memo);r.frames.remove(r.frame(1));
        assertNotEquals(0,FilmRecordEdits.apply(r,1,"first",Field.NAME,new String[]{"wrong"}));assertEquals("latest name",r.name);
    }
    @Test public void countCannotHideRecordedPhysicalNumbers() {
        FilmRollRecord r=roll();assertNotEquals(0,FilmRecordEdits.apply(r,1,"first",Field.COUNT,new String[]{"24"}));assertEquals(36,r.plannedFrames);
        assertEquals(0,FilmRecordEdits.apply(r,1,"first",Field.COUNT,new String[]{"48"}));assertEquals(48,r.plannedFrames);assertEquals(0,r.countMode);
        assertNotEquals(0,FilmRecordEdits.apply(r,1,"first",Field.COUNT,new String[]{"1000"}));
    }
    @Test public void dateParsingIsStrictAndTimezoneIndependent() {
        TimeZone before=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));long date=FilmRecordEdits.parseDate("2028.02.29");
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));assertEquals(date,FilmRecordEdits.parseDate("2028.02.29"));
            assertEquals("2028.02.29",FilmRollRules.date(date));
            try { FilmRecordEdits.parseDate("2026.02.29");fail(); } catch(IllegalArgumentException expected) { }
            try { FilmRecordEdits.parseDate("2026.10.04suffix");fail(); } catch(IllegalArgumentException expected) { }
        } finally {TimeZone.setDefault(before);}
    }
    @Test public void invalidDatesLeavePreviousValuesUntouchedAndBlankPairClears() {
        FilmRollRecord r=roll();r.start=FilmRecordEdits.parseDate("2026.10.01");r.end=FilmRecordEdits.parseDate("2026.10.04");long oldStart=r.start,oldEnd=r.end;
        assertNotEquals(0,FilmRecordEdits.apply(r,1,"first",Field.PERIOD,new String[]{"2026.10.05","2026.10.02"}));
        assertEquals(oldStart,r.start);assertEquals(oldEnd,r.end);
        assertNotEquals(0,FilmRecordEdits.apply(r,1,"first",Field.PERIOD,new String[]{"2026.10.05",""}));
        assertEquals(0,FilmRecordEdits.apply(r,1,"first",Field.PERIOD,new String[]{"",""}));assertEquals(FilmRollRules.NO_DATE,r.start);
    }
    @Test public void labDatesAreValidatedAgainstLatestOtherDate() {
        FilmRollRecord r=roll();r.received=FilmRecordEdits.parseDate("2026.10.10");
        assertNotEquals(0,FilmRecordEdits.apply(r,1,"first",Field.COMPLETED,new String[]{"2026.10.09"}));assertEquals(FilmRollRules.NO_DATE,r.completed);
        assertEquals(0,FilmRecordEdits.apply(r,1,"first",Field.COMPLETED,new String[]{"2026.10.11"}));
        assertNotEquals(0,FilmRecordEdits.apply(r,1,"first",Field.RECEIVED,new String[]{"2026.10.12"}));
    }
    @Test public void exposureRecordEditKeepsImageIdentityAndOtherCuts() {
        FilmRollRecord r=roll();assertEquals(0,FilmRecordEdits.apply(r,1,"first",Field.EXPOSURE,new String[]{"400","f/8","1/500","50mm"}));
        assertEquals("400",r.frame(1).iso);assertEquals("original.jpg",r.frame(1).photo);assertEquals("first",r.frame(1).captureId);
        assertEquals("keep",r.frame(36).memo);
        assertNotEquals(0,FilmRecordEdits.apply(r,1,"first",Field.EXPOSURE,new String[]{"invalid","f/4","1/60","35mm"}));assertEquals("f/8",r.frame(1).aperture);
    }
    @Test public void invalidPayloadAndOversizedMemoDoNotModifyRecords() {
        FilmRollRecord r=roll();assertNotEquals(0,FilmRecordEdits.apply(r,1,"first",Field.CUT_MEMO,new String[]{"a".repeat(1001)}));
        assertNotEquals(0,FilmRecordEdits.apply(r,1,"first",Field.FILM,new String[]{"id",""}));
        assertNotEquals(0,FilmRecordEdits.apply(r,1,"first",Field.STATUS,new String[]{"3"}));
        assertNotEquals(0,FilmRecordEdits.apply(r,1,"first",Field.PERIOD,new String[]{""}));assertEquals("latest cut memo",r.frame(1).memo);
    }
}
