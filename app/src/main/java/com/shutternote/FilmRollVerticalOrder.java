package com.shutternote;

/** Geometry for a single opaque row moving along one axis; does not alter records. */
final class FilmRollVerticalOrder {
    private final int[] heights, tops;
    final int source;
    private int target;
    private float top;

    FilmRollVerticalOrder(int[] heights, int source) {
        this.heights=heights.clone(); this.source=source; target=source;
        tops=new int[heights.length];
        for (int i=1;i<heights.length;i++) tops[i]=tops[i-1]+heights[i-1];
        top=tops[source];
    }
    void move(float desiredTop) {
        top=Math.max(0,Math.min(total()-heights[source],desiredTop));
        target=source;
        if (top>tops[source]) {
            for (int i=source+1;i<heights.length;i++) {
                if (top+heights[source]>tops[i]+heights[i]/2f) target=i; else break;
            }
        } else if (top<tops[source]) {
            for (int i=source-1;i>=0;i--) {
                if (top<tops[i]+heights[i]/2f) target=i; else break;
            }
        }
    }
    void target(int index) { target=Math.max(0,Math.min(heights.length-1,index)); top=tops[source]+settledOffset(); }
    int target() { return target; }
    float offset() { return top-tops[source]; }
    int settledOffset() {
        if (target<source) return tops[target]-tops[source];
        if (target>source) return tops[target]+heights[target]-heights[source]-tops[source];
        return 0;
    }
    int siblingOffset(int index) {
        if (index==source) return 0;
        if (target<source && index>=target && index<source) return heights[source];
        if (target>source && index>source && index<=target) return -heights[source];
        return 0;
    }
    private int total() { return tops[heights.length-1]+heights[heights.length-1]; }
}
