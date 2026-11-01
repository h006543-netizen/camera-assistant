package com.shutternote;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Local view fixtures only; does not open the user's app screens or read/write saved rolls. */
@RunWith(AndroidJUnit4.class)
public class FilmRollFolderGestureTest {
    private static final class Fixture {
        final FilmRollSelection selection=new FilmRollSelection();
        final LinearLayout list;
        final FilmRollGroupDrag drag;
        int writes;
        Fixture() {
            Context context=new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),R.style.Theme_CameraOption);
            ScrollView scroll=new ScrollView(context); list=new LinearLayout(context); list.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(list,new ScrollView.LayoutParams(300,500));
            drag=new FilmRollGroupDrag(list,scroll,()->true,selection,()->{},ids->writes++,(ids,folder)->writes++);
            for(String id:Arrays.asList("a","b","c","d","e")) {
                View card=new View(context); FilmRollRecord roll=new FilmRollRecord(); roll.id=id;
                card.setTag(roll); card.setClickable(true); list.addView(card,new LinearLayout.LayoutParams(300,100)); drag.attach(card);
            }
            scroll.measure(View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.EXACTLY));
            scroll.layout(0,0,300,500);
        }
        void event(View source,int action,float y) {
            long time=SystemClock.uptimeMillis(); MotionEvent event=MotionEvent.obtain(time,time,action,150,y,0);
            try { source.dispatchTouchEvent(event); } finally { event.recycle(); }
        }
    }
    @Test public void longPressAndDragExtendsThenRetractsSelectionWithoutSavingOrReordering() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Fixture fixture=new Fixture(); fixture.selection.begin("e"); View source=fixture.list.getChildAt(1);
            assertTrue(source.performLongClick()); assertTrue(fixture.drag.active());
            fixture.event(source,MotionEvent.ACTION_MOVE,350);
            assertEquals(new HashSet<>(Arrays.asList("b","c","d","e")),fixture.selection.snapshot());
            fixture.event(source,MotionEvent.ACTION_MOVE,250);
            assertEquals(new HashSet<>(Arrays.asList("b","c","e")),fixture.selection.snapshot());
            fixture.event(source,MotionEvent.ACTION_UP,250);
            assertFalse(fixture.drag.active()); assertEquals(0,fixture.writes);
            assertSame(source,fixture.list.getChildAt(1));
        });
    }
    @Test public void cancelledRangeKeepsExplicitChoicesAndDoesNotSubmitFolderOrOrderWrites() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Fixture fixture=new Fixture(); View source=fixture.list.getChildAt(0);
            source.performLongClick(); fixture.event(source,MotionEvent.ACTION_MOVE,250);
            fixture.drag.cancel();
            assertFalse(fixture.drag.active()); assertEquals(0,fixture.writes);
            assertEquals(new HashSet<>(Arrays.asList("a","b","c")),fixture.selection.snapshot());
            assertEquals(1f,source.getAlpha(),.001f);
        });
    }
}
