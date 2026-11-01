package com.shutternote;

import org.json.*;
import org.junit.Test;
import java.nio.file.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPInputStream;
import static org.junit.Assert.*;

public class Cine800SceneProfileTest {
    private JSONObject profile() throws Exception {return new JSONObject(Files.readString(Path.of("src/main/assets/film_corrections/latest/cine800t.json")));}
    private JSONArray fixtures() throws Exception {return new JSONArray(new String(getClass().getResourceAsStream("/cine800t_scene_parity.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
    private float[] noise(String asset) throws java.io.IOException {byte[] bytes;try(GZIPInputStream input=new GZIPInputStream(Files.newInputStream(Path.of("src/main/assets").resolve(asset)))){bytes=input.readAllBytes();}float[] out=new float[bytes.length/4];ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out);return out;}
    @Test public void approvedSettingsAndPreviousVersionArePreserved() throws Exception {
        JSONObject p=profile(),s=p.getJSONObject("effects").getJSONObject("cine800t_scene");assertEquals(27,p.getInt("schema_version"));assertEquals(150,s.getJSONObject("day_parameters").getDouble("tungsten"),0);assertEquals(300,s.getJSONObject("night_parameters").getDouble("tungsten"),0);
        assertEquals(41.6,s.getJSONObject("day_parameters").getDouble("grain_percent"),0);assertEquals(200,s.getJSONObject("night_parameters").getDouble("grain_percent"),0);
        assertTrue(FilmCorrectionProfiles.supported(FilmCorrectionProfiles.OLD_800T));assertEquals("cine800t",FilmCorrectionProfiles.key(FilmCorrectionProfiles.OLD_800T));
        new LearnedFilmProfile(Files.readString(Path.of("src/main/assets").resolve(FilmCorrectionProfiles.OLD_800T_ASSET)),this::noise);
        assertTrue(FilmCorrectionProfiles.supported(FilmCorrectionProfiles.OLD_800T_SCENE));
        new LearnedFilmProfile(Files.readString(Path.of("src/main/assets").resolve(FilmCorrectionProfiles.OLD_800T_SCENE_ASSET)),this::noise);
    }
    @Test public void sourceBrightnessAndActualColorLightRenderMatchPc() throws Exception {
        JSONObject p=new JSONObject(Files.readString(Path.of("src/main/assets").resolve(FilmCorrectionProfiles.OLD_800T_GUARD_ASSET))),s=p.getJSONObject("effects").getJSONObject("cine800t_scene");s.getJSONObject("day_parameters").put("grain_percent",0);s.getJSONObject("night_parameters").put("grain_percent",0);
        Cine800SceneProfile scene=new Cine800SceneProfile(p,this::noise);LearnedFilmProfile renderer=new LearnedFilmProfile(p.toString(),this::noise);JSONArray cases=fixtures();int overallMax=0;
        for(int n=0;n<cases.length();n++){
            JSONObject c=cases.getJSONObject(n);JSONArray input=c.getJSONArray("input"),expected=c.getJSONArray("expected");int[] pixels=new int[input.length()];for(int i=0;i<pixels.length;i++)pixels[i]=0xff000000|input.getInt(i);int w=c.getInt("width"),h=c.getInt("height");
            double weight=scene.dayWeight(pixels,w,h,()->false);assertEquals(c.getString("name")+" day weight",c.getDouble("day_weight"),weight,2e-4);
            Cine800Lights.Fields f=Cine800Lights.paired(pixels,w,h,s.getJSONObject("source_profile"),55,()->false);double fieldError=0,coreError=0;
            for(int i=0;i<pixels.length;i++){fieldError=Math.max(fieldError,Math.abs(f.red[i]-c.getJSONArray("halo_field").getDouble(i)));coreError=Math.max(coreError,Math.abs(f.weight[i]-c.getJSONArray("halo_weight").getDouble(i)));}
            renderer.render(pixels,w,h,()->false);int max=0;double mae=0;for(int i=0;i<pixels.length;i++)for(int channel=0;channel<3;channel++){int d=Math.abs(((pixels[i]>>>(16-8*channel))&255)-((expected.getInt(i)>>>(16-8*channel))&255));max=Math.max(max,d);mae+=d;}
            System.out.println("800T "+c.getString("name")+" max="+max+" mean="+mae/(pixels.length*3)+" field="+fieldError+" core="+coreError);
            overallMax=Math.max(overallMax,max);
        }
        assertTrue("maximum channel error="+overallMax,overallMax<=1);
    }
    @Test public void nativeGrainIsDeterministicAndCancellationCannotPublish() throws Exception {
        LearnedFilmProfile renderer=new LearnedFilmProfile(profile().toString(),this::noise);int[] a=new int[64*48];Arrays.fill(a,0xff334455);int[] b=a.clone(),original=a.clone();
        assertThrows(CancellationException.class,()->renderer.render(a,64,48,()->true));assertArrayEquals(original,a);
        renderer.render(a,64,48,()->false);renderer.render(b,64,48,()->false);assertArrayEquals(a,b);assertFalse(Arrays.equals(original,a));
        JSONObject broken=profile();broken.getJSONObject("effects").getJSONObject("cine800t_scene").getJSONObject("night_parameters").put("tungsten",601);assertThrows(JSONException.class,()->new LearnedFilmProfile(broken.toString(),this::noise));
    }
    @Test public void previousSceneVersionStillMatchesItsOriginalPcPixels() throws Exception {
        JSONObject p=new JSONObject(Files.readString(Path.of("src/main/assets").resolve(FilmCorrectionProfiles.OLD_800T_SCENE_ASSET))),s=p.getJSONObject("effects").getJSONObject("cine800t_scene");
        s.getJSONObject("day_parameters").put("grain_percent",0);s.getJSONObject("night_parameters").put("grain_percent",0);LearnedFilmProfile renderer=new LearnedFilmProfile(p.toString(),this::noise);
        JSONArray cases=new JSONArray(new String(getClass().getResourceAsStream("/cine800t_scene_parity_v1.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        for(int k=0;k<cases.length();k++){JSONObject c=cases.getJSONObject(k);JSONArray input=c.getJSONArray("input"),expected=c.getJSONArray("expected");int[] pixels=new int[input.length()];for(int i=0;i<pixels.length;i++)pixels[i]=0xff000000|input.getInt(i);renderer.render(pixels,c.getInt("width"),c.getInt("height"),()->false);int max=0;for(int i=0;i<pixels.length;i++)for(int ch=0;ch<3;ch++)max=Math.max(max,Math.abs(((pixels[i]>>>(16-ch*8))&255)-((expected.getInt(i)>>>(16-ch*8))&255)));assertTrue(c.getString("name")+" old version max="+max,max<=1);}
    }
    @Test public void fullReferenceResolutionLampAndSignGeometryMatchPc() throws Exception {
        org.junit.Assume.assumeTrue("Private full-resolution fixtures are local only",getClass().getResource("/cine800t-guard-full/3-pixels.bin")!=null);
        JSONObject p=profile(),s=p.getJSONObject("effects").getJSONObject("cine800t_scene");s.getJSONObject("day_parameters").put("grain_percent",0);s.getJSONObject("night_parameters").put("grain_percent",0);
        LearnedFilmProfile renderer=new LearnedFilmProfile(p.toString(),this::noise);int overall=0;
        for(int pair:new int[]{3,4,7,8,13}){
            java.io.DataInputStream input=new java.io.DataInputStream(getClass().getResourceAsStream("/cine800t-guard-full/"+pair+"-pixels.bin"));
            int w=input.readInt(),h=input.readInt(),n=w*h;int[] pixels=new int[n],target=new int[n];for(int i=0;i<n;i++)pixels[i]=input.readInt();for(int i=0;i<n;i++)target[i]=input.readInt();input.close();
            Cine800Lights.Fields f=Cine800Lights.paired(pixels,w,h,s.getJSONObject("source_profile"),55,()->false);
            byte[] raw=getClass().getResourceAsStream("/cine800t-guard-full/"+pair+"-field.bin").readAllBytes(),core=getClass().getResourceAsStream("/cine800t-guard-full/"+pair+"-core.bin").readAllBytes();ByteBuffer field=ByteBuffer.wrap(raw),weight=ByteBuffer.wrap(core);double fieldError=0,coreError=0;
            for(int i=0;i<n;i++){fieldError=Math.max(fieldError,Math.abs(f.red[i]-field.getFloat()));coreError=Math.max(coreError,Math.abs(f.weight[i]-weight.getFloat()));}
            renderer.render(pixels,w,h,()->false);int max=0;double mae=0;int[] err=new int[256];
            for(int i=0;i<n;i++)for(int c=0;c<3;c++){int d=Math.abs(((pixels[i]>>>(16-c*8))&255)-((target[i]>>>(16-c*8))&255));max=Math.max(max,d);mae+=d;err[d]++;}
            System.out.println("800T full-"+pair+" max="+max+" mean="+mae/(n*3)+" field="+fieldError+" core="+coreError);overall=Math.max(overall,max);
            assertTrue("exterior field",fieldError<2e-6);assertTrue("protected core",coreError<2e-6);
        }
        assertTrue("full-reference maximum="+overall,overall<=1);
    }
}
