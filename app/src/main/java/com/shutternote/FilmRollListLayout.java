package com.shutternote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

/** Draws the actual moving row last without changing its layout order or adding a shadow copy. */
public final class FilmRollListLayout extends LinearLayout {
    private View moving;
    private final Paint separator=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float separatorHeight;
    public FilmRollListLayout(Context context, AttributeSet attrs) {
        super(context,attrs); setChildrenDrawingOrderEnabled(true);
        setClipToPadding(false);
        separator.setColor(Color.rgb(119,119,119));
        separatorHeight=.5f*getResources().getDisplayMetrics().density;
    }
    void setMoving(View row) { moving=row; invalidate(); }
    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (getChildCount()>0) {
            separator.setAlpha(255);
            canvas.drawRect(0,0,getWidth(),separatorHeight,separator);
        }
    }
    @Override protected boolean drawChild(Canvas canvas,View child,long drawingTime) {
        boolean result=super.drawChild(canvas,child,drawingTime);
        int index=indexOfChild(child);
        if (index>=0 && index<getChildCount()-1 && child.getVisibility()==VISIBLE) {
            // Paint inside the row: spacing and variable-height reorder geometry stay intact.
            int saved=canvas.save();
            canvas.translate(child.getLeft(),child.getTop()); canvas.concat(child.getMatrix());
            separator.setAlpha(Math.round(child.getAlpha()*255));
            canvas.drawRect(-child.getLeft(),child.getHeight()-separatorHeight,
                    getWidth()-child.getLeft(),child.getHeight(),separator);
            canvas.restoreToCount(saved);
        }
        return result;
    }
    @Override protected int getChildDrawingOrder(int count,int position) {
        int index=indexOfChild(moving);
        if (index<0) return position;
        if (position==count-1) return index;
        return position>=index?position+1:position;
    }
}
