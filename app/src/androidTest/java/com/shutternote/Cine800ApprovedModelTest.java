package com.shutternote;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.CancellationException;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class Cine800ApprovedModelTest {
    private String read(Context app,String path) throws Exception {try(InputStream in=app.getAssets().open(path);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);}}
    @Test public void installedSceneAssetAndBothBrightnessModesAreAvailable() throws Exception {
        Context app=ApplicationProvider.getApplicationContext();JSONObject profile=new JSONObject(read(app,"film_corrections/latest/cine800t.json")),p=profile.getJSONObject("effects").getJSONObject("cine800t_scene");
        assertEquals(27,profile.getInt("schema_version"));assertEquals(150,p.getJSONObject("day_parameters").getDouble("tungsten"),0);assertEquals(300,p.getJSONObject("night_parameters").getDouble("tungsten"),0);
        Cine800SceneProfile model=new Cine800SceneProfile(profile,asset->{throw new IOException("Unexpected noise for brightness analysis");});
        int[] black=new int[32*24],white=new int[black.length];java.util.Arrays.fill(black,0xff000000);java.util.Arrays.fill(white,0xffffffff);
        assertEquals(0,model.dayWeight(black,32,24,()->false),0);assertEquals(1,model.dayWeight(white,32,24,()->false),0);
    }
    @Test public void installedRenderIsDeterministicPreservesOriginalAndHonoursCancellation() throws Exception {
        Context app=ApplicationProvider.getApplicationContext();int[] input=new int[64*48];for(int y=0;y<48;y++)for(int x=0;x<64;x++)input[y*64+x]=0xff000000|((20+x)<<16)|((30+y)<<8)|45;
        Bitmap source=Bitmap.createBitmap(input,64,48,Bitmap.Config.ARGB_8888);String id=FilmCorrectionProfiles.latest("cine800t");
        Bitmap a=FilmColorCorrection.apply(app,source,id,()->false),b=FilmColorCorrection.apply(app,source,id,()->false);
        try{int[] unchanged=new int[input.length],first=new int[input.length],second=new int[input.length];source.getPixels(unchanged,0,64,0,0,64,48);a.getPixels(first,0,64,0,0,64,48);b.getPixels(second,0,64,0,0,64,48);assertArrayEquals(input,unchanged);assertArrayEquals(first,second);assertFalse(java.util.Arrays.equals(input,first));
            assertThrows(CancellationException.class,()->FilmColorCorrection.apply(app,source,id,()->true));
        }finally{a.recycle();b.recycle();source.recycle();}
    }
    @Test public void previous800tRecipeStillRendersSavedNotes() throws Exception {
        Context app=ApplicationProvider.getApplicationContext();Bitmap source=Bitmap.createBitmap(32,24,Bitmap.Config.ARGB_8888);source.eraseColor(0xff445566);
        Bitmap corrected=FilmColorCorrection.apply(app,source,FilmCorrectionProfiles.OLD_800T,()->false);
        try{assertTrue(FilmCorrectionProfiles.supported(FilmCorrectionProfiles.OLD_800T));assertEquals("cine800t",FilmCorrectionProfiles.key(FilmCorrectionProfiles.OLD_800T));assertEquals(32,corrected.getWidth());}finally{corrected.recycle();}
        corrected=FilmColorCorrection.apply(app,source,FilmCorrectionProfiles.OLD_800T_SCENE,()->false);
        try{assertEquals(32,corrected.getWidth());assertEquals("cine800t",FilmCorrectionProfiles.key(FilmCorrectionProfiles.OLD_800T_SCENE));}finally{source.recycle();corrected.recycle();}
    }
    @Test public void realFileDecodeAnd512PixelNoteRenderKeepLampHalation() throws Exception {
        org.junit.Assume.assumeTrue("Private lamp photograph is local only",java.util.Arrays.asList(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getContext().getAssets().list("")).contains("cine800t-lamp-source.png"));
        Context app=ApplicationProvider.getApplicationContext(),tests=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getContext();File file=new File(app.getCacheDir(),"mobile-halo-"+java.util.UUID.randomUUID()+".png");
        try(InputStream in=tests.getAssets().open("cine800t-lamp-source.png");OutputStream out=new FileOutputStream(file)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
        byte[] before=java.nio.file.Files.readAllBytes(file.toPath());Bitmap previous=null,current=null;
        try {
            previous=FilmRecipeImages.read(app,file,512,0,FilmCorrectionProfiles.OLD_800T_GUARD,()->false);
            current=FilmRecipeImages.read(app,file,512,0,FilmCorrectionProfiles.latest("cine800t"),()->false);
            assertTrue(Math.max(current.getWidth(),current.getHeight())<=512);
            int[] oldPixels=new int[previous.getWidth()*previous.getHeight()],newPixels=new int[current.getWidth()*current.getHeight()];previous.getPixels(oldPixels,0,previous.getWidth(),0,0,previous.getWidth(),previous.getHeight());current.getPixels(newPixels,0,current.getWidth(),0,0,current.getWidth(),current.getHeight());
            Bitmap analysis=FilmColorCorrection.analysisSource(app,file,FilmCorrectionProfiles.latest("cine800t"),()->false);assertNotNull(analysis);int aw=analysis.getWidth(),ah=analysis.getHeight();assertEquals(1024,Math.max(aw,ah));int[] raw=new int[aw*ah];analysis.getPixels(raw,0,aw,0,0,aw,ah);analysis.recycle();
            JSONObject p=new JSONObject(read(app,"film_corrections/latest/cine800t.json")).getJSONObject("effects").getJSONObject("cine800t_scene").getJSONObject("source_profile");
            Cine800Lights.Fields f=Cine800Analysis.resize(Cine800Lights.paired(raw,aw,ah,p,55,()->false),aw,ah,current.getWidth(),current.getHeight(),()->false);double increase=0;int active=0;
            for(int y=0;y<current.getHeight();y++)for(int x=0;x<current.getWidth();x++){int i=y*current.getWidth()+x;if(f.red[i]<.05)continue;int xx=Math.min(previous.getWidth()-1,x*previous.getWidth()/current.getWidth()),yy=Math.min(previous.getHeight()-1,y*previous.getHeight()/current.getHeight());increase+=((newPixels[i]>>>16)&255)-((oldPixels[yy*previous.getWidth()+xx]>>>16)&255);active++;}
            assertTrue("actual reduced note has supported lamp pixels",active>0);assertTrue("halation remains visibly red after decode/reduction",increase/active>10);assertArrayEquals(before,java.nio.file.Files.readAllBytes(file.toPath()));
        }finally{if(previous!=null)previous.recycle();if(current!=null)current.recycle();file.delete();}
    }
}
