package com.shutternote;

import java.util.TimeZone;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class Vision500SceneTimeTest {
    @Test public void weakTimeCannotReversePhotoDecisionAcrossFullDay() {
        for(int p=0;p<=1000;p++)for(int minute=0;minute<1440;minute+=3) {
            double photo=p/1000.,result=Vision500SceneTime.dayWeight(photo,minute/60.);
            assertTrue(Double.isFinite(result)&&result>=0&&result<=1);
            assertTrue(Math.abs(result-photo)<=.10+1e-15);
            if(photo<.5)assertTrue(result<.5);
            if(photo>.5)assertTrue(result>.5);
            if(photo==0||photo==1||photo==.5)assertEquals(photo,result,0);
        }
        assertTrue(Vision500SceneTime.dayWeight(.3,12)>.3);
        assertTrue(Vision500SceneTime.dayWeight(.7,0)<.7);
        assertEquals(Vision500SceneTime.dayWeight(.3,0),Vision500SceneTime.dayWeight(.3,23.999999),1e-10);
    }
    @Test public void invalidAndMissingTimeLeavePhotoWeightAlone() {
        for(double time:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,24,100})
            assertEquals(.31,Vision500SceneTime.dayWeight(.31,time),0);
        assertThrows(IllegalArgumentException.class,()->Vision500SceneTime.dayWeight(Double.NaN,12));
    }
    @Test public void exifUsesOriginalLocalHourAndRequiresValidCaptureDateAndOffset() {
        assertEquals(12.35,Vision500SceneTime.fromExif("2026:10:14 12:21:00","+09:00"),1e-12);
        assertEquals(23.5,Vision500SceneTime.fromExif("2026:10:14 23:30:00","-07:00"),0);
        for(String[] bad:new String[][]{
                {null,"+09:00"},{"2026:10:14 12:21:00",null},{"2026:10:14 12:21:00",""},
                {"2026:02:30 12:21:00","+09:00"},{"2026:10:14 24:21:00","+09:00"},
                {"2026:10:14 12:21:00x","+09:00"},{"2026:10:14 12:21:00","+14:01"},
                {"2026:10:14 12:21:00","-12:01"},{"2026:10:14 12:21:00","+09:60"}})
            assertTrue(Vision500SceneTime.fromExif(bad[0],bad[1])+"",Double.isNaN(Vision500SceneTime.fromExif(bad[0],bad[1])));
    }
    @Test public void capturedSnapshotSurvivesTravelAndUnknownOffsetsAreSkipped() {
        long utc=java.time.Instant.parse("2026-10-14T03:21:00Z").toEpochMilli();
        TimeZone previous=TimeZone.getDefault();
        try {
            for(String zone:new String[]{"Asia/Seoul","America/Los_Angeles","Pacific/Honolulu"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                assertEquals(12.35,Vision500SceneTime.fromSnapshot(utc,540),1e-12);
                assertEquals(20.35,Vision500SceneTime.fromSnapshot(utc,-420),1e-12);
            }
        }finally{TimeZone.setDefault(previous);}
        assertTrue(Double.isNaN(Vision500SceneTime.fromSnapshot(utc,Integer.MIN_VALUE)));
        assertTrue(Double.isNaN(Vision500SceneTime.fromSnapshot(0,540)));
    }
    @Test public void recipeIdentityAndStoredTimeAreSeparateFromLegacyAndOtherFilms() throws Exception {
        String current=FilmCorrectionProfiles.latest("vision3-500t"),legacy=FilmCorrectionProfiles.IDS[4];
        assertNotEquals(legacy,current);assertTrue(FilmCorrectionProfiles.supported(current));
        assertTrue(FilmCorrectionProfiles.isPrevious500(legacy));assertFalse(FilmCorrectionProfiles.isPrevious500(current));
        assertEquals(legacy,FilmCorrectionProfiles.assetId(current));assertEquals("vision3-500t",FilmCorrectionProfiles.key(current));
        assertFalse(FilmCorrectionProfiles.supported(current+"x"));
        assertFalse(FilmCorrectionProfiles.supported(FilmCorrectionProfiles.IDS[3]+Vision500SceneTime.RECIPE_SUFFIX));
        FilmFrameRecord frame=new FilmFrameRecord();frame.number=1;frame.photo="source.jpg";
        frame.sceneCaptureHour=12.35;frame.correctionProfile=current;frame.correctionEnabled=true;
        FilmFrameRecord restored=FilmFrameRecord.fromJson(frame.toJson());
        assertEquals(12.35,restored.sceneCaptureHour,0);assertEquals(frame.imageKey(),restored.imageKey());
        frame.correctionProfile=legacy;assertNotEquals(frame.imageKey(),restored.imageKey());
        assertTrue(Double.isNaN(FilmFrameRecord.fromJson(new JSONObject().put("number",1).put("capturedAt",12345)).sceneCaptureHour));
        frame.sceneCaptureHour=24;assertTrue(Double.isNaN(FilmFrameRecord.fromJson(frame.toJson()).sceneCaptureHour));
        for(int i=0;i<FilmCorrectionProfiles.KEYS.length;i++)if(i!=4)
            assertEquals(FilmCorrectionProfiles.IDS[i],FilmCorrectionProfiles.latest(FilmCorrectionProfiles.KEYS[i]));
    }
}
