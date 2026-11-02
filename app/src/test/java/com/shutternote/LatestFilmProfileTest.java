package com.shutternote;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPInputStream;
import static org.junit.Assert.*;

public class LatestFilmProfileTest {
    private static final Path ASSETS=Path.of("src/main/assets");
    private String profile(String key) throws Exception {return Files.readString(ASSETS.resolve("film_corrections/latest/"+key+".json"));}
    private float[] noise(String asset) throws java.io.IOException {
        byte[] bytes;
        try(GZIPInputStream input=new GZIPInputStream(Files.newInputStream(ASSETS.resolve(asset)))) {bytes=input.readAllBytes();}
        float[] out=new float[bytes.length/4];ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out);return out;
    }
    private String hash(Path file) throws Exception {
        StringBuilder out=new StringBuilder();
        for(byte value:MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)))out.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
        return out.toString();
    }
    @Test public void pinnedCatalogHasFiveLatestQualifyingVersionsAndUnchangedAssets() throws Exception {
        JSONObject catalog=new JSONObject(Files.readString(ASSETS.resolve("film_corrections/latest/catalog.json")));
        JSONArray films=catalog.getJSONArray("films");assertEquals(5,films.length());
        for(int i=0;i<5;i++) {
            JSONObject film=films.getJSONObject(i);
            if(film.getString("key").equals("e100d"))assertEquals("user_approved_manual_settings",film.getString("origin"));
            else if(film.getString("key").equals("cine800t"))assertEquals("user_approved_day_night_settings",film.getString("origin"));
            else if(film.getString("key").equals("vision3-250d")) {
                assertEquals("user_approved_report_grade_grain20",film.getString("origin"));
                assertFalse(film.has("mean_delta_e00"));
                assertEquals("report_reproduction_not_independent_film_accuracy",film.getString("evaluation_scope"));
            }
            else if(film.getString("key").equals("vision3-500t")) {
                assertEquals("confidence_source_preservation_policy_no_retraining",film.getString("origin"));
                assertFalse(film.has("mean_delta_e00"));
                assertEquals("local_regression_not_independent_film_accuracy",film.getString("evaluation_scope"));
            }
            else assertTrue(film.getDouble("mean_delta_e00")<6);
            assertEquals(FilmCorrectionProfiles.IDS[i],FilmCorrectionProfiles.assetId(FilmCorrectionProfiles.latest(film.getString("key"))));
            if(i==4)assertEquals(FilmCorrectionProfiles.latest("vision3-500t"),film.getString("default_recipe_id"));
            assertEquals(FilmCorrectionProfiles.HASHES[i],hash(ASSETS.resolve(FilmCorrectionProfiles.ASSETS[i])));
            assertEquals(film.getString("key"),FilmCorrectionProfiles.key(film.getString("id")));
            assertTrue(FilmCorrectionProfiles.supported(film.getString("id")));
        }
        for(int i=0;i<3;i++)assertEquals(FilmCorrectionProfiles.GRAIN_HASHES[i],hash(ASSETS.resolve(FilmCorrectionProfiles.GRAIN_ASSETS[i])));
        assertFalse(FilmCorrectionProfiles.supported("unknown-version"));
        assertTrue(FilmCorrectionProfiles.supported(FilmColorCorrection.E100D));
    }
    @Test public void nativeGrainMatricesAndAreaSamplingMatchPcAtCalibrationScale() throws Exception {
        JSONObject data=new JSONObject(new String(getClass().getResourceAsStream("/latest_grain_parity.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        JSONObject model=new JSONObject(profile("cine800t-previous")),grain=model.getJSONObject("effects").getJSONObject("grain");
        grain.put("native_reference_edge",data.getInt("calibration_native_edge"));
        LearnedFilmGrain renderer=new LearnedFilmGrain(grain,model.getJSONObject("android_runtime"),this::noise);
        JSONArray input=data.getJSONArray("input"),expected=data.getJSONArray("expected");
        float[] color=new float[input.length()*3],levels=new float[input.length()];
        for(int i=0;i<input.length();i++) {
            int packed=input.getInt(i);
            for(int c=0;c<3;c++)color[i*3+c]=((packed >>> (16-c*8))&255)/255f;
            levels[i]=(float)FilmRenderMath.luma(color[i*3],color[i*3+1],color[i*3+2]);
        }
        renderer.apply(color,levels,data.getInt("width"),data.getInt("height"),()->false);
        int max=0;
        for(int i=0;i<input.length();i++)for(int c=0;c<3;c++)max=Math.max(max,Math.abs((int)Math.rint(FilmRenderMath.clamp(color[i*3+c])*255)-((expected.getInt(i) >>> (16-c*8))&255)));
        System.out.println("Native grain calibration max="+max);assertTrue(max<=1);
    }
    @Test public void actual6854PixelGrainIsDeterministicAndBoundedWithoutAllocatingNativeFullFrames() throws Exception {
        JSONObject model=new JSONObject(profile("cine800t"));
        LearnedFilmGrain grain=new LearnedFilmGrain(model.getJSONObject("effects").getJSONObject("grain"),model.getJSONObject("android_runtime"),this::noise);
        float[] a=new float[128*128*3],b=new float[a.length],levels=new float[128*128];
        java.util.Arrays.fill(a,.4f);java.util.Arrays.fill(b,.4f);java.util.Arrays.fill(levels,.4f);
        grain.apply(a,levels,128,128,()->false);grain.apply(b,levels,128,128,()->false);
        assertArrayEquals(a,b,0);double energy=0;for(float value:a)energy+=(value-.4)*(value-.4);
        assertTrue(energy>0);assertTrue(energy/a.length<.01);
        for(String key:new String[]{"vision3-50d","e100d-previous","vision3-250d","vision3-500t"}) {
            JSONObject p=new JSONObject(profile(key));
            assertFalse(new LearnedFilmGrain(p.getJSONObject("effects").getJSONObject("grain"),p.getJSONObject("android_runtime"),this::noise).active());
        }
    }
    @Test public void cancelledRenderCannotPublishAndContractsAreValidated() throws Exception {
        LearnedFilmProfile renderer=new LearnedFilmProfile(profile("vision3-50d"),this::noise);
        int[] pixels=new int[64*64];java.util.Arrays.fill(pixels,0xff335577);int[] before=pixels.clone();
        assertThrows(CancellationException.class,()->renderer.render(pixels,64,64,()->true));assertArrayEquals(before,pixels);
        JSONObject broken=new JSONObject(profile("vision3-50d"));broken.put("schema_version",4);
        assertThrows(org.json.JSONException.class,()->new LearnedFilmProfile(broken.toString(),this::noise));
        assertTrue(ImageMemoryBudget.sampleSize(6000,4000,128L*1024*1024,80)>ImageMemoryBudget.sampleSize(6000,4000,128L*1024*1024));
    }
}
