package com.shutternote;

import org.json.*;
import org.junit.Test;
import java.nio.file.*;
import java.util.concurrent.CancellationException;
import static org.junit.Assert.*;

public class E100dManualProfileTest {
    private JSONObject profile() throws Exception {return new JSONObject(Files.readString(Path.of("src/main/assets/film_corrections/latest/e100d.json")));}
    @Test public void exactApprovedParametersAndOldVersionsRemainAvailable() throws Exception {
        JSONObject p=profile(),v=p.getJSONObject("effects").getJSONObject("separated_color").getJSONObject("parameters");
        assertEquals(24,p.getInt("schema_version"));assertEquals(5.564644038677216,v.getDouble("green_percent"),0);
        assertEquals(25,v.getDouble("base_percent"),0);assertEquals(70,v.getDouble("blue_percent"),0);assertEquals(55,v.getDouble("black_percent"),0);
        assertTrue(FilmCorrectionProfiles.supported(FilmCorrectionProfiles.OLD_E100D));assertEquals("e100d",FilmCorrectionProfiles.key(FilmCorrectionProfiles.OLD_E100D));
        assertTrue(FilmCorrectionProfiles.supported(FilmColorCorrection.E100D));
    }
    @Test public void completeAndroidRenderMatchesPcAcrossRealAndIndependentInputs() throws Exception {
        JSONArray cases=new JSONArray(new String(getClass().getResourceAsStream("/e100d_manual_parity.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        E100dManualProfile manual=new E100dManualProfile(profile());
        LearnedFilmProfile renderer=new LearnedFilmProfile(profile().toString(),asset->{throw new AssertionError("Manual model has no grain atlas");});
        for(int n=0;n<cases.length();n++) {
            JSONObject c=cases.getJSONObject(n);JSONArray input=c.getJSONArray("input"),expected=c.getJSONArray("expected");
            int[] pixels=new int[input.length()];for(int i=0;i<pixels.length;i++)pixels[i]=0xff000000|input.getInt(i);
            int w=c.getInt("width"),h=c.getInt("height");double amount=manual.strength(pixels,w,h,()->false);
            assertEquals(c.getString("name")+" source strength",c.getDouble("strength"),amount,2e-5);
            renderer.render(pixels,w,h,()->false);int max=0;
            for(int i=0;i<pixels.length;i++)for(int channel=0;channel<3;channel++)max=Math.max(max,Math.abs(((pixels[i]>>>(16-8*channel))&255)-((expected.getInt(i)>>>(16-8*channel))&255)));
            System.out.println("E100D manual "+c.getString("name")+" max="+max);assertTrue(c.getString("name")+" max="+max,max<=1);
        }
    }
    @Test public void cancellationProtectsInputAndInvalidContractIsRejected() throws Exception {
        E100dManualProfile renderer=new E100dManualProfile(profile());int[] input=new int[32*24];java.util.Arrays.fill(input,0xff7799bb);int[] before=input.clone();
        assertThrows(CancellationException.class,()->renderer.render(input,32,24,()->true));assertArrayEquals(before,input);
        JSONObject broken=profile();broken.getJSONObject("effects").getJSONObject("separated_color").getJSONObject("parameters").put("black_percent",501);
        assertThrows(JSONException.class,()->new E100dManualProfile(broken));
    }
}
