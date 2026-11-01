package com.shutternote;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Reserves a portrait photo area so rotating a cut never moves its records. */
public final class FilmNotePhotoLayout extends FrameLayout {
    private int photoOnlyHeight;
    public FilmNotePhotoLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void setPhotoOnlyHeight(int height) {
        if (photoOnlyHeight == height) return;
        photoOnlyHeight = height;
        requestLayout();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = photoOnlyHeight > 0 ? photoOnlyHeight : Math.round(width * 1.5f);
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        ImageView photo = findViewById(R.id.noteDetailPhoto);
        View delete = findViewById(R.id.frameDelete);
        Drawable image = photo.getDrawable();
        if (delete.getVisibility() == View.GONE || image == null
                || image.getIntrinsicWidth() <= 0 || image.getIntrinsicHeight() <= 0) return;
        // The image is centered inside the fixed area; keep deletion on the photo itself.
        float scale = Math.min((float) photo.getWidth() / image.getIntrinsicWidth(),
                (float) photo.getHeight() / image.getIntrinsicHeight());
        float imageWidth = image.getIntrinsicWidth() * scale;
        float imageHeight = image.getIntrinsicHeight() * scale;
        float imageLeft = photo.getLeft() + (photo.getWidth() - imageWidth) / 2f;
        float imageTop = photo.getTop() + (photo.getHeight() - imageHeight) / 2f;
        LayoutParams params = (LayoutParams) delete.getLayoutParams();
        int x = Math.round(getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                ? imageLeft + params.leftMargin
                : imageLeft + imageWidth - params.rightMargin - delete.getMeasuredWidth());
        int y = Math.round(imageTop + params.topMargin);
        delete.layout(x, y, x + delete.getMeasuredWidth(), y + delete.getMeasuredHeight());
    }
}
