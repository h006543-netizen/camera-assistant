package com.shutternote;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real row fixtures; no Activity, native drag window, photo, preference or roll store access. */
@RunWith(AndroidJUnit4.class)
public class FilmRollVerticalReorderTest {
    private static final class Fixture {
        final ScrollView viewport;
        final FilmRollListLayout list;
        final FilmRollVerticalReorder reorder;
        final List<View> rows=new ArrayList<>();
        int writes;
        Fixture() {
            Context context=new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),R.style.Theme_CameraOption);
            viewport=new ScrollView(context); viewport.setBackgroundColor(0xFFF7F7F7);
            list=new FilmRollListLayout(context,null); list.setOrientation(LinearLayout.VERTICAL);
            viewport.addView(list,new ScrollView.LayoutParams(360,-2));
            for(int i=0;i<3;i++) {
                View row=new View(context); FilmRollRecord record=new FilmRollRecord(); record.id="roll"+i;
                row.setTag(record); row.setBackgroundColor(Color.TRANSPARENT);
                list.addView(row,new LinearLayout.LayoutParams(360,new int[]{100,180,80}[i])); rows.add(row);
            }
            viewport.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.EXACTLY));
            viewport.layout(0,0,360,500);
            reorder=new FilmRollVerticalReorder(list,viewport,()->true,()->{},ids->writes++);
        }
    }
    @Test public void actualRowMovesOnlyVerticallyWithFullOpacityAndNoDuplicate() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Fixture fixture=new Fixture(); View row=fixture.rows.get(0);
            assertTrue(fixture.reorder.start(row,50)); fixture.reorder.move(210);
            assertEquals(160,row.getTranslationY(),.001f); assertEquals(0,row.getTranslationX(),.001f);
            assertEquals(1,row.getAlpha(),.001f); assertEquals(1,row.getScaleX(),.001f); assertEquals(1,row.getScaleY(),.001f);
            assertEquals(255,Color.alpha(((ColorDrawable)row.getBackground()).getColor()));
            assertSame(fixture.list,row.getParent()); assertEquals(3,fixture.list.getChildCount());
            assertSame(row,fixture.list.getChildAt(0)); assertEquals(0,fixture.writes);
            fixture.reorder.cancel();
        });
    }
    @Test public void cancelRestoresOriginalRowsBackgroundAndTranslationsWithoutSaving() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Fixture fixture=new Fixture(); View row=fixture.rows.get(1); Drawable original=row.getBackground();
            assertTrue(fixture.reorder.start(row,190)); fixture.reorder.move(-1000);
            fixture.reorder.cancel(); assertFalse(fixture.reorder.active());
            for(int i=0;i<3;i++) {
                assertSame(fixture.rows.get(i),fixture.list.getChildAt(i));
                assertEquals(0,fixture.rows.get(i).getTranslationY(),.001f);
                assertEquals(0,fixture.rows.get(i).getTranslationX(),.001f);
            }
            assertSame(original,row.getBackground()); assertEquals(Color.TRANSPARENT,((ColorDrawable)original).getColor());
            assertEquals(0,fixture.writes);
        });
    }
    @Test public void leavingDuringDropAnimationCancelsPendingCommit() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Fixture fixture=new Fixture(); View row=fixture.rows.get(0);
            fixture.reorder.start(row,50); fixture.reorder.move(1000); fixture.reorder.finish(false);
            fixture.reorder.cancel(); assertFalse(fixture.reorder.active()); assertEquals(0,fixture.writes);
            assertSame(row,fixture.list.getChildAt(0)); assertEquals(0,row.getTranslationY(),.001f);
        });
    }

    @Test public void dropAlignsTheSameRowThenSavesOnce() {
        Fixture[] fixture={null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            fixture[0]=new Fixture(); View row=fixture[0].rows.get(0);
            fixture[0].reorder.start(row,50); fixture[0].reorder.move(1000);
            fixture[0].reorder.finish(false);
            assertEquals(0,fixture[0].writes); assertTrue(fixture[0].reorder.active());
        });
        SystemClock.sleep(400);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Fixture value=fixture[0]; assertFalse(value.reorder.active()); assertEquals(1,value.writes);
            assertSame(value.rows.get(1),value.list.getChildAt(0));
            assertSame(value.rows.get(2),value.list.getChildAt(1));
            assertSame(value.rows.get(0),value.list.getChildAt(2));
            for(View row:value.rows) {
                assertEquals(0,row.getTranslationX(),.001f); assertEquals(0,row.getTranslationY(),.001f);
                assertEquals(1,row.getAlpha(),.001f); assertEquals(1,row.getScaleX(),.001f);
            }
        });
    }
}
