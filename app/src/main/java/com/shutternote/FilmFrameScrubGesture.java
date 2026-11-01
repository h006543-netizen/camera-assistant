package com.shutternote;

/** Relative finger motion, independent of where on the track the gesture begins. */
final class FilmFrameScrubGesture {
    private float down, last, progress, maximum, width;
    private boolean moved;
    void begin(float x, float value, float max, float trackWidth) {
        down=last=x; maximum=Math.max(0,max); width=Math.max(1,trackWidth);
        progress=Math.max(0,Math.min(maximum,value)); moved=false;
    }
    boolean move(float x, float slop) {
        if (!moved && Math.abs(x-down)<=slop) return false;
        moved=true;
        progress=Math.max(0,Math.min(maximum,progress+(x-last)*maximum/width));
        last=x;
        return true;
    }
    void reanchor(float x) { down=last=x; }
    float progress() { return progress; }
    boolean moved() { return moved; }
}
