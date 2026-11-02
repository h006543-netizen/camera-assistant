package com.shutternote;

/** Per-render, bounded exact memoization; scene-dependent results never cross photos. */
final class FilmPixelCache {
    private static int capacity(int pixels){return Integer.highestOneBit(Math.max(256,Math.min(1048576,pixels)));}
    static final class Packed {
        private final int[] keys,values;private final int mask;
        Packed(int pixels){int n=capacity(pixels);keys=new int[n];values=new int[n];mask=n-1;}
        int get(int rgb){int i=((rgb*0x9e3779b9)>>>8)&mask;for(int p=0;p<4;p++){int at=(i+p)&mask;if(keys[at]==rgb+1)return values[at];if(keys[at]==0)return -1;}return -1;}
        void put(int rgb,int value){int i=((rgb*0x9e3779b9)>>>8)&mask,at=i;for(int p=0;p<4;p++){at=(i+p)&mask;if(keys[at]==0||keys[at]==rgb+1)break;}values[at]=value;keys[at]=rgb+1;}
    }
}
