package com.shutternote;

/** Per-render, bounded exact memoization; scene-dependent results never cross photos. */
final class FilmPixelCache {
    private static int capacity(int pixels){return Integer.highestOneBit(Math.max(256,Math.min(65536,pixels)));}
    static final class Packed {
        private final int[] keys,values;private final int mask;
        Packed(int pixels){int n=capacity(pixels);keys=new int[n];values=new int[n];mask=n-1;}
        int get(int rgb){int i=((rgb*0x9e3779b9)>>>8)&mask;return keys[i]==rgb+1?values[i]:-1;}
        void put(int rgb,int value){int i=((rgb*0x9e3779b9)>>>8)&mask;values[i]=value;keys[i]=rgb+1;}
    }
}
