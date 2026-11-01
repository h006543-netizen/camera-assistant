package com.shutternote;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.appcompat.widget.AppCompatSeekBar;

/** Keeps normal key/accessibility SeekBar actions, while touch uses a relative drag. */
public class FilmFrameScrubber extends AppCompatSeekBar {
    interface TouchListener {
        float start();
        void move(float progress);
        void finish(boolean moved);
    }
    private final FilmFrameScrubGesture gesture=new FilmFrameScrubGesture();
    private final int touchSlop;
    private TouchListener touch;
    private boolean tracking;
    private int pointer=-1;

    public FilmFrameScrubber(Context context, AttributeSet attributes) {
        super(context,attributes);
        touchSlop=ViewConfiguration.get(context).getScaledTouchSlop();
    }
    void setTouchListener(TouchListener listener) { touch=listener; }
    private float x(MotionEvent event,int index) {
        return getLayoutDirection()==LAYOUT_DIRECTION_RTL ? -event.getX(index) : event.getX(index);
    }
    private void move(MotionEvent event,int index) {
        if (!gesture.move(x(event,index),touchSlop)) return;
        setProgress(Math.round(gesture.progress()));
        if (touch!=null) touch.move(gesture.progress());
    }
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || getMax()<=0) return false;
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                tracking=true; pointer=event.getPointerId(0); setPressed(true);
                float start=touch==null?getProgress():touch.start();
                gesture.begin(x(event,0),start,getMax(),getWidth()-getPaddingLeft()-getPaddingRight());
                setProgress(Math.round(gesture.progress()));
                if (getParent()!=null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!tracking) return false;
                int index=event.findPointerIndex(pointer);
                if (index<0) cancelTouch(); else move(event,index);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN: return tracking;
            case MotionEvent.ACTION_POINTER_UP:
                if (!tracking) return false;
                if (event.getPointerId(event.getActionIndex())==pointer) {
                    int replacement=event.getActionIndex()==0?1:0;
                    pointer=event.getPointerId(replacement); gesture.reanchor(x(event,replacement));
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!tracking) return false;
                int last=event.findPointerIndex(pointer);
                if (last>=0) move(event,last);
                boolean clicked=!gesture.moved();
                cancelTouch();
                if (clicked) performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (!tracking) return false;
                cancelTouch(); return true;
            default: return tracking;
        }
    }
    void cancelTouch() {
        if (!tracking) return;
        tracking=false; pointer=-1; setPressed(false);
        if (getParent()!=null) getParent().requestDisallowInterceptTouchEvent(false);
        if (touch!=null) touch.finish(gesture.moved());
    }
    @Override protected void onDetachedFromWindow() { cancelTouch(); super.onDetachedFromWindow(); }
    @Override public boolean performClick() { super.performClick(); return true; }
}
