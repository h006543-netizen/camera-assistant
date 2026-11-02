package com.shutternote;

import org.json.JSONObject;
import org.json.JSONException;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import static com.shutternote.FilmRenderMath.*;

/** Native-pixel grain statistics with a bounded, deterministic Gaussian noise atlas.
 * The learned matrices/scales are unchanged; random pixel placement differs from PC full-frame noise.
 */
final class LearnedFilmGrain {
    interface Noise { float[] load(String asset) throws IOException; }
    private final double nativeEdge;
    private final int edge,parts;
    private final String[] assets;
    private final double[] transforms,centers;
    private final boolean active;
    private final Noise noise;

    LearnedFilmGrain(JSONObject grain,JSONObject runtime,Noise noise) throws JSONException {
        if(!runtime.getString("grain_rng").equals("reflected_native_gaussian_atlas_v1"))throw new JSONException("Unsupported grain noise contract");
        this.noise=noise;nativeEdge=grain.getDouble("native_reference_edge");
        edge=runtime.getInt("grain_atlas_edge");parts=grain.getJSONArray("components").length();
        if(edge!=512 || nativeEdge<64 || nativeEdge>8192 || parts!=3)throw new JSONException("Invalid native grain contract");
        transforms=array(runtime,"grain_transforms",parts*4*9);centers=array(grain,"brightness_centers",4);
        assets=new String[parts];
        for(int i=0;i<parts;i++)assets[i]=runtime.getJSONArray("grain_atlases").getString(i);
        boolean any=false;for(double value:transforms)any|=value!=0;active=any;
    }
    boolean active() { return active; }
    void apply(float[] color,float[] levels,int w,int h,BooleanSupplier cancelled) throws IOException {
        if(!active)return;
        int nw=Math.max(2,(int)Math.rint(w*nativeEdge/Math.max(w,h))),nh=Math.max(2,(int)Math.rint(h*nativeEdge/Math.max(w,h)));
        double rx=nw/(double)w,ry=nh/(double)h;
        int atlasPeriod=edge*2;
        BoxSampler box=rx>=1||ry>=1?new BoxSampler(w,h,rx,ry,atlasPeriod):null;
        BilinearSampler bilinear=box==null?new BilinearSampler(w,h,rx,ry,edge):null;
        for(int part=0;part<parts;part++) {
            check(cancelled);boolean measured=false;
            for(int i=part*36;i<(part+1)*36;i++)measured|=transforms[i]!=0;
            if(!measured)continue;
            float[] atlas=noise.load(assets[part]);
            if(atlas.length!=edge*edge*3)throw new IOException("Invalid grain atlas");
            for(float value:atlas)if(!Float.isFinite(value))throw new IOException("Nonfinite grain atlas");
            int period=edge*2,stride=period+1;
            float[][] integral=rx>=1 || ry>=1?new float[3][stride*stride]:null;
            if(integral!=null)for(int c=0;c<3;c++)for(int y=0;y<period;y++) {
                if((y&31)==0)check(cancelled);
                double row=0;int sy=y<edge?y:period-1-y;
                for(int x=0;x<period;x++) {
                    int sx=x<edge?x:period-1-x;
                    row+=atlas[(sy*edge+sx)*3+c];
                    integral[c][(y+1)*stride+x+1]=(float)(row+integral[c][y*stride+x+1]);
                }
            }
            int component=part;
            rows(w,h,cancelled,(first,last,stop)->{
            for(int y=first;y<last;y++) {
                if((y&15)==0)check(stop);
                for(int x=0;x<w;x++) {
                    int i=y*w+x,lo=0;double level=levels[i];
                    while(lo<2 && level>centers[lo+1])lo++;
                    double fraction=clamp((level-centers[lo])/(centers[lo+1]-centers[lo]));
                    double a=box==null?bilinear.sample(atlas,0,x,y):box.sample(integral[0],x,y);
                    double b=box==null?bilinear.sample(atlas,1,x,y):box.sample(integral[1],x,y);
                    double c=box==null?bilinear.sample(atlas,2,x,y):box.sample(integral[2],x,y);
                    for(int channel=0;channel<3;channel++) {
                        int at=component*36+lo*9+channel*3;
                        double m0=transforms[at]*(1-fraction)+transforms[at+9]*fraction;
                        double m1=transforms[at+1]*(1-fraction)+transforms[at+10]*fraction;
                        double m2=transforms[at+2]*(1-fraction)+transforms[at+11]*fraction;
                        color[i*3+channel]+=(float)(a*m0+b*m1+c*m2);
                    }
                }
            }
            });
        }
    }
    private static final class BoxSampler {
        final double[] qx,xx,qy,yy;final int period;final double area;
        BoxSampler(int w,int h,double rx,double ry,int period){this.period=period;area=rx*ry;qx=new double[w+1];xx=new double[w+1];qy=new double[h+1];yy=new double[h+1];
            for(int i=0;i<=w;i++){double v=i*rx;qx[i]=Math.floor(v/period);xx[i]=v-qx[i]*period;}
            for(int i=0;i<=h;i++){double v=i*ry;qy[i]=Math.floor(v/period);yy[i]=v-qy[i]*period;}}
        double prefix(float[] p,int x,int y){return qx[x]*qy[y]*p[p.length-1]+qx[x]*at(p,period,yy[y],period)+qy[y]*at(p,xx[x],period,period)+at(p,xx[x],yy[y],period);}
        double sample(float[] p,int x,int y){return (prefix(p,x+1,y+1)-prefix(p,x,y+1)-prefix(p,x+1,y)+prefix(p,x,y))/area;}
    }
    private static final class BilinearSampler {
        final int[] x0,x1,y0,y1;final double[] fx,fy;final int edge;
        BilinearSampler(int w,int h,double rx,double ry,int edge){this.edge=edge;x0=new int[w];x1=new int[w];y0=new int[h];y1=new int[h];fx=new double[w];fy=new double[h];
            for(int x=0;x<w;x++){double v=(x+.5)*rx-.5;int i=(int)Math.floor(v);fx[x]=v-i;x0[x]=reflect(i,edge*2);x1[x]=reflect(i+1,edge*2);}
            for(int y=0;y<h;y++){double v=(y+.5)*ry-.5;int i=(int)Math.floor(v);fy[y]=v-i;y0[y]=reflect(i,edge*2);y1[y]=reflect(i+1,edge*2);}}
        private int reflect(int i,int period){int v=Math.floorMod(i,period);return v<edge?v:period-1-v;}
        double sample(float[] atlas,int channel,int x,int y){double result=0;
            result+=atlas[(y0[y]*edge+x0[x])*3+channel]*(1-fx[x])*(1-fy[y]);
            result+=atlas[(y0[y]*edge+x1[x])*3+channel]*fx[x]*(1-fy[y]);
            result+=atlas[(y1[y]*edge+x0[x])*3+channel]*(1-fx[x])*fy[y];
            result+=atlas[(y1[y]*edge+x1[x])*3+channel]*fx[x]*fy[y];return result;}
    }
    private double sample(float[] atlas,float[][] integral,int channel,int x,int y,double rx,double ry,int period) {
        if(integral!=null) {
            double x0=x*rx,y0=y*ry,x1=(x+1)*rx,y1=(y+1)*ry;
            float[] p=integral[channel];
            return (prefix(p,x1,y1,period)-prefix(p,x0,y1,period)-prefix(p,x1,y0,period)+prefix(p,x0,y0,period))/(rx*ry);
        }
        double xx=(x+.5)*rx-.5,yy=(y+.5)*ry-.5;
        int ix=(int)Math.floor(xx),iy=(int)Math.floor(yy);double fx=xx-ix,fy=yy-iy,result=0;
        for(int dy=0;dy<2;dy++)for(int dx=0;dx<2;dx++) {
            int sx=Math.floorMod(ix+dx,period),sy=Math.floorMod(iy+dy,period);
            sx=sx<edge?sx:period-1-sx;sy=sy<edge?sy:period-1-sy;
            result+=atlas[(sy*edge+sx)*3+channel]*(dx==0?1-fx:fx)*(dy==0?1-fy:fy);
        }
        return result;
    }
    private static double prefix(float[] p,double x,double y,int period) {
        double qx=Math.floor(x/period),qy=Math.floor(y/period),rx=x-qx*period,ry=y-qy*period;
        return qx*qy*p[p.length-1]+qx*at(p,period,ry,period)+qy*at(p,rx,period,period)+at(p,rx,ry,period);
    }
    private static double at(float[] p,double x,double y,int period) {
        int ix=Math.min((int)x,period-1),iy=Math.min((int)y,period-1),stride=period+1;
        double fx=x-ix,fy=y-iy;
        return (p[iy*stride+ix]*(1-fx)+p[iy*stride+ix+1]*fx)*(1-fy)
                +(p[(iy+1)*stride+ix]*(1-fx)+p[(iy+1)*stride+ix+1]*fx)*fy;
    }
}
