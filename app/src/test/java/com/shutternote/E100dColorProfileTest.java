package com.shutternote;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class E100dColorProfileTest {
    private String json() throws Exception {
        return Files.readString(Path.of("src/main/assets/film_corrections/e100d/profile.json"));
    }
    @Test public void matchesProvidedFixtureAndIndependentPcScenesWithinOneCodeValue() throws Exception {
        E100dColorProfile profile = new E100dColorProfile(json());
        JSONArray cases = new JSONArray(new String(getClass().getResourceAsStream("/e100d_parity.json").readAllBytes(), StandardCharsets.UTF_8));
        for (int c = 0; c < cases.length(); c++) {
            JSONObject sample = cases.getJSONObject(c);
            JSONArray input = sample.getJSONArray("input"), expected = sample.getJSONArray("expected");
            int[] pixels = new int[input.length()];
            for (int i = 0; i < pixels.length; i++) pixels[i] = input.getInt(i) | 0xff000000;
            profile.render(pixels, sample.getInt("width"), sample.getInt("height"), () -> false);
            int maximum = 0; long total = 0;
            for (int i = 0; i < pixels.length; i++) for (int shift : new int[]{0,8,16}) {
                int difference = Math.abs(((pixels[i] >>> shift) & 255) - ((expected.getInt(i) >>> shift) & 255));
                maximum = Math.max(maximum, difference); total += difference;
                assertEquals(255, pixels[i] >>> 24);
            }
            System.out.println(sample.getString("name") + " max=" + maximum + " mean=" + total/(pixels.length*3.0));
            assertTrue(sample.getString("name") + " max error " + maximum, maximum <= 1);
        }
    }
    @Test public void rejectsWrongVersionAndNonzeroUnimplementedTexture() throws Exception {
        JSONObject data = new JSONObject(json()); data.put("model_version", "other");
        String wrongVersion = data.toString();
        assertThrows(org.json.JSONException.class, () -> new E100dColorProfile(wrongVersion));
        data = new JSONObject(json());
        data.getJSONObject("effects").getJSONObject("halation").getJSONArray("gain_rgb").getJSONArray(0).put(0, .1);
        String modified = data.toString();
        assertThrows(org.json.JSONException.class, () -> new E100dColorProfile(modified));
    }
    @Test public void cancellationStopsWithoutPublishingAnOutput() throws Exception {
        E100dColorProfile profile = new E100dColorProfile(json());
        assertThrows(CancellationException.class, () -> profile.render(new int[64*64],64,64,() -> true));
    }
}
