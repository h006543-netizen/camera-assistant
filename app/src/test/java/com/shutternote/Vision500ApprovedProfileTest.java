package com.shutternote;

import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPInputStream;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class Vision500ApprovedProfileTest {
    private JSONObject profile() throws Exception {return new JSONObject(Files.readString(Path.of("src/main/assets/film_corrections/latest/vision3-500t-approved-night-v1.json")));}
    private float[] noise(String name) throws java.io.IOException {
        byte[] raw;try(GZIPInputStream in=new GZIPInputStream(Files.newInputStream(Path.of("src/main/assets",name)))){raw=in.readAllBytes();}
        float[] f=new float[raw.length/4];ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(f);return f;
    }
    private int[] unpack(JSONArray a) throws Exception {int[] out=new int[a.length()];for(int i=0;i<out.length;i++)out[i]=a.getInt(i);return out;}
    @Test public void approvedValuesAndPreviousVersionArePinned() throws Exception {
        JSONObject p=profile(),v=p.getJSONObject("night_tuning");
        assertEquals(31,p.getInt("schema_version"));assertEquals(5,v.getDouble("base_position"),0);
        assertEquals(50,v.getDouble("grain_percent"),0);assertEquals(0,v.getDouble("light_protection"),0);
        assertEquals(300,v.getDouble("tungsten"),0);assertEquals(-50,v.getDouble("tint"),0);assertEquals(135,v.getDouble("saturation"),0);
        assertEquals("vision3-500t-"+p.getString("model_version"),FilmCorrectionProfiles.OLD_500T_APPROVED);
        assertTrue(FilmCorrectionProfiles.supported(FilmCorrectionProfiles.OLD_500T_SCENE));
        assertEquals("vision3-500t",FilmCorrectionProfiles.key(FilmCorrectionProfiles.OLD_500T_SCENE));
        assertEquals(112,FilmCorrectionProfiles.workingBytes(FilmCorrectionProfiles.OLD_500T_APPROVED));
        assertEquals(120,FilmCorrectionProfiles.workingBytes(FilmCorrectionProfiles.latest("vision3-500t")));
    }
    @Test public void calibratedNightDayAndMixedMatchPython() throws Exception {
        JSONObject fixture=new JSONObject(new String(getClass().getResourceAsStream("/vision500_approved_parity.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        JSONArray samples=fixture.getJSONArray("cases");
        for(int k=0;k<samples.length();k++) {
            JSONObject sample=samples.getJSONObject(k),p=profile(),base=p.getJSONObject("base_profile");
            base.getJSONObject("grain_profile").put("native_reference_edge",fixture.getInt("native_edge"));
            if(sample.getBoolean("router_equal")){base.getJSONObject("router").put("day",new JSONArray("[[0,0,0,0,0,0,0]]"));base.getJSONObject("router").put("night",new JSONArray("[[0,0,0,0,0,0,0]]"));}
            int[] source=unpack(sample.getJSONArray("input")),actual=source.clone(),expected=unpack(sample.getJSONArray("expected"));
            Vision500ApprovedProfile probe=new Vision500ApprovedProfile(p,this::noise);
            assertEquals(sample.getDouble("day_weight"),probe.dayWeight(source,sample.getInt("width"),sample.getInt("height"),()->false),1e-5);
            new LearnedFilmProfile(p.toString(),this::noise).render(actual,sample.getInt("width"),sample.getInt("height"),()->false);
            int max=0;for(int i=0;i<actual.length;i++)for(int c=0;c<3;c++)max=Math.max(max,Math.abs(((actual[i]>>>(16-c*8))&255)-((expected[i]>>>(16-c*8))&255)));
            System.out.println(sample.getString("name")+" approved max="+max);assertTrue(max<=1);
        }
    }
    @Test public void gradeMatchesDoublePrecisionTunerWithoutLegacyErrors() throws Exception {
        JSONObject fixture=new JSONObject(new String(getClass().getResourceAsStream("/vision500_approved_grade.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        JSONArray source=fixture.getJSONArray("input"),expected=fixture.getJSONArray("expected");
        Vision500ApprovedProfile renderer=new Vision500ApprovedProfile(profile(),this::noise);double[] output=new double[3];
        for(int i=0;i<source.length();i++) {
            JSONArray x=source.getJSONArray(i);renderer.grade(x.getDouble(0),x.getDouble(1),x.getDouble(2),output);
            for(int c=0;c<3;c++)assertEquals(expected.getJSONArray(i).getDouble(c),output[c],2e-12);
        }
    }
    @Test public void pureDayIsPixelIdenticalToPreservedRenderer() throws Exception {
        JSONObject p=profile();int w=48,h=64;int[] source=new int[w*h],evidence=new int[32*32];Random random=new Random(50031);
        for(int i=0;i<source.length;i++)source[i]=0x55000000|random.nextInt(0x1000000);Arrays.fill(evidence,0xffeeeeee);
        int[] current=source.clone(),previous=source.clone();
        new LearnedFilmProfile(p.toString(),this::noise).render(current,w,h,()->false,evidence,32,32);
        new LearnedFilmProfile(p.getJSONObject("base_profile").toString(),this::noise).render(previous,w,h,()->false,evidence,32,32);
        assertArrayEquals(previous,current);
    }
    @Test public void independentGrainChangesNightAndRenderingIsDeterministic() throws Exception {
        JSONObject p=profile();p.getJSONObject("base_profile").getJSONObject("grain_profile").put("native_reference_edge",128);
        int w=96,h=64;int[] source=new int[w*h];Arrays.fill(source,0xff253b50);
        int[] a=source.clone(),b=source.clone(),zero=source.clone();LearnedFilmProfile renderer=new LearnedFilmProfile(p.toString(),this::noise);
        renderer.render(a,w,h,()->false);renderer.render(b,w,h,()->false);assertArrayEquals(a,b);
        p.getJSONObject("night_tuning").put("grain_percent",0);new LearnedFilmProfile(p.toString(),this::noise).render(zero,w,h,()->false);
        assertFalse(Arrays.equals(a,zero));for(int pixel:a)assertEquals(0xff000000,pixel&0xff000000);
    }
    @Test public void cancellationAndInvalidParametersCannotPublish() throws Exception {
        LearnedFilmProfile renderer=new LearnedFilmProfile(profile().toString(),this::noise);int[] source=new int[80*80];Arrays.fill(source,0xff203040);int[] before=source.clone(),calls={0};
        assertThrows(CancellationException.class,()->renderer.render(source,80,80,()->++calls[0]>65));assertArrayEquals(before,source);
        JSONObject maskInvalid=profile();maskInvalid.getJSONObject("night_tuning").put("light_protection",1);
        assertThrows(JSONException.class,()->new LearnedFilmProfile(maskInvalid.toString(),this::noise));
        JSONObject p=profile();p.getJSONObject("night_tuning").put("grain_percent",400);final JSONObject invalid=p;
        assertThrows(JSONException.class,()->new LearnedFilmProfile(invalid.toString(),this::noise));
    }
    @Test public void newPolicyKeepsBrightNightAndDarkDayWhenPhotoSupportSaysSo() throws Exception {
        for(boolean brightNight:new boolean[]{true,false}) {
            JSONObject p=profile(),base=p.getJSONObject("base_profile"),router=base.getJSONObject("router");
            int[] source=new int[32*24];Arrays.fill(source,brightNight?0xffdddddd:0xff010101);
            JSONArray actual=new JSONArray(Cine800SceneProfile.features(source,32,24,()->false));
            JSONArray opposite=new JSONArray(brightNight?"[0,0,0,0,0,0,0]":"[1,1,1,1,1,1,1]");
            router.put("day",new JSONArray().put(brightNight?opposite:actual));
            router.put("night",new JSONArray().put(brightNight?actual:opposite));
            Vision500SceneProfile probe=new Vision500SceneProfile(base,this::noise);
            assertEquals(brightNight?1:0,probe.dayWeight(source,32,24,()->false),0);
            assertEquals(brightNight?0:1,probe.similarityDayWeight(source,32,24,()->false),0);
            int[] expected=brightNight?new Vision500ApprovedProfile(p,this::noise).renderNight(source,32,24,()->false)
                    :probe.renderDayComplete(source,32,24,()->false);
            for(double hour:new double[]{Double.NaN,0,12,23.99}) {
                int[] output=source.clone();
                new LearnedFilmProfile(p.toString(),this::noise).renderWithSceneTime(output,32,24,()->false,null,0,0,hour);
                assertArrayEquals(expected,output);
            }
        }
    }
    @Test public void newTimePolicyBlendsCompleteBranchesAndCancelsAtomically() throws Exception {
        JSONObject p=profile(),base=p.getJSONObject("base_profile"),router=base.getJSONObject("router");
        router.put("day",new JSONArray("[[0.31,0.31,0.31,0.31,0.31,0.31,0.31]]"));
        router.put("night",new JSONArray("[[0.30,0.30,0.30,0.30,0.30,0.30,0.30]]"));
        int[] source=new int[32*24];Arrays.fill(source,0xff333333);int[] before=source.clone();
        Vision500SceneProfile day=new Vision500SceneProfile(base,this::noise);
        double photo=day.similarityDayWeight(source,32,24,()->false);
        assertTrue(photo>0&&photo<.5);
        int[] night=new Vision500ApprovedProfile(p,this::noise).renderNight(source,32,24,()->false);
        int[] pureDay=day.renderDayComplete(source,32,24,()->false);
        LearnedFilmProfile renderer=new LearnedFilmProfile(p.toString(),this::noise);
        for(double hour:new double[]{Double.NaN,0,12}) {
            double weight=Vision500SceneTime.dayWeight(photo,hour);int[] output=source.clone(),again=source.clone();
            renderer.renderWithSceneTime(output,32,24,()->false,null,0,0,hour);
            renderer.renderWithSceneTime(again,32,24,()->false,null,0,0,hour);assertArrayEquals(output,again);
            for(int i=0;i<source.length;i++)for(int c=0;c<3;c++) {
                int shift=16-c*8,n=(night[i]>>>shift)&255,d=(pureDay[i]>>>shift)&255;
                assertEquals((int)Math.rint(n+weight*(d-n)),(output[i]>>>shift)&255);
            }
        }
        int[] calls={0};assertThrows(CancellationException.class,()->renderer.renderWithSceneTime(source,32,24,()->++calls[0]>40,null,0,0,12));
        assertArrayEquals(before,source);
    }
}
