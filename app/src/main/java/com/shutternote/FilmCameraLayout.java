package com.shutternote;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

/** Fits the complete film frame between the controls, including their system insets. */
public final class FilmCameraLayout extends FrameLayout {
    public FilmCameraLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        View preview = findViewById(R.id.exposurePreviewView);
        View top = findViewById(R.id.exposureTopControls);
        View bottom = findViewById(R.id.exposureBottomControls);
        LayoutParams topParams = (LayoutParams) top.getLayoutParams();
        LayoutParams bottomParams = (LayoutParams) bottom.getLayoutParams();
        int availableHeight = Math.max(0, getMeasuredHeight()
                - top.getMeasuredHeight() - topParams.topMargin - topParams.bottomMargin
                - bottom.getMeasuredHeight() - bottomParams.topMargin - bottomParams.bottomMargin);
        // ExposureCameraActivity keeps the UI in portrait; even in a short window
        // the film frame remains 2:3 rather than following the window aspect ratio.
        int unit = Math.min(getMeasuredWidth() / 2, availableHeight / 3);
        preview.measure(MeasureSpec.makeMeasureSpec(unit * 2, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(unit * 3, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        View preview = findViewById(R.id.exposurePreviewView);
        View topControls = findViewById(R.id.exposureTopControls);
        View bottomControls = findViewById(R.id.exposureBottomControls);
        int x = (getWidth() - preview.getMeasuredWidth()) / 2;
        int y = topControls.getBottom()
                + Math.max(0, (bottomControls.getTop() - topControls.getBottom()
                - preview.getMeasuredHeight()) / 2);
        preview.layout(x, y, x + preview.getMeasuredWidth(), y + preview.getMeasuredHeight());
    }
}
