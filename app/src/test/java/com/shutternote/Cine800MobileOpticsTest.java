package com.shutternote;

import org.json.*;
import org.junit.Test;
import java.io.*;
import java.nio.file.*;
import static org.junit.Assert.*;

public class Cine800MobileOpticsTest {
    private JSONObject profile() throws Exception {JSONObject p=new JSONObject(Files.readString(Path.of("src/main/assets/film_corrections/latest/cine800t.json")));JSONObject s=p.getJSONObject("effects").getJSONObject("cine800t_scene");s.getJSONObject("day_parameters").put("grain_percent",0);s.getJSONObject("night_parameters").put("grain_percent",0);return p;}
    @Test public void fullSourceAnalysisSurvivesAllMobileOutputSizes() throws Exception {
        org.junit.Assume.assumeTrue("Private full-resolution fixtures are local only",getClass().getResource("/cine800t-guard-full/8-pixels.bin")!=null);
        JSONObject p=profile(),sourceProfile=p.getJSONObject("effects").getJSONObject("cine800t_scene").getJSONObject("source_profile");LearnedFilmProfile renderer=new LearnedFilmProfile(p.toString(),asset->{throw new IOException("Grain disabled");});assertEquals(1024,renderer.analysisEdge());
        for(int pair:new int[]{8,4,7}){
            DataInputStream canonical=new DataInputStream(getClass().getResourceAsStream("/cine800t-guard-full/"+pair+"-pixels.bin"));int aw=canonical.readInt(),ah=canonical.readInt();int[] analysis=new int[aw*ah];for(int i=0;i<analysis.length;i++)analysis[i]=0xff000000|canonical.readInt();canonical.close();
            for(int edge:pair==8?new int[]{768,640,512,384,256}:new int[]{512}){
                DataInputStream fixture=new DataInputStream(getClass().getResourceAsStream("/cine800t-mobile/"+pair+"-"+edge+".bin"));int w=fixture.readInt(),h=fixture.readInt(),n=w*h;int[] pixels=new int[n],expected=new int[n];for(int i=0;i<n;i++)pixels[i]=0xff000000|fixture.readInt();for(int i=0;i<n;i++)expected[i]=fixture.readInt();float[] field=new float[n];for(int i=0;i<n;i++)field[i]=fixture.readFloat();fixture.close();
                Cine800Lights.Fields full=Cine800Lights.paired(analysis,aw,ah,sourceProfile,55,()->false),mapped=Cine800Analysis.resize(full,aw,ah,w,h,()->false);double maxField=0,fieldError=0;for(int i=0;i<n;i++){maxField=Math.max(maxField,mapped.red[i]);fieldError=Math.max(fieldError,Math.abs(mapped.red[i]-field[i]));}
                if(pair==8){assertTrue("lamp response remains at "+edge,maxField>.05);if(edge<=640){Cine800Lights.Fields old=Cine800Lights.paired(pixels,w,h,sourceProfile,55,()->false);for(float v:old.red)assertEquals("old-size detector reproduces missing halo",0,v,0);}}
                renderer.render(pixels,w,h,()->false,analysis,aw,ah);int max=0;for(int i=0;i<n;i++)for(int c=0;c<3;c++)max=Math.max(max,Math.abs(((pixels[i]>>>(16-c*8))&255)-((expected[i]>>>(16-c*8))&255)));
                System.out.println("mobile "+pair+" edge="+edge+" max="+max+" field="+maxField+" field-error="+fieldError);assertTrue("colour and halo PC parity",max<=1);assertTrue("resampled field",fieldError<2e-6);
            }
        }
    }
}
