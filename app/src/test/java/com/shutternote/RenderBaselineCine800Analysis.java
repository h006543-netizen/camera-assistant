package com.shutternote;

import java.util.function.BooleanSupplier;
import static com.shutternote.RenderBaselineFilmRenderMath.*;

/** Fixed optical coordinates survive memory-limited output decoding. */
final class RenderBaselineCine800Analysis {
    static final class Image {final int[] pixels;final int w,h;Image(int[] pixels,int w,int h){this.pixels=pixels;this.w=w;this.h=h;}}
    static Image normalize(int[] pixels,int w,int h,int edge,BooleanSupplier cancel){
        if(Math.max(w,h)==edge)return new Image(pixels,w,h);
        int ww=Math.max(1,(int)Math.rint(w*edge/(double)Math.max(w,h))),hh=Math.max(1,(int)Math.rint(h*edge/(double)Math.max(w,h)));int[] out=new int[ww*hh];boolean down=ww<=w&&hh<=h;
        for(int y=0;y<hh;y++){check(cancel);for(int x=0;x<ww;x++){
            double[] sum=new double[3];
            if(down){double x0=x*w/(double)ww,x1=(x+1)*w/(double)ww,y0=y*h/(double)hh,y1=(y+1)*h/(double)hh,area=(x1-x0)*(y1-y0);
                for(int yy=(int)y0;yy<Math.ceil(y1)&&yy<h;yy++)for(int xx=(int)x0;xx<Math.ceil(x1)&&xx<w;xx++){double a=(Math.min(xx+1,x1)-Math.max(xx,x0))*(Math.min(yy+1,y1)-Math.max(yy,y0))/area;int p=pixels[yy*w+xx];for(int c=0;c<3;c++)sum[c]+=((p>>>(16-c*8))&255)*a;}
            }else{double sx=(x+.5)*w/ww-.5,sy=(y+.5)*h/hh-.5;int ix=(int)Math.floor(sx),iy=(int)Math.floor(sy);double fx=sx-ix,fy=sy-iy;
                for(int dy=0;dy<2;dy++)for(int dx=0;dx<2;dx++){int xx=Math.max(0,Math.min(w-1,ix+dx)),yy=Math.max(0,Math.min(h-1,iy+dy));double a=(dx==0?1-fx:fx)*(dy==0?1-fy:fy);int p=pixels[yy*w+xx];for(int c=0;c<3;c++)sum[c]+=((p>>>(16-c*8))&255)*a;}
            }
            int p=0xff000000;for(int c=0;c<3;c++)p|=((int)Math.rint(Math.max(0,Math.min(255,sum[c]))))<<(16-c*8);out[y*ww+x]=p;
        }}return new Image(out,ww,hh);
    }
    static RenderBaselineCine800Lights.Fields resize(RenderBaselineCine800Lights.Fields fields,int sw,int sh,int w,int h,BooleanSupplier cancel){
        if(sw==w&&sh==h)return fields;RenderBaselineCine800Lights.Fields out=new RenderBaselineCine800Lights.Fields(w*h);boolean down=w<=sw&&h<=sh;
        for(int y=0;y<h;y++){check(cancel);for(int x=0;x<w;x++){
            double[] sum=new double[5];
            if(down){double x0=x*sw/(double)w,x1=(x+1)*sw/(double)w,y0=y*sh/(double)h,y1=(y+1)*sh/(double)h,area=(x1-x0)*(y1-y0);
                for(int yy=(int)y0;yy<Math.ceil(y1)&&yy<sh;yy++)for(int xx=(int)x0;xx<Math.ceil(x1)&&xx<sw;xx++){double a=(Math.min(xx+1,x1)-Math.max(xx,x0))*(Math.min(yy+1,y1)-Math.max(yy,y0))/area;add(fields,yy*sw+xx,a,sum);}
            }else{double sx=(x+.5)*sw/w-.5,sy=(y+.5)*sh/h-.5;int ix=(int)Math.floor(sx),iy=(int)Math.floor(sy);double fx=sx-ix,fy=sy-iy;
                for(int dy=0;dy<2;dy++)for(int dx=0;dx<2;dx++){int xx=Math.max(0,Math.min(sw-1,ix+dx)),yy=Math.max(0,Math.min(sh-1,iy+dy));add(fields,yy*sw+xx,(dx==0?1-fx:fx)*(dy==0?1-fy:fy),sum);}
            }
            int i=y*w+x;out.red[i]=(float)sum[0];out.weight[i]=(float)sum[1];for(int c=0;c<3;c++)out.target[i*3+c]=(float)(sum[1]>1e-9?sum[2+c]/sum[1]:0);
        }}return out;
    }
    private static void add(RenderBaselineCine800Lights.Fields f,int i,double a,double[] sum){sum[0]+=f.red[i]*a;sum[1]+=f.weight[i]*a;for(int c=0;c<3;c++)sum[2+c]+=(float)(f.target[i*3+c]*f.weight[i])*a;}
}
