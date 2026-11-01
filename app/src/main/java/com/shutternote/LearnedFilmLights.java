package com.shutternote;

import org.json.JSONObject;
import org.json.JSONException;
import java.util.function.BooleanSupplier;
import static com.shutternote.FilmRenderMath.*;

/** Source-component conditioned, energy-normalized optical response from the saved coefficients. */
final class LearnedFilmLights {
    private final double threshold,kernelWidth,reference;
    private final double[] widths,center,scales,prototypes,gains;
    private final int prototypeCount;
    private final boolean boundaryOnly;
    private static final float[] LINEAR=new float[256];
    static { for(int i=0;i<256;i++)LINEAR[i]=(float)linear(i/255f); }

    LearnedFilmLights(JSONObject p,double reference) throws JSONException {
        if(!p.getString("method").equals("source_light_response_rbf_v4") ||
                !p.getString("kernel_tail").equals("compact_56px_v2"))throw new JSONException("Unsupported light response");
        this.reference=reference;
        threshold=p.getDouble("threshold_linear"); kernelWidth=p.getDouble("kernel_width");
        if(threshold<=0 || threshold>=1 || kernelWidth<=0 || reference<=0)throw new JSONException("Invalid light settings");
        widths=array(p,"sigma_reference_px",4); center=array(p,"descriptor_center",8); scales=array(p,"descriptor_scale",8);
        prototypeCount=p.getJSONArray("prototypes").length();
        if(prototypeCount<1 || prototypeCount>8)throw new JSONException("Invalid light prototypes");
        prototypes=array(p,"prototypes",prototypeCount*8); gains=array(p,"gain_rgb",prototypeCount*18);
        for(double v:scales)if(v<.049999)throw new JSONException("Invalid light scale");
        for(double v:widths)if(v<=0 || v>100)throw new JSONException("Invalid spread width");
        boundaryOnly=p.optString("core_policy").equals("boundary_and_scatter_v2");
    }

    void apply(int[] original,float[] color,int w,int h,BooleanSupplier cancelled) {
        boolean active=false;for(double gain:gains)active|=gain!=0;
        if(!active)return;
        int n=original.length;
        float[] signal=new float[n]; int bright=0; double scene=0;
        for(int i=0;i<n;i++) {
            int p=original[i]; signal[i]=Math.max(LINEAR[(p>>>16)&255],Math.max(LINEAR[(p>>>8)&255],LINEAR[p&255]));
            if(signal[i]>threshold)bright++; scene+=signal[i];
        }
        if(bright==0 || bright==n)return;
        check(cancelled);
        int[] labels=new int[n]; int components=label(signal,labels,w,h,threshold,cancelled);
        double scale=Math.max(w,h)/reference;
        float[] spread=new float[n],scratch=new float[n],energy=new float[n];
        filter(signal,w,h,gaussian(Math.max(1,12*scale)),spread,scratch,cancelled);
        double[] stats=new double[(components+1)*8];
        for(int i=0;i<n;i++) {
            double contrast=clamp((signal[i]-spread[i])/.25);
            energy[i]=(float)(Math.max(signal[i]-threshold,0)/(1-threshold)*contrast);
            int id=labels[i];if(id==0)continue;
            int x=i%w,y=i/w,p=original[i],at=id*8;
            stats[at]++;
            stats[at+1]+=signal[i];
            stats[at+2]+=LINEAR[(p>>>16)&255];stats[at+3]+=LINEAR[(p>>>8)&255];stats[at+4]+=LINEAR[p&255];
            stats[at+5]+=contrast;stats[at+6]+=spread[i];
            stats[at+7]+=(x==0||labels[i-1]!=id?1:0)+(x==w-1||labels[i+1]!=id?1:0)
                    +(y==0||labels[i-w]!=id?1:0)+(y==h-1||labels[i+w]!=id?1:0);
        }
        double[] weights=new double[(components+1)*prototypeCount],d=new double[8];
        for(int id=1;id<=components;id++) {
            double count=stats[id*8],r=stats[id*8+2]/count,g=stats[id*8+3]/count,b=stats[id*8+4]/count;
            double total=Math.max(r+g+b,1e-9),perimeter=stats[id*8+7];
            d[0]=stats[id*8+1]/count;d[1]=r/total;d[2]=b/total;
            d[3]=Math.tanh(Math.log(Math.max(Math.sqrt(count/Math.PI)/scale,.25)/6)/2);
            d[4]=clamp(4*Math.PI*count/Math.max(perimeter*perimeter,1));
            d[5]=stats[id*8+5]/count;d[6]=stats[id*8+6]/count;d[7]=scene/n;
            double sum=0,max=0;
            for(int k=0;k<prototypeCount;k++) {
                double distance=0;
                for(int j=0;j<8;j++) { double diff=(d[j]-center[j])/scales[j]-prototypes[k*8+j];distance+=diff*diff/8; }
                double value=Math.exp(-distance/(2*kernelWidth*kernelWidth));
                weights[id*prototypeCount+k]=value;sum+=value;max=Math.max(max,value);
            }
            for(int k=0;k<prototypeCount;k++)weights[id*prototypeCount+k]*=max/Math.max(sum,1e-12);
        }
        float[] inner=new float[n];
        for(int i=0;i<n;i++)inner[i]=energy[i]>0?1e10f:0;
        distance(inner,w,h);
        double innerWidth=Math.max(2*scale,.1);
        for(int i=0;i<n;i++)inner[i]=energy[i]>0?(float)Math.exp(-.5*Math.pow(inner[i]/innerWidth,2)):0;
        for(int i=0;i<color.length;i++)color[i]=(float)linear(color[i]);
        // Process one source membership at a time, reusing three scalar images.
        for(int k=0;k<prototypeCount;k++) {
            check(cancelled);
            for(int i=0;i<n;i++) {
                signal[i]=(float)(energy[i]*weights[labels[i]*prototypeCount+k]);
                float a=boundaryOnly?0:signal[i]*(1-inner[i]),b=signal[i]*inner[i];
                for(int c=0;c<3;c++)color[i*3+c]+=a*(float)gains[k*18+c]+b*(float)gains[k*18+3+c];
            }
            for(int band=0;band<4;band++) {
                double sigma=Math.max(widths[band]*scale,.1),radius=Math.max(.75,Math.min(4*sigma,56*scale/Math.sqrt(2)));
                int edge=(int)Math.ceil(radius);double[] kernel=new double[edge*2+1];double total=0;
                for(int x=-edge;x<=edge;x++) {
                    kernel[x+edge]=Math.exp(-.5*x*x/(sigma*sigma))*Math.pow(Math.max(1-x*x/(radius*radius),0),2);
                    total+=kernel[x+edge];
                }
                for(int x=0;x<kernel.length;x++)kernel[x]/=total;
                filter(signal,w,h,kernel,spread,scratch,cancelled);
                for(int i=0;i<n;i++) {
                    float field=Math.max(spread[i]-signal[i],0);
                    for(int c=0;c<3;c++)color[i*3+c]+=field*(float)gains[k*18+(band+2)*3+c];
                }
            }
        }
        for(int i=0;i<color.length;i++)color[i]=(float)clamp(srgb(clamp(color[i])));
    }

    private static int label(float[] signal,int[] labels,int w,int h,double threshold,BooleanSupplier cancelled) {
        int[] queue=new int[signal.length];int count=0;
        for(int i=0;i<signal.length;i++) {
            if((i&4095)==0)check(cancelled);
            if(labels[i]!=0 || signal[i]<=threshold)continue;
            count++;int head=0,tail=1;queue[0]=i;labels[i]=count;
            while(head<tail) {
                int p=queue[head++],x=p%w,y=p/w;
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                    int xx=x+dx,yy=y+dy;if(xx<0||xx>=w||yy<0||yy>=h)continue;
                    int at=yy*w+xx;if(labels[at]==0 && signal[at]>threshold) {labels[at]=count;queue[tail++]=at;}
                }
            }
        }
        return count;
    }
    /** OpenCV mask-5 L2 chamfer transform; image boundaries are not artificial zeros. */
    private static void distance(float[] d,int w,int h) {
        // Explicit nearest and knight neighbors, evaluated in both scan directions.
        int[][] offsets={{-1,0},{0,-1},{-1,-1},{1,-1},{-2,-1},{2,-1},{-1,-2},{1,-2}};
        for(int pass=0;pass<2;pass++)for(int yy=0;yy<h;yy++)for(int xx=0;xx<w;xx++) {
            int x=pass==0?xx:w-1-xx,y=pass==0?yy:h-1-yy,at=y*w+x;
            if(d[at]==0)continue;
            for(int[] offset:offsets) {
                int ox=pass==0?offset[0]:-offset[0],oy=pass==0?offset[1]:-offset[1],nx=x+ox,ny=y+oy;
                if(nx<0||nx>=w||ny<0||ny>=h)continue;
                float cost=Math.abs(ox)+Math.abs(oy)==1?1:Math.abs(ox)+Math.abs(oy)==2?1.4f:2.1969f;
                d[at]=Math.min(d[at],d[ny*w+nx]+cost);
            }
        }
    }
}
