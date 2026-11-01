package com.shutternote;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class E100dApprovedModelTest {
    @Test public void installedAssetMatchesApprovedPcOutputAndPreservesSource() throws Exception {
        Context app=ApplicationProvider.getApplicationContext();
        Context tests=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getContext();
        byte[] bytes;
        try(InputStream in=tests.getAssets().open("e100d-approved-parity.json");ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);bytes=out.toByteArray();
        }
        JSONArray cases=new JSONArray(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
        for(int n=0;n<cases.length();n++) {
            JSONObject c=cases.getJSONObject(n);JSONArray input=c.getJSONArray("input"),expected=c.getJSONArray("expected");
            int w=c.getInt("width"),h=c.getInt("height");int[] raw=new int[input.length()];for(int i=0;i<raw.length;i++)raw[i]=0xff000000|input.getInt(i);
            Bitmap source=Bitmap.createBitmap(raw,w,h,Bitmap.Config.ARGB_8888);
            Bitmap corrected=FilmColorCorrection.apply(app,source,FilmCorrectionProfiles.latest("e100d"),()->false);
            try {
                int[] result=new int[raw.length],unchanged=new int[raw.length];corrected.getPixels(result,0,w,0,0,w,h);source.getPixels(unchanged,0,w,0,0,w,h);
                assertArrayEquals(raw,unchanged);int max=0;
                for(int i=0;i<raw.length;i++)for(int channel=0;channel<3;channel++)max=Math.max(max,Math.abs(((result[i]>>>(16-8*channel))&255)-((expected.getInt(i)>>>(16-8*channel))&255)));
                assertTrue(c.getString("name")+" max="+max,max<=1);
            } finally {source.recycle();corrected.recycle();}
        }
    }
    @Test public void previousPinnedE100dStillRendersForSavedNotes() throws Exception {
        Context app=ApplicationProvider.getApplicationContext();Bitmap source=Bitmap.createBitmap(32,24,Bitmap.Config.ARGB_8888);source.eraseColor(0xff688ba4);
        Bitmap old=FilmColorCorrection.apply(app,source,FilmCorrectionProfiles.OLD_E100D,()->false);
        try {assertEquals(32,old.getWidth());assertEquals("e100d",FilmCorrectionProfiles.key(FilmCorrectionProfiles.OLD_E100D));}
        finally {source.recycle();old.recycle();}
    }
}
