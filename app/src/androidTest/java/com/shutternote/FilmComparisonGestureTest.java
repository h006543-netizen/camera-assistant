package com.shutternote;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.widget.NestedScrollView;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises real touch events without opening activities or reading the user's saved rolls. */
@RunWith(AndroidJUnit4.class)
public class FilmComparisonGestureTest {
    private Context context() {
        return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), androidx.appcompat.R.style.Theme_AppCompat);
    }

    private FilmComparisonImageView image(List<Boolean> switches) {
        FilmComparisonImageView view = new FilmComparisonImageView(context(), null);
        int size = Math.round(300 * view.getResources().getDisplayMetrics().density);
        view.setImageBitmap(Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888));
        view.layout(0, 0, size, size);
        view.refresh(); view.setPhotoSwipe(switches::add);
        return view;
    }

    private void event(FilmComparisonImageView view, long start, long elapsed, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(start, start + elapsed, action, x, y, 0);
        try { view.onTouchEvent(event); } finally { event.recycle(); }
    }

    private void swipe(FilmComparisonImageView view, float fromX, float fromY, float toX, float toY) {
        long start = SystemClock.uptimeMillis();
        event(view, start, 0, MotionEvent.ACTION_DOWN, fromX, fromY);
        event(view, start, 100, MotionEvent.ACTION_MOVE, toX, toY);
        event(view, start, 200, MotionEvent.ACTION_UP, toX, toY);
    }

    @Test public void fullViewSwitchesInBothDirectionsWithoutChangingZoom() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            List<Boolean> switches = new ArrayList<>(); FilmComparisonImageView view = image(switches);
            float size = view.getWidth();
            swipe(view, size * .9f, size * .5f, size * .1f, size * .5f);
            swipe(view, size * .1f, size * .5f, size * .9f, size * .5f);
            assertEquals(java.util.Arrays.asList(true, false), switches);
            assertEquals(1f, view.zoom(), .001f);
        });
    }

    @Test public void zoomedDragPansAndNeverSwitchesPhotos() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            List<Boolean> switches = new ArrayList<>(); FilmComparisonImageView view = image(switches);
            view.restore(2, .5f, .5f); float size = view.getWidth();
            swipe(view, size * .9f, size * .5f, size * .1f, size * .5f);
            assertTrue(switches.isEmpty()); assertEquals(2f, view.zoom(), .001f);
            assertTrue(view.centerX() > .5f);
        });
    }

    @Test public void verticalSmallCancelledAndDisabledGesturesDoNotSwitch() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            List<Boolean> switches = new ArrayList<>(); FilmComparisonImageView view = image(switches);
            float size = view.getWidth();
            swipe(view, size * .5f, size * .1f, size * .6f, size * .9f);
            swipe(view, size * .5f, size * .5f, size * .52f, size * .5f);
            long start = SystemClock.uptimeMillis();
            event(view, start, 0, MotionEvent.ACTION_DOWN, size * .9f, size * .5f);
            event(view, start, 100, MotionEvent.ACTION_CANCEL, size * .1f, size * .5f);
            event(view, start, 200, MotionEvent.ACTION_UP, size * .1f, size * .5f);
            view.setEnabled(false);
            swipe(view, size * .9f, size * .5f, size * .1f, size * .5f);
            assertTrue(switches.isEmpty());
        });
    }

    @Test public void finalFingerAfterMultiTouchCannotSwitchPhotos() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            List<Boolean> switches = new ArrayList<>(); FilmComparisonImageView view = image(switches);
            float size = view.getWidth(); long start = SystemClock.uptimeMillis();
            event(view, start, 0, MotionEvent.ACTION_DOWN, size * .9f, size * .5f);
            MotionEvent.PointerProperties[] properties = {new MotionEvent.PointerProperties(), new MotionEvent.PointerProperties()};
            MotionEvent.PointerCoords[] coords = {new MotionEvent.PointerCoords(), new MotionEvent.PointerCoords()};
            for (int i = 0; i < 2; i++) {
                properties[i].id = i; properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
                coords[i].x = size * (.9f - i * .2f); coords[i].y = size * .5f;
                coords[i].pressure = 1; coords[i].size = 1;
            }
            MotionEvent event = MotionEvent.obtain(start, start + 50,
                    MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    2, properties, coords, 0, 0, 1, 1, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0);
            try { view.onTouchEvent(event); } finally { event.recycle(); }
            event = MotionEvent.obtain(start, start + 75,
                    MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    2, properties, coords, 0, 0, 1, 1, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0);
            try { view.onTouchEvent(event); } finally { event.recycle(); }
            event(view, start, 100, MotionEvent.ACTION_MOVE, size * .1f, size * .5f);
            event(view, start, 200, MotionEvent.ACTION_UP, size * .1f, size * .5f);
            assertTrue(switches.isEmpty());
        });
    }

    @Test public void pairedPhotosExposeClickAndFocus() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View screen = LayoutInflater.from(context()).inflate(R.layout.activity_film_scan_comparison, null);
            for (int id : new int[]{R.id.scanNoteImage, R.id.scanFilmImage}) {
                View photo = screen.findViewById(id);
                assertTrue("Photo must be clickable: " + id, photo.isClickable());
                assertTrue("Photo must expose keyboard focus: " + id + " mode=" + photo.getFocusable(), photo.isFocusable());
            }
        });
    }

    @Test public void longRecordsRemainBelowPhotosAndControlsWithoutShrinkingPhotosInEitherMode() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View screen = LayoutInflater.from(context()).inflate(R.layout.activity_film_scan_comparison, null);
            int width = Math.round(360 * screen.getResources().getDisplayMetrics().density);
            int height = Math.round(640 * screen.getResources().getDisplayMetrics().density);
            measurePage(screen, width, height);
            View photo = screen.findViewById(R.id.scanPhotoArea);
            int photoHeight = photo.getHeight();
            LinearLayout records = screen.findViewById(R.id.scanNotesRecords);
            TextView memo = new TextView(context());
            StringBuilder text = new StringBuilder();
            for (int line = 0; line < 80; line++) text.append("A long note line\n");
            memo.setText(text); records.addView(memo);
            measurePage(screen, width, height);
            assertEquals(photoHeight, photo.getHeight());
            assertTrue(records.getTop() >= screen.findViewById(R.id.scanPhotoArea).getBottom());
            NestedScrollView scroll = screen.findViewById(R.id.scanPageScroll);
            assertTrue(scroll.canScrollVertically(1));
            scroll.scrollTo(0, records.getTop()); assertTrue(scroll.getScrollY() > 0);
            screen.findViewById(R.id.scanPair).setVisibility(View.GONE);
            screen.findViewById(R.id.scanZoomImage).setVisibility(View.VISIBLE);
            screen.findViewById(R.id.scanZoomControls).setVisibility(View.VISIBLE);
            screen.findViewById(R.id.scanSwitchActions).setVisibility(View.VISIBLE);
            measurePage(screen, width, height);
            assertEquals(photoHeight, photo.getHeight());
            assertTrue(records.getTop() >= screen.findViewById(R.id.scanSwitchActions).getBottom());
            assertTrue(scroll.canScrollVertically(1));
        });
    }

    private void measurePage(View screen, int width, int height) {
        screen.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        screen.layout(0, 0, width, height);
    }

    @Test public void fullViewVerticalDragYieldsToPageButZoomedDragKeepsPhotoControl() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            List<Boolean> switches = new ArrayList<>(); FilmComparisonImageView view = image(switches);
            TouchParent parent = new TouchParent(context()); parent.addView(view);
            float size = view.getWidth(); long start = SystemClock.uptimeMillis();
            event(view, start, 0, MotionEvent.ACTION_DOWN, size * .5f, size * .8f);
            assertTrue(parent.disallowed);
            event(view, start, 100, MotionEvent.ACTION_MOVE, size * .5f, size * .2f);
            assertFalse(parent.disallowed);
            event(view, start, 200, MotionEvent.ACTION_UP, size * .5f, size * .2f);
            view.restore(2, .5f, .5f);
            event(view, start + 500, 0, MotionEvent.ACTION_DOWN, size * .5f, size * .8f);
            event(view, start + 500, 100, MotionEvent.ACTION_MOVE, size * .5f, size * .2f);
            assertTrue(parent.disallowed); assertTrue(view.centerY() > .5f);
            event(view, start + 500, 200, MotionEvent.ACTION_UP, size * .5f, size * .2f);
            assertFalse(parent.disallowed); assertTrue(switches.isEmpty());
        });
    }

    private static final class TouchParent extends FrameLayout {
        boolean disallowed;
        TouchParent(Context context) { super(context); }
        @Override public void requestDisallowInterceptTouchEvent(boolean value) {
            disallowed = value; super.requestDisallowInterceptTouchEvent(value);
        }
    }
}
