package com.shutternote;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Isolated pager fixtures; never launches an Activity or reads user rolls. */
@RunWith(AndroidJUnit4.class)
public class FilmNextRollPagerTest {
    private RecyclerView pager(boolean hasNext) {
        Context context = ApplicationProvider.getApplicationContext();
        RecyclerView pager = new RecyclerView(context);
        pager.setLayoutManager(new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false));
        pager.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                View page = new View(context);
                page.setLayoutParams(new RecyclerView.LayoutParams(1000, ViewGroup.LayoutParams.MATCH_PARENT));
                return new RecyclerView.ViewHolder(page) { };
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                holder.itemView.getLayoutParams().width = position == 2
                        ? Math.round(1000 * FilmNextRollNavigation.REVEAL_FRACTION) : 1000;
            }
            @Override public int getItemCount() { return hasNext ? 3 : 2; }
        });
        pager.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        pager.layout(0, 0, 1000, 800);
        return pager;
    }

    @Test public void revealsFiftyFivePercentThenReturnsToLastPhoto() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RecyclerView pager = pager(true);
            FilmFramePagerSnapHelper snap = new FilmFramePagerSnapHelper(() -> 2);
            snap.attachToRecyclerView(pager);
            RecyclerView.LayoutManager layout = pager.getLayoutManager();
            pager.scrollBy(10000, 0);
            View card = layout.findViewByPosition(2), photo = layout.findViewByPosition(1);
            assertNotNull(card); assertNotNull(photo);
            assertEquals(450, card.getLeft()); assertEquals(1000, card.getRight());
            assertEquals(-550, photo.getLeft());
            assertSame(card, snap.findSnapView(layout));
            assertArrayEquals(new int[]{0, 0}, snap.calculateDistanceToFinalSnap(layout, card));
            pager.scrollBy(-550, 0);
            photo = layout.findViewByPosition(1);
            assertEquals(0, photo.getLeft()); assertEquals(1000, photo.getRight());
            assertSame(photo, snap.findSnapView(layout));
            assertArrayEquals(new int[]{0, 0}, snap.calculateDistanceToFinalSnap(layout, photo));
        });
    }

    @Test public void finalFilmStopsAtFullWidthPhotoWithoutExtraSpace() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RecyclerView pager = pager(false);
            pager.scrollBy(10000, 0);
            View last = pager.getLayoutManager().findViewByPosition(1);
            assertNotNull(last); assertEquals(0, last.getLeft()); assertEquals(1000, last.getRight());
            assertNull(pager.getLayoutManager().findViewByPosition(2));
        });
    }
}
