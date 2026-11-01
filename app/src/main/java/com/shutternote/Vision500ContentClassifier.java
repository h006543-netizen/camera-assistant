package com.shutternote;

import org.json.*;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import static com.shutternote.FilmRenderMath.*;
import static com.shutternote.Cine800Geometry.smooth;

/** Learned holistic spatial-colour/texture classifier; no filenames or object-name rules. */
final class Vision500ContentClassifier {
    static final int FEATURES=204;
    private final double[] center,scale,centers,heads;
    private final int[] labels;
    private final int count;
    private final double width,temperature,soft,hard,start,full,agreeStart,agreeFull,prototypeStart,prototypeFull;

    static final class Decision {
        final double nightProbability,agreement,distance,margin,support,nightWeight,dayWeight;
        final String state;
        Decision(double p,double a,double d,double m,double s,double n) {
            nightProbability=p;agreement=a;distance=d;margin=m;support=s;nightWeight=n;dayWeight=1-n;
            state=n>=.999999?"night":n>0?"mixed":p<=.35?"day":"abstain";
        }
    }
    private static double value(JSONObject p,String key,double lo,double hi) throws JSONException {
        Object o=p.get(key);if(!(o instanceof Number))throw new JSONException("Invalid content classifier value");
        double v=((Number)o).doubleValue();if(!Double.isFinite(v)||v<lo||v>hi)throw new JSONException("Invalid content classifier range");return v;
    }
    Vision500ContentClassifier(JSONObject p) throws JSONException {
        if(!p.getString("method").equals("source_spatial_colour_texture_rbf_v1")||p.getInt("feature_count")!=FEATURES)
            throw new JSONException("Unsupported content classifier");
        center=array(p,"center",FEATURES);scale=array(p,"scale",FEATURES);
        for(double v:scale)if(v<.039999)throw new JSONException("Invalid content scale");
        count=p.getJSONArray("centers").length();if(count<2||count>512)throw new JSONException("Invalid content support count");
        centers=array(p,"centers",count*FEATURES);heads=array(p,"heads",3*(count+1));
        for(double v:centers)if(Math.abs(v)>6.00001)throw new JSONException("Invalid normalized content support");
        for(double v:heads)if(Math.abs(v)>100000)throw new JSONException("Invalid content coefficient");
        double[] lab=array(p,"center_labels",count);labels=new int[count];int day=0,night=0;
        for(int i=0;i<count;i++){if(lab[i]!=0&&lab[i]!=1)throw new JSONException("Invalid content label");labels[i]=(int)lab[i];if(labels[i]==0)day++;else night++;}
        if(day==0||night==0)throw new JSONException("Both content classes required");
        width=value(p,"kernel_width",.1,3);temperature=value(p,"temperature",.05,5);
        soft=value(p,"support_soft",0,100);hard=value(p,"support_hard",.001,100);
        start=value(p,"night_start",.8,.999);full=value(p,"night_full",.9,1);
        agreeStart=value(p,"agreement_start",.5,.999);agreeFull=value(p,"agreement_full",.6,1);
        prototypeStart=value(p,"prototype_start",.5,.95);prototypeFull=value(p,"prototype_full",.6,1);
        if(hard<=soft||full<=start||agreeFull<=agreeStart||prototypeFull<=prototypeStart)throw new JSONException("Invalid confidence transition");
    }
    Decision evaluate(int[] source,int w,int h,BooleanSupplier cancelled) {
        double[] f=features(source,w,h,cancelled),z=new double[FEATURES],kernel=new double[count];
        for(int j=0;j<FEATURES;j++)z[j]=Math.max(-6,Math.min(6,(f[j]-center[j])/scale[j]));
        double nearest=Double.POSITIVE_INFINITY,day=nearest,night=nearest;
        for(int i=0;i<count;i++) {
            if((i&15)==0)check(cancelled);double distance=0;
            for(int j=0;j<FEATURES;j++){double v=z[j]-centers[i*FEATURES+j];distance+=v*v/FEATURES;}
            nearest=Math.min(nearest,distance);if(labels[i]==0)day=Math.min(day,distance);else night=Math.min(night,distance);
            kernel[i]=Math.exp(-distance/(2*width*width));
        }
        double probability=0,agreement=1;
        for(int head=0;head<3;head++) {
            int off=head*(count+1);double logit=heads[off];for(int i=0;i<count;i++)logit+=kernel[i]*heads[off+1+i];
            double p=1/(1+Math.exp(-Math.max(-40,Math.min(40,logit/temperature))));probability+=p/3;agreement=Math.min(agreement,p);
        }
        double support=1-smooth(soft,hard,nearest),margin=(day-night)/Math.max(day+night,1e-9);
        double learned=smooth(start,full,probability)*smooth(agreeStart,agreeFull,agreement);
        double local=smooth(prototypeStart,prototypeFull,margin)*smooth(.45,.55,probability);
        check(cancelled);return new Decision(probability,agreement,nearest,margin,support,Math.max(learned,local)*support);
    }

    /** 16 spatial cells x 12 colour/normalized-texture features + 12 global values. */
    static double[] features(int[] pixels,int w,int h,BooleanSupplier cancelled) {
        if(w<=0||h<=0||(long)w*h!=pixels.length)throw new IllegalArgumentException("Invalid content source");
        check(cancelled);double[] r=new double[4096],g=new double[4096],b=new double[4096],y=new double[4096],log=new double[4096];
        double mean=0,meanY=0;
        for(int sy=0;sy<64;sy++) {
            if((sy&7)==0)check(cancelled);
            double y0=sy*h/64.,y1=(sy+1)*h/64.;
            for(int sx=0;sx<64;sx++) {
                double x0=sx*w/64.,x1=(sx+1)*w/64.,area=(x1-x0)*(y1-y0),rr=0,gg=0,bb=0;
                for(int yy=(int)y0;yy<Math.ceil(y1);yy++)for(int xx=(int)x0;xx<Math.ceil(x1);xx++) {
                    double weight=(Math.min(x1,xx+1)-Math.max(x0,xx))*(Math.min(y1,yy+1)-Math.max(y0,yy))/area;
                    int p=pixels[Math.min(yy,h-1)*w+Math.min(xx,w-1)];
                    rr+=((p>>>16)&255)/255f*weight;gg+=((p>>>8)&255)/255f*weight;bb+=(p&255)/255f*weight;
                }
                int at=sy*64+sx;r[at]=(float)rr;g[at]=(float)gg;b[at]=(float)bb;
                y[at]=r[at]*.2126+g[at]*.7152+b[at]*.0722;log[at]=Math.log(.02+y[at]);mean+=log[at]/4096;meanY+=y[at]/4096;
            }
        }
        double variance=0;for(double v:log)variance+=(v-mean)*(v-mean)/4096;
        double logStd=Math.sqrt(variance),std=Math.max(logStd,.15);double[] z=new double[4096];
        for(int i=0;i<z.length;i++)z[i]=Math.max(-3,Math.min(3,(log[i]-mean)/std));
        double[] out=new double[FEATURES],fields=new double[12];double satMean=0,rgMean=0,bgMean=0,gxMean=0,gyMean=0,lapMean=0,colourMean=0;
        for(int yy=0;yy<64;yy++) {
            if((yy&7)==0)check(cancelled);
            for(int xx=0;xx<64;xx++) {
                int at=yy*64+xx,left=yy*64+Math.max(0,xx-1),right=yy*64+Math.min(63,xx+1),top=Math.max(0,yy-1)*64+xx,bottom=Math.min(63,yy+1)*64+xx;
                double gx=(z[right]-z[left])*.5,gy=(z[bottom]-z[top])*.5,lap=Math.abs(z[left]+z[right]+z[top]+z[bottom]-4*z[at]);
                double max=Math.max(r[at],Math.max(g[at],b[at])),min=Math.min(r[at],Math.min(g[at],b[at]));
                double rg=(r[at]-g[at])/(r[at]+g[at]+b[at]+.1),bg=(b[at]-g[at])/(r[at]+g[at]+b[at]+.1),sat=(max-min)/(max+.05);
                double red=smooth(.03,.15,r[at]-g[at])*smooth(.03,.15,r[at]-b[at]);
                double green=smooth(.03,.15,g[at]-r[at])*smooth(.03,.15,g[at]-b[at]);
                double blue=smooth(.03,.15,b[at]-r[at])*smooth(.03,.15,b[at]-g[at]);
                fields[0]=z[at];fields[1]=z[at]*z[at];fields[2]=rg;fields[3]=bg;fields[4]=sat;fields[5]=Math.abs(gx);fields[6]=Math.abs(gy);
                fields[7]=lap;fields[8]=smooth(1,2.5,z[at])*(Math.abs(gx)+Math.abs(gy));fields[9]=red;fields[10]=green;fields[11]=blue;
                int off=((yy/16)*4+xx/16)*12;for(int j=0;j<12;j++)out[off+j]+=fields[j]/256;
                satMean+=sat/4096;rgMean+=rg/4096;bgMean+=bg/4096;gxMean+=Math.abs(gx)/4096;gyMean+=Math.abs(gy)/4096;lapMean+=lap/4096;colourMean+=(red+green+blue)/4096;
            }
        }
        Arrays.sort(y);out[192]=logStd;out[193]=satMean;out[194]=rgMean;out[195]=bgMean;
        double[] qs={.1,.5,.9};for(int i=0;i<3;i++){double p=qs[i]*4095;int lo=(int)p;out[196+i]=y[lo]+(y[Math.min(lo+1,4095)]-y[lo])*(p-lo);}
        out[199]=meanY;out[200]=gxMean;out[201]=gyMean;out[202]=lapMean;out[203]=colourMean;
        check(cancelled);return out;
    }
}
