package com.shutternote;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;

/** Disposable emulator, UUID notes only. Both note capture and stored-note selectors are exercised. */
@RunWith(AndroidJUnit4.class)
public class LatestFilmNotesUiTest {
    private Context context;private FilmRollStore store;private String id;private File source;
    private final int[] labels={R.string.correction_50d,R.string.correction_e100d,R.string.correction_250d,R.string.correction_800t,R.string.correction_500t,R.string.correction_portra400};
    @Before public void setup() throws Exception {
        assumeTrue(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"));
        context=ApplicationProvider.getApplicationContext();store=new FilmRollStore(context);id="latest-ui-"+UUID.randomUUID();
        FilmRollRecord roll=new FilmRollRecord();roll.id=id;assertTrue(store.save(roll));
        Bitmap bitmap=Bitmap.createBitmap(128,96,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.rgb(80,130,190));
        FilmFrameRecord frame=new FilmFrameRecord();frame.number=1;frame.captureId=id;assertTrue(store.saveFrame(id,frame,bitmap));
        source=new File(context.getCacheDir(),id+".png");
        try(OutputStream out=new FileOutputStream(source)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
        finally {bitmap.recycle();}
    }
    private void waitDetail(ActivityScenario<FilmFrameDetailActivity> scenario,String profile) {
        long deadline=SystemClock.uptimeMillis()+45000;AtomicBoolean ready=new AtomicBoolean();
        while(SystemClock.uptimeMillis()<deadline) {
            scenario.onActivity(activity->{
                try {
                    FilmFrameRecord frame=store.find(id).frame(1);
                    ready.set(!frame.pendingCorrection() && activity.findViewById(R.id.noteCorrectionCategory).isEnabled() &&
                        (profile==null||FilmCorrectionProfiles.assetId(profile).equals(FilmCorrectionProfiles.assetId(frame.correctionProfile))) &&
                        ((ImageView)activity.findViewById(R.id.noteDetailPhoto)).getDrawable() instanceof BitmapDrawable);
                } catch(Exception error) {throw new AssertionError(error);}
            });
            if(ready.get())return;SystemClock.sleep(50);
        }
        fail("Note correction did not become ready: "+profile);
    }
    private void choose(ActivityScenario<FilmFrameDetailActivity> scenario,int label) {
        scenario.onActivity(activity->activity.findViewById(R.id.noteCorrectionCategory).performClick());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();SystemClock.sleep(300);
        onView(withText(label)).inRoot(isPlatformPopup()).perform(click());
    }
    @Test public void storedNoteOriginalRetainsEveryFilmAndSameFilmReselectionReusesCopy() throws Exception {
        Intent intent=new Intent(context,FilmFrameDetailActivity.class).putExtra(FilmRollActivity.EXTRA_ROLL_ID,id).putExtra(FilmFrameCapture.NUMBER,1);
        try(ActivityScenario<FilmFrameDetailActivity> scenario=ActivityScenario.launch(intent)) {
            waitDetail(scenario,null);
            byte[] original=java.nio.file.Files.readAllBytes(store.photo(store.find(id).frame(1)).toPath());
            for(int i=0;i<labels.length;i++) {
                choose(scenario,labels[i]);waitDetail(scenario,FilmCorrectionProfiles.IDS[i]);
                File cached=store.correction(store.find(id).frame(1));assertTrue(cached.isFile());
                choose(scenario,R.string.correction_original);waitDetail(scenario,null);
                assertFalse(store.find(id).frame(1).correctionEnabled);assertTrue(cached.exists());
                long savedTime=cached.lastModified();byte[] saved=java.nio.file.Files.readAllBytes(cached.toPath());
                choose(scenario,labels[i]);waitDetail(scenario,FilmCorrectionProfiles.IDS[i]);
                assertEquals(cached,store.correction(store.find(id).frame(1)));assertEquals(savedTime,cached.lastModified());
                assertArrayEquals(saved,java.nio.file.Files.readAllBytes(cached.toPath()));
                choose(scenario,R.string.correction_original);waitDetail(scenario,null);
                assertArrayEquals(original,java.nio.file.Files.readAllBytes(store.photo(store.find(id).frame(1)).toPath()));
            }
            scenario.recreate();waitDetail(scenario,null);
            scenario.onActivity(activity->assertEquals(activity.getString(R.string.correction_original),((TextView)activity.findViewById(R.id.noteCorrectionCategory)).getText().toString()));
            choose(scenario,R.string.correction_800t);waitDetail(scenario,FilmCorrectionProfiles.IDS[3]);
            choose(scenario,R.string.correction_original);
            scenario.onActivity(activity->activity.getOnBackPressedDispatcher().onBackPressed());

        }
        try(ActivityScenario<FilmFrameDetailActivity> reopened=ActivityScenario.launch(intent)) {
            waitDetail(reopened,null);assertFalse(store.find(id).frame(1).correctionEnabled);
            reopened.onActivity(activity->assertEquals(activity.getString(R.string.correction_original),((TextView)activity.findViewById(R.id.noteCorrectionCategory)).getText().toString()));
        }
    }
    @Test public void explicit500TReselectionUpgradesOldCopyAndThenReusesLatest() throws Exception {
        FilmFrameRecord frame=store.find(id).frame(1);
        Bitmap old=FilmRecipeImages.read(context,store.photo(frame),Integer.MAX_VALUE,0,
                FilmCorrectionProfiles.OLD_500T_CONTENT_TIME,()->false);
        try {assertTrue(store.saveCorrection(id,1,frame.captureId,frame.correctionRevision,true,
                FilmCorrectionProfiles.OLD_500T_CONTENT_TIME,old));}
        finally {old.recycle();}
        frame=store.find(id).frame(1);
        byte[] original=java.nio.file.Files.readAllBytes(store.photo(frame).toPath());
        File previous=store.correction(frame);byte[] saved=java.nio.file.Files.readAllBytes(previous.toPath());
        Intent intent=new Intent(context,FilmFrameDetailActivity.class).putExtra(FilmRollActivity.EXTRA_ROLL_ID,id)
                .putExtra(FilmFrameCapture.NUMBER,1);
        try(ActivityScenario<FilmFrameDetailActivity> scenario=ActivityScenario.launch(intent)) {
            waitDetail(scenario,FilmCorrectionProfiles.OLD_500T_CONTENT_TIME);
            assertArrayEquals(saved,java.nio.file.Files.readAllBytes(previous.toPath()));
            choose(scenario,R.string.correction_500t);
            waitDetail(scenario,FilmCorrectionProfiles.latest("vision3-500t"));
            FilmFrameRecord updated=store.find(id).frame(1);
            assertEquals(FilmCorrectionProfiles.latest("vision3-500t"),updated.correctionProfile);
            File latest=store.correction(updated);long modified=latest.lastModified();
            assertNotEquals(previous,latest);assertFalse(previous.exists());
            choose(scenario,R.string.correction_original);waitDetail(scenario,null);
            choose(scenario,R.string.correction_500t);waitDetail(scenario,FilmCorrectionProfiles.latest("vision3-500t"));
            assertEquals(latest,store.correction(store.find(id).frame(1)));assertEquals(modified,latest.lastModified());
            assertArrayEquals(original,java.nio.file.Files.readAllBytes(store.photo(updated).toPath()));
        }
    }
    @Test public void noteCaptureOffersSixFilmsAndRestoresPortraChoice() throws Exception {
        long modified=source.lastModified(),size=source.length();
        Intent intent=new Intent(context,FilmResultActivity.class).putExtra(FilmResultActivity.EXTRA_IMAGE_PATH,source.getAbsolutePath())
                .putExtra(FilmResultActivity.EXTRA_EXPOSURE_MULTIPLIER,1.).putExtra(FilmFrameCapture.ROLL,id).putExtra(FilmFrameCapture.NUMBER,2);
        try(ActivityScenario<FilmResultActivity> scenario=ActivityScenario.launch(intent)) {
            E100dResultUiTest.awaitReady(scenario);
            for(int label:labels) {
                E100dResultUiTest.selectCorrection(scenario,label);
                scenario.onActivity(activity->E100dResultUiTest.assertChoice(activity,label));
            }
            scenario.recreate();E100dResultUiTest.awaitReady(scenario);
            scenario.onActivity(activity->E100dResultUiTest.assertChoice(activity,R.string.correction_portra400));
            E100dResultUiTest.selectCorrection(scenario,R.string.correction_original);
            E100dResultUiTest.selectCorrection(scenario,R.string.correction_portra400);
            assertEquals(modified,source.lastModified());assertEquals(size,source.length());
            scenario.onActivity(activity->activity.findViewById(R.id.btnFilmResultSave).performClick());
            long deadline=SystemClock.uptimeMillis()+30000;
            while(SystemClock.uptimeMillis()<deadline && (store.find(id).frame(2)==null||store.find(id).frame(2).pendingCorrection()))SystemClock.sleep(50);
            FilmFrameRecord saved=store.find(id).frame(2);assertNotNull(saved);
            assertEquals(FilmCorrectionProfiles.latest("portra400"),saved.correctionProfile);assertTrue(saved.correctionEnabled);
            assertTrue(store.photo(saved).isFile());assertTrue(store.correction(saved).isFile());
            assertEquals(modified,source.lastModified());assertEquals(size,source.length());
        }
    }
    @Test public void noteCaptureOriginalChoiceSavesAnUncorrectedFrame() throws Exception {
        Intent intent=new Intent(context,FilmResultActivity.class).putExtra(FilmResultActivity.EXTRA_IMAGE_PATH,source.getAbsolutePath())
                .putExtra(FilmResultActivity.EXTRA_EXPOSURE_MULTIPLIER,1.).putExtra(FilmFrameCapture.ROLL,id).putExtra(FilmFrameCapture.NUMBER,2);
        try(ActivityScenario<FilmResultActivity> scenario=ActivityScenario.launch(intent)) {
            E100dResultUiTest.awaitReady(scenario);
            E100dResultUiTest.selectCorrection(scenario,R.string.correction_50d);
            E100dResultUiTest.selectCorrection(scenario,R.string.correction_original);
            scenario.recreate();E100dResultUiTest.awaitReady(scenario);
            scenario.onActivity(activity->activity.findViewById(R.id.btnFilmResultSave).performClick());
            long deadline=SystemClock.uptimeMillis()+30000;
            while(SystemClock.uptimeMillis()<deadline && (store.find(id).frame(2)==null||store.find(id).frame(2).pendingCorrection()))SystemClock.sleep(50);
            FilmFrameRecord saved=store.find(id).frame(2);assertNotNull(saved);assertFalse(saved.correctionEnabled);
            assertTrue(saved.correctionPhoto.isEmpty());assertTrue(saved.correctionProfile.isEmpty());
        }
    }
    @After public void cleanup() {
        if(id!=null)assertTrue(store.delete(Collections.singleton(id)));
        if(source!=null && source.exists())assertTrue(source.delete());
    }
}
