package com.shutternote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Local view fixtures only: no Activity launch, saved roll access or user data mutation. */
@RunWith(AndroidJUnit4.class)
public class FilmFramePhotoGestureTest {
    private FilmComparisonImageView photo() {
        Context context = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_CameraOption);
        FilmComparisonImageView view = LayoutInflater.from(context)
                .inflate(R.layout.item_film_frame_feed, null).findViewById(R.id.noteDetailPhoto);
        view.setBackgroundColor(Color.BLACK);
        view.setImageBitmap(Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888));
        view.layout(0, 0, 600, 1000);
        view.refresh();
        return view;
    }

    private void finger(FilmComparisonImageView view, long start, int elapsed, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(start, start + elapsed, action, x, y, 0);
        try { view.onTouchEvent(event); } finally { event.recycle(); }
    }

    private void pair(FilmComparisonImageView view, long start, int elapsed, int action, float radius) {
        MotionEvent.PointerProperties[] properties = {new MotionEvent.PointerProperties(), new MotionEvent.PointerProperties()};
        MotionEvent.PointerCoords[] coords = {new MotionEvent.PointerCoords(), new MotionEvent.PointerCoords()};
        for (int i = 0; i < 2; i++) {
            properties[i].id = i; properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i].x = 300 + (i == 0 ? -radius : radius);
            coords[i].y = 500 + (i == 0 ? -radius : radius);
            coords[i].pressure = 1; coords[i].size = 1;
        }
        MotionEvent event = MotionEvent.obtain(start, start + elapsed, action, 2, properties, coords,
                0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try { view.onTouchEvent(event); } finally { event.recycle(); }
    }

    private float pinchRadius(FilmComparisonImageView view) {
        android.view.ViewConfiguration config=android.view.ViewConfiguration.get(view.getContext());
        float minimum=android.os.Build.VERSION.SDK_INT>=29?config.getScaledMinimumScalingSpan():0;
        return Math.max(60,Math.max(minimum*1.1f,config.getScaledTouchSlop()*12)/(float)Math.sqrt(8));
    }

    @Test public void diagonalPinchZoomsBothWaysWithoutClosingPhoto() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            FilmComparisonImageView view = photo();
            int[] clicks = {0}; view.setOnClickListener(v -> clicks[0]++);
            long start = SystemClock.uptimeMillis();float radius=pinchRadius(view);
            finger(view, start, 0, MotionEvent.ACTION_DOWN, 240, 440);
            pair(view, start, 20, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), radius);
            pair(view, start, 60, MotionEvent.ACTION_MOVE, radius*1.2f);
            pair(view, start, 100, MotionEvent.ACTION_MOVE, radius*2);
            pair(view, start, 140, MotionEvent.ACTION_MOVE, radius*3);
            assertTrue("Diagonal pinch scale=" + view.zoom(), view.zoom() > 1.5f);
            pair(view, start, 180, MotionEvent.ACTION_MOVE, radius*1.5f);
            pair(view, start, 220, MotionEvent.ACTION_MOVE, radius*1.1f);
            assertEquals(1f, view.zoom(), .001f);
            pair(view, start, 240, MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), radius*1.1f);
            finger(view, start, 260, MotionEvent.ACTION_UP, 270, 470);
            assertEquals(0, clicks[0]);
        });
    }

    @Test public void photoIsCenteredUncroppedOnBlackAndZoomedDragStaysBounded() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            FilmComparisonImageView view = photo();
            assertEquals(Color.BLACK, ((ColorDrawable) view.getBackground()).getColor());
            float[] bounds = {0, 0, 600, 400}; view.getImageMatrix().mapPoints(bounds);
            assertArrayEquals(new float[]{0, 300, 600, 700}, bounds, .01f);
            int[] clicks = {0}; view.setOnClickListener(v -> clicks[0]++);
            view.restore(3, .5f, .5f);
            long start = SystemClock.uptimeMillis();
            finger(view, start, 0, MotionEvent.ACTION_DOWN, 500, 700);
            finger(view, start, 100, MotionEvent.ACTION_MOVE, 100, 300);
            finger(view, start, 200, MotionEvent.ACTION_UP, 100, 300);
            assertTrue(view.centerX() > .5f); assertTrue(view.centerY() > .5f);
            assertEquals(0, clicks[0]);
            bounds = new float[]{0, 0, 600, 400}; view.getImageMatrix().mapPoints(bounds);
            assertTrue(bounds[0] <= 0 && bounds[1] <= 0);
            assertTrue(bounds[2] >= view.getWidth() && bounds[3] >= view.getHeight());
        });
    }

    @Test public void restoredZoomAndCenterSurviveDelayedImageAndViewportChange() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            FilmComparisonImageView view = photo(); view.restore(3, .65f, .5f);
            float x = view.centerX(), y = view.centerY();
            view.setImageDrawable(null);
            view.restore(3, x, y);
            view.layout(0, 0, 1000, 600);
            view.setImageBitmap(Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)); view.refresh();
            assertEquals(3f, view.zoom(), .001f);
            assertEquals(x, view.centerX(), .001f); assertEquals(y, view.centerY(), .001f);
            float[] matrix = new float[9]; view.getImageMatrix().getValues(matrix);
            assertEquals(matrix[Matrix.MSCALE_X], matrix[Matrix.MSCALE_Y], .001f);
            view.restore(100, .5f, .5f); assertEquals(5f, view.zoom(), .001f);
            view.restore(.1f, .5f, .5f); assertEquals(1f, view.zoom(), .001f);
        });
    }

    @Test public void confirmedSingleTapClosesButDoubleTapOnlyZooms() {
        FilmComparisonImageView[] view = new FilmComparisonImageView[1]; int[] clicks = {0};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            view[0] = photo(); view[0].setOnClickListener(v -> clicks[0]++);
            long start = SystemClock.uptimeMillis();
            finger(view[0], start, 0, MotionEvent.ACTION_DOWN, 300, 500);
            finger(view[0], start, 10, MotionEvent.ACTION_UP, 300, 500);
        });
        SystemClock.sleep(400);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            assertEquals(1, clicks[0]);
            long start = SystemClock.uptimeMillis();
            finger(view[0], start, 0, MotionEvent.ACTION_DOWN, 300, 500);
            finger(view[0], start, 10, MotionEvent.ACTION_UP, 300, 500);
            finger(view[0], start, 80, MotionEvent.ACTION_DOWN, 300, 500);
            finger(view[0], start, 90, MotionEvent.ACTION_UP, 300, 500);
            assertEquals(2f, view[0].zoom(), .001f);
        });
        SystemClock.sleep(400);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> assertEquals(1, clicks[0]));
    }

    @Test public void noteTapHidesImmediatelyAndOnlyTheNextConfirmedTapRestores() {
        FilmComparisonImageView[] view = new FilmComparisonImageView[1];
        boolean[] active = {false}; int[] exits = {0};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            view[0] = photo();
            view[0].setPhotoOnlyListener(new FilmComparisonImageView.PhotoOnlyListener() {
                @Override public boolean isActive() { return active[0]; }
                @Override public void show() { active[0] = true; }
            });
            view[0].setOnClickListener(v -> { active[0] = false; exits[0]++; });
            Object parent = view[0].getParent(), drawable = view[0].getDrawable();
            long start = SystemClock.uptimeMillis();
            finger(view[0], start, 0, MotionEvent.ACTION_DOWN, 300, 500);
            finger(view[0], start, 10, MotionEvent.ACTION_UP, 300, 500);
            assertTrue(active[0]); // No double-tap timeout before hiding.
            assertSame(parent, view[0].getParent()); assertSame(drawable, view[0].getDrawable());
        });
        SystemClock.sleep(400);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            assertTrue(active[0]); assertEquals(0, exits[0]);
            long start = SystemClock.uptimeMillis();
            finger(view[0], start, 0, MotionEvent.ACTION_DOWN, 300, 500);
            finger(view[0], start, 10, MotionEvent.ACTION_UP, 300, 500);
            finger(view[0], start, 80, MotionEvent.ACTION_DOWN, 300, 500);
            finger(view[0], start, 90, MotionEvent.ACTION_UP, 300, 500);
            assertTrue(active[0]); assertEquals(2f, view[0].zoom(), .001f);
        });
        SystemClock.sleep(400);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            assertTrue(active[0]); assertEquals(0, exits[0]);
            long start = SystemClock.uptimeMillis();
            finger(view[0], start, 0, MotionEvent.ACTION_DOWN, 300, 500);
            finger(view[0], start, 10, MotionEvent.ACTION_UP, 300, 500);
        });
        SystemClock.sleep(400);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { assertFalse(active[0]); assertEquals(1, exits[0]); });
    }

    @Test public void secondFingerHidesBeforeScaleThresholdAndSamePinchContinuesAfterResize() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            FilmComparisonImageView view = photo(); boolean[] active = {false}; int[] clicks = {0};
            view.setPhotoOnlyListener(new FilmComparisonImageView.PhotoOnlyListener() {
                @Override public boolean isActive() { return active[0]; }
                @Override public void show() { active[0] = true; }
            });
            view.setOnClickListener(v -> clicks[0]++);
            Object parent = view.getParent(), drawable = view.getDrawable();
            long start = SystemClock.uptimeMillis();float radius=pinchRadius(view);
            finger(view, start, 0, MotionEvent.ACTION_DOWN, 240, 440);
            pair(view, start, 20, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), radius);
            assertTrue(active[0]); assertEquals(1f, view.zoom(), .001f);
            view.layout(0, 0, 600, 1200);
            pair(view, start, 60, MotionEvent.ACTION_MOVE, radius*1.2f);
            pair(view, start, 100, MotionEvent.ACTION_MOVE, radius*2);
            pair(view, start, 140, MotionEvent.ACTION_MOVE, radius*3);
            assertTrue("Pinch after resize scale=" + view.zoom(), view.zoom() > 1.5f);
            assertSame(parent, view.getParent()); assertSame(drawable, view.getDrawable());
            pair(view, start, 180, MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), radius*3);
            finger(view, start, 200, MotionEvent.ACTION_UP, 60, 260);
            assertEquals(0, clicks[0]);
        });
    }

    @Test public void notePhotoReservesViewportOnlyWhileRecordsAreHidden() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            FilmComparisonImageView view = photo();
            FilmNotePhotoLayout area = (FilmNotePhotoLayout) view.getParent();
            View records = ((View) area.getParent()).findViewById(R.id.noteFeedRecords);
            Object drawable = view.getDrawable();
            int width = View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY);
            int height = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            area.measure(width, height); assertEquals(900, area.getMeasuredHeight());
            records.setVisibility(View.GONE); area.setPhotoOnlyHeight(1200);
            area.measure(width, height); area.layout(0, 0, 600, 1200);
            assertEquals(1200, area.getMeasuredHeight()); assertEquals(1200, view.getHeight());
            view.restore(3, .6f, .5f);
            records.setVisibility(View.VISIBLE); area.setPhotoOnlyHeight(0);
            area.measure(width, height); area.layout(0, 0, 600, 900);
            assertEquals(900, area.getMeasuredHeight()); assertEquals(View.VISIBLE, records.getVisibility());
            assertSame(drawable, view.getDrawable()); assertSame(area, view.getParent());
        });
    }

    @Test public void unzoomedNoteHandsHorizontalDragToPagerButZoomedPhotoKeepsIt() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_CameraOption);
            boolean[] disallowed = {false};
            FrameLayout parent = new FrameLayout(context) {
                @Override public void requestDisallowInterceptTouchEvent(boolean value) {
                    disallowed[0] = value; super.requestDisallowInterceptTouchEvent(value);
                }
            };
            FilmComparisonImageView view = new FilmComparisonImageView(context, null);
            parent.addView(view);
            view.setImageBitmap(Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888));
            view.layout(0, 0, 600, 1000); view.refresh();
            view.setPhotoOnlyListener(new FilmComparisonImageView.PhotoOnlyListener() {
                @Override public boolean isActive() { return true; }
                @Override public void show() { }
            });
            long start = SystemClock.uptimeMillis();
            finger(view, start, 0, MotionEvent.ACTION_DOWN, 500, 500); assertTrue(disallowed[0]);
            finger(view, start, 100, MotionEvent.ACTION_MOVE, 100, 500); assertFalse(disallowed[0]);
            finger(view, start, 110, MotionEvent.ACTION_CANCEL, 100, 500);
            view.restore(3, .5f, .5f);
            finger(view, start, 500, MotionEvent.ACTION_DOWN, 500, 500);
            finger(view, start, 600, MotionEvent.ACTION_MOVE, 100, 500); assertTrue(disallowed[0]);
            finger(view, start, 700, MotionEvent.ACTION_UP, 100, 500); assertFalse(disallowed[0]);
        });
    }
}
