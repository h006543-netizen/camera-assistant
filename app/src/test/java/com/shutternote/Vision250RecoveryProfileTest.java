package com.shutternote;

import org.json.*;
import org.junit.Test;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import static org.junit.Assert.*;

public class Vision250RecoveryProfileTest {
    private JSONObject profile() throws Exception {
        return new JSONObject(Files.readString(Path.of("src/main/assets/film_corrections/latest/vision3-250d-recovery-v1.json")));
    }
    private LearnedFilmProfile renderer(JSONObject p) throws Exception {
        return new LearnedFilmProfile(p.toString(),a->{throw new AssertionError("250D grain must stay disabled");});
    }
    private int[] pixels(JSONArray a) throws JSONException {
        int[] out=new int[a.length()];for(int i=0;i<out.length;i++) {
            JSONArray c=a.getJSONArray(i);out[i]=(i%256<<24)|(c.getInt(0)<<16)|(c.getInt(1)<<8)|c.getInt(2);
        }return out;
    }
    @Test public void actualScenesMatchIndependentPcAndRetainAlphaDeterministically() throws Exception {
        JSONArray cases=new JSONArray(new String(getClass().getResourceAsStream("/vision250_recovery_parity.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        LearnedFilmProfile current=renderer(profile());assertEquals(1024,current.analysisEdge());
        for(int n=0;n<cases.length();n++) {
            JSONObject c=cases.getJSONObject(n);int[] input=pixels(c.getJSONArray("input")),a=input.clone(),b=input.clone();
            current.render(a,c.getInt("width"),c.getInt("height"),()->false);current.render(b,c.getInt("width"),c.getInt("height"),()->false);
            assertArrayEquals(a,b);JSONArray expected=c.getJSONArray("expected");int max=0;
            for(int i=0;i<a.length;i++) {
                assertEquals(input[i]&0xff000000,a[i]&0xff000000);
                for(int j=0;j<3;j++)max=Math.max(max,Math.abs(((a[i]>>>(16-j*8))&255)-expected.getJSONArray(i).getInt(j)));
            }
            assertTrue(c.getString("case")+" parity error "+max,max<=1);
        }
    }
    @Test public void weightsAreBoundedAndVaryContinuouslyWithSourceFeatures() throws Exception {
        JSONObject p=profile();Vision250RecoveryProfile recovery=new Vision250RecoveryProfile(p,a->{throw new AssertionError();});
        Random random=new Random(25029);int[] a=new int[64*64],b=new int[a.length];
        for(int i=0;i<a.length;i++) {int r=32+random.nextInt(180),g=32+random.nextInt(180),blue=32+random.nextInt(180);
            a[i]=0xff000000|(r<<16)|(g<<8)|blue;b[i]=0xff000000|((r+1)<<16)|(g<<8)|blue;}
        double[] first=recovery.weights(a,64,64,()->false),second=recovery.weights(b,64,64,()->false);double sum=0,difference=0;
        for(int i=0;i<first.length;i++){assertTrue(first[i]>=0&&first[i]<=1);sum+=first[i];difference+=Math.abs(first[i]-second[i]);}
        assertTrue(sum<=1+1e-10);assertTrue(difference<.15);
        for(int level:new int[]{0,255}) {int[] source=new int[64*64];Arrays.fill(source,0xff000000|(level<<16)|(level<<8)|level);
            double total=0;for(double w:recovery.weights(source,64,64,()->false))total+=w;assertTrue(total<=1+1e-10);}
    }
    @Test public void directReferencePreservesBlackWhiteAndMonotoneGrayWithoutPublishingOnCancel() throws Exception {
        JSONObject p=profile(),q=p.getJSONObject("recovery");
        int[] analysis=new int[64*64];Arrays.fill(analysis,0xff808080);
        double[] d=E100dColorProfile.descriptor(analysis,64,64);
        JSONObject ref=new JSONObject(q.getJSONArray("references").getJSONObject(2).toString());
        ref.put("descriptor",new JSONArray(d));q.put("references",new JSONArray().put(ref));
        LearnedFilmProfile r=renderer(p);int[] gray=new int[256];for(int i=0;i<256;i++)gray[i]=0xff000000|(i<<16)|(i<<8)|i;
        int[] source=gray.clone();r.render(gray,256,1,()->false,analysis,64,64);
        assertEquals(0xff000000,gray[0]);assertEquals(0xffffffff,gray[255]);
        for(int i=1;i<256;i++)for(int c=0;c<3;c++)assertTrue(((gray[i]>>>(16-c*8))&255)>=((gray[i-1]>>>(16-c*8))&255));
        int[] checks={0};assertThrows(CancellationException.class,()->r.render(source,256,1,()->++checks[0]>4,analysis,64,64));
        for(int i=0;i<source.length;i++)assertEquals(0xff000000|(i<<16)|(i<<8)|i,source[i]);
        assertThrows(IllegalArgumentException.class,()->r.render(source,255,1,()->false));
        assertThrows(IllegalArgumentException.class,()->r.render(source,256,1,()->false,new int[1],2,2));
    }
    @Test public void outsideSupportRemainsPixelIdenticalAndOldNotesRemainRestorable() throws Exception {
        JSONObject p=profile();LearnedFilmProfile current=renderer(p),old=renderer(p.getJSONObject("base_profile"));
        int[] source=new int[64*64];Arrays.fill(source,0xff000000);int[] a=source.clone(),b=source.clone();
        current.render(a,64,64,()->false);old.render(b,64,64,()->false);assertArrayEquals(b,a);
        assertTrue(FilmCorrectionProfiles.supported(FilmCorrectionProfiles.OLD_250D));
        assertEquals("vision3-250d",FilmCorrectionProfiles.key(FilmCorrectionProfiles.OLD_250D));
        assertTrue(FilmCorrectionProfiles.upgradeOnReselect(FilmCorrectionProfiles.OLD_250D));
        String latest=FilmCorrectionProfiles.latest("vision3-250d");assertFalse(FilmCorrectionProfiles.upgradeOnReselect(latest));
        assertEquals(112,FilmCorrectionProfiles.workingBytes(latest));assertEquals(80,FilmCorrectionProfiles.workingBytes(FilmCorrectionProfiles.OLD_250D));
        for(String id:new String[]{FilmCorrectionProfiles.OLD_250D,latest}) {
            FilmFrameRecord f=new FilmFrameRecord();f.number=1;f.photo="original.jpg";f.correctionPhoto="corrected.jpg";f.correctionEnabled=true;f.correctionProfile=id;
            FilmFrameRecord restored=FilmFrameRecord.fromJson(f.toJson());assertEquals(id,restored.correctionProfile);
            assertEquals("original.jpg",restored.photo);assertEquals("corrected.jpg",restored.displayPhotoName());
        }
        JSONObject portra=new JSONObject(Files.readString(Path.of("src/main/assets/film_corrections/latest/portra400.json")));
        assertEquals(p.getJSONObject("base_profile").toString(),portra.getJSONObject("base_profile").toString());
    }
    @Test public void corruptedNonmonotoneAndRecursiveProfilesAreRejected() throws Exception {
        JSONObject p=profile();p.getJSONObject("recovery").put("radius",0);assertThrows(JSONException.class,()->renderer(p));
        JSONObject curve=profile();JSONArray c=curve.getJSONObject("recovery").getJSONArray("references").getJSONObject(2).getJSONArray("curves");
        c.getJSONArray(128).put(0,-.1);assertThrows(JSONException.class,()->renderer(curve));
        JSONObject nested=profile();nested.getJSONObject("base_profile").put("schema_version",32);assertThrows(JSONException.class,()->renderer(nested));
    }
}
