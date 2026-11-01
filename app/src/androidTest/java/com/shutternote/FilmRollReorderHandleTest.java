package com.shutternote;

import android.content.Context;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Local views only: touch ownership and continuous vertical movement without stored roll access. */
@RunWith(AndroidJUnit4.class)
public class FilmRollReorderHandleTest {
    private static final class Fixture {
        final FilmRollReorderHandle handle;
        final View card;
        final int slop;
        int drags, clicks, selections, moves, finishes;
        boolean cancelled;
        Fixture() {
            Context context=new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),R.style.Theme_CameraOption);
            card=LayoutInflater.from(context).inflate(R.layout.item_film_roll,new LinearLayout(context),false);
            handle=card.findViewById(R.id.filmRollReorder);
            slop=ViewConfiguration.get(context).getScaledTouchSlop();
            handle.setDrag(new FilmRollReorderHandle.Drag() {
                @Override public boolean start(float y) { drags++; return true; }
                @Override public void move(float y) { moves++; }
                @Override public void finish(boolean value) { finishes++; cancelled=value; }
            });
            handle.setOnClickListener(view->clicks++);
            card.setOnLongClickListener(view->{selections++;return true;});
            card.setOnClickListener(view->selections++);
            card.measure(View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.EXACTLY));
            card.layout(0,0,800,200);
        }
        void event(int action,float delta) {
            long now=SystemClock.uptimeMillis();
            MotionEvent event=MotionEvent.obtain(now,now,action,handle.getLeft()+handle.getWidth()/2f,
                    handle.getTop()+handle.getHeight()/2f+delta,0);
            try { assertTrue(card.dispatchTouchEvent(event)); } finally { event.recycle(); }
        }
    }

    @Test public void handleDragStartsOnceWithoutClickingOrSelectingTheRow() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Fixture fixture=new Fixture();
            fixture.event(MotionEvent.ACTION_DOWN,0);
            fixture.event(MotionEvent.ACTION_MOVE,fixture.slop/2f);
            assertEquals(0,fixture.drags);
            fixture.event(MotionEvent.ACTION_MOVE,fixture.slop*2f);
            fixture.event(MotionEvent.ACTION_MOVE,fixture.slop*3f);
            fixture.event(MotionEvent.ACTION_UP,fixture.slop*3f);
            assertEquals(1,fixture.drags); assertEquals(0,fixture.clicks); assertEquals(0,fixture.selections);
            assertEquals(3,fixture.moves); assertEquals(1,fixture.finishes); assertFalse(fixture.cancelled);
        });
    }

    @Test public void tappingOnlyClicksTheHandleAndDoesNotSelectTheRow() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Fixture fixture=new Fixture();
            fixture.event(MotionEvent.ACTION_DOWN,0); fixture.event(MotionEvent.ACTION_UP,0);
            assertEquals(0,fixture.drags); assertEquals(1,fixture.clicks); assertEquals(0,fixture.selections);
        });
    }

    @Test public void disabledHandleConsumesTouchWithoutChangingSelection() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Fixture fixture=new Fixture(); fixture.handle.setEnabled(false);
            fixture.event(MotionEvent.ACTION_DOWN,0);
            fixture.event(MotionEvent.ACTION_MOVE,fixture.slop*3f); fixture.event(MotionEvent.ACTION_UP,0);
            assertEquals(0,fixture.drags); assertEquals(0,fixture.clicks); assertEquals(0,fixture.selections);
        });
    }

    @Test public void cancelAndExplicitScreenExitCannotBecomeAClickOrDrag() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Fixture fixture=new Fixture();
            fixture.event(MotionEvent.ACTION_DOWN,0); fixture.event(MotionEvent.ACTION_CANCEL,0);
            fixture.event(MotionEvent.ACTION_UP,0);
            fixture.event(MotionEvent.ACTION_DOWN,0); fixture.handle.cancelTouch();
            fixture.event(MotionEvent.ACTION_MOVE,fixture.slop*3f); fixture.event(MotionEvent.ACTION_UP,0);
            assertEquals(0,fixture.drags); assertEquals(0,fixture.clicks); assertEquals(0,fixture.selections);
        });
    }

    @Test public void secondPointerAbortsHandleGestureWithoutSelectionOrClick() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Fixture fixture=new Fixture();
            fixture.event(MotionEvent.ACTION_DOWN,0);
            long now=SystemClock.uptimeMillis();
            MotionEvent.PointerProperties[] properties={new MotionEvent.PointerProperties(),new MotionEvent.PointerProperties()};
            MotionEvent.PointerCoords[] coords={new MotionEvent.PointerCoords(),new MotionEvent.PointerCoords()};
            for (int i=0;i<2;i++) {
                properties[i].id=i; properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
                coords[i].x=fixture.handle.getWidth()/2f; coords[i].y=fixture.handle.getHeight()/2f+i*10;
                coords[i].pressure=1; coords[i].size=1;
            }
            MotionEvent pair=MotionEvent.obtain(now,now,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    2,properties,coords,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);
            try { fixture.handle.onTouchEvent(pair); } finally { pair.recycle(); }
            fixture.event(MotionEvent.ACTION_MOVE,fixture.slop*3f); fixture.event(MotionEvent.ACTION_UP,0);
            assertEquals(0,fixture.drags); assertEquals(0,fixture.clicks); assertEquals(0,fixture.selections);
        });
    }

    @Test public void disablingAnUntouchedHandleCannotReleaseTheRowsRangeSelectionGesture() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Fixture fixture=new Fixture(); boolean[] disallowed={false};
            LinearLayout parent=new LinearLayout(fixture.card.getContext()) {
                @Override public void requestDisallowInterceptTouchEvent(boolean value) {
                    disallowed[0]=value; super.requestDisallowInterceptTouchEvent(value);
                }
            };
            parent.addView(fixture.card);
            parent.requestDisallowInterceptTouchEvent(true);
            fixture.handle.setEnabled(false);
            fixture.handle.cancelTouch();
            assertTrue(disallowed[0]);
            assertEquals(0,fixture.drags); assertEquals(0,fixture.clicks); assertEquals(0,fixture.selections);
        });
    }

    @Test public void horizontalMovementCannotBeginReordering() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Fixture fixture=new Fixture(); fixture.event(MotionEvent.ACTION_DOWN,0);
            long now=SystemClock.uptimeMillis();
            MotionEvent event=MotionEvent.obtain(now,now,MotionEvent.ACTION_MOVE,
                    fixture.handle.getLeft()+fixture.handle.getWidth()/2f+fixture.slop*3,
                    fixture.handle.getTop()+fixture.handle.getHeight()/2f,0);
            try { fixture.card.dispatchTouchEvent(event); } finally { event.recycle(); }
            fixture.event(MotionEvent.ACTION_UP,0);
            assertEquals(0,fixture.drags); assertEquals(0,fixture.clicks); assertEquals(0,fixture.selections);
        });
    }
}
