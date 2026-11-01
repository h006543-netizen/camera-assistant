package com.shutternote;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.appcompat.widget.AppCompatImageButton;

/** A dedicated drag surface: movement lifts a row; a tap remains an accessible menu click. */
public final class FilmRollReorderHandle extends AppCompatImageButton {
    interface Drag {
        boolean start(float screenY);
        void move(float screenY);
        void finish(boolean cancelled);
    }
    private Drag drag;
    private final int slop;
    private boolean tracking, dragging, moved;
    private float downX, downY;

    public FilmRollReorderHandle(Context context, AttributeSet attrs) {
        super(context, attrs);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    void setDrag(Drag listener) { drag = listener; }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent event) {
        // Consume a disabled handle too, so the touch cannot become a row selection.
        if (!isEnabled()) { cancelTouch(); return true; }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                tracking = true; moved=false; downX = event.getRawX(); downY = event.getRawY(); setPressed(true);
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                cancelTouch(); return true;
            case MotionEvent.ACTION_MOVE:
                if (!tracking) return true;
                if (event.getPointerCount() != 1) { cancelTouch(); return true; }
                if (dragging) { drag.move(event.getRawY()); return true; }
                float dx=Math.abs(event.getRawX()-downX),dy=Math.abs(event.getRawY()-downY);
                if (dx>slop || dy>slop) moved=true;
                if (dy>slop && dy>=dx && drag!=null) {
                    dragging=drag.start(downY); setPressed(false);
                    if (dragging) drag.move(event.getRawY());
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) drag.move(event.getRawY());
                boolean clicked = tracking && !moved;
                endTouch(false);
                if (clicked) performClick();
                return true;
            default: return true;
        }
    }

    void cancelTouch() {
        endTouch(true);
    }
    private void endTouch(boolean cancelled) {
        boolean wasTracking = tracking;
        boolean wasDragging = dragging;
        tracking = false; dragging=false; moved=false; setPressed(false);
        if (wasTracking && getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        if (wasDragging && drag!=null) drag.finish(cancelled);
    }
    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) cancelTouch();
    }
    @Override protected void onDetachedFromWindow() { cancelTouch(); super.onDetachedFromWindow(); }
    @Override public boolean performClick() { super.performClick(); return true; }
}
