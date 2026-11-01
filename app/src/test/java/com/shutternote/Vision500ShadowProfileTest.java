package com.shutternote;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class Vision500ShadowProfileTest {
    private JSONObject profile() throws Exception {
        return new JSONObject(Files.readString(Path.of("src/main/assets/film_corrections/latest/vision3-500t-shadow-v1.json")));
    }
    private LearnedFilmProfile renderer(JSONObject p) throws Exception {
        return new LearnedFilmProfile(p.toString(),asset->{throw new AssertionError("500T must not load synthetic grain");});
    }
    @Test public void actualSourceAndIndependentRampsMatchPcAndProtectEveryBrighterPixel() throws Exception {
        JSONArray cases=new JSONArray(new String(getClass().getResourceAsStream("/vision500_shadow_parity.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        JSONObject p=profile();LearnedFilmProfile current=renderer(p),previous=renderer(p.getJSONObject("base_profile"));
        for(int n=0;n<cases.length();n++) {
            JSONObject sample=cases.getJSONObject(n);JSONArray input=sample.getJSONArray("input"),expected=sample.getJSONArray("expected");
            int[] pixels=new int[input.length()];for(int i=0;i<pixels.length;i++)pixels[i]=input.getInt(i)|0xff000000;
            int w=sample.getInt("width"),h=sample.getInt("height");
            assertEquals(sample.getDouble("black"),previous.blackLevel(pixels,w,h,()->false),2e-6);
            int[] baseline=pixels.clone();previous.render(baseline,w,h,()->false);current.render(pixels,w,h,()->false);
            int max=0;
            for(int i=0;i<pixels.length;i++) {
                int b=baseline[i];double level=FilmRenderMath.luma((b>>>16)&255,(b>>>8)&255,b&255);
                if(level>=48)assertEquals(sample.getString("name")+" protected pixel",b,pixels[i]);
                for(int c=0;c<3;c++) {
                    int shift=16-c*8,value=(pixels[i]>>>shift)&255,prior=(b>>>shift)&255;
                    assertTrue("Shadow finish must not brighten a channel",value<=prior);
                    max=Math.max(max,Math.abs(value-((expected.getInt(i)>>>shift)&255)));
                }
            }
            System.out.println(sample.getString("name")+" 500T shadow parity max="+max);
            assertTrue(sample.getString("name")+" max="+max,max<=1);
        }
    }
    @Test public void denseToneCurveHasNoReversalAndJoinsIdentitySmoothly() {
        double target=3/255.,cutoff=48/255.;
        for(double black:new double[]{0,.005,.04,.08,.115,.16,.187,.25,.4}) {
            double a=Vision500ShadowProfile.shape(black,target,cutoff,20),last=-1;
            for(int i=0;i<=65536;i++) {
                double y=i/65536.,v=y*Vision500ShadowProfile.gain(y,cutoff,a);
                assertTrue(Double.isFinite(v));assertTrue(v>=last);assertTrue(v<=y);last=v;
                if(y>=cutoff)assertEquals(y,v,0);
            }
            double epsilon=1e-7;
            double below=(cutoff-epsilon)*Vision500ShadowProfile.gain(cutoff-epsilon,cutoff,a);
            assertEquals(1,(cutoff-below)/epsilon,1e-4);
        }
        double black=.115,a=Vision500ShadowProfile.shape(black,target,cutoff,20);
        assertEquals(target,black*Vision500ShadowProfile.gain(black,cutoff,a),1e-12);
        assertEquals(0,Vision500ShadowProfile.activation(8/255.,8/255.,16/255.),0);
        assertEquals(1,Vision500ShadowProfile.activation(16/255.,8/255.,16/255.),0);
    }
    @Test public void previousVersionRemainsRestorableAndAssetsStaySeparate() throws Exception {
        assertTrue(FilmCorrectionProfiles.supported(FilmCorrectionProfiles.OLD_500T));
        assertEquals("vision3-500t",FilmCorrectionProfiles.key(FilmCorrectionProfiles.OLD_500T));
        assertNotEquals(FilmCorrectionProfiles.OLD_500T,FilmCorrectionProfiles.latest("vision3-500t"));
        JSONObject old=new JSONObject(Files.readString(Path.of("src/main/assets/"+FilmCorrectionProfiles.OLD_500T_ASSET)));
        assertEquals(old.toString(),profile().getJSONObject("base_profile").toString());
        for(String id:new String[]{FilmCorrectionProfiles.OLD_500T,FilmCorrectionProfiles.latest("vision3-500t")}) {
            FilmFrameRecord frame=new FilmFrameRecord();frame.number=2;frame.photo="source.jpg";
            frame.correctionPhoto="correction.jpg";frame.correctionEnabled=true;frame.correctionProfile=id;
            FilmFrameRecord restored=FilmFrameRecord.fromJson(frame.toJson());
            assertEquals(id,restored.correctionProfile);assertTrue(FilmCorrectionProfiles.supported(restored.correctionProfile));
            assertEquals("source.jpg",restored.photo);assertEquals("correction.jpg",restored.displayPhotoName());
        }
    }
    @Test public void cancellationDoesNotPublishAndInvalidSettingsAreRejected() throws Exception {
        LearnedFilmProfile current=renderer(profile());int[] pixels=new int[80*80];Arrays.fill(pixels,0xff080a0c);int[] original=pixels.clone();
        int[] checks={0};
        assertThrows(CancellationException.class,()->current.render(pixels,80,80,()->++checks[0]>8));
        assertArrayEquals(original,pixels);
        JSONObject invalid=profile();invalid.getJSONObject("parameters").put("protected_luma_8bit",256);
        assertThrows(org.json.JSONException.class,()->renderer(invalid));
        JSONObject recursive=profile();recursive.getJSONObject("base_profile").put("schema_version",29);
        assertThrows(org.json.JSONException.class,()->renderer(recursive));
    }
}
