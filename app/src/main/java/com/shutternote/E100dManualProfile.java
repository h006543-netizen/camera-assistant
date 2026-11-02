package com.shutternote;

import org.json.JSONObject;
import org.json.JSONException;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import static com.shutternote.FilmRenderMath.*;

/** Exact manual schema-2 sequence, pinned as portable renderer schema 24. */
final class E100dManualProfile {
    private final double[] slopes,q;
    private final double saturation,attenuation,greenRatio,density,protection,legacyGreen;
    private final double blueScale,teal,transfer,greenEffect;
    private final double greenPercent,bluePercent,blackPercent,basePercent;
    private final double[] lightGate,blueGate;
    private static final double[][] BRIGHTNESS_BASIS=brightnessBasis();
    private static double[][] brightnessBasis(){
        double[][] out=new double[256][6];int[] choose={1,6,15,20,15,6,1};
        for(int level=0;level<256;level++){double v=level/255f;double[] b=new double[7];for(int i=0;i<7;i++)b[i]=choose[i]*Math.pow(v,i)*Math.pow(1-v,6-i);for(int j=0;j<6;j++)for(int i=j+1;i<7;i++)out[level][j]+=b[i]/6;}
        return out;
    }

    E100dManualProfile(JSONObject profile) throws JSONException {
        if(profile.getInt("schema_version")!=24 || !profile.getString("film_name").equals("E100D") ||
           !profile.getString("color_space").equals("sRGB_D65_encoded") ||
           !profile.getString("input_pipeline_version").equals("shutternote-exposure-jpeg-srgb-v1"))
            throw new JSONException("Unsupported manual E100D contract");
        JSONObject p=profile.getJSONObject("effects").getJSONObject("separated_color");
        if(!p.getString("method").equals("e100d_manual_controls_v9") || !p.getString("engine").equals("e100d_manual_tuning_schema2"))
            throw new JSONException("Unsupported E100D controls");
        JSONObject b=p.getJSONObject("baseline"),g=b.getJSONObject("previous"),t=g.getJSONObject("previous"),a=t.getJSONObject("approved"),s=a.getJSONObject("base");
        if(!b.getString("method").equals("e100d_green_channel_v8") || !g.getString("method").equals("e100d_green_gain_v6") ||
           !t.getString("method").equals("e100d_teal_transfer_v5") || !s.getString("method").equals("e100d_smooth_hue_v2") ||
           s.getJSONArray("tone_slopes").length()!=5)throw new JSONException("Unsupported E100D baseline");
        slopes=array(s,"tone_slopes",30);q=array(s,"saturation_coefficients",5);saturation=s.getDouble("saturation_gain_max");
        attenuation=a.getDouble("red_log_attenuation");greenRatio=a.getDouble("green_ratio");density=a.getDouble("black_density");protection=a.getDouble("shadow_protection");
        legacyGreen=b.getDouble("relative_green_gain");blueScale=t.getDouble("blue_effect_scale");teal=t.getDouble("teal_gain");transfer=t.getDouble("blue_to_green_transfer");greenEffect=g.getDouble("green_effect_scale");
        lightGate=array(a,"light_gate",2);blueGate=array(a,"blue_gate",2);
        JSONObject v=p.getJSONObject("parameters");
        if(!v.getString("pipeline").equals("base_then_controls"))throw new JSONException("Unsupported manual pipeline");
        greenPercent=bounded(v,"green_percent",0,60);bluePercent=bounded(v,"blue_percent",0,150);
        blackPercent=bounded(v,"black_percent",0,500);basePercent=bounded(v,"base_percent",0,100);
    }
    private static double bounded(JSONObject p,String key,double low,double high) throws JSONException {
        double v=p.getDouble(key);if(!Double.isFinite(v)||v<low||v>high)throw new JSONException("Invalid manual value: "+key);return v;
    }
    private static double smooth(double a,double b,double x) {double t=clamp((x-a)/(b-a));return t*t*(3-2*t);}
    private static double max(double[] x) {return Math.max(x[0],Math.max(x[1],x[2]));}
    private static void floats(double[] x) {for(int c=0;c<3;c++)x[c]=(float)x[c];}
    private static double[] hsv(double r,double g,double b) {
        float v=(float)Math.max(r,Math.max(g,b)),minimum=(float)Math.min(r,Math.min(g,b));
        float d=v-minimum,eps=1.1920929e-7f,s=d/(v+eps),h;
        if(d==0)h=0;
        else if(v==(float)r)h=(float)((g-b)*(60f/(d+eps)));
        else if(v==(float)g)h=(float)(120+(b-r)*(60f/(d+eps)));
        else h=(float)(240+(r-g)*(60f/(d+eps)));
        if(h<0)h+=360;
        return new double[]{h,s,v};
    }
    double strength(int[] pixels,int w,int h,BooleanSupplier cancelled) {
        double scale=Math.min(1,256.0/Math.max(w,h));int ww=Math.max(1,(int)Math.rint(w*scale)),hh=Math.max(1,(int)Math.rint(h*scale));
        float[] rgb=new float[ww*hh*3],values=new float[ww*hh];
        // Area integration matches INTER_AREA on encoded float source pixels.
        for(int y=0;y<hh;y++) {
            check(cancelled);double y0=y*h/(double)hh,y1=(y+1)*h/(double)hh;
            for(int x=0;x<ww;x++) {
                double x0=x*w/(double)ww,x1=(x+1)*w/(double)ww,area=(x1-x0)*(y1-y0);double[] sum=new double[3];
                for(int yy=(int)y0;yy<Math.ceil(y1)&&yy<h;yy++)for(int xx=(int)x0;xx<Math.ceil(x1)&&xx<w;xx++) {
                    double weight=(Math.min(xx+1,x1)-Math.max(xx,x0))*(Math.min(yy+1,y1)-Math.max(yy,y0))/area;int px=pixels[yy*w+xx];
                    for(int c=0;c<3;c++)sum[c]+=((px>>>(16-c*8))&255)/255f*weight;
                }
                int at=y*ww+x;for(int c=0;c<3;c++)rgb[at*3+c]=(float)sum[c];
                values[at]=Math.max(rgb[at*3],Math.max(rgb[at*3+1],rgb[at*3+2]));
            }
        }
        float[] sorted=values.clone();Arrays.sort(sorted);int n=sorted.length;
        float median=n%2==0?(sorted[n/2-1]+sorted[n/2])/2:sorted[n/2];double blueArea=0,light=0;
        for(int i=0;i<n;i++) {
            float r=rgb[i*3],g=rgb[i*3+1],b=rgb[i*3+2],v=values[i];double s=hsv(r,g,b)[1];
            float d=b-(.65f*r+.35f*g);double blue=smooth(.005,.105,d),white=1-smooth(.15,.40,s);
            double contrast=smooth(.12,.50,v-median),core=Math.max(blue,.6*white)*smooth(.35,.80,v)*contrast;
            blueArea+=blue*smooth(.12,.42,v);light+=Math.pow(core,4);
        }
        double a=smooth(lightGate[0],lightGate[1],Math.pow(light/n,.25)),b=smooth(blueGate[0],blueGate[1],blueArea/n);
        return 1-(1-a)*(1-b);
    }
    /** Scratch arrays are private to a render and reset for each uncached RGB value. */
    private static final class Workspace {
        final double[][] values=new double[32][5];int at;
        double[] next(){return values[at++];}
        double[] zeros(){double[] v=next();Arrays.fill(v,0);return v;}
        double[] rgb(double r,double g,double b){double[] v=next();v[0]=r;v[1]=g;v[2]=b;return v;}
        double[] copy(double[] source){return rgb(source[0],source[1],source[2]);}
        double[] harmonics(double theta){double[] v=next();v[0]=1;v[1]=Math.cos(theta);v[2]=Math.sin(theta);v[3]=Math.cos(2*theta);v[4]=Math.sin(2*theta);return v;}
    }
    private double[] smoothBase(double[] source,Workspace work) {
        double[] hv=hsv(source[0],source[1],source[2]);double s=hv[1],v=hv[2],theta=hv[0]*Math.PI/180;
        double[] harmonic=work.harmonics(theta),weights=work.zeros();
        double confidence=s*s/(s*s+.25*.25),total=0;
        weights[0]=1-confidence;
        for(int i=0;i<4;i++){weights[i+1]=Math.exp(2*Math.cos(theta-(45+90*i)*Math.PI/180));total+=weights[i+1];}
        for(int i=1;i<5;i++)weights[i]=weights[i]/total*confidence;
        double[] basis=BRIGHTNESS_BASIS[(int)Math.rint(v*255)];
        double t=0;
        for(int j=0;j<6;j++){double slope=0;for(int i=0;i<5;i++)slope+=weights[i]*slopes[i*6+j];t+=basis[j]*slope;}
        double logit=0;for(int i=0;i<5;i++)logit+=harmonic[i]*q[i];double sig=1/(1+Math.exp(-Math.max(-30,Math.min(30,logit))));
        double extra=saturation*sig*s*s/(s*s+.12*.12),multiplier=(1+extra)/(1+extra*s);double[] out=work.next();
        for(int c=0;c<3;c++)out[c]=(float)clamp(t*(1-(1-(v>0?source[c]/v:1))*multiplier));return out;
    }
    private double[] dense(double[] base,double black,double amount,Workspace work) {
        double v=max(base),den=v+black*amount*(1-v)*(1-v),scale=v>0&&den>0?v/den:1;
        double[] out=work.rgb(base[0]*scale,base[1]*scale,base[2]*scale);floats(out);return out;
    }
    private double[] transmit(double[] base,double black,double red,double amount,Workspace work) {
        double[] z=dense(base,black,amount,work);double v=max(z),depth=red*amount*v*v/(v*v+protection*protection);double[] out=work.next();
        for(int c=0;c<3;c++){double gain=Math.exp(depth*(c==0?-1:c==1?-greenRatio:1));out[c]=(float)(z[c]*gain/(1+z[c]*(gain-1)));}return out;
    }
    private double[] directGreen(double[] rgb,double gain,double amount,Workspace work) {
        double g=rgb[1],room=1-g,desired=g*gain*amount*smooth(.08,.25,max(rgb));
        double[] result=work.copy(rgb);result[1]=(float)Math.min(g+(room>0?room*(-Math.expm1(-desired/room)):0),1);return result;
    }
    private double[] legacy(double[] base,double amount,Workspace work) {
        double[] original=transmit(base,density,attenuation,amount,work),dense=dense(base,density,amount,work),candidate=work.copy(original);
        double gate=smooth(.08,.25,max(original)),blueDelta=Math.max(original[2]-dense[2],0);
        candidate[2]-=(1-blueScale)*gate*blueDelta;
        candidate[1]+=teal*amount*gate*original[1]*(1-original[1])+transfer*gate*blueDelta;floats(candidate);
        double y=luma(original[0],original[1],original[2]),cy=luma(candidate[0],candidate[1],candidate[2]),scale=cy>0?y/cy:1,limit=1;double[] delta=work.next(),old=work.next();
        for(int c=0;c<3;c++){delta[c]=(float)((float)(candidate[c]*scale)-original[c]);if(delta[c]>0)limit=Math.min(limit,(1-original[c])/delta[c]);else if(delta[c]<0)limit=Math.min(limit,original[c]/-delta[c]);}
        for(int c=0;c<3;c++)old[c]=gate==0?original[c]:(float)clamp(original[c]+clamp(limit)*delta[c]);
        double green=gate*(teal*amount*original[1]*(1-original[1])+transfer*blueDelta);
        double extra=(greenEffect-1)*green*smooth(.08,.25,max(old)),ratio=Y[1]/Y[0];extra=Math.min(extra,Math.min(1-old[1],old[0]/ratio));
        old[0]=(float)Math.max(old[0]-ratio*extra,0);old[1]=(float)Math.min(old[1]+extra,1);
        return directGreen(old,legacyGreen,amount,work);
    }
    private double[] pixel(double[] source,double amount,Workspace work) {
        double[] approved=legacy(smoothBase(source,work),amount,work),base=work.next();
        for(int c=0;c<3;c++)base[c]=(float)(source[c]+(float)(basePercent/100)*(float)(approved[c]-source[c]));
        double[] value=bluePercent==0&&blackPercent==0?base:transmit(base,Math.min(blackPercent,50)/100,attenuation*bluePercent/100,amount,work);
        if(blackPercent>50)value=dense(value,(blackPercent-50)/100,1,work);
        return directGreen(value,greenPercent/100,amount,work);
    }
    void render(int[] pixels,int w,int h,BooleanSupplier cancelled) {
        if(w<=0||h<=0||(long)w*h!=pixels.length)throw new IllegalArgumentException("Invalid image");
        check(cancelled);double amount=strength(pixels,w,h,cancelled);int[] output=new int[pixels.length];FilmPixelCache.Packed cache=new FilmPixelCache.Packed(pixels.length);Workspace work=new Workspace();
        for(int i=0;i<pixels.length;i++) {
            if((i&1023)==0)check(cancelled);int px=pixels[i],key=px&0xffffff,value=cache.get(key);
            if(value<0){work.at=0;double[] source=work.rgb(((px>>>16)&255)/255f,((px>>>8)&255)/255f,(px&255)/255f);double[] rgb=pixel(source,amount,work);value=((int)Math.rint(clamp(rgb[0])*255)<<16)|((int)Math.rint(clamp(rgb[1])*255)<<8)|(int)Math.rint(clamp(rgb[2])*255);cache.put(key,value);}
            output[i]=(px&0xff000000)|value;
        }
        check(cancelled);System.arraycopy(output,0,pixels,0,pixels.length);
    }
}
