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
        int inspected=Math.min(source.length,16384),zero=0;
        for(int i=0;i<inspected;i++)if(source[i]==0)zero++;
        boolean sparse=kernel.length>=9 && zero>inspected/8;
        for(double value:kernel)if(!Double.isFinite(value))sparse=false;
        final boolean skipEmpty=sparse;
        rows(w,h,cancelled,(firstRow,lastRow,stop)-> {
        for(int y=firstRow;y<lastRow;y++) {
            if((y&15)==0)check(stop);
            int row=y*w;
            int active=0;if(skipEmpty)for(int j=-radius;j<=radius;j++)if(source[row+reflect(j,w)]!=0)active++;
            for(int x=0;x<w;x++) {
                if(!skipEmpty&&x>=radius&&x+3<w-radius){
                    double s0=0,s1=0,s2=0,s3=0;int first=row+x-radius;
                    for(int j=0;j<kernel.length;j++){double k=kernel[j];int p=first+j;s0+=source[p]*k;s1+=source[p+1]*k;s2+=source[p+2]*k;s3+=source[p+3]*k;}
                    scratch[row+x]=(float)s0;scratch[row+x+1]=(float)s1;scratch[row+x+2]=(float)s2;scratch[row+x+3]=(float)s3;x+=3;continue;
                }
                double sum=0;
                // Interior taps already have valid coordinates; preserve tap and sum order.
                if(skipEmpty&&active==0) {scratch[row+x]=0;}
                else {
                if(x>=radius&&x<w-radius) {
                    int first=row+x-radius;
                    for(int j=0;j<kernel.length;j++)sum+=source[first+j]*kernel[j];
                } else for(int j=-radius;j<=radius;j++)sum+=source[row+reflect(x+j,w)]*kernel[j+radius];
                scratch[row+x]=(float)sum;
                }
                if(skipEmpty&&x+1<w){int leaving=x-radius,entering=x+radius+1;
                    leaving=leaving>=0&&leaving<w?leaving:reflect(leaving,w);entering=entering>=0&&entering<w?entering:reflect(entering,w);
                    if(source[row+leaving]!=0)active--;if(source[row+entering]!=0)active++;
                }
            }
        }
        }); // All horizontal rows finish before any vertical row reads scratch.
        rows(w,h,cancelled,(firstRow,lastRow,stop)-> {
        int[] rows=new int[kernel.length];
        int[] active=skipEmpty?new int[w]:null;
        if(skipEmpty)for(int j=-radius;j<=radius;j++){int row=reflect(firstRow+j,h)*w;for(int x=0;x<w;x++)if(scratch[row+x]!=0)active[x]++;}
        for(int y=firstRow;y<lastRow;y++) {
            if((y&15)==0)check(stop);
            for(int j=-radius;j<=radius;j++)rows[j+radius]=reflect(y+j,h)*w;
            for(int x=0;x<w;x++) {
                if(!skipEmpty&&x+3<w){
                    double s0=0,s1=0,s2=0,s3=0;
                    for(int j=0;j<kernel.length;j++){double k=kernel[j];int p=rows[j]+x;s0+=scratch[p]*k;s1+=scratch[p+1]*k;s2+=scratch[p+2]*k;s3+=scratch[p+3]*k;}
                    int p=y*w+x;out[p]=(float)s0;out[p+1]=(float)s1;out[p+2]=(float)s2;out[p+3]=(float)s3;x+=3;continue;
                }
                if(skipEmpty&&active[x]==0){out[y*w+x]=0;continue;}
                double sum=0;
                for(int j=0;j<kernel.length;j++)sum+=scratch[rows[j]+x]*kernel[j];
                out[y*w+x]=(float)sum;
            }
            if(skipEmpty&&y+1<lastRow){int leaving=reflect(y-radius,h)*w,entering=reflect(y+radius+1,h)*w;
                for(int x=0;x<w;x++){if(scratch[leaving+x]!=0)active[x]--;if(scratch[entering+x]!=0)active[x]++;}}
        }
        });
    }
    /** Exact sparse convolution: omit zero products, retaining increasing tap order and reflection. */
    static void sparseFilter(float[] source,int w,int h,double[] kernel,float[] out,float[] scratch,BooleanSupplier cancelled) {
        for(double value:kernel)if(!Double.isFinite(value)){filter(source,w,h,kernel,out,scratch,cancelled);return;}
        int radius=kernel.length/2;
        rows(w,h,cancelled,(first,last,stop)->{
            int[] positions=new int[w+2*radius];float[] values=new float[positions.length];
            for(int y=first;y<last;y++) {
                check(stop);int row=y*w,count=0;
                for(int x=-radius;x<w+radius;x++) {
                    float value=source[row+reflect(x,w)];
                    if(value!=0){positions[count]=x;values[count++]=value;}
                }
                int left=0,right=0;
                for(int x=0;x<w;x++) {
                    while(left<count&&positions[left]<x-radius)left++;
                    while(right<count&&positions[right]<=x+radius)right++;
                    double sum=0;
                    for(int j=left;j<right;j++)sum+=values[j]*kernel[positions[j]-x+radius];
                    scratch[row+x]=(float)sum;
                }
            }
        });
        // Partition columns: each column's sparse positions are prepared once, not once per row stripe.
        rows(h,w,cancelled,(first,last,stop)->{
            int[] positions=new int[h+2*radius];float[] values=new float[positions.length];
            for(int x=first;x<last;x++) {
                check(stop);int count=0;
                for(int y=-radius;y<h+radius;y++) {
                    float value=scratch[reflect(y,h)*w+x];
                    if(value!=0){positions[count]=y;values[count++]=value;}
                }
                int left=0,right=0;
                for(int y=0;y<h;y++) {
                    while(left<count&&positions[left]<y-radius)left++;
                    while(right<count&&positions[right]<=y+radius)right++;
                    double sum=0;
                    for(int j=left;j<right;j++)sum+=values[j]*kernel[positions[j]-y+radius];
                    out[y*w+x]=(float)sum;
                }
            }
        });
    }
    interface RowWork { void run(int first,int last,BooleanSupplier cancelled); }
    private static final class FilterWorkers {
        static final int COUNT=Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors()));
        static final java.util.concurrent.ForkJoinPool POOL=new java.util.concurrent.ForkJoinPool(COUNT);
    }
    static void rows(int w,int h,BooleanSupplier cancelled,RowWork work) {
        check(cancelled);
        if((long)w*h<131072 || h<8 || FilterWorkers.COUNT==1) {work.run(0,h,cancelled);return;}
        Thread owner=Thread.currentThread();
        BooleanSupplier stop=()->owner.isInterrupted() || cancelled.getAsBoolean();
        java.util.List<java.util.concurrent.ForkJoinTask<?>> tasks=new java.util.ArrayList<>();
        for(int i=0;i<FilterWorkers.COUNT;i++) {
            int first=h*i/FilterWorkers.COUNT,last=h*(i+1)/FilterWorkers.COUNT;
            tasks.add(FilterWorkers.POOL.submit(()->work.run(first,last,stop)));
        }
        Throwable failure=null;
        // Join every stripe before returning or throwing: scratch must never be written after cancellation returns.
        for(java.util.concurrent.ForkJoinTask<?> task:tasks)try {task.join();}
        catch(RuntimeException|Error error){if(failure==null)failure=error;}
        if(failure instanceof Error)throw (Error)failure;
        if(failure!=null)throw (RuntimeException)failure;
        check(cancelled);
    }
}
