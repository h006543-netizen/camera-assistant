package com.shutternote;

import org.json.*;
import org.junit.Test;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import static org.junit.Assert.*;

public class Portra400ProfileTest {
    private JSONObject profile() throws Exception {
        return new JSONObject(Files.readString(Path.of("src/main/assets/film_corrections/latest/portra400.json")));
    }
    private LearnedFilmProfile renderer(JSONObject profile) throws Exception {
        return new LearnedFilmProfile(profile.toString(),asset->{throw new AssertionError("No synthetic grain atlas");});
    }
    @Test public void referencesAreUnpairedAndTheVersionIsPinned() throws Exception {
        JSONObject p=profile(),origin=p.getJSONObject("reference_provenance");
        assertEquals(28,p.getInt("schema_version"));assertEquals(39,origin.getInt("reference_count"));
        assertEquals(0,origin.getInt("paired_phone_originals"));assertFalse(origin.getBoolean("independent_accuracy_validated"));
        assertEquals(0,p.getJSONObject("parameters").getDouble("grain"),0);
        assertEquals(60,p.getJSONObject("parameters").getDouble("base"),0);
        String id=FilmCorrectionProfiles.latest("portra400");assertFalse(id.isEmpty());
        assertTrue(FilmCorrectionProfiles.supported(id));assertEquals("portra400",FilmCorrectionProfiles.key(id));
        assertEquals(96,FilmCorrectionProfiles.workingBytes(id));
        StringBuilder hash=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of("src/main/assets/"+FilmCorrectionProfiles.ASSETS[FilmCorrectionProfiles.index(id)]))))
            hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        assertEquals(FilmCorrectionProfiles.HASHES[FilmCorrectionProfiles.index(id)],hash.toString());
    }
    @Test public void realPhotosAndSyntheticInputsMatchPcWithinOneChannelLevel() throws Exception {
        JSONArray cases=new JSONArray(new String(getClass().getResourceAsStream("/portra400_reference_parity.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        LearnedFilmProfile renderer=renderer(profile());
        int global=0;
        for(int n=0;n<cases.length();n++) {
            JSONObject c=cases.getJSONObject(n);JSONArray in=c.getJSONArray("input"),expected=c.getJSONArray("expected");
            int[] pixels=new int[in.length()];for(int i=0;i<pixels.length;i++)pixels[i]=0x7f000000|in.getInt(i);
            renderer.render(pixels,c.getInt("width"),c.getInt("height"),()->false);int max=0;
            for(int i=0;i<pixels.length;i++) {
                assertEquals(0x7f,pixels[i]>>>24);
                for(int channel=0;channel<3;channel++)max=Math.max(max,Math.abs(((pixels[i]>>>(16-8*channel))&255)-((expected.getInt(i)>>>(16-8*channel))&255)));
            }
            global=Math.max(global,max);assertTrue(c.getString("name")+" error "+max,max<=1);
        }
        System.out.println("Portra 400 reference PC/Java max channel error="+global+" cases="+cases.length());
    }
    @Test public void cancellationAndInvalidContractsCannotChangeInput() throws Exception {
        LearnedFilmProfile renderer=renderer(profile());
        int[] pixels=new int[64*48];Arrays.fill(pixels,0xff456789);int[] before=pixels.clone();
        final int[] calls={0};
        assertThrows(CancellationException.class,()->renderer.render(pixels,64,48,()->++calls[0]>3));
        assertArrayEquals(before,pixels);
        final JSONObject broken=profile();broken.getJSONObject("parameters").put("grain",20);
        assertThrows(JSONException.class,()->renderer(broken));
        final JSONObject invalid=profile();invalid.getJSONObject("parameters").put("contrast",151);
        assertThrows(JSONException.class,()->renderer(invalid));
        assertEquals(0,renderer.analysisEdge());
    }
}
