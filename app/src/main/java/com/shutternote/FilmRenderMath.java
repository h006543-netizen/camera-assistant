package com.shutternote;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Numeric helpers shared by the pinned, offline learned-film renderer. */
final class FilmRenderMath {
    static final double[] Y = {.2126f, .7152f, .0722f};
    private FilmRenderMath() { }
    static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
    static double linear(double v) { return v <= .04045 ? v/12.92 : Math.pow((v+.055)/1.055, 2.4); }
    static double srgb(double v) { return v <= .0031308 ? 12.92*v : 1.055*Math.pow(Math.max(0,v),1/2.4)-.055; }
    static double luma(double r, double g, double b) { return r*Y[0]+g*Y[1]+b*Y[2]; }
    static void check(BooleanSupplier cancelled) {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new CancellationException();
    }
    static double[] array(JSONObject object, String key, int length) throws JSONException {
        double[] out = new double[length];
        if (flatten(object.getJSONArray(key), out, 0) != length) throw new JSONException("Invalid coefficients: "+key);
        return out;
    }
    private static int flatten(JSONArray a, double[] out, int at) throws JSONException {
        for (int i=0;i<a.length();i++) {
            Object value = a.get(i);
            if (value instanceof JSONArray) at=flatten((JSONArray)value,out,at);
            else {
                if (!(value instanceof Number) || at>=out.length) throw new JSONException("Invalid coefficients");
                double number=((Number)value).doubleValue();
                if (!Double.isFinite(number)) throw new JSONException("Nonfinite coefficient");
                out[at++]=number;
            }
        }
        return at;
    }
    static double curve(double[] values, double level, int channel, int channels) {
        int count=values.length/channels;
        double p=clamp(level)*(count-1); int low=Math.min((int)p,count-2); double f=p-low;
        return values[low*channels+channel]*(1-f)+values[(low+1)*channels+channel]*f;
    }
    static double[] monotone(double[] input) {
        double[] sums=new double[input.length]; int[] counts=new int[input.length]; int blocks=0;
        for (double value:input) {
            sums[blocks]=value; counts[blocks++]=1;
            while(blocks>1 && sums[blocks-2]/counts[blocks-2]>sums[blocks-1]/counts[blocks-1]) {
                sums[blocks-2]+=sums[blocks-1]; counts[blocks-2]+=counts[blocks-1]; blocks--;
            }
        }
        double[] out=new double[input.length]; int at=0;
        for(int i=0;i<blocks;i++) for(int j=0;j<counts[i];j++) out[at++]=(float)(sums[i]/counts[i]);
        return out;
    }
    static void gamut(double r,double g,double b,float[] out,int offset) {
        double tone=clamp(luma(r,g,b)), y=luma(r,g,b), scale=1;
        r-=y;g-=y;b-=y;double drift=luma(r,g,b);r-=drift;g-=drift;b-=drift;
        scale=Math.min(scale,r>0?(1-tone)/Math.max(r,1e-8):tone/Math.max(-r,1e-8));
        scale=Math.min(scale,g>0?(1-tone)/Math.max(g,1e-8):tone/Math.max(-g,1e-8));
        scale=Math.min(scale,b>0?(1-tone)/Math.max(b,1e-8):tone/Math.max(-b,1e-8));
        scale=Math.max(scale,0);
        out[offset]=(float)clamp(tone+r*scale);out[offset+1]=(float)clamp(tone+g*scale);out[offset+2]=(float)clamp(tone+b*scale);
    }
    static int reflect(int x,int length) {
        if(length==1)return 0;
        int period=2*(length-1); x%=period; if(x<0)x+=period;
        return x<length ? x : period-x;
    }
    static double[] gaussian(double sigma) {
        int length=((int)Math.rint(sigma*8+1))|1;
        length=Math.max(1,length); int radius=length/2;
        double[] kernel=new double[length]; double total=0;
        for(int i=-radius;i<=radius;i++) { kernel[i+radius]=Math.exp(-.5*i*i/(sigma*sigma)); total+=kernel[i+radius]; }
        for(int i=0;i<length;i++)kernel[i]/=total;
        return kernel;
    }
    static void filter(float[] source,int w,int h,double[] kernel,float[] out,float[] scratch,BooleanSupplier cancelled) {
        int radius=kernel.length/2;
        for(int y=0;y<h;y++) {
            if((y&15)==0)check(cancelled);
            int row=y*w;
            for(int x=0;x<w;x++) {
                double sum=0;
                // Interior taps already have valid coordinates; preserve tap and sum order.
                if(x>=radius&&x<w-radius) {
                    int first=row+x-radius;
                    for(int j=0;j<kernel.length;j++)sum+=source[first+j]*kernel[j];
                } else for(int j=-radius;j<=radius;j++)sum+=source[row+reflect(x+j,w)]*kernel[j+radius];
                scratch[row+x]=(float)sum;
            }
        }
        int[] rows=new int[kernel.length];
        for(int y=0;y<h;y++) {
            if((y&15)==0)check(cancelled);
            for(int j=-radius;j<=radius;j++)rows[j+radius]=reflect(y+j,h)*w;
            for(int x=0;x<w;x++) {
                double sum=0;
                for(int j=0;j<kernel.length;j++)sum+=scratch[rows[j]+x]*kernel[j];
                out[y*w+x]=(float)sum;
            }
        }
    }
}
