package com.shutternote;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import android.content.Context;
import java.io.*;
@RunWith(AndroidJUnit4.class)
public class RenderPerformanceDeviceTest {
    @Test public void parallelFiltersAndManualColorKeepExactPixelsAndCancelSafely() throws Exception {
        RenderPerformanceCases.filters();
        Context context=ApplicationProvider.getApplicationContext();
        String json=new String(RenderPerformanceCases.bytes(context.getAssets().open("film_corrections/latest/e100d.json")),java.nio.charset.StandardCharsets.UTF_8);
        int[] original=new int[256*128];java.util.Random random=new java.util.Random(4127);
        for(int i=0;i<original.length;i++)original[i]=random.nextInt();
        int[] before=original.clone(),after=original.clone();
        new RenderBaselineE100dManualProfile(new org.json.JSONObject(json)).render(before,256,128,()->false);
        new E100dManualProfile(new org.json.JSONObject(json)).render(after,256,128,()->false);
        org.junit.Assert.assertArrayEquals(before,after);
        float[] source=new float[513*257],saved=source.clone();
        java.util.concurrent.atomic.AtomicInteger checks=new java.util.concurrent.atomic.AtomicInteger();
        try {
            FilmRenderMath.filter(source,513,257,FilmRenderMath.gaussian(8),new float[source.length],new float[source.length],()->checks.incrementAndGet()>8);
            org.junit.Assert.fail("Expected cancellation within parallel filtering");
        } catch(java.util.concurrent.CancellationException expected) { }
        org.junit.Assert.assertArrayEquals(saved,source,0);
    }
    @Test public void exactPixelsAndConservativePerformance() throws Exception {
        org.junit.Assume.assumeTrue("Private performance fixtures are local only",java.util.Arrays.asList(InstrumentationRegistry.getInstrumentation().getContext().getAssets().list("render-speed")).contains("8.bin.fixture"));
        RenderPerformanceCases.geometry();RenderPerformanceCases.filters();Context app=ApplicationProvider.getApplicationContext(),test=InstrumentationRegistry.getInstrumentation().getContext();
        org.json.JSONObject result=RenderPerformanceCases.run(name->app.getAssets().open(name),name->test.getAssets().open(name.replace(".gz",".fixture")),3);
        try(FileOutputStream out=new FileOutputStream(new File(app.getFilesDir(),"render-speed.json"))){out.write(result.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    }
}
