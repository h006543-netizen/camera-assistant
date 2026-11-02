package com.shutternote;

import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Explicit synthetic fixtures and a bounded worker latch make navigation races repeatable. */
@RunWith(AndroidJUnit4.class)
public class FilmInteractionRegressionTest {
    private Context context; private FilmRollStore store; private String id;
    @Before public void setup() {
        assumeTrue(Build.MODEL.contains("sdk"));
        context=ApplicationProvider.getApplicationContext();store=new FilmRollStore(context);
        FilmRollRecord roll=new FilmRollRecord();id=roll.id="interaction-"+UUID.randomUUID();assertTrue(store.save(roll));
        Bitmap bitmap=Bitmap.createBitmap(160,96,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.rgb(70,130,180));
        try {for(int cut=1;cut<=2;cut++) {FilmFrameRecord frame=new FilmFrameRecord();frame.number=cut;frame.captureId=id+cut;assertTrue(store.saveFrame(id,frame,bitmap));}}
        finally {bitmap.recycle();}
    }
    private Intent intent() {return new Intent(context,FilmFrameDetailActivity.class).putExtra(FilmRollActivity.EXTRA_ROLL_ID,id).putExtra(FilmFrameCapture.NUMBER,1);}
    private void ready(ActivityScenario<FilmFrameDetailActivity> scenario) {
        AtomicBoolean ready=new AtomicBoolean();long end=SystemClock.uptimeMillis()+20000;
        while(!ready.get()&&SystemClock.uptimeMillis()<end) {scenario.onActivity(a->ready.set(a.findViewById(R.id.noteCorrectionCategory).isEnabled()));SystemClock.sleep(50);}
        assertTrue("Detail did not become ready",ready.get());
    }
    @Test public void backDismissesVisibleDeleteControlBeforeLeavingDetail() {
        try(ActivityScenario<FilmFrameDetailActivity> scenario=ActivityScenario.launch(intent())) {
            ready(scenario);
            scenario.onActivity(a->{
                a.findViewById(R.id.noteDetailPhoto).performLongClick();assertEquals(View.VISIBLE,a.findViewById(R.id.frameDelete).getVisibility());
                a.getOnBackPressedDispatcher().onBackPressed();
                assertFalse("Back must dismiss deletion before leaving",a.isFinishing());
                assertEquals(View.GONE,a.findViewById(R.id.frameDelete).getVisibility());
            });
        }
    }
    private void busyBack(boolean alsoCorrect) throws Exception {
        CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1);
        try(ActivityScenario<FilmFrameDetailActivity> scenario=ActivityScenario.launch(intent())) {
            ready(scenario);
            FilmFrameRotationViewModel[] rotation={null};
            scenario.onActivity(a->rotation[0]=new ViewModelProvider(a).get(FilmFrameRotationViewModel.class));
            java.lang.reflect.Field field=FilmFrameRotationViewModel.class.getDeclaredField("worker");field.setAccessible(true);
            ExecutorService worker=(ExecutorService)field.get(rotation[0]);
            worker.execute(()->{locked.countDown();try{release.await(30,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}});
            assertTrue(locked.await(5,TimeUnit.SECONDS));
            try {
                scenario.onActivity(a->{
                    a.findViewById(R.id.noteRotate).performClick();
                    if(alsoCorrect)try {
                        java.lang.reflect.Method choose=FilmFrameDetailActivity.class.getDeclaredMethod("selectCorrection",int.class,String.class);
                        choose.setAccessible(true);choose.invoke(a,1,"vision3-50d");
                    }catch(ReflectiveOperationException error){throw new AssertionError(error);}
                    a.getOnBackPressedDispatcher().onBackPressed();
                    assertFalse("Exit must wait for all in-flight edits",a.isFinishing());
                });
                if(alsoCorrect) {
                    long end=SystemClock.uptimeMillis()+20000;
                    while(!store.find(id).frame(1).correctionEnabled&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50);
                    assertTrue(store.find(id).frame(1).correctionEnabled);
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                    scenario.onActivity(a->{assertEquals(FilmFrameRotationViewModel.State.SAVING,rotation[0].state.getValue());assertFalse("Correction completion cannot skip pending rotation",a.isFinishing());});
                    scenario.recreate();
                    scenario.onActivity(a->assertFalse("Recreation must preserve the pending exit",a.isFinishing()));
                }
            } finally {release.countDown();}
            long end=SystemClock.uptimeMillis()+20000;
            while(SystemClock.uptimeMillis()<end&&store.find(id).frame(1).rotation!=270)SystemClock.sleep(50);
        } finally {release.countDown();}
        FilmFrameRecord frame=store.find(id).frame(1);assertEquals(270,frame.rotation);
        if(alsoCorrect) {
            assertEquals(FilmCorrectionProfiles.IDS[0],frame.selectedProfile());
            long end=SystemClock.uptimeMillis()+20000;
            while(store.find(id).frame(1).pendingCorrection()&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50);
            assertFalse(store.find(id).frame(1).pendingCorrection());
            assertEquals(FilmCorrectionProfiles.IDS[0],store.find(id).frame(1).correctionProfile);
        }
        try(ActivityScenario<FilmFrameDetailActivity> reopened=ActivityScenario.launch(intent())) {ready(reopened);assertEquals(270,store.find(id).frame(1).rotation);}
    }
    @Test public void rotationThenBackWaitsForCommit() throws Exception {busyBack(false);}
    @Test public void simultaneousRotationAndCorrectionThenBackWaitForBoth() throws Exception {busyBack(true);}
    @Test public void scanPairStaysDisabledWhileLoadingAndWithoutLinkedScan() throws Exception {
        FilmFrameRecord frame=store.find(id).frame(1);
        CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1);
        Thread blocker=new Thread(()->{synchronized(FilmRollStore.LOCK){locked.countDown();try{release.await(20,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}}});
        blocker.start();assertTrue(locked.await(5,TimeUnit.SECONDS));
        try(ActivityScenario<FilmScanComparisonActivity> scenario=ActivityScenario.launch(FilmScanComparisonActivity.intent(context,id,frame,false))) {
            try {
                scenario.onActivity(a->{
                    assertEquals(FilmScanViewModel.State.LOADING,new ViewModelProvider(a).get(FilmScanViewModel.class).state.getValue());
                    assertFalse(a.findViewById(R.id.scanNoteImage).isEnabled());assertFalse(a.findViewById(R.id.scanFilmImage).isEnabled());
                });
            } finally {release.countDown();blocker.join(5000);}
            AtomicBoolean loaded=new AtomicBoolean();long end=SystemClock.uptimeMillis()+20000;
            while(!loaded.get()&&SystemClock.uptimeMillis()<end) {scenario.onActivity(a->loaded.set(new ViewModelProvider(a).get(FilmScanViewModel.class).state.getValue()==FilmScanViewModel.State.READY));SystemClock.sleep(50);}
            assertTrue(loaded.get());
            scenario.onActivity(a->{assertFalse(a.findViewById(R.id.scanNoteImage).isEnabled());assertFalse(a.findViewById(R.id.scanFilmImage).isEnabled());});
        } finally {release.countDown();if(blocker.isAlive())blocker.join(5000);}
    }
    private EditText input(View view) {
        if(view instanceof EditText)return (EditText)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){EditText found=input(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}
        return null;
    }
    private TextView text(View view,String value) {
        if(view instanceof TextView&&value.contentEquals(((TextView)view).getText()))return (TextView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView found=text(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;}
        return null;
    }
    @Test public void memoDraftSurvivesRecreationSaveAndBackCancelsOnlyTheDraft() throws Exception {
        try(ActivityScenario<FilmFrameDetailActivity> scenario=ActivityScenario.launch(intent())) {
            ready(scenario);
            scenario.onActivity(a->{a.findViewById(R.id.recordHit).performClick();EditText field=input(a.findViewById(R.id.noteRecords));assertNotNull(field);field.setText("메모 복원 " + id);});
            scenario.recreate();ready(scenario);
            scenario.onActivity(a->{EditText field=input(a.findViewById(R.id.noteRecords));assertNotNull(field);assertEquals("메모 복원 "+id,field.getText().toString());text(a.findViewById(R.id.recordEditor),a.getString(R.string.save)).performClick();});
            long end=SystemClock.uptimeMillis()+15000;
            while(!store.find(id).frame(1).memo.equals("메모 복원 "+id)&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50);
            assertEquals("메모 복원 "+id,store.find(id).frame(1).memo);assertEquals("",store.find(id).frame(2).memo);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{a.findViewById(R.id.recordHit).performClick();EditText field=input(a.findViewById(R.id.noteRecords));assertNotNull(field);field.setText("취소할 메모");a.findViewById(R.id.noteDetailPhoto).performClick();a.getOnBackPressedDispatcher().onBackPressed();assertFalse(a.isFinishing());assertEquals(View.VISIBLE,a.findViewById(R.id.noteDetailHeader).getVisibility());assertEquals("취소할 메모",input(a.findViewById(R.id.noteRecords)).getText().toString());a.getOnBackPressedDispatcher().onBackPressed();assertFalse(a.isFinishing());assertNull(input(a.findViewById(R.id.noteRecords)));});
            assertEquals("메모 복원 "+id,store.find(id).frame(1).memo);
        }
    }
    @After public void cleanup(){if(store!=null&&id!=null)assertTrue(store.delete(Collections.singleton(id)));}
}
