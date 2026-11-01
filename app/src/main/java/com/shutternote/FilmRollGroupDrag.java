package com.shutternote;

import android.content.ClipData;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.transition.TransitionManager;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Row bodies select/move groups into folders; only dedicated handles reorder a single row. */
final class FilmRollGroupDrag {
    private final LinearLayout list;
    private final ScrollView viewport;
    private final BooleanSupplier allowed;
    private final FilmRollSelection selection;
    private final Runnable selectionChanged;
    private final FilmRollVerticalReorder vertical;
    private final BiConsumer<Set<String>, String> moveFolder;
    private final Map<View, String> targets = new LinkedHashMap<>();
    private final List<View> original = new ArrayList<>();
    private Set<String> moving = new HashSet<>(), baseline;
    private View dragged, rangeSource, hover;
    private String anchor;
    private boolean dropped;
    private float screenX, screenY;
    private final int[] position = new int[2];
    private final Runnable scroll = new Runnable() {
        @Override public void run() {
            if (!active() || dropped) return;
            viewport.getLocationOnScreen(position);
            float y = screenY - position[1], edge = Math.min(dp(64), viewport.getHeight() / 4f);
            if (edge <= 0) return;
            int step = y < edge ? -Math.round(dp(12) * Math.min(1, (edge-y)/edge))
                    : y > viewport.getHeight()-edge ? Math.round(dp(12)*Math.min(1,(y-viewport.getHeight()+edge)/edge)) : 0;
            if (step != 0) { viewport.scrollBy(0, step); if (rangeSource != null) selectAtPointer(); else moveAtPointer(); }
            viewport.postOnAnimation(this);
        }
    };

    FilmRollGroupDrag(LinearLayout list, ScrollView viewport, BooleanSupplier allowed,
            FilmRollSelection selection, Runnable selectionChanged, Consumer<List<String>> saveOrder,
            BiConsumer<Set<String>, String> moveFolder) {
        this.list=list; this.viewport=viewport; this.allowed=allowed; this.selection=selection;
        this.selectionChanged=selectionChanged; this.moveFolder=moveFolder;
        vertical=new FilmRollVerticalReorder(list,viewport,
                ()->allowed.getAsBoolean() && dragged==null && rangeSource==null && !selection.isActive(),selectionChanged,saveOrder);
        viewport.setOnDragListener((view,event) -> {
            if (dragged == null || event.getLocalState() != this) return false;
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED: return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                case DragEvent.ACTION_DRAG_LOCATION:
                    viewport.getLocationOnScreen(position);
                    screenX=position[0]+event.getX(); screenY=position[1]+event.getY();
                    viewport.removeCallbacks(scroll); viewport.postOnAnimation(scroll); moveAtPointer(); return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    viewport.removeCallbacks(scroll); highlight(null); return true;
                case DragEvent.ACTION_DROP:
                    if (!allowed.getAsBoolean()) return false;
                    viewport.getLocationOnScreen(position);
                    screenX=position[0]+event.getX(); screenY=position[1]+event.getY(); moveAtPointer();
                    viewport.removeCallbacks(scroll);
                    if (hover == null) return false;
                    dropped=true;
                    moveFolder.accept(new HashSet<>(moving), targets.get(hover));
                    return true;
                case DragEvent.ACTION_DRAG_ENDED: finish(dropped && event.getResult()); return true;
                default: return true;
            }
        });
    }

    void target(View card, String folder) { targets.put(card, folder); }
    void clearTargets() { highlight(null); targets.clear(); }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    void attach(View card) {
        card.setOnTouchListener((view,event) -> {
            screenX=event.getRawX(); screenY=event.getRawY();
            if (rangeSource != card) return false;
            if (event.getActionMasked()==MotionEvent.ACTION_UP || event.getActionMasked()==MotionEvent.ACTION_CANCEL || event.getPointerCount()>1) {
                if (event.getActionMasked()==MotionEvent.ACTION_UP) selectAtPointer();
                endRange(); return true;
            }
            if (event.getActionMasked()==MotionEvent.ACTION_MOVE) selectAtPointer();
            return true;
        });
        card.setOnLongClickListener(view -> {
            if (!allowed.getAsBoolean() || active()) return true;
            String id=id(card);
            if (selection.contains(id)) lift(card);
            else {
                baseline=selection.snapshot(); anchor=id; rangeSource=card; dropped=false;
                selection.begin(id); selectionChanged.run();
                viewport.requestDisallowInterceptTouchEvent(true);
                viewport.removeCallbacks(scroll); viewport.postOnAnimation(scroll);
            }
            return true;
        });
    }

    void attachHandle(View card, FilmRollReorderHandle handle) {
        handle.setDrag(new FilmRollReorderHandle.Drag() {
            @Override public boolean start(float y) {
                return !active() && vertical.start(card,y);
            }
            @Override public void move(float y) { vertical.move(y); }
            @Override public void finish(boolean cancelled) { vertical.finish(cancelled); }
        });
        handle.setOnClickListener(view -> {
            if (!allowed.getAsBoolean() || active() || selection.isActive()) return;
            int index=children().indexOf(card);
            androidx.appcompat.widget.PopupMenu menu=new androidx.appcompat.widget.PopupMenu(handle.getContext(),handle);
            menu.getMenu().add(0,1,0,R.string.roll_move_up).setEnabled(index>0);
            menu.getMenu().add(0,2,1,R.string.roll_move_down).setEnabled(index>=0 && index<list.getChildCount()-1);
            menu.setOnMenuItemClickListener(item -> { moveOne(card,item.getItemId()==1?-1:1); return true; });
            menu.show();
        });
    }

    private void selectAtPointer() {
        if (rangeSource==null) return;
        list.getLocationOnScreen(position);
        float y=screenY-position[1]; int top=list.getPaddingTop();
        String end=anchor;
        for (View row:children()) {
            end=id(row); top+=row.getMeasuredHeight(); if (y<top) break;
        }
        selection.range(ids(), anchor, end, baseline); selectionChanged.run();
    }

    private void endRange() {
        viewport.removeCallbacks(scroll); viewport.requestDisallowInterceptTouchEvent(false);
        if (rangeSource!=null) { rangeSource.cancelLongPress(); rangeSource.setPressed(false); }
        rangeSource=null; baseline=null; anchor=null;
    }

    private void lift(View source) {
        original.clear(); original.addAll(children());
        moving=new HashSet<>(selection.snapshot());
        moving.retainAll(new HashSet<>(ids()));
        dragged=source; dropped=false;
        source.getLocationOnScreen(position);
        int touchX=source.getWidth()/2, touchY=source.getHeight()/2;
        if (!source.startDragAndDrop(ClipData.newPlainText("", ""), new GroupShadow(source,moving.size(),touchX,touchY,true), this, 0)) {
            dragged=null; original.clear(); return;
        }
        for (View row:original) if (moving.contains(id(row))) row.setAlpha(.28f);
    }

    private void moveAtPointer() {
        if (dragged==null) return;
        View target=null; Rect rect=new Rect();
        for (View candidate:targets.keySet()) if (candidate.getGlobalVisibleRect(rect)) {
            candidate.getRootView().getLocationOnScreen(position); rect.offset(position[0],position[1]);
            if (rect.contains((int)screenX,(int)screenY)) { target=candidate; break; }
        }
        highlight(target);
    }

    private void highlight(View target) {
        if (hover==target) return;
        if (hover!=null) { hover.setActivated(false); hover.animate().scaleX(1).scaleY(1).setDuration(120).start(); }
        hover=target;
        if (hover!=null) { hover.setActivated(true); hover.animate().scaleX(1.025f).scaleY(1.025f).setDuration(120).start(); }
    }

    void moveOne(View card,int direction) {
        if (!allowed.getAsBoolean() || active() || selection.isActive()) return;
        vertical.moveOne(card,direction);
    }

    boolean active() { return dragged!=null || rangeSource!=null || vertical.active(); }
    void cancel() {
        for (View card:children()) {
            View handle=card.findViewById(R.id.filmRollReorder);
            if (handle instanceof FilmRollReorderHandle) ((FilmRollReorderHandle)handle).cancelTouch();
        }
        vertical.cancel(); endRange();
        if (dragged!=null) { View source=dragged; finish(false); source.cancelDragAndDrop(); }
    }
    private void finish(boolean keep) {
        viewport.removeCallbacks(scroll); highlight(null); TransitionManager.endTransitions(list);
        if (!keep && !dropped) { list.removeAllViews(); for (View row:original) list.addView(row); }
        for (View row:original) {
            row.setAlpha(1);
            if (keep && moving.contains(id(row))) { row.setScaleX(1.025f); row.setScaleY(1.025f); row.animate().scaleX(1).scaleY(1).setDuration(180).start(); }
        }
        dragged=null; original.clear(); moving.clear(); dropped=false;
    }
    private List<View> children() { List<View> rows=new ArrayList<>(); for(int i=0;i<list.getChildCount();i++) rows.add(list.getChildAt(i)); return rows; }
    private List<String> ids() { List<String> ids=new ArrayList<>(); for(View row:children()) ids.add(id(row)); return ids; }
    private String id(View row) { return ((FilmRollRecord)row.getTag()).id; }
    private int dp(int value) { return Math.round(value*list.getResources().getDisplayMetrics().density); }

    private final class GroupShadow extends View.DragShadowBuilder {
        private final int count;
        private final int touchX, touchY;
        private final boolean showCount;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        GroupShadow(View view,int count,int touchX,int touchY,boolean showCount) {
            super(view); this.count=count; this.touchX=touchX; this.touchY=touchY; this.showCount=showCount;
        }
        @Override public void onProvideShadowMetrics(Point size,Point touch) {
            size.set(getView().getWidth()+dp(12),getView().getHeight()+dp(12));
            touch.set(touchX,touchY);
        }
        @Override public void onDrawShadow(Canvas canvas) {
            if (count>1) {
                paint.setColor(0xFFE0DED9); canvas.drawRoundRect(dp(10),dp(10),getView().getWidth()+dp(10),getView().getHeight()+dp(10),dp(8),dp(8),paint);
                paint.setColor(0xFFF0EFEB); canvas.drawRoundRect(dp(5),dp(5),getView().getWidth()+dp(5),getView().getHeight()+dp(5),dp(8),dp(8),paint);
            }
            canvas.save(); canvas.clipRect(0,0,getView().getWidth(),getView().getHeight());
            canvas.drawColor(0xFFFAF9F6); getView().draw(canvas); canvas.restore();
            if (!showCount) return;
            String label=Integer.toString(count); paint.setTextSize(dp(14));
            float width=Math.max(dp(32),paint.measureText(label)+dp(16)), right=getView().getWidth(), bottom=dp(32);
            paint.setColor(Color.BLACK); canvas.drawRoundRect(right-width,0,right,bottom,dp(16),dp(16),paint);
            paint.setColor(Color.WHITE); paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(label,right-width/2,bottom/2-(paint.ascent()+paint.descent())/2,paint);
        }
    }
}
