package com.shutternote;

import org.json.JSONObject;
import org.json.JSONException;
import java.util.function.BooleanSupplier;
import static com.shutternote.FilmRenderMath.*;

/** Whole-source conditioned RGB / diagonal-zero RBF response; no image IDs or target pixels. */
final class LearnedFilmColor {
    private final int references, size;
    private final double width, bandwidth;
    private final double[] center,scale,descriptors,neutralCommon,colorCommon,neutralKernel,colorKernel;

    LearnedFilmColor(JSONObject p) throws JSONException {
        String method=p.getString("method");
        boolean rbf=method.equals("source_conditioned_rbf_v5");
        if(!rbf && !method.equals("source_conditioned_rgb_v4"))throw new JSONException("Unsupported learned color response");
        references=p.getJSONArray("descriptors").length();
        size=rbf ? p.getInt("basis_grid") : 0;
        if(references<1 || references>32 || rbf && (size<3 || size>5))throw new JSONException("Invalid scene support");
        width=p.getDouble("width"); bandwidth=rbf?p.getDouble("basis_bandwidth"):0;
        if(width<=0 || rbf && bandwidth<=0)throw new JSONException("Invalid response width");
        center=array(p,"center",8); scale=array(p,"scale",8);
        for(double v:scale)if(v<.049999)throw new JSONException("Invalid scene scale");
        descriptors=array(p,"descriptors",references*8);
        neutralCommon=array(p,"neutral_common",9);
        int colors=rbf?size*size*size:2;
        colorCommon=array(p,"color_common",colors*3);
        neutralKernel=array(p,"neutral_kernel",references*9);
        colorKernel=array(p,"color_kernel",references*colors*3);
    }

    /** Neutral black under the complete source's context; the legacy renderer is unchanged. */
    double blackLevel(int[] source,int w,int h,BooleanSupplier cancelled) {
        check(cancelled);
        double[] d=E100dColorProfile.descriptor(source,w,h),neutral=neutralCommon.clone();
        for(int i=0;i<references;i++) {
            double distance=0;
            for(int j=0;j<8;j++){double v=(descriptors[i*8+j]-d[j])/scale[j];distance+=v*v/8;}
            double weight=(float)Math.exp(-distance/(2*width*width));
            for(int j=0;j<neutral.length;j++)neutral[j]+=weight*neutralKernel[i*neutral.length+j];
        }
        double[] tone=new double[257];
        for(int i=0;i<257;i++) {
            double level=i/256.0;
            tone[i]=luma(level+neutral[0]+neutral[3]*level+neutral[6]*level*level,
                    level+neutral[1]+neutral[4]*level+neutral[7]*level*level,
                    level+neutral[2]+neutral[5]*level+neutral[8]*level*level);
        }
        double t=clamp(monotone(tone)[0]),y=luma(neutral[0],neutral[1],neutral[2]);
        float[] black=new float[3];gamut(t+neutral[0]-y,t+neutral[1]-y,t+neutral[2]-y,black,0);
        check(cancelled);return luma(black[0],black[1],black[2]);
    }

    float[] render(int[] source,int w,int h,BooleanSupplier cancelled) {
        check(cancelled);
        double[] d=E100dColorProfile.descriptor(source,w,h), weights=new double[references];
        for(int i=0;i<references;i++) {
            double distance=0;
            for(int j=0;j<8;j++) { double v=(descriptors[i*8+j]-d[j])/scale[j]; distance+=v*v/8; }
            weights[i]=(float)Math.exp(-distance/(2*width*width));
        }
        double[] neutral=neutralCommon.clone(), color=colorCommon.clone();
        for(int i=0;i<references;i++) {
            for(int j=0;j<neutral.length;j++)neutral[j]+=weights[i]*neutralKernel[i*neutral.length+j];
            for(int j=0;j<color.length;j++)color[j]+=weights[i]*colorKernel[i*color.length+j];
        }
        double[] tone=new double[257],tint=new double[257*3];
        for(int i=0;i<257;i++) {
            double level=i/256.0;
            double r=level+neutral[0]+neutral[3]*level+neutral[6]*level*level;
            double g=level+neutral[1]+neutral[4]*level+neutral[7]*level*level;
            double b=level+neutral[2]+neutral[5]*level+neutral[8]*level*level;
            double y=luma(r,g,b); tone[i]=y;
            tint[i*3]=r-y; tint[i*3+1]=g-y; tint[i*3+2]=b-y;
        }
        tone=monotone(tone); for(int i=0;i<257;i++)tone[i]=clamp(tone[i]);
        // Exploit separability: source values are 8-bit, so the 125 RBF terms
        // reduce to a small blue-axis sum after two tensor contractions.
        float[] exponent=null,rg=null;
        double[] diagonal=null;
        if(size>0) {
            exponent=new float[256*size];
            for(int v=0;v<256;v++)for(int k=0;k<size;k++) {
                float difference=(float)v/255-(float)(.05+.9*k/(size-1));
                exponent[v*size+k]=(float)Math.exp(-difference*difference/(2*bandwidth*bandwidth));
            }
            double[] red=new double[256*size*size*3];
            for(int r=0;r<256;r++)for(int j=0;j<size;j++)for(int k=0;k<size;k++)for(int i=0;i<size;i++)for(int c=0;c<3;c++)
                red[((r*size+j)*size+k)*3+c]+=exponent[r*size+i]*color[((i*size+j)*size+k)*3+c];
            rg=new float[256*256*size*3];
            for(int r=0;r<256;r++)for(int g=0;g<256;g++)for(int k=0;k<size;k++)for(int c=0;c<3;c++) {
                double sum=0;
                for(int j=0;j<size;j++)sum+=red[((r*size+j)*size+k)*3+c]*exponent[g*size+j];
                rg[((r*256+g)*size+k)*3+c]=(float)sum;
            }
            diagonal=new double[4097*3];
            double[] e=new double[size];
            for(int step=0;step<=4096;step++) {
                double level=step/4096.;
                for(int i=0;i<size;i++)e[i]=Math.exp(-Math.pow(level-(float)(.05+.9*i/(size-1)),2)/(2*bandwidth*bandwidth));
                for(int i=0;i<size;i++)for(int j=0;j<size;j++)for(int k=0;k<size;k++)for(int c=0;c<3;c++)
                    diagonal[step*3+c]+=e[i]*e[j]*e[k]*color[((i*size+j)*size+k)*3+c];
            }
        }
        float[] output=new float[source.length*3];
        double[] delta=new double[3];
        for(int at=0;at<source.length;at++) {
            if((at&1023)==0)check(cancelled);
            int pixel=source[at],ri=(pixel>>>16)&255,gi=(pixel>>>8)&255,bi=pixel&255;
            float r=ri/255f,g=gi/255f,b=bi/255f,level=(float)luma(r,g,b);
            double t=curve(tone,level,0,1);
            for(int c=0;c<3;c++) {
                double correction=0;
                if(size>0) {
                    for(int k=0;k<size;k++)correction+=rg[((ri*256+gi)*size+k)*3+c]*exponent[bi*size+k];
                    correction-=curve(diagonal,level,c,3);
                    if(ri==gi && gi==bi)correction=0;
                } else correction=((r-level)*color[c]+(b-level)*color[3+c])/.25;
                delta[c]=t+(c==0?r:c==1?g:b)-level+curve(tint,level,c,3)+correction;
            }
            gamut(delta[0],delta[1],delta[2],output,at*3);
        }
        return output;
    }
}
