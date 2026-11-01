package com.shutternote;

import java.nio.file.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPInputStream;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class Vision500SceneProfileTest {
    private JSONObject profile() throws Exception {
        return new JSONObject(Files.readString(Path.of("src/main/assets/film_corrections/latest/vision3-500t-day-b-v1.json")));
    }
    private float[] noise(String name) throws java.io.IOException {
        byte[] raw;try(GZIPInputStream in=new GZIPInputStream(Files.newInputStream(Path.of("src/main/assets",name)))){raw=in.readAllBytes();}
        float[] values=new float[raw.length/4];ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(values);return values;
    }
    private int[] unpack(JSONArray a) throws Exception {int[] p=new int[a.length()];for(int i=0;i<p.length;i++)p[i]=0xff000000|a.getInt(i);return p;}
    @Test public void skyNightRampsAndSameAtlasGrainMatchPc() throws Exception {
        JSONArray cases=new JSONArray(new String(getClass().getResourceAsStream("/vision500_scene_parity.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        for(int n=0;n<cases.length();n++) {
            JSONObject sample=cases.getJSONObject(n),p=profile();p.getJSONObject("grain_profile").put("native_reference_edge",sample.getInt("calibration_native_edge"));
            int[] source=unpack(sample.getJSONArray("input"));int w=sample.getInt("width"),h=sample.getInt("height");
            Vision500SceneProfile probe=new Vision500SceneProfile(p,this::noise);
            assertEquals(sample.getDouble("day_weight"),probe.dayWeight(source,w,h,()->false),1e-5);
            for(boolean withGrain:new boolean[]{false,true}) {
                JSONObject selected=new JSONObject(p.toString());
                if(!withGrain){selected.put("day_grain_percent",0);selected.put("night_grain_percent",0);}
                LearnedFilmProfile renderer=new LearnedFilmProfile(selected.toString(),this::noise);
                int[] output=source.clone();renderer.render(output,w,h,()->false);
                int[] expected=unpack(sample.getJSONArray(withGrain?"expected_grain":"expected_clean"));int max=0;
                for(int i=0;i<output.length;i++)for(int c=0;c<3;c++)max=Math.max(max,Math.abs(((output[i]>>>(16-c*8))&255)-((expected[i]>>>(16-c*8))&255)));
                System.out.println(sample.getString("name")+" grain="+withGrain+" max="+max);assertTrue(max<=1);
                if(sample.getString("name").equals("grain_calibration")&&withGrain)assertFalse(Arrays.equals(output,unpack(sample.getJSONArray("expected_clean"))));
            }
        }
    }
    @Test public void nightColourIsExactWhenAddedGrainIsOffAndOldVersionsRestore() throws Exception {
        JSONObject p=profile();p.put("day_grain_percent",0);p.put("night_grain_percent",0);
        int[] input=new int[48*64];Arrays.fill(input,0xff040506);
        LearnedFilmProfile current=new LearnedFilmProfile(p.toString(),this::noise),previous=new LearnedFilmProfile(p.getJSONObject("night_profile").toString(),this::noise);
        assertEquals(0,new Vision500SceneProfile(p,this::noise).dayWeight(input,48,64,()->false),0);
        int[] a=input.clone(),b=input.clone();current.render(a,48,64,()->false);previous.render(b,48,64,()->false);assertArrayEquals(a,b);
        for(String id:new String[]{FilmCorrectionProfiles.OLD_500T,FilmCorrectionProfiles.OLD_500T_SHADOW,FilmCorrectionProfiles.OLD_500T_SCENE}) {
            assertTrue(FilmCorrectionProfiles.supported(id));assertTrue(FilmCorrectionProfiles.isPrevious500(id));assertEquals("vision3-500t",FilmCorrectionProfiles.key(id));
        }
        assertFalse(FilmCorrectionProfiles.isPrevious500(FilmCorrectionProfiles.latest("vision3-500t")));
        assertTrue(FilmCorrectionProfiles.workingBytes(FilmCorrectionProfiles.OLD_500T)>=80);
        assertEquals(96,FilmCorrectionProfiles.workingBytes(FilmCorrectionProfiles.OLD_500T_SCENE));
        assertEquals(ImageMemoryBudget.sampleSize(1360,2040,150L*1024*1024,112),ImageMemoryBudget.sampleSize(1360,2040,150L*1024*1024,FilmCorrectionProfiles.workingBytes(FilmCorrectionProfiles.latest("vision3-500t"))));
    }
    @Test public void specifiedGrainIsIndependentOfColourStrengthAndDeterministic() throws Exception {
        JSONObject p=profile();assertEquals(.85,p.getDouble("day_strength"),0);assertEquals(30,p.getDouble("day_grain_percent"),0);assertEquals(100,p.getDouble("night_grain_percent"),0);
        p.getJSONObject("grain_profile").put("native_reference_edge",128);
        int[] source=new int[96*64];Arrays.fill(source,0xff808080);
        JSONObject zero=new JSONObject(p.toString());zero.put("day_grain_percent",0);zero.put("night_grain_percent",0);
        JSONObject full=new JSONObject(p.toString());full.put("day_grain_percent",100);full.put("night_grain_percent",100);
        int[] clean=source.clone(),a=source.clone(),again=source.clone(),b=source.clone();
        new LearnedFilmProfile(zero.toString(),this::noise).render(clean,96,64,()->false);
        LearnedFilmProfile day=new LearnedFilmProfile(p.toString(),this::noise);day.render(a,96,64,()->false);day.render(again,96,64,()->false);
        new LearnedFilmProfile(full.toString(),this::noise).render(b,96,64,()->false);assertArrayEquals(a,again);
        double e30=0,e100=0;for(int i=0;i<a.length;i++){double x=(a[i]&255)-(clean[i]&255),y=(b[i]&255)-(clean[i]&255);e30+=x*x;e100+=y*y;}
        double ratio=Math.sqrt(e30/e100);assertTrue("30/100 amplitude ratio="+ratio,ratio>.26&&ratio<.34);
    }
    @Test public void cancelledAndInvalidProfilesDoNotPublish() throws Exception {
        LearnedFilmProfile renderer=new LearnedFilmProfile(profile().toString(),this::noise);int[] input=new int[80*80];Arrays.fill(input,0xff8090b0);int[] before=input.clone();int[] count={0};
        assertThrows(CancellationException.class,()->renderer.render(input,80,80,()->++count[0]>70));assertArrayEquals(before,input);
        JSONObject invalid=profile();invalid.put("day_grain_percent",300);assertThrows(JSONException.class,()->new LearnedFilmProfile(invalid.toString(),this::noise));
    }
}
