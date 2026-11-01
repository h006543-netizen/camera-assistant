package com.shutternote;

import android.content.*;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.View;
import android.widget.TextView;
import androidx.exifinterface.media.ExifInterface;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Generated notes and exact returned gallery URIs only; no user photos. */
@RunWith(AndroidJUnit4.class)
public class FilmNoteGallerySaveTest {
    private Context context;
    private FilmRollStore store;
    private String id;
    private final List<Uri> outputs = new ArrayList<>();
    @Before public void setup() throws Exception {
        assumeTrue(Build.MODEL.contains("sdk") && Build.VERSION.SDK_INT >= 29);
        context = ApplicationProvider.getApplicationContext(); store = new FilmRollStore(context);
        id = "gallery-" + UUID.randomUUID();
        FilmRollRecord roll = new FilmRollRecord(); roll.id = id; assertTrue(store.save(roll));
        Bitmap bitmap = Bitmap.createBitmap(160, 96, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.rgb(70, 135, 185));
        try {
            for (int number = 1; number <= 2; number++) {
                FilmFrameRecord frame = new FilmFrameRecord(); frame.number = number; frame.captureId = id + number;
                frame.iso = id; frame.memo = "Gallery fixture " + number; frame.capturedAt = 946684800000L;
                assertTrue(store.saveFrame(id, frame, bitmap));
            }
        } finally { bitmap.recycle(); }
    }
    private byte[] read(Uri uri) throws Exception {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) { assertNotNull(in); return in.readAllBytes(); }
    }
    private byte[] read(File file) throws Exception {
        try (InputStream in = new FileInputStream(file)) { return in.readAllBytes(); }
    }
    private FilmFrameRecord frame() throws Exception { return store.find(id).frame(1); }
    private void selectProfile(String profile) throws Exception {
        FilmFrameRecord frame = frame();
        Bitmap corrected = FilmRecipeImages.read(context, store.photo(frame), Integer.MAX_VALUE, 0, profile, () -> false);
        try { assertTrue(store.saveCorrection(id, 1, frame.captureId, frame.correctionRevision, true, profile, corrected)); }
        finally { corrected.recycle(); }
    }
    @Test public void originalCopyIsByteExactAndLeavesNoteUntouched() throws Exception {
        FilmFrameRecord frame = frame(); byte[] source = read(store.photo(frame)); String before = store.find(id).toJson().toString();
        Uri output = FilmNoteGallerySaver.save(context, frame); outputs.add(output);
        assertArrayEquals(source, read(output)); assertArrayEquals(source, read(store.photo(frame)));
        assertEquals(before, store.find(id).toJson().toString());
    }
    @Test public void correctedCopyReusesJpegAndRotationOnlyChangesOutputExif() throws Exception {
        FilmFrameRecord frame = frame();
        selectProfile(FilmCorrectionProfiles.IDS[0]);
        assertTrue(store.rotateFrame(id, 1, frame.captureId)); // counterclockwise = 270
        frame = frame(); File corrected = store.correction(frame);
        byte[] raw = read(store.photo(frame)), selected = read(corrected); long modified = corrected.lastModified();
        Uri output = FilmNoteGallerySaver.save(context, frame); outputs.add(output);
        try (InputStream in = context.getContentResolver().openInputStream(output)) {
            assertEquals(ExifInterface.ORIENTATION_ROTATE_270, new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0));
        }
        Bitmap a = android.graphics.BitmapFactory.decodeByteArray(selected, 0, selected.length);
        byte[] result = read(output); Bitmap b = android.graphics.BitmapFactory.decodeByteArray(result, 0, result.length);
        try { assertTrue(a.sameAs(b)); } finally { a.recycle(); b.recycle(); }
        assertArrayEquals(raw, read(store.photo(frame))); assertArrayEquals(selected, read(corrected));
        assertEquals(modified, corrected.lastModified());
    }
    @Test public void failedCopyRemovesItsPendingMediaStoreRow() throws Exception {
        String name = "missing-note-" + UUID.randomUUID() + ".jpg";
        try { FilmNoteGallerySaver.publish(context, new File(context.getCacheDir(), name), name); fail("Missing source accepted"); }
        catch (IOException expected) { }
        try (Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID}, MediaStore.Images.Media.DISPLAY_NAME + " = ?", new String[]{name}, null)) {
            assertNotNull(cursor); assertEquals(0, cursor.getCount());
        }
        File[] temporary = context.getCacheDir().listFiles((directory, file) -> file.startsWith("note-gallery-"));
        assertNotNull(temporary); assertEquals(0, temporary.length);
    }
    @Test public void permissionDenialAndAnotherCutCannotReplaceThePendingPhoto() throws Exception {
        FilmFrameRecord first = frame(), second = store.find(id).frame(2);
        first.rotation = 90; second.rotation = 180;
        FilmNoteGalleryViewModel model = new FilmNoteGalleryViewModel((android.app.Application) context, new androidx.lifecycle.SavedStateHandle());
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            model.save(first, true); model.save(second, false);
            assertEquals(FilmNoteGalleryViewModel.State.PERMISSION, model.state.getValue());
            model.permissionResult(false); assertEquals(FilmNoteGalleryViewModel.State.DENIED, model.state.getValue());
            assertNull(model.lastOutput); model.acknowledge();
            model.save(first, true); model.save(second, false); model.permissionResult(true);
        });
        try {
            long deadline = SystemClock.uptimeMillis() + 20000;
            while (model.state.getValue() == FilmNoteGalleryViewModel.State.SAVING && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
            assertEquals(FilmNoteGalleryViewModel.State.SAVED, model.state.getValue()); assertNotNull(model.lastOutput);
            outputs.add(model.lastOutput);
            try (InputStream in = context.getContentResolver().openInputStream(model.lastOutput)) {
                assertEquals(ExifInterface.ORIENTATION_ROTATE_90, new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0));
            }
        } finally { model.onCleared(); }
    }
    @Test public void headerSaveIsBesideCountAndSurvivesRecreationWithoutDuplicateOutput() throws Exception {
        FilmFrameRecord frame = frame();
        selectProfile(FilmCorrectionProfiles.IDS[3]);
        byte[] expected = read(store.correction(frame()));
        Intent intent = new Intent(context, FilmFrameDetailActivity.class).putExtra(FilmRollActivity.EXTRA_ROLL_ID, id)
                .putExtra(FilmFrameCapture.NUMBER, 1);
        try (ActivityScenario<FilmFrameDetailActivity> scenario = ActivityScenario.launch(intent)) {
            AtomicBoolean ready = new AtomicBoolean(); long deadline = SystemClock.uptimeMillis() + 20000;
            while (!ready.get() && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity(activity -> ready.set(activity.findViewById(R.id.noteCorrectionCategory).isEnabled())); SystemClock.sleep(50);
            }
            assertTrue(ready.get());
            Bitmap screen = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            try (OutputStream out = new FileOutputStream(new File(context.getExternalFilesDir(null), "note-gallery-header.png"))) {
                assertNotNull(screen); assertTrue(screen.compress(Bitmap.CompressFormat.PNG, 100, out));
            } finally { if (screen != null) screen.recycle(); }
            scenario.onActivity(activity -> {
                View count = activity.findViewById(R.id.noteDetailIndex), button = activity.findViewById(R.id.noteDetailSave);
                assertTrue(button.isShown()); assertTrue(button.isEnabled()); assertTrue(button.getLeft() >= count.getRight());
                assertTrue(button.getRight() <= ((View)button.getParent()).getWidth());
                assertEquals(activity.getString(R.string.note_save_gallery), button.getContentDescription());
                assertEquals(activity.getString(R.string.correction_800t), ((TextView)activity.findViewById(R.id.noteCorrectionCategory)).getText().toString());
                button.performClick(); assertFalse(button.isEnabled()); button.performClick();
            });
            scenario.recreate(); Uri[] output = new Uri[1]; deadline = SystemClock.uptimeMillis() + 20000;
            while (output[0] == null && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity(activity -> output[0] = new ViewModelProvider(activity).get(FilmNoteGalleryViewModel.class).lastOutput);
                SystemClock.sleep(50);
            }
            assertNotNull(output[0]); outputs.add(output[0]); assertArrayEquals(expected, read(output[0]));
            // One explicit click group produces exactly one photo, even across recreation.
            try (Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Images.Media._ID}, MediaStore.Images.Media.DISPLAY_NAME + " LIKE ?",
                    new String[]{"ISO" + id + "%_Note_%"}, null)) { assertNotNull(cursor); assertEquals(1, cursor.getCount()); }
            scenario.onActivity(activity -> activity.findViewById(R.id.noteDetailPhoto).performClick());
            scenario.onActivity(activity -> assertEquals(View.GONE, activity.findViewById(R.id.noteDetailHeader).getVisibility()));
            scenario.onActivity(activity -> activity.getOnBackPressedDispatcher().onBackPressed());
            scenario.onActivity(activity -> {
                try {
                    java.lang.reflect.Method select = FilmFrameDetailActivity.class.getDeclaredMethod("selectCorrection", int.class, String.class);
                    select.setAccessible(true); select.invoke(activity, 1, FilmCorrectionCategory.ORIGINAL);
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            ready.set(false); deadline = SystemClock.uptimeMillis() + 20000;
            while (!ready.get() && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity(activity -> ready.set(activity.findViewById(R.id.noteDetailSave).isEnabled())); SystemClock.sleep(50);
            }
            assertTrue(ready.get()); assertFalse(frame().correctionEnabled);
            Uri correctedOutput = output[0];
            scenario.onActivity(activity -> activity.findViewById(R.id.noteDetailSave).performClick());
            deadline = SystemClock.uptimeMillis() + 20000;
            while (correctedOutput.equals(output[0]) && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity(activity -> output[0] = new ViewModelProvider(activity).get(FilmNoteGalleryViewModel.class).lastOutput); SystemClock.sleep(50);
            }
            assertNotEquals(correctedOutput, output[0]); outputs.add(output[0]);
            assertArrayEquals(read(store.photo(frame())), read(output[0]));
        }
    }
    @After public void cleanup() {
        for (Uri uri : outputs) context.getContentResolver().delete(uri, null, null);
        if (store != null && id != null) assertTrue(store.delete(Collections.singleton(id)));
    }
}
