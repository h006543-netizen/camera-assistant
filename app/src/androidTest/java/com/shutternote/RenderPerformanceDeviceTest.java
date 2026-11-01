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
    @Test public void exactPixelsAndConservativePerformance() throws Exception {
        org.junit.Assume.assumeTrue("Private performance fixtures are local only",java.util.Arrays.asList(InstrumentationRegistry.getInstrumentation().getContext().getAssets().list("render-speed")).contains("8.bin.fixture"));
        RenderPerformanceCases.geometry();RenderPerformanceCases.filters();Context app=ApplicationProvider.getApplicationContext(),test=InstrumentationRegistry.getInstrumentation().getContext();
        org.json.JSONObject result=RenderPerformanceCases.run(name->app.getAssets().open(name),name->test.getAssets().open(name.replace(".gz",".fixture")),3);
        try(FileOutputStream out=new FileOutputStream(new File(app.getFilesDir(),"render-speed.json"))){out.write(result.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    }
}
