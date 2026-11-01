package com.shutternote;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;

/** Generated cache input only; never saves to the gallery or opens user rolls. */
@RunWith(AndroidJUnit4.class)
public class E100dResultUiTest {
    static void awaitReady(ActivityScenario<FilmResultActivity> scenario) {
        long deadline=SystemClock.uptimeMillis()+45000; AtomicBoolean ready=new AtomicBoolean();
        java.util.concurrent.atomic.AtomicReference<String> status=new java.util.concurrent.atomic.AtomicReference<>();
        while(SystemClock.uptimeMillis()<deadline) {
            scenario.onActivity(activity->{
                ready.set(activity.findViewById(R.id.noteCorrectionCategory).isEnabled());
                status.set(((TextView)activity.findViewById(R.id.tvFilmResultStatus)).getText().toString());
            });
            if(ready.get())return;
            SystemClock.sleep(50);
        }
        fail("Result preview did not become ready: "+status.get());
    }
    static void selectCorrection(ActivityScenario<FilmResultActivity> scenario, int label) {
        scenario.onActivity(activity->activity.findViewById(R.id.noteCorrectionCategory).performClick());
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(300); // Popup window transitions are not Espresso idling resources.
        Bitmap popup=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        try(OutputStream out=new FileOutputStream(new File(ApplicationProvider.<Context>getApplicationContext().getExternalCacheDir(),"category-popup.png"))) {
            popup.compress(Bitmap.CompressFormat.PNG,100,out);
        } catch(IOException error) { throw new AssertionError(error); }
        finally { popup.recycle(); }
        onView(withText(label)).inRoot(isPlatformPopup()).perform(click());
        awaitReady(scenario);
    }
    static void assertChoice(FilmResultActivity activity, int label) {
        assertEquals(activity.getString(label), ((TextView)activity.findViewById(R.id.noteCorrectionCategory)).getText().toString());
    }
    @Test public void originalAndE100dToggleAndRecreationKeepSourceAndChoice() throws Exception {
        Context context=ApplicationProvider.getApplicationContext();
        File source=new File(context.getCacheDir(),"e100d-ui-"+UUID.randomUUID()+".png");
        Bitmap image=Bitmap.createBitmap(192,128,Bitmap.Config.ARGB_8888); image.eraseColor(Color.rgb(80,130,190));
        try(OutputStream out=new FileOutputStream(source)) { assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out)); }
        finally { image.recycle(); }
        long length=source.length(), modified=source.lastModified();
        Intent intent=new Intent(context,FilmResultActivity.class).putExtra(FilmResultActivity.EXTRA_IMAGE_PATH,source.getAbsolutePath())
                .putExtra(FilmResultActivity.EXTRA_EXPOSURE_MULTIPLIER,1.0);
        try(ActivityScenario<FilmResultActivity> scenario=ActivityScenario.launch(intent)) {
            awaitReady(scenario);
            scenario.onActivity(activity->{
                assertChoice(activity,R.string.correction_original);
                Bitmap displayed=((android.graphics.drawable.BitmapDrawable)((ImageView)activity.findViewById(R.id.ivFilmResult)).getDrawable()).getBitmap();
                assertEquals("A fresh capture must display the original pixels",Color.rgb(80,130,190),displayed.getPixel(0,0));
                assertCorrectionMemory(activity,false);
                android.view.View back=activity.findViewById(R.id.btnFilmResultBack), category=activity.findViewById(R.id.noteCorrectionCategory);
                android.view.View top=activity.findViewById(R.id.filmResultTopControls), area=activity.findViewById(R.id.filmResultImageArea);
                assertSame(back.getParent(),category.getParent());
                assertEquals(back.getRight(),category.getLeft());
                assertTrue(category.getWidth()<activity.getWindow().getDecorView().getWidth()/2);
                assertTrue(area.getTop()>=top.getBottom());
                assertTrue(area.getHeight()>top.getHeight());
                assertTrue(activity.findViewById(R.id.ivFilmResult).getHeight()>0);
                android.view.View root=activity.getWindow().getDecorView();
                Bitmap preview=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
                root.draw(new android.graphics.Canvas(preview));
                try(OutputStream out=new FileOutputStream(new File(context.getExternalCacheDir(),"e100d-ui-preview.png"))) {
                    preview.compress(Bitmap.CompressFormat.PNG,100,out);
                } catch(IOException error) { throw new AssertionError(error); }
                finally { preview.recycle(); }
                Object correction=((ImageView)activity.findViewById(R.id.ivFilmResult)).getDrawable(); assertNotNull(correction);
            });
            selectCorrection(scenario,R.string.correction_original);
            scenario.onActivity(activity->assertChoice(activity,R.string.correction_original));
            selectCorrection(scenario,R.string.correction_e100d);
            scenario.recreate(); awaitReady(scenario);
            scenario.onActivity(activity->assertChoice(activity,R.string.correction_e100d));
            assertInstantComparison(scenario);
            selectCorrection(scenario,R.string.correction_original);
            scenario.recreate(); awaitReady(scenario);
            scenario.onActivity(activity->{
                assertChoice(activity,R.string.correction_original);
                assertCorrectionMemory(activity,false);
                assertTrue(activity.findViewById(R.id.btnFilmResultSave).isEnabled());
            });
            selectCorrection(scenario,R.string.correction_e100d);
            scenario.onActivity(activity->assertChoice(activity,R.string.correction_e100d));
            assertEquals(length,source.length()); assertEquals(modified,source.lastModified());
        } finally { assertTrue(source.delete()); }
    }
    static void assertInstantComparison(ActivityScenario<FilmResultActivity> scenario) {
        java.util.concurrent.atomic.AtomicReference<Bitmap> correction=new java.util.concurrent.atomic.AtomicReference<>();
        scenario.onActivity(activity->correction.set(displayed(activity)));
        selectCorrection(scenario,R.string.correction_original);
        scenario.onActivity(activity->{
            assertFalse(correction.get().isRecycled());
            assertNotSame(correction.get(),displayed(activity));
            assertEquals(android.view.View.GONE, activity.findViewById(R.id.progressFilmProcessing).getVisibility());
        });
        selectCorrection(scenario,R.string.correction_e100d);
        scenario.onActivity(activity->assertSame("Comparison must reuse the decoded bitmap",correction.get(),displayed(activity)));
        scenario.onActivity(activity->{
            try {
                java.lang.reflect.Method select=FilmResultActivity.class.getDeclaredMethod("selectCorrection",boolean.class);
                select.setAccessible(true);
                for(int i=0;i<20;i++) {
                    select.invoke(activity,false);
                    assertNotSame(correction.get(),displayed(activity));
                    select.invoke(activity,true);
                    assertSame(correction.get(),displayed(activity));
                    assertEquals(android.view.View.GONE,activity.findViewById(R.id.progressFilmProcessing).getVisibility());
                }
            } catch(ReflectiveOperationException error) { throw new AssertionError(error); }
        });
    }
    private static Bitmap displayed(FilmResultActivity activity) {
        return ((android.graphics.drawable.BitmapDrawable)((ImageView)activity.findViewById(R.id.ivFilmResult)).getDrawable()).getBitmap();
    }
    private static void assertCorrectionMemory(FilmResultActivity activity,boolean present) {
        try {
            java.lang.reflect.Field field=FilmResultActivity.class.getDeclaredField("correctedBitmap"); field.setAccessible(true);
            assertEquals("Only a selected correction may retain its preview",present,field.get(activity)!=null);
        } catch(ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}
