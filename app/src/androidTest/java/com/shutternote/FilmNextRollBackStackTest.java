package com.shutternote;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Emulator-only integration fixtures. Run on a read-only AVD; delete only UUID test rolls. */
@RunWith(AndroidJUnit4.class)
public class FilmNextRollBackStackTest {
    private Context context;
    private FilmRollStore store;
    private final Set<String> ids=new HashSet<>();
    private String a,b,c;
    private void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }

    @Before public void setup() {
        assumeTrue("Use a disposable emulator",Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"));
        context=ApplicationProvider.getApplicationContext(); store=new FilmRollStore(context);
        a=create("A",1); b=create("B",7); c=create("C",13); // Saved list is C, B, A.
        addFrame(b,21); addFrame(c,29); // Default capture positions differ from the opened cuts.
    }
    private String create(String name,int cut) {
        FilmRollRecord roll=new FilmRollRecord(); roll.id="next-roll-test-"+UUID.randomUUID();
        roll.name="Navigation fixture "+name; ids.add(roll.id); assertTrue(store.save(roll));
        addFrame(roll.id,cut);
        return roll.id;
    }
    private void addFrame(String id,int cut) {
        FilmFrameRecord frame=new FilmFrameRecord(); frame.number=cut; frame.captureId=id+"-shot-"+cut;
        frame.iso="100"; frame.aperture="f/2.8"; frame.shutter="1/125";
        Bitmap image=Bitmap.createBitmap(96,64,Bitmap.Config.ARGB_8888); image.eraseColor(Color.GRAY);
        try { assertTrue(store.saveFrame(id,frame,image)); } finally { image.recycle(); }
    }
    private <T extends Activity> T await(Class<T> type,String rollId) {
        long deadline=SystemClock.uptimeMillis()+15000;
        AtomicReference<T> result=new AtomicReference<>();
        while(SystemClock.uptimeMillis()<deadline) {
            main(()->{
                for(Activity activity:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
                    if(type.isInstance(activity)&&rollId.equals(activity.getIntent().getStringExtra(FilmRollActivity.EXTRA_ROLL_ID)))result.set(type.cast(activity));
            });
            if(result.get()!=null) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync(); return result.get();
            }
            SystemClock.sleep(50);
        }
        throw new AssertionError("Expected "+type.getSimpleName()+" for test roll");
    }
    private FilmFrameDetailActivity open(FilmRollFramesActivity frames,String rollId,int cut) {
        main(()->((LinearLayoutManager)((RecyclerView)frames.findViewById(R.id.framesPager)).getLayoutManager())
                .scrollToPositionWithOffset((cut-1)/2,0));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        main(()->{
            RecyclerView pager=frames.findViewById(R.id.framesPager);
            View column=pager.getLayoutManager().findViewByPosition((cut-1)/2);
            assertNotNull(column);
            column.findViewById(cut%2==1?R.id.frameTop:R.id.frameBottom).performClick();
        });
        return await(FilmFrameDetailActivity.class,rollId);
    }
    private void assertPair(FilmRollFramesActivity frames,int cut) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        main(()->{
            RecyclerView pager=frames.findViewById(R.id.framesPager);
            View column=pager.getLayoutManager().findViewByPosition((cut-1)/2);
            assertNotNull("Viewed cut must be visible",column);
            assertEquals("Viewed column must be snapped",pager.getPaddingLeft(),column.getLeft());
        });
    }
    private void assertPreviousPhotoPeek(FilmRollFramesActivity frames,int cut) {
        main(()->{
            RecyclerView pager=frames.findViewById(R.id.framesPager);
            View previous=pager.getLayoutManager().findViewByPosition((cut-1)/2-1);
            assertNotNull(previous);
            View photo=previous.findViewById(R.id.framePhoto);
            android.graphics.Rect visible=new android.graphics.Rect();
            assertTrue(photo.getGlobalVisibleRect(visible));
            assertEquals(pager.getWidth()*.10f,visible.width(),2f);
        });
    }
    private FilmFrameDetailActivity next(FilmFrameDetailActivity detail,String target) {
        AtomicReference<View> button=new AtomicReference<>(); long deadline=SystemClock.uptimeMillis()+5000;
        while(button.get()==null&&SystemClock.uptimeMillis()<deadline) {
            main(()->{
                ((RecyclerView)detail.findViewById(R.id.noteDetailPager)).scrollBy(10000,0);
                View candidate=detail.findViewById(R.id.noteNextRoll);
                if(candidate!=null&&candidate.isShown())button.set(candidate);
            });
            SystemClock.sleep(50);
        }
        assertNotNull("Next film button was not revealed",button.get());
        main(()->button.get().performClick());
        return await(FilmFrameDetailActivity.class,target);
    }
    private void noPreviousRollInHistory(String current) {
        AtomicReference<String> previous=new AtomicReference<>(); long deadline=SystemClock.uptimeMillis()+5000;
        do {
            main(()->{
                previous.set(null);
                for(Stage stage:Stage.values()) if(stage!=Stage.DESTROYED)
                    for(Activity activity:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage)) {
                        if(activity.isFinishing())continue;
                        if(activity instanceof FilmRollFramesActivity||activity instanceof FilmFrameDetailActivity) {
                            String roll=activity.getIntent().getStringExtra(FilmRollActivity.EXTRA_ROLL_ID);
                            if(ids.contains(roll)&&!current.equals(roll))previous.set(activity.getClass().getSimpleName()+" "+stage+" "+roll);
                        }
                    }
            });
            if(previous.get()==null)return;
            SystemClock.sleep(50); // Destruction can follow the new Activity's RESUMED callback.
        } while(SystemClock.uptimeMillis()<deadline);
        fail("Previous film remained in the activity history: "+previous.get());
    }

    @Test public void toolbarBackAfterNextOpensTheNewFilmsList() {
        try(ActivityScenario<FilmRollFramesActivity> scenario=ActivityScenario.launch(new Intent(context,FilmRollFramesActivity.class)
                .putExtra(FilmRollActivity.EXTRA_ROLL_ID,a))) {
            FilmRollFramesActivity original=await(FilmRollFramesActivity.class,a);
            FilmFrameDetailActivity detail=next(open(original,a,1),b);
            assertEquals(7,detail.getIntent().getIntExtra(FilmFrameCapture.NUMBER,0));
            main(()->detail.findViewById(R.id.noteDetailBack).performClick());
            FilmRollFramesActivity destination=await(FilmRollFramesActivity.class,b);
            assertNotSame(original,destination); noPreviousRollInHistory(b);
            assertPair(destination,7); assertPreviousPhotoPeek(destination,7);
            main(()->destination.findViewById(R.id.framesFirst).performClick());
            SystemClock.sleep(600); assertPair(destination,1);
            main(()->{
                FilmFrameScrubber bar=destination.findViewById(R.id.framesScrubber);
                android.os.Bundle arguments=new android.os.Bundle();
                arguments.putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,bar.getMax());
                assertTrue(bar.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),arguments));
            });
            SystemClock.sleep(500); assertPair(destination,35); assertPreviousPhotoPeek(destination,35);
        }
    }

    @Test public void repeatedNextAndPhotoOnlySystemBackReturnToLatestFilmsList() {
        try(ActivityScenario<FilmRollFramesActivity> scenario=ActivityScenario.launch(new Intent(context,FilmRollFramesActivity.class)
                .putExtra(FilmRollActivity.EXTRA_ROLL_ID,a))) {
            FilmFrameDetailActivity first=open(await(FilmRollFramesActivity.class,a),a,1);
            main(()->first.findViewById(R.id.noteDetailPhoto).performClick());
            FilmFrameDetailActivity latest=next(next(first,b),c);
            main(()->{
                assertEquals(View.GONE,latest.findViewById(R.id.noteDetailHeader).getVisibility());
                latest.getOnBackPressedDispatcher().onBackPressed();
                assertEquals(View.VISIBLE,latest.findViewById(R.id.noteDetailHeader).getVisibility());
            });
            noPreviousRollInHistory(c);
            main(()->latest.getOnBackPressedDispatcher().onBackPressed());
            FilmRollFramesActivity destination=await(FilmRollFramesActivity.class,c); noPreviousRollInHistory(c);
            assertPair(destination,13);
        }
    }

    @Test public void changedCutAndRecreationReturnToViewedColumn() {
        try(ActivityScenario<FilmRollFramesActivity> scenario=ActivityScenario.launch(new Intent(context,FilmRollFramesActivity.class)
                .putExtra(FilmRollActivity.EXTRA_ROLL_ID,b))) {
            FilmFrameDetailActivity detail=open(await(FilmRollFramesActivity.class,b),b,7);
            main(()->((LinearLayoutManager)((RecyclerView)detail.findViewById(R.id.noteDetailPager)).getLayoutManager())
                    .scrollToPositionWithOffset(1,0));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            // Real scrolling must report the current cut, rather than only the initial intent cut.
            main(()->((RecyclerView)detail.findViewById(R.id.noteDetailPager)).smoothScrollBy(1,0));
            SystemClock.sleep(500);
            main(detail::recreate);
            SystemClock.sleep(500);
            FilmFrameDetailActivity recreated=await(FilmFrameDetailActivity.class,b);
            assertNotSame(detail,recreated);
            main(()->recreated.getOnBackPressedDispatcher().onBackPressed());
            FilmRollFramesActivity frames=await(FilmRollFramesActivity.class,b);
            assertPair(frames,21); assertPreviousPhotoPeek(frames,21);
            scenario.recreate();
            assertPair(await(FilmRollFramesActivity.class,b),21);
        }
    }

    @Test public void changedCutInNextFilmReturnsToThatCut() {
        try(ActivityScenario<FilmRollFramesActivity> scenario=ActivityScenario.launch(new Intent(context,FilmRollFramesActivity.class)
                .putExtra(FilmRollActivity.EXTRA_ROLL_ID,a))) {
            FilmFrameDetailActivity detail=next(open(await(FilmRollFramesActivity.class,a),a,1),b);
            main(()->((RecyclerView)detail.findViewById(R.id.noteDetailPager)).smoothScrollToPosition(1));
            SystemClock.sleep(700);
            main(()->detail.findViewById(R.id.noteDetailBack).performClick());
            assertPair(await(FilmRollFramesActivity.class,b),21);
        }
    }

    @After public void cleanup() {
        if(context==null)return;
        main(()->{
            Set<Activity> owned=Collections.newSetFromMap(new IdentityHashMap<>());
            for(Stage stage:Stage.values()) if(stage!=Stage.DESTROYED)
                for(Activity activity:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage))
                    if(ids.contains(activity.getIntent().getStringExtra(FilmRollActivity.EXTRA_ROLL_ID)))owned.add(activity);
            for(Activity activity:owned)activity.finish();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertTrue(store.delete(ids));
    }
}
