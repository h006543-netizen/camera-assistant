package com.shutternote;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Local touch fixtures only; no Activity launch or saved photos/rolls are accessed. */
@RunWith(AndroidJUnit4.class)
public class FilmFrameScrubberTouchTest {
    private static final class Fixture implements FilmFrameScrubber.TouchListener {
        final FilmFrameScrubber view;
        float position=4000;
        int moves,finishes;
        boolean moved;
        Fixture() {
            Context context=new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),R.style.Theme_CameraOption);
            view=new FilmFrameScrubber(context,null); view.setPadding(0,0,0,0); view.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            view.setMax(17000); view.setProgress(4000); view.layout(0,0,1000,120); view.setTouchListener(this);
        }
        @Override public float start() { return position; }
        @Override public void move(float progress) { position=progress; moves++; }
        @Override public void finish(boolean changed) { finishes++; moved=changed; }
        void event(int action,int[] ids,float... xs) {
            MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[ids.length];
            MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[ids.length];
            for(int i=0;i<ids.length;i++) {
                properties[i]=new MotionEvent.PointerProperties(); properties[i].id=ids[i]; properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
                coords[i]=new MotionEvent.PointerCoords(); coords[i].x=xs[i]; coords[i].y=60; coords[i].pressure=1; coords[i].size=1;
            }
            long now=SystemClock.uptimeMillis();
            MotionEvent event=MotionEvent.obtain(now,now,action,ids.length,properties,coords,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);
            try { view.onTouchEvent(event); } finally { event.recycle(); }
        }
        void event(int action,float x) { event(action,new int[]{0},x); }
    }

    @Test public void tappingEitherEndDoesNotMoveTheCurrentPhotoOrSnapIt() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Fixture fixture=new Fixture(); fixture.position=4234.75f;
            for(float x:new float[]{0,999}) {
                fixture.event(MotionEvent.ACTION_DOWN,x); fixture.event(MotionEvent.ACTION_UP,x);
                assertEquals(4234.75f,fixture.position,.001f); assertEquals(4235,fixture.view.getProgress());
                assertEquals(0,fixture.moves); assertFalse(fixture.moved);
            }
            assertEquals(2,fixture.finishes);
        });
    }

    @Test public void theSameDragStartingAtEitherEndMovesByTheSameAmount() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Fixture first=new Fixture(),second=new Fixture();
            float distance=Math.max(100,2*ViewConfiguration.get(first.view.getContext()).getScaledTouchSlop());
            first.event(MotionEvent.ACTION_DOWN,100); first.event(MotionEvent.ACTION_MOVE,100+distance); first.event(MotionEvent.ACTION_UP,100+distance);
            second.event(MotionEvent.ACTION_DOWN,900); second.event(MotionEvent.ACTION_MOVE,900+distance); second.event(MotionEvent.ACTION_UP,900+distance);
            assertEquals(first.position,second.position,.001f); assertEquals(4000+distance*17,first.position,.001f);
            assertTrue(first.moved); assertTrue(second.moved); assertFalse(first.view.isPressed());
        });
    }

    @Test public void fingerReplacementDoesNotJumpToTheReplacementFingerLocation() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Fixture fixture=new Fixture();
            fixture.event(MotionEvent.ACTION_DOWN,800);
            fixture.event(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{0,1},800,100);
            fixture.event(MotionEvent.ACTION_POINTER_UP,new int[]{0,1},800,100);
            fixture.event(MotionEvent.ACTION_MOVE,new int[]{1},100);
            assertEquals(4000,fixture.position,.001f);
            float distance=Math.max(100,2*ViewConfiguration.get(fixture.view.getContext()).getScaledTouchSlop());
            fixture.event(MotionEvent.ACTION_MOVE,new int[]{1},100+distance);
            fixture.event(MotionEvent.ACTION_UP,new int[]{1},100+distance);
            assertEquals(4000+distance*17,fixture.position,.001f); assertEquals(1,fixture.finishes);
        });
    }

    @Test public void cancellingReleasesTheGestureWithoutUsingTheCancelEventLocation() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Fixture fixture=new Fixture(); float distance=Math.max(100,2*ViewConfiguration.get(fixture.view.getContext()).getScaledTouchSlop());
            fixture.event(MotionEvent.ACTION_DOWN,800); fixture.event(MotionEvent.ACTION_MOVE,800+distance);
            float before=fixture.position; fixture.event(MotionEvent.ACTION_CANCEL,0); fixture.view.cancelTouch();
            assertEquals(before,fixture.position,.001f); assertEquals(1,fixture.finishes); assertFalse(fixture.view.isPressed());
        });
    }
}
