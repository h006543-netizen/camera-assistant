package com.shutternote;

import java.util.*;
import java.util.function.BooleanSupplier;
import static com.shutternote.RenderBaselineFilmRenderMath.*;

/** Source-only component geometry. No photo IDs, targets, or capture times. */
final class RenderBaselineCine800Geometry {
    static final class Component {
        int id,x0,y0,x1,y1,count; double cx,cy;
        Component(int id,int w,int h){this.id=id;x0=w;y0=h;}
    }
    static final class Labels {
        final int[] ids;final List<Component> components;
        Labels(int[] ids,List<Component> components){this.ids=ids;this.components=components;}
    }
    static Labels label(boolean[] mask,int w,int h,BooleanSupplier cancel) {
        int[] ids=new int[mask.length],queue=new int[mask.length];List<Component> rows=new ArrayList<>();
        for(int i=0;i<mask.length;i++) {
            if((i&4095)==0)check(cancel);if(!mask[i]||ids[i]!=0)continue;
            Component c=new Component(rows.size()+1,w,h);int head=0,tail=1;queue[0]=i;ids[i]=c.id;
            while(head<tail) {
                int p=queue[head++],x=p%w,y=p/w;c.count++;c.cx+=x;c.cy+=y;
                c.x0=Math.min(c.x0,x);c.y0=Math.min(c.y0,y);c.x1=Math.max(c.x1,x+1);c.y1=Math.max(c.y1,y+1);
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                    int xx=x+dx,yy=y+dy;if(xx<0||yy<0||xx>=w||yy>=h)continue;
                    int at=yy*w+xx;if(mask[at]&&ids[at]==0){ids[at]=c.id;queue[tail++]=at;}
                }
            }
            c.cx/=c.count;c.cy/=c.count;rows.add(c);
        }
        return new Labels(ids,rows);
    }
    static float[] distance(boolean[] core,int w,int h,int[] owners) {
        float[] d=new float[core.length];for(int i=0;i<d.length;i++)d[i]=core[i]?0:1e10f;
        int[][] offsets={{-1,0},{0,-1},{-1,-1},{1,-1},{-2,-1},{2,-1},{-1,-2},{1,-2}};
        for(int pass=0;pass<2;pass++)for(int yy=0;yy<h;yy++)for(int xx=0;xx<w;xx++) {
            int x=pass==0?xx:w-1-xx,y=pass==0?yy:h-1-yy,at=y*w+x;if(d[at]==0)continue;
            for(int[] o:offsets){int nx=x+(pass==0?o[0]:-o[0]),ny=y+(pass==0?o[1]:-o[1]);if(nx<0||ny<0||nx>=w||ny>=h)continue;
                float cost=Math.abs(o[0])+Math.abs(o[1])==1?1:Math.abs(o[0])+Math.abs(o[1])==2?1.4f:2.1969f;
                float v=d[ny*w+nx]+cost;if(v<d[at]){d[at]=v;if(owners!=null)owners[at]=owners[ny*w+nx];}
            }
        }
        return d;
    }
    static boolean[] morph(boolean[] input,int w,int h,int radius,boolean dilate,boolean ellipse,BooleanSupplier cancel) {
        boolean[] out=new boolean[input.length];
        for(int y=0;y<h;y++){if((y&15)==0)check(cancel);for(int x=0;x<w;x++) {
            boolean value=!dilate;
            outer:for(int dy=-radius;dy<=radius;dy++) {
                int span=ellipse?(int)Math.rint(radius*Math.sqrt(Math.max(0,1-dy*dy/(double)(radius*radius)))):radius;
                for(int dx=-span;dx<=span;dx++){int xx=x+dx,yy=y+dy;if(xx<0||yy<0||xx>=w||yy>=h)continue;
                    if(input[yy*w+xx]==dilate){value=dilate;break outer;}}
            }out[y*w+x]=value;
        }}return out;
    }
    static float[] dilate(float[] input,int w,int h,int radius,BooleanSupplier cancel) {
        float[] out=new float[input.length];
        for(int y=0;y<h;y++){if((y&15)==0)check(cancel);for(int x=0;x<w;x++)for(int dy=-radius;dy<=radius;dy++) {
            int yy=y+dy;if(yy<0||yy>=h)continue;int span=(int)Math.rint(radius*Math.sqrt(Math.max(0,1-dy*dy/(double)(radius*radius))));
            for(int dx=-span;dx<=span;dx++){int xx=x+dx;if(xx>=0&&xx<w)out[y*w+x]=Math.max(out[y*w+x],input[yy*w+xx]);}
        }}return out;
    }
    private static long cross(int a,int b,int c,int w){return (long)(b%w-a%w)*(c/w-a/w)-(long)(b/w-a/w)*(c%w-a%w);}
    static boolean[] hulls(boolean[] input,int w,int h,BooleanSupplier cancel) {
        Labels labels=label(input,w,h,cancel);boolean[] output=new boolean[input.length];
        for(Component c:labels.components) {
            ArrayList<Integer> points=new ArrayList<>();
            for(int x=c.x0;x<c.x1;x++)for(int y=c.y0;y<c.y1;y++)if(labels.ids[y*w+x]==c.id)points.add(y*w+x);
            if(points.size()<3){for(int i:points)output[i]=true;continue;}
            int[] hull=new int[points.size()*2];int k=0;
            for(int p:points){while(k>=2&&cross(hull[k-2],hull[k-1],p,w)<=0)k--;hull[k++]=p;}
            int t=k+1;
            for(int n=points.size()-2;n>=0;n--){int p=points.get(n);while(k>=t&&cross(hull[k-2],hull[k-1],p,w)<=0)k--;hull[k++]=p;}
            k--;
            // Fill integer scan lines with inclusive boundary pixels.
            for(int y=c.y0;y<c.y1;y++) {
                double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
                for(int j=0;j<k;j++){int a=hull[j],b=hull[(j+1)%k],ay=a/w,by=b/w;
                    if(y<Math.min(ay,by)||y>Math.max(ay,by))continue;
                    if(ay==by){lo=Math.min(lo,Math.min(a%w,b%w));hi=Math.max(hi,Math.max(a%w,b%w));}
                    else {double x=a%w+(y-ay)*(b%w-a%w)/(double)(by-ay);lo=Math.min(lo,x);hi=Math.max(hi,x);}
                }
                if(Double.isFinite(lo))for(int x=Math.max(0,(int)Math.ceil(lo));x<=Math.min(w-1,(int)Math.floor(hi));x++)output[y*w+x]=true;
            }
            for(int j=0;j<k;j++)line(output,w,h,hull[j]%w,hull[j]/w,hull[(j+1)%k]%w,hull[(j+1)%k]/w);
        }return output;
    }
    private static void line(boolean[] out,int w,int h,int x0,int y0,int x1,int y1){
        if(x0>x1){int t=x0;x0=x1;x1=t;t=y0;y0=y1;y1=t;}
        int dx=x1-x0,dy=Math.abs(y1-y0),sy=y1>=y0?1:-1;
        if(dx>=dy){int error=dx-2*dy;for(int x=x0,y=y0;x<=x1;x++){if(x>=0&&x<w&&y>=0&&y<h)out[y*w+x]=true;if(error<0){y+=sy;error+=2*dx;}error-=2*dy;}}
        else{int error=dy-2*dx,x=x0;for(int t=0,y=y0;t<=dy;t++,y+=sy){if(x>=0&&x<w&&y>=0&&y<h)out[y*w+x]=true;if(error<0){x++;error+=2*dy;}error-=2*dx;}}
    }
    static Labels grouped(boolean[] bright,int w,int h,double scale,double groupRadius,BooleanSupplier cancel) {
        Labels raw=label(bright,w,h,cancel);boolean[] atomic=new boolean[bright.length],fragments=new boolean[bright.length];
        boolean[] use=new boolean[raw.components.size()+1];
        for(Component c:raw.components){int ww=c.x1-c.x0,hh=c.y1-c.y0;use[c.id]=c.count/(scale*scale)>=256&&c.count/(double)(ww*hh)>=.60&&Math.max(ww,hh)/(double)Math.min(ww,hh)<=2.5;}
        for(int i=0;i<bright.length;i++){atomic[i]=use[raw.ids[i]];fragments[i]=bright[i]&&!atomic[i];}
        int r=Math.max(1,(int)Math.rint(groupRadius*scale));boolean[] closed=morph(morph(fragments,w,h,r,true,true,cancel),w,h,r,false,true,cancel);
        closed=hulls(closed,w,h,cancel);boolean[] separation=morph(atomic,w,h,1,true,false,cancel);
        for(int i=0;i<closed.length;i++)closed[i]=(closed[i]&&!separation[i])||atomic[i];
        return label(closed,w,h,cancel);
    }
    static double median(double[] values,int length){if(length==0)return 0;Arrays.sort(values,0,length);return length%2==1?values[length/2]:(values[length/2-1]+values[length/2])/2;}
    static double smooth(double a,double b,double x){double t=clamp((x-a)/(b-a));return t*t*(3-2*t);}
}
