package com.shutternote;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import java.util.function.IntSupplier;

/** Photos fill the viewport; the optional next-film card stops at its right edge. */
final class FilmFramePagerSnapHelper extends PagerSnapHelper {
    private final IntSupplier photoCount;
    FilmFramePagerSnapHelper(IntSupplier photoCount) { this.photoCount = photoCount; }

    @Override public int[] calculateDistanceToFinalSnap(@NonNull RecyclerView.LayoutManager manager,
                                                       @NonNull View target) {
        if (manager.getPosition(target) == photoCount.getAsInt())
            return new int[]{manager.getDecoratedRight(target) - manager.getWidth() + manager.getPaddingRight(), 0};
        return super.calculateDistanceToFinalSnap(manager, target);
    }
}
