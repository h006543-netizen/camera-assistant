package com.shutternote;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Translates real rows vertically. Child order changes only after the final alignment. */
final class FilmRollVerticalReorder {
    private final LinearLayout list;
    private final ScrollView viewport;
    private final BooleanSupplier allowed;
    private final Runnable changed;
    private final Consumer<List<String>> save;
    private final List<View> rows=new ArrayList<>();
    private View moving;
    private Drawable background;
    private FilmRollVerticalOrder order;
    private int[] siblingTargets;
    private float grabOffset, pointerY;
    private boolean settling;
    private final int[] location=new int[2];
    private final Runnable scroll=new Runnable() {
        @Override public void run() {
            if (moving==null || settling) return;
            if (!allowed.getAsBoolean()) { cancel(); return; }
            viewport.getLocationOnScreen(location);
            float y=pointerY-location[1],edge=Math.min(dp(64),viewport.getHeight()/4f);
            if (edge>0) {
                int step=y<edge?-Math.round(dp(12)*Math.min(1,(edge-y)/edge))
                        :y>viewport.getHeight()-edge?Math.round(dp(12)*Math.min(1,(y-viewport.getHeight()+edge)/edge)):0;
                if (step!=0) { viewport.scrollBy(0,step); updatePosition(); }
            }
            viewport.postOnAnimation(this);
        }
    };

    FilmRollVerticalReorder(LinearLayout list,ScrollView viewport,BooleanSupplier allowed,Runnable changed,Consumer<List<String>> save) {
        this.list=list; this.viewport=viewport; this.allowed=allowed; this.changed=changed; this.save=save;
    }
    boolean active() { return moving!=null; }

    boolean start(View row,float screenY) {
        if (active() || !allowed.getAsBoolean() || list.getChildCount()<2 || row.getHeight()<=0) return false;
        rows.clear(); int[] heights=new int[list.getChildCount()];
        for (int i=0;i<list.getChildCount();i++) {
            View child=list.getChildAt(i); child.animate().cancel();
            child.setTranslationX(0); child.setTranslationY(0); child.setAlpha(1); child.setScaleX(1); child.setScaleY(1);
            rows.add(child); heights[i]=child.getHeight();
        }
        int index=rows.indexOf(row);
        if (index<0) { rows.clear(); return false; }
        moving=row; background=row.getBackground();
        order=new FilmRollVerticalOrder(heights,index); siblingTargets=new int[heights.length];
        list.getLocationOnScreen(location); grabOffset=screenY-location[1]-row.getTop(); pointerY=screenY;
        row.setBackground(new ColorDrawable(surfaceColor()));
        row.setAlpha(1); row.setScaleX(1); row.setScaleY(1); row.setTranslationX(0);
        if (list instanceof FilmRollListLayout) ((FilmRollListLayout)list).setMoving(row);
        settling=false;
        viewport.requestDisallowInterceptTouchEvent(true);
        viewport.postOnAnimation(scroll); changed.run();
        return true;
    }

    void move(float screenY) {
        if (moving==null || settling) return;
        if (!allowed.getAsBoolean()) { cancel(); return; }
        pointerY=screenY; updatePosition();
    }
    private void updatePosition() {
        list.getLocationOnScreen(location);
        order.move(pointerY-location[1]-grabOffset-list.getPaddingTop());
        moving.setTranslationX(0); moving.setTranslationY(order.offset());
        animateSiblings();
    }
    private void animateSiblings() {
        for (int i=0;i<rows.size();i++) if (rows.get(i)!=moving) {
            int offset=order.siblingOffset(i);
            if (offset!=siblingTargets[i]) {
                siblingTargets[i]=offset;
                rows.get(i).animate().translationY(offset).setDuration(160).setInterpolator(new DecelerateInterpolator()).start();
            }
        }
    }

    void finish(boolean cancelled) {
        if (moving==null || settling) return;
        settling=true; viewport.removeCallbacks(scroll);
        if (cancelled) order.target(order.source);
        animateSiblings();
        moving.animate().translationY(order.settledOffset()).setDuration(180)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(()->complete(!cancelled)).start();
    }
    void moveOne(View row,int direction) {
        int index=list.indexOfChild(row),next=index+direction;
        if (index<0 || next<0 || next>=list.getChildCount()) return;
        list.getLocationOnScreen(location);
        if (!start(row,location[1]+row.getTop()+row.getHeight()/2f)) return;
        order.target(next); animateSiblings(); finish(false);
    }
    void cancel() {
        if (moving==null) return;
        complete(false);
    }
    private void complete(boolean commit) {
        if (moving==null) return;
        viewport.removeCallbacks(scroll);
        boolean reordered=commit && order.target()!=order.source;
        View finished=moving; Drawable originalBackground=background;
        List<View> originals=new ArrayList<>(rows),result=new ArrayList<>(rows);
        if (reordered) { result.remove(moving); result.add(order.target(),moving); }
        // Clear the gesture before cancellation/detachment can dispatch callbacks into it.
        moving=null; background=null; order=null; rows.clear(); siblingTargets=null; settling=false;
        for (View row:originals) {
            row.animate().withEndAction(null); row.animate().cancel();
            row.setTranslationX(0); row.setTranslationY(0);
        }
        finished.setBackground(originalBackground);
        if (list instanceof FilmRollListLayout) ((FilmRollListLayout)list).setMoving(null);
        if (reordered) { list.removeAllViews(); for (View row:result) list.addView(row); }
        List<String> ids=new ArrayList<>(); for (View row:result) ids.add(((FilmRollRecord)row.getTag()).id);
        viewport.requestDisallowInterceptTouchEvent(false);
        if (reordered) save.accept(ids);
        changed.run();
    }
    private int surfaceColor() {
        View candidate=list;
        while (candidate!=null) {
            Drawable drawable=candidate.getBackground();
            if (drawable instanceof ColorDrawable && Color.alpha(((ColorDrawable)drawable).getColor())==255)
                return ((ColorDrawable)drawable).getColor();
            ViewParent parent=candidate.getParent(); candidate=parent instanceof View?(View)parent:null;
        }
        return androidx.core.content.ContextCompat.getColor(list.getContext(),R.color.note_paper);
    }
    private int dp(int value) { return Math.round(value*list.getResources().getDisplayMetrics().density); }
}
