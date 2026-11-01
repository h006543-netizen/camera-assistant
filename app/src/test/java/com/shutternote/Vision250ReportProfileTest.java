package com.shutternote;

import org.json.*;
import org.junit.Test;
import java.nio.file.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPInputStream;
import static org.junit.Assert.*;

public class Vision250ReportProfileTest {
    private static final Path ASSETS=Path.of("src/main/assets");
    private JSONObject profile() throws Exception {
        return new JSONObject(Files.readString(ASSETS.resolve("film_corrections/latest/vision3-250d-report-grain20-v1.json")));
    }
    private float[] noise(String asset) throws java.io.IOException {
        try(GZIPInputStream in=new GZIPInputStream(Files.newInputStream(ASSETS.resolve(asset)))) {
            byte[] bytes=in.readAllBytes();float[] out=new float[bytes.length/4];
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out);return out;
        }
    }
    private LearnedFilmProfile renderer(JSONObject p) throws Exception {return new LearnedFilmProfile(p.toString(),this::noise);}

    @Test public void oldRecipesRestoreAndExplicitReselectUsesReportVersion() throws Exception {
        String latest=FilmCorrectionProfiles.latest("vision3-250d");
        assertEquals("vision3-250d-report-grain20-20261101-v1",latest);
        for(String id:new String[]{latest,FilmCorrectionProfiles.OLD_250D,FilmCorrectionProfiles.OLD_250D_RECOVERY}) {
            assertTrue(FilmCorrectionProfiles.supported(id));assertEquals("vision3-250d",FilmCorrectionProfiles.key(id));
            assertEquals(!id.equals(latest),FilmCorrectionProfiles.upgradeOnReselect(id));
            FilmFrameRecord f=new FilmFrameRecord();f.number=1;f.photo="source.jpg";f.correctionPhoto="existing.jpg";
            f.correctionEnabled=true;f.correctionProfile=id;
            FilmFrameRecord restored=FilmFrameRecord.fromJson(f.toJson());
            assertEquals(id,restored.correctionProfile);assertEquals("existing.jpg",restored.displayPhotoName());
        }
        assertEquals(112,FilmCorrectionProfiles.workingBytes(latest));
        assertEquals(0,renderer(profile()).analysisEdge());
    }

    @Test public void deterministicGrainRetainsAlphaAndCancellationNeverPublishes() throws Exception {
        LearnedFilmProfile r=renderer(profile());int w=256,h=128;
        int[] source=new int[w*h];
        for(int i=0;i<source.length;i++)source[i]=(i%256<<24)|0x586b83;
        int[] a=source.clone(),b=source.clone();r.render(a,w,h,()->false);r.render(b,w,h,()->false);assertArrayEquals(a,b);
        Set<Integer> colors=new HashSet<>();
        for(int i=0;i<a.length;i++){assertEquals(source[i]&0xff000000,a[i]&0xff000000);colors.add(a[i]&0xffffff);}
        assertTrue(colors.size()>2);
        int[] cancelled=source.clone(),checks={0};
        assertThrows(CancellationException.class,()->r.render(cancelled,w,h,()->++checks[0]>10));
        assertArrayEquals(source,cancelled);
        assertThrows(IllegalArgumentException.class,()->r.render(source,1,1,()->false));
        for(int edge:new int[]{64,128,512,1024,1536}) {
            int[] x=new int[edge*8];Arrays.fill(x,0xff445566);r.render(x,edge,8,()->false);
            assertEquals(0xff000000,x[x.length/2]&0xff000000);
        }
    }

    @Test public void unsupportedNestedProfilesAndInvalidControlsAreRejected() throws Exception {
        JSONObject p=profile();p.getJSONObject("base_profile").put("schema_version",34);
        assertThrows(JSONException.class,()->renderer(p));
        JSONObject q=profile();q.getJSONObject("parameters").put("contrast",0);
        assertThrows(JSONException.class,()->renderer(q));
        JSONObject g=profile();g.put("grain_percent",100);
        assertThrows(JSONException.class,()->renderer(g));
    }

    @Test public void colorAndGrainMatchIndependentPcFixtures() throws Exception {
        JSONArray cases=new JSONArray(new String(getClass().getResourceAsStream("/vision250_report_parity.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        LearnedFilmProfile r=renderer(profile());
        for(int n=0;n<cases.length();n++) {
            JSONObject c=cases.getJSONObject(n);JSONArray in=c.getJSONArray("input"),expected=c.getJSONArray("expected");
            int[] pixels=new int[in.length()];for(int i=0;i<pixels.length;i++)pixels[i]=in.getInt(i);
            r.render(pixels,c.getInt("width"),c.getInt("height"),()->false);
            int max=0;double sum=0;
            for(int i=0;i<pixels.length;i++)for(int channel=0;channel<3;channel++) {
                int shift=16-channel*8,d=Math.abs(((pixels[i]>>>shift)&255)-((expected.getInt(i)>>>shift)&255));
                max=Math.max(max,d);sum+=d;
            }
            assertTrue("PC parity max="+max+" mean="+sum/(pixels.length*3),max<=3&&sum/(pixels.length*3)<.03);
        }
    }

    @Test public void allFortyReportImagesMatchIndependentPcColorAndAtlasFinish() throws Exception {
        Path root=Path.of("../outputs/analysis/250d-report-grain20-app-20261101");
        org.junit.Assume.assumeTrue("Generate optional 40-photo integration fixtures first",Files.isRegularFile(root.resolve("oracle.json")));
        // The independent fixtures are generated by film-research/verify_250d_report_app.py.
        JSONArray cases=new JSONArray(Files.readString(root.resolve("oracle.json")));
        assertEquals(40,cases.length());LearnedFilmProfile r=renderer(profile());
        LearnedFilmProfile clean=new LearnedFilmProfile(profile().toString(),asset->new float[512*512*3]);
        JSONArray records=new JSONArray();int global=0;double total=0;
        Path rendered=root.resolve("android-render");Files.createDirectories(rendered);
        for(int n=0;n<cases.length();n++) {
            JSONObject c=cases.getJSONObject(n);ByteBuffer binary=ByteBuffer.wrap(Files.readAllBytes(Path.of(c.getString("binary"))));
            int w=binary.getInt(),h=binary.getInt();
            int[] input=new int[w*h],ep=new int[w*h],cp=new int[w*h];
            for(int i=0;i<input.length;i++)input[i]=binary.getInt();
            for(int i=0;i<cp.length;i++)cp[i]=binary.getInt();
            for(int i=0;i<ep.length;i++)ep[i]=binary.getInt();
            int[] actual=input.clone(),color=input.clone();
            r.render(actual,w,h,()->false);clean.render(color,w,h,()->false);
            int max=0,colorMax=0;double sum=0,colorSum=0;
            for(int i=0;i<actual.length;i++)for(int channel=0;channel<3;channel++) {
                int shift=16-channel*8,d=Math.abs(((actual[i]>>>shift)&255)-((ep[i]>>>shift)&255));
                int cd=Math.abs(((color[i]>>>shift)&255)-((cp[i]>>>shift)&255));
                max=Math.max(max,d);sum+=d;colorMax=Math.max(colorMax,cd);colorSum+=cd;
            }
            double mean=sum/(actual.length*3),colorMean=colorSum/(actual.length*3);
            records.put(new JSONObject().put("number",n+1).put("max_channel_error",max).put("mean_channel_error",mean)
                .put("color_max_channel_error",colorMax).put("color_mean_channel_error",colorMean));
            global=Math.max(global,max);total+=mean;
            ByteBuffer output=ByteBuffer.allocate(8+actual.length*4);output.putInt(w).putInt(h);
            for(int pixel:actual)output.putInt(pixel);
            Files.write(rendered.resolve(String.format(java.util.Locale.ROOT,"%02d.bin",n+1)),output.array());
            assertTrue("color image "+(n+1)+" max="+colorMax+" mean="+colorMean,colorMax<=4&&colorMean<.03);
            assertTrue("grain image "+(n+1)+" max="+max+" mean="+mean,max<=4&&mean<.03);
        }
        Files.writeString(root.resolve("java-parity.json"),new JSONObject().put("photos",40).put("max_channel_error",global)
            .put("mean_per_image_channel_error",total/40).put("records",records).toString(2));
        System.out.println("250D report40 max="+global+" mean="+total/40);
    }
}
