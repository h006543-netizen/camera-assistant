package com.shutternote;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/** Reserves the same portrait area as the note feed, independent of records below it. */
public final class FilmComparisonPhotoLayout extends FrameLayout {
    public FilmComparisonPhotoLayout(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec); return;
        }
        int width = MeasureSpec.getSize(widthMeasureSpec);
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.round(width * 1.5f), MeasureSpec.EXACTLY));
    }
}
