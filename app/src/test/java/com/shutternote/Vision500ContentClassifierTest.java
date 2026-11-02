package com.shutternote;

import org.json.*;
import org.junit.Test;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPInputStream;
import static org.junit.Assert.*;

public class Vision500ContentClassifierTest {
    private JSONObject profile() throws Exception {return new JSONObject(Files.readString(Path.of("src/main/assets/film_corrections/latest/vision3-500t-content-v1.json")));}
    private JSONArray fixtures() throws Exception {return new JSONArray(new String(getClass().getResourceAsStream("/vision500_content_classifier.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
    private int[] input(JSONObject row) throws Exception {
        JSONArray a=row.getJSONArray("input");int[] pixels=new int[a.length()];for(int i=0;i<pixels.length;i++)pixels[i]=a.getInt(i);return pixels;
    }
    private final Map<String,float[]> atlas=new HashMap<>();
    private float[] noise(String name) throws java.io.IOException {
        if(atlas.containsKey(name))return atlas.get(name);byte[] raw;
        try(GZIPInputStream in=new GZIPInputStream(Files.newInputStream(Path.of("src/main/assets",name)))){raw=in.readAllBytes();}
        float[] out=new float[raw.length/4];ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out);atlas.put(name,out);return out;
    }
    @Test public void sourceColourSpatialTextureAndDecisionsMatchIndependentPython() throws Exception {
        JSONArray rows=fixtures();Vision500ContentClassifier classifier=new Vision500ContentClassifier(profile().getJSONObject("classifier"));
        assertEquals(38,rows.length());
        for(int k=0;k<rows.length();k++) {
            JSONObject row=rows.getJSONObject(k);int[] source=input(row);int w=row.getInt("width"),h=row.getInt("height");
            double[] f=Vision500ContentClassifier.features(source,w,h,()->false);JSONArray expected=row.getJSONArray("features");
            for(int j=0;j<f.length;j++)assertEquals(row.getString("name")+" feature "+j,expected.getDouble(j),f[j],2e-5);
            Vision500ContentClassifier.Decision d=classifier.evaluate(source,w,h,()->false);JSONObject e=row.getJSONObject("decision");
            assertEquals(e.getDouble("night_probability"),d.nightProbability,2e-4);assertEquals(e.getDouble("night_weight"),d.nightWeight,2e-4);
            assertEquals(e.getDouble("class_support_margin"),d.margin,2e-4);assertEquals(e.getString("state"),d.state);
        }
    }
    @Test public void bothUserTargetsUseExactApprovedDayAtDifferentOutputSizesAndAllTimes() throws Exception {
        JSONArray rows=fixtures();JSONObject p=profile();LearnedFilmProfile current=new LearnedFilmProfile(p.toString(),this::noise);
        Vision500ApprovedProfile approved=new Vision500ApprovedProfile(p.getJSONObject("base_profile"),this::noise);
        Vision500ContentClassifier classifier=new Vision500ContentClassifier(p.getJSONObject("classifier"));
        for(int k=0;k<2;k++) {
            JSONObject row=rows.getJSONObject(k);int[] analysis=input(row);
            assertEquals(1,classifier.evaluate(analysis,96,96,()->false).dayWeight,0);
            for(double hour:new double[]{Double.NaN,0,6,12,18,23.99})assertEquals(1,Vision500SceneTime.dayWeight(1,hour),0);
            int[] source=new int[48*32];for(int i=0;i<source.length;i++)source[i]=(analysis[i%analysis.length]&0xffffff)|0x55000000;
            int[] actual=source.clone(),day=source.clone();current.renderWithSceneTime(actual,48,32,()->false,analysis,96,96,12);
            approved.renderWithDayWeight(day,48,32,()->false,analysis,96,96,1);assertArrayEquals(day,actual);
            for(int pixel:actual)assertEquals(0x55000000,pixel&0xff000000);
        }
    }
    private JSONObject controlled(boolean identicalSupports) throws Exception {
        JSONObject p=profile().getJSONObject("classifier");double[] center=new double[204],scale=new double[204];Arrays.fill(scale,1);
        double[] distant=new double[204];Arrays.fill(distant,4);
        p.put("center",new JSONArray(center)).put("scale",new JSONArray(scale));
        p.put("centers",new JSONArray().put(new JSONArray(center)).put(new JSONArray(identicalSupports?center:distant)));
        p.put("center_labels",new JSONArray().put(1).put(0));
        double score=Math.log(.9/.1)*p.getDouble("temperature");JSONArray heads=new JSONArray();
        for(int i=0;i<3;i++)heads.put(new JSONArray().put(score).put(0).put(0));p.put("heads",heads);return p;
    }
    @Test public void highRawScoreCannotEnableNightWithoutSupportOrClassSeparation() throws Exception {
        int[] black=new int[64*64];Arrays.fill(black,0xff000000);double[] f=Vision500ContentClassifier.features(black,64,64,()->false);
        JSONObject p=controlled(true);p.put("center",new JSONArray(f));
        Vision500ContentClassifier.Decision d=new Vision500ContentClassifier(p).evaluate(black,64,64,()->false);
        assertEquals(.9,d.nightProbability,1e-9);assertEquals(0,d.nightWeight,0);assertEquals("abstain",d.state);
        JSONObject outside=controlled(false);double[] far=new double[204];Arrays.fill(far,6);
        outside.put("centers",new JSONArray().put(new JSONArray(far)).put(new JSONArray(far)));
        outside.put("support_soft",.001).put("support_hard",.002);
        assertEquals(0,new Vision500ContentClassifier(outside).evaluate(black,64,64,()->false).nightWeight,0);
        JSONObject strong=controlled(false);strong.put("center",new JSONArray(f));
        assertEquals(1,new Vision500ContentClassifier(strong).evaluate(black,64,64,()->false).nightWeight,1e-12);
    }
    @Test public void cancelledAndInvalidModelsCannotPublishPixels() throws Exception {
        JSONObject p=profile();LearnedFilmProfile current=new LearnedFilmProfile(p.toString(),this::noise);
        int[] source=new int[64*64];Arrays.fill(source,0xff162b3a);int[] original=source.clone();int[] calls={0};
        assertThrows(CancellationException.class,()->current.render(source,64,64,()->++calls[0]>10));assertArrayEquals(original,source);
        assertThrows(IllegalArgumentException.class,()->current.render(source,32,64,()->false));
        assertThrows(IllegalArgumentException.class,()->current.render(source,64,64,()->false,new int[1],2,2));
        JSONObject bad=profile();bad.getJSONObject("classifier").put("support_hard",0);assertThrows(JSONException.class,()->new LearnedFilmProfile(bad.toString(),this::noise));
        JSONObject nested=profile();nested.getJSONObject("base_profile").put("schema_version",33);assertThrows(JSONException.class,()->new LearnedFilmProfile(nested.toString(),this::noise));
        for(int level:new int[]{0,255}) {
            int[] pixels=new int[]{0xff000000|(level<<16)|(level<<8)|level};
            for(double v:Vision500ContentClassifier.features(pixels,1,1,()->false))assertTrue(Double.isFinite(v));
        }
    }
    @Test public void oldApprovedAndTimeRecipesRemainPinnedAndRestorable() throws Exception {
        JSONObject p=profile(),old=new JSONObject(Files.readString(Path.of("src/main/assets/film_corrections/latest/vision3-500t-approved-night-v1.json")));
        assertEquals(old.toString(),p.getJSONObject("base_profile").toString());
        String current=FilmCorrectionProfiles.latest("vision3-500t");assertEquals("vision3-500t-"+p.getString("model_version"),FilmCorrectionProfiles.OLD_500T_CONTENT);
        for(String id:new String[]{FilmCorrectionProfiles.OLD_500T_APPROVED,FilmCorrectionProfiles.OLD_500T_TIME,current}) {
            assertTrue(FilmCorrectionProfiles.supported(id));assertEquals("vision3-500t",FilmCorrectionProfiles.key(id));
            FilmFrameRecord frame=new FilmFrameRecord();frame.number=1;frame.photo="source.jpg";frame.correctionPhoto="corrected.jpg";frame.correctionEnabled=true;frame.correctionProfile=id;frame.sceneCaptureHour=12.35;
            FilmFrameRecord restored=FilmFrameRecord.fromJson(frame.toJson());assertEquals(id,restored.correctionProfile);assertEquals("source.jpg",restored.photo);assertEquals("corrected.jpg",restored.displayPhotoName());
            assertEquals(12.35,restored.sceneCaptureHour,0);
        }
        assertEquals(FilmCorrectionProfiles.OLD_500T_APPROVED,FilmCorrectionProfiles.assetId(FilmCorrectionProfiles.OLD_500T_TIME));
        assertTrue(FilmCorrectionProfiles.upgradeOnReselect(FilmCorrectionProfiles.OLD_500T_TIME));
        assertFalse(FilmCorrectionProfiles.upgradeOnReselect(current));assertTrue(FilmCorrectionProfiles.usesSceneTime(current));
        assertTrue(FilmCorrectionProfiles.usesSceneTime(FilmCorrectionProfiles.OLD_500T_TIME));assertFalse(FilmCorrectionProfiles.supported(current+"x"));
    }
}
