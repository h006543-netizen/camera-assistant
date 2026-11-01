package com.shutternote;

import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class LatestFilmColorTest {
    @Test public void learnedHalationMatchesPcWithoutChangingItsCoefficients() throws Exception {
        JSONArray cases=new JSONArray(new String(getClass().getResourceAsStream("/latest_films_parity.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        for(int n=0;n<cases.length();n++) {
            JSONObject sample=cases.getJSONObject(n);
            String key=sample.getString("key");if(key.equals("e100d"))key="e100d-previous";if(key.equals("cine800t"))key="cine800t-previous";
            JSONObject effects=new JSONObject(Files.readString(Path.of("src/main/assets/film_corrections/latest/"+key+".json"))).getJSONObject("effects");
            JSONArray input=sample.getJSONArray("input"),expected=sample.getJSONArray("halation");
            int[] pixels=new int[input.length()];for(int i=0;i<pixels.length;i++)pixels[i]=input.getInt(i)|0xff000000;
            int w=sample.getInt("width"),h=sample.getInt("height");
            float[] output=new LearnedFilmColor(effects.getJSONObject("separated_color")).render(pixels,w,h,()->false);
            new LearnedFilmLights(effects.getJSONObject("halation"),effects.getDouble("reference_edge")).apply(pixels,output,w,h,()->false);
            int max=0;
            for(int i=0;i<pixels.length;i++)for(int c=0;c<3;c++) {
                int value=(int)Math.rint(Math.max(0,Math.min(1,output[i*3+c]))*255);
                max=Math.max(max,Math.abs(value-((expected.getInt(i) >>> (16-8*c))&255)));
            }
            System.out.println(key+" "+sample.getString("name")+" halation max="+max);
            assertTrue(key+" "+sample.getString("name")+" halo max="+max,max<=1);
        }
    }
    @Test public void fiveLearnedResponsesMatchPcAcrossIndependentScenes() throws Exception {
        JSONArray cases=new JSONArray(new String(getClass().getResourceAsStream("/latest_films_parity.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        for(int n=0;n<cases.length();n++) {
            JSONObject sample=cases.getJSONObject(n);
            String key=sample.getString("key");if(key.equals("e100d"))key="e100d-previous";if(key.equals("cine800t"))key="cine800t-previous";
            JSONObject profile=new JSONObject(Files.readString(Path.of("src/main/assets/film_corrections/latest/"+key+".json")));
            LearnedFilmColor color=new LearnedFilmColor(profile.getJSONObject("effects").getJSONObject("separated_color"));
            JSONArray input=sample.getJSONArray("input"),expected=sample.getJSONArray("color");
            int[] pixels=new int[input.length()];for(int i=0;i<pixels.length;i++)pixels[i]=input.getInt(i)|0xff000000;
            float[] output=color.render(pixels,sample.getInt("width"),sample.getInt("height"),()->false);
            int max=0;
            for(int i=0;i<pixels.length;i++)for(int c=0;c<3;c++) {
                int value=(int)Math.rint(Math.max(0,Math.min(1,output[i*3+c]))*255);
                max=Math.max(max,Math.abs(value-((expected.getInt(i) >>> (16-8*c))&255)));
            }
            System.out.println(key+" "+sample.getString("name")+" color max="+max);
            assertTrue(key+" "+sample.getString("name")+" max="+max,max<=1);
        }
    }
}
