package com.shutternote;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.widget.ImageView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;

/** Disposable emulator only: creates and removes one UUID note, never edits existing notes. */
@RunWith(AndroidJUnit4.class)
public class FilmRecipeNoteUiTest {
    private Context context;
    private FilmRollStore store;
    private String id;
    private int originalPixel,correctedPixel;
    private void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    @Before public void setup() throws Exception {
        assumeTrue(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"));
        context=ApplicationProvider.getApplicationContext(); store=new FilmRollStore(context);
        FilmRollRecord roll=new FilmRollRecord(); id="recipe-ui-"+UUID.randomUUID(); roll.id=id;
        assertTrue(store.save(roll));
        FilmFrameRecord frame=new FilmFrameRecord(); frame.number=1; frame.captureId=id;
        frame.correctionEnabled=true; frame.correctionProfile=FilmColorCorrection.E100D;
        Bitmap input=Bitmap.createBitmap(96,64,Bitmap.Config.ARGB_8888); input.eraseColor(Color.rgb(80,130,190));
        try { assertTrue(store.saveFrame(id,frame,input)); } finally { input.recycle(); }
        frame=store.find(id).frame(1);
        Bitmap raw=BitmapFactory.decodeFile(store.photo(frame).getAbsolutePath());
        Bitmap rendered=BitmapFactory.decodeFile(store.correction(frame).getAbsolutePath());
        try { originalPixel=raw.getPixel(0,0); correctedPixel=rendered.getPixel(0,0); assertNotEquals(originalPixel,correctedPixel); }
        finally { raw.recycle(); rendered.recycle(); }
    }
    private <T extends Activity> T resumed(Class<T> type) {
        long deadline=SystemClock.uptimeMillis()+15000; AtomicReference<T> result=new AtomicReference<>();
        while(SystemClock.uptimeMillis()<deadline) {
            main(()->{
                for(Activity activity:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
                    if(type.isInstance(activity)&&id.equals(activity.getIntent().getStringExtra(FilmRollActivity.EXTRA_ROLL_ID))) result.set(type.cast(activity));
            });
            if(result.get()!=null)return result.get(); SystemClock.sleep(50);
        }
        throw new AssertionError("Expected fixture Activity "+type.getSimpleName());
    }
    private void photo(Activity activity,int viewId,int pixel) {
        long deadline=SystemClock.uptimeMillis()+45000; java.util.concurrent.atomic.AtomicBoolean matched=new java.util.concurrent.atomic.AtomicBoolean();
        while(SystemClock.uptimeMillis()<deadline) {
            main(()->{
                ImageView image=activity.findViewById(viewId);
                if(image!=null&&image.getDrawable() instanceof BitmapDrawable)
                    matched.set(((BitmapDrawable)image.getDrawable()).getBitmap().getPixel(0,0)==pixel);
            });
            if(matched.get())return; SystemClock.sleep(50);
        }
        fail("Selected recipe pixels were not displayed");
    }
    private void choose(FilmFrameDetailActivity detail,int label) {
        main(()->detail.findViewById(R.id.noteCorrectionCategory).performClick());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(300); // Popup window transitions are not Espresso idling resources.
        onView(withText(label)).inRoot(isPlatformPopup()).perform(click());
    }
    @Test public void originalChoiceUpdatesListAndScanAfterBack() throws Exception {
        android.content.Intent intent=new android.content.Intent(context,FilmRollFramesActivity.class)
                .putExtra(FilmRollActivity.EXTRA_ROLL_ID,id).putExtra(FilmFrameCapture.NUMBER,1);
        try(ActivityScenario<FilmRollFramesActivity> frames=ActivityScenario.launch(intent)) {
            FilmRollFramesActivity list=resumed(FilmRollFramesActivity.class);photo(list,R.id.framePhoto,correctedPixel);
            FilmRollFramesActivity ready=list;
            main(()->((RecyclerView)ready.findViewById(R.id.framesPager)).getLayoutManager().findViewByPosition(0).findViewById(R.id.frameTop).performClick());
            FilmFrameDetailActivity detail=resumed(FilmFrameDetailActivity.class);photo(detail,R.id.noteDetailPhoto,correctedPixel);
            java.io.File cached=store.correction(store.find(id).frame(1));
            choose(detail,R.string.correction_original);photo(detail,R.id.noteDetailPhoto,originalPixel);
            main(()->detail.findViewById(R.id.noteDetailBack).performClick());
            photo(resumed(FilmRollFramesActivity.class),R.id.framePhoto,originalPixel);
            FilmFrameRecord frame=store.find(id).frame(1);assertFalse(frame.correctionEnabled);assertTrue(cached.exists());
            try(ActivityScenario<FilmScanComparisonActivity> comparison=ActivityScenario.launch(FilmScanComparisonActivity.intent(context,id,frame,false))) {
                photo(resumed(FilmScanComparisonActivity.class),R.id.scanNoteImage,originalPixel);
            }
            assertTrue(store.photo(frame).isFile());
        }
    }
    @Test public void originalChoiceRetainsPairAndRepeatedSelectionReusesBitmapAcrossRecreation() throws Exception {
        java.io.File cached=store.correction(store.find(id).frame(1));
        android.content.Intent intent=new android.content.Intent(context,FilmFrameDetailActivity.class)
                .putExtra(FilmRollActivity.EXTRA_ROLL_ID,id).putExtra(FilmFrameCapture.NUMBER,1);
        AtomicReference<Bitmap> old=new AtomicReference<>();
        try(ActivityScenario<FilmFrameDetailActivity> scenario=ActivityScenario.launch(intent)) {
            AtomicReference<FilmFrameDetailActivity> activity=new AtomicReference<>();scenario.onActivity(activity::set);
            photo(activity.get(),R.id.noteDetailPhoto,correctedPixel);
            main(()->old.set(((BitmapDrawable)((ImageView)activity.get().findViewById(R.id.noteDetailPhoto)).getDrawable()).getBitmap()));
            choose(activity.get(),R.string.correction_original);photo(activity.get(),R.id.noteDetailPhoto,originalPixel);
            long deadline=SystemClock.uptimeMillis()+15000;
            while(store.find(id).frame(1).correctionEnabled&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(50);
            assertFalse(store.find(id).frame(1).correctionEnabled);assertTrue(cached.exists());
            assertFalse(old.get().isRecycled());
            byte[] saved=java.nio.file.Files.readAllBytes(cached.toPath());long modified=cached.lastModified();
            for(int repeat=0;repeat<3;repeat++) {
                choose(activity.get(),R.string.correction_e100d);photo(activity.get(),R.id.noteDetailPhoto,correctedPixel);
                main(()->assertSame(old.get(),((BitmapDrawable)((ImageView)activity.get().findViewById(R.id.noteDetailPhoto)).getDrawable()).getBitmap()));
                assertEquals(cached,store.correction(store.find(id).frame(1)));assertEquals(modified,cached.lastModified());
                assertArrayEquals(saved,java.nio.file.Files.readAllBytes(cached.toPath()));
                choose(activity.get(),R.string.correction_original);photo(activity.get(),R.id.noteDetailPhoto,originalPixel);
            }
            scenario.recreate();scenario.onActivity(activity::set);photo(activity.get(),R.id.noteDetailPhoto,originalPixel);
            assertTrue(old.get().isRecycled());
            main(()->assertEquals(activity.get().getString(R.string.correction_original),
                    ((android.widget.TextView)activity.get().findViewById(R.id.noteCorrectionCategory)).getText().toString()));
        }
        assertTrue(old.get().isRecycled());assertTrue(cached.exists());
    }
    @After public void cleanup() {
        if(id==null)return;
        main(()->{
            for(Stage stage:Stage.values()) if(stage!=Stage.DESTROYED)
                for(Activity activity:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage))
                    if(id.equals(activity.getIntent().getStringExtra(FilmRollActivity.EXTRA_ROLL_ID))) activity.finish();
        });
        assertTrue(store.delete(Collections.singleton(id)));
    }
}
