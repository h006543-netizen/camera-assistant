package com.shutternote;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;
import androidx.appcompat.widget.AppCompatImageView;

/** Zoom and pan a display copy; normalized centres survive switching between differently sized photos. */
public class FilmComparisonImageView extends AppCompatImageView {
    private final Matrix transform = new Matrix();
    private final ScaleGestureDetector pinch;
    private final GestureDetector gestures;
    private float zoom = 1f, centerX = .5f, centerY = .5f, drawWidth, drawHeight, left, top;
    private Runnable changed;
    interface PhotoOnlyListener { boolean isActive(); void show(); }
    private PhotoOnlyListener photoOnly;
    private boolean tapStartedPhotoOnly;
    private final int[] screenLocation = new int[2];
    interface PhotoSwipe { void select(boolean scan); }
    private PhotoSwipe photoSwipe;
    private final float swipeDistance;
    private final int touchSlop;
    private float downX, downY;
    private boolean swipeAllowed;

    public FilmComparisonImageView(Context context, AttributeSet attrs) {
        super(context, attrs); setScaleType(ScaleType.MATRIX);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        swipeDistance = Math.max(48 * getResources().getDisplayMetrics().density, touchSlop * 4);
        pinch = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                if (photoOnly != null) photoOnly.show();
                return true;
            }
            @Override public boolean onScale(ScaleGestureDetector detector) {
                zoomAtGesture(zoom * detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY()); return true;
            }
        });
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent event) {
                // Enter immediately; defer exit so a double tap can still zoom.
                if (photoOnly != null) { tapStartedPhotoOnly = photoOnly.isActive(); photoOnly.show(); }
                return true;
            }
            @Override public boolean onSingleTapConfirmed(MotionEvent event) {
                if (photoOnly == null || tapStartedPhotoOnly) performClick();
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent event) {
                if (photoOnly != null) photoOnly.show();
                zoomAtGesture(zoom > 1 ? 1 : 2, event.getX(), event.getY()); return true;
            }
            @Override public void onLongPress(MotionEvent event) {
                if (photoOnly != null && !photoOnly.isActive() && isLongClickable()) performLongClick();
            }
            @Override public boolean onScroll(MotionEvent first, MotionEvent current, float distanceX, float distanceY) {
                if (!pinch.isInProgress() && zoom > 1 && drawWidth > 0 && drawHeight > 0) {
                    centerX += distanceX / drawWidth; centerY += distanceY / drawHeight; applyTransform();
                }
                return true;
            }
        });
    }

    void setChanged(Runnable listener) { changed = listener; }
    void setPhotoOnlyListener(PhotoOnlyListener listener) { photoOnly = listener; }
    void setPhotoSwipe(PhotoSwipe listener) { photoSwipe = listener; }
    float zoom() { return zoom; }
    float centerX() { return centerX; }
    float centerY() { return centerY; }
    void restore(float scale, float x, float y) {
        zoom = Float.isFinite(scale) ? Math.max(1, Math.min(5, scale)) : 1;
        centerX = Float.isFinite(x) ? Math.max(0, Math.min(1, x)) : .5f;
        centerY = Float.isFinite(y) ? Math.max(0, Math.min(1, y)) : .5f;
        applyTransform();
    }
    void stepZoom(boolean in) { zoomAt(in ? zoom * 1.5f : zoom / 1.5f, getWidth() / 2f, getHeight() / 2f); }
    void refresh() { applyTransform(); }

    private void zoomAtGesture(float value, float x, float y) {
        if (photoOnly != null) {
            getLocationOnScreen(screenLocation);
            x -= screenLocation[0]; y -= screenLocation[1];
        }
        zoomAt(value, x, y);
    }

    private void zoomAt(float value, float x, float y) {
        swipeAllowed = false;
        if (drawWidth <= 0 || drawHeight <= 0) return;
        float next = Math.max(1, Math.min(5, value));
        float targetWidth = drawWidth * next / zoom, targetHeight = drawHeight * next / zoom;
        centerX = (getWidth() / 2f - x + ((x - left) / drawWidth) * targetWidth) / targetWidth;
        centerY = (getHeight() / 2f - y + ((y - top) / drawHeight) * targetHeight) / targetHeight;
        zoom = next; applyTransform();
    }

    private void applyTransform() {
        Drawable photo = getDrawable();
        if (photo == null || photo.getIntrinsicWidth() <= 0 || photo.getIntrinsicHeight() <= 0 || getWidth() == 0 || getHeight() == 0) return;
        float fit = Math.min(getWidth() / (float) photo.getIntrinsicWidth(), getHeight() / (float) photo.getIntrinsicHeight());
        float scale = fit * zoom;
        drawWidth = photo.getIntrinsicWidth() * scale; drawHeight = photo.getIntrinsicHeight() * scale;
        left = translation(getWidth(), drawWidth, centerX); top = translation(getHeight(), drawHeight, centerY);
        centerX = (getWidth() / 2f - left) / drawWidth; centerY = (getHeight() / 2f - top) / drawHeight;
        transform.setScale(scale, scale); transform.postTranslate(left, top); setImageMatrix(transform);
        if (changed != null) changed.run();
    }

    private static float translation(float viewport, float image, float center) {
        return image <= viewport ? (viewport - image) / 2 : Math.max(viewport - image, Math.min(0, viewport / 2 - center * image));
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w,h,oldw,oldh); applyTransform(); }
    // GestureDetector.onSingleTapConfirmed calls performClick; pinch and drag are separate actions.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || getDrawable() == null) return false;
        int action = event.getActionMasked();
        float eventX = photoOnly == null ? event.getX() : event.getRawX();
        float eventY = photoOnly == null ? event.getY() : event.getRawY();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = eventX; downY = eventY; swipeAllowed = zoom <= 1;
            // Decide the axis before the page takes the gesture; zoomed drags stay with the photo.
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        }
        // A pinch returning to 1x must not turn its final finger movement into a photo switch.
        if (event.getPointerCount() > 1 || action == MotionEvent.ACTION_CANCEL) swipeAllowed = false;
        if (action == MotionEvent.ACTION_POINTER_DOWN && photoOnly != null) photoOnly.show();
        if (action == MotionEvent.ACTION_MOVE && swipeAllowed && event.getPointerCount() == 1) {
            float dx = Math.abs(eventX - downX), dy = Math.abs(eventY - downY);
            boolean verticalScroll = dy > touchSlop && dy > dx;
            boolean pageScroll = photoOnly != null && dx > touchSlop && dx > dy;
            if ((verticalScroll || pageScroll) && getParent() != null) {
                swipeAllowed = false; getParent().requestDisallowInterceptTouchEvent(false);
            }
        }
        if (photoOnly == null) {
            pinch.onTouchEvent(event); gestures.onTouchEvent(event);
        } else {
            // Hiding the header/records moves this view during the same gesture. Track in
            // screen coordinates so layout changes do not become finger motion or break double taps.
            MotionEvent tracked = MotionEvent.obtain(event);
            tracked.offsetLocation(event.getRawX() - event.getX(), event.getRawY() - event.getY());
            try { pinch.onTouchEvent(tracked); gestures.onTouchEvent(tracked); }
            finally { tracked.recycle(); }
        }
        if (action == MotionEvent.ACTION_UP) {
            float dx = eventX - downX, dy = eventY - downY;
            if (swipeAllowed && photoSwipe != null && Math.abs(dx) >= swipeDistance && Math.abs(dx) > Math.abs(dy) * 1.5f)
                photoSwipe.select(dx < 0);
            swipeAllowed = false;
        }
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && getParent() != null)
            getParent().requestDisallowInterceptTouchEvent(false);
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
