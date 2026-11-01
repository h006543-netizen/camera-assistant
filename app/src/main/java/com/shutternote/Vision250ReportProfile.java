package com.shutternote;

import org.json.JSONObject;
import org.json.JSONException;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import static com.shutternote.FilmRenderMath.*;

/** Frozen two-stage report grade followed by synthetic, monochrome grain 20. */
final class Vision250ReportProfile {
    private final LearnedFilmProfile base;
    private final LearnedFilmGrain grain;
    private final double[] anchor,adjustment;
    private static final String[] PARAMETERS={"base_position","brightness","contrast","shadows","highlights","saturation","temperature","tint","red","green","blue"};
    private static final double[] LOW={0,-75,25,-75,-75,0,-150,-150,-75,-75,-75};
    private static final double[] HIGH={150,75,175,75,75,250,150,150,75,75,75};

    Vision250ReportProfile(JSONObject p,LearnedFilmGrain.Noise noise) throws JSONException {
        if(p.getInt("schema_version")!=34 || !p.getString("method").equals("vision250_report_grade_grain20_v1") ||
                !p.getString("color_space").equals("sRGB_D65_encoded") ||
                !p.getString("input_pipeline_version").equals("shutternote-exposure-jpeg-srgb-v1"))
            throw new JSONException("Unsupported 250D report contract");
        JSONObject previous=p.getJSONObject("base_profile");
        if(previous.getInt("schema_version")!=9 || !previous.getString("model_version").equals("vision3-250d-shadow-color-studio-20261023"))
            throw new JSONException("Unsupported 250D report baseline");
        base=new LearnedFilmProfile(previous.toString(),noise);
        anchor=parameters(p.getJSONObject("anchor_parameters"));
        adjustment=parameters(p.getJSONObject("parameters"));
        if(p.getDouble("grain_percent")!=20 || p.getDouble("grain_rms_100_8bit")!=8)
            throw new JSONException("Unsupported 250D grain strength");
        grain=new LearnedFilmGrain(p.getJSONObject("grain"),p.getJSONObject("android_runtime"),noise);
    }

    private static double[] parameters(JSONObject p) throws JSONException {
        if(p.length()!=PARAMETERS.length)throw new JSONException("Invalid report controls");
        double[] values=new double[PARAMETERS.length];
        for(int i=0;i<values.length;i++) {
            Object raw=p.get(PARAMETERS[i]);
            if(!(raw instanceof Number))throw new JSONException("Invalid report control");
            double v=((Number)raw).doubleValue();
            if(!Double.isFinite(v)||v<LOW[i]||v>HIGH[i])throw new JSONException("Invalid report range");
            values[i]=v;
        }
        return values;
    }

    private static double smooth(double lo,double hi,double v) {
        double t=clamp((v-lo)/(hi-lo));return t*t*(3-2*t);
    }

    private static void grade(double r,double g,double b,double[] p,double[] out) {
        double y=r*.2126+g*.7152+b*.0722;
        double rr=r*Math.exp(.11*p[6]/100+.05*p[7]/100+Math.log1p(p[8]/100));
        double gg=g*Math.exp(.008*p[6]/100-.10*p[7]/100+Math.log1p(p[9]/100));
        double bb=b*Math.exp(-.11*p[6]/100+.05*p[7]/100+Math.log1p(p[10]/100));
        double ty=rr*.2126+gg*.7152+bb*.0722;
        double normalize=ty>0?y/ty:1;
        rr=(rr*normalize-y)*p[5]/100;gg=(gg*normalize-y)*p[5]/100;bb=(bb*normalize-y)*p[5]/100;
        double exposure=p[1]/50+p[3]/100*(1-smooth(.12,.55,y))+p[4]/100*smooth(.45,.90,y);
        double k=Math.pow(2,exposure),target=k*y/(1+(k-1)*y);
        double safe=Math.max(1e-9,Math.min(1-1e-9,target)),odds=Math.pow(safe/(1-safe),p[2]/100);
        target=y==0?0:y==1?1:odds/(1+odds);
        double ratio=y>0?target/y:1;rr*=ratio;gg*=ratio;bb*=ratio;
        double scale=1;
        scale=Math.min(scale,rr>0?(1-target)/Math.max(rr,1e-12):target/Math.max(-rr,1e-12));
        scale=Math.min(scale,gg>0?(1-target)/Math.max(gg,1e-12):target/Math.max(-gg,1e-12));
        scale=Math.min(scale,bb>0?(1-target)/Math.max(bb,1e-12):target/Math.max(-bb,1e-12));
        scale=Math.max(scale,0);
        out[0]=clamp(target+rr*scale);out[1]=clamp(target+gg*scale);out[2]=clamp(target+bb*scale);
    }

    void render(int[] pixels,int w,int h,BooleanSupplier cancelled) throws IOException {
        if(w<=0||h<=0||(long)w*h!=pixels.length||pixels.length>Integer.MAX_VALUE/3)
            throw new IllegalArgumentException("Invalid image");
        check(cancelled);
        int[] completed=pixels.clone();
        base.render(completed,w,h,cancelled);
        double[] value=new double[3],rgb=new double[3];
        for(int i=0;i<pixels.length;i++) {
            if((i&1023)==0)check(cancelled);
            for(int c=0;c<3;c++) {
                float source=((pixels[i]>>>(16-c*8))&255)/255f;
                float old=((completed[i]>>>(16-c*8))&255)/255f;
                rgb[c]=(float)(source+(float)(anchor[0]/100)*(old-source));
            }
            grade(rgb[0],rgb[1],rgb[2],anchor,value);
            for(int c=0;c<3;c++) {
                float source=((pixels[i]>>>(16-c*8))&255)/255f;
                float frozen=(float)Math.rint(value[c]*255)/255f;
                rgb[c]=Math.max(0f,Math.min(1f,source+(float)(adjustment[0]/100)*(frozen-source)));
            }
            grade(rgb[0],rgb[1],rgb[2],adjustment,value);
            completed[i]=(pixels[i]&0xff000000)|((int)Math.rint(value[0]*255)<<16)|
                    ((int)Math.rint(value[1]*255)<<8)|(int)Math.rint(value[2]*255);
        }
        // A separate residual lets grain visibility follow the frozen final grade.
        float[] residual=new float[pixels.length*3],levels=new float[pixels.length];
        grain.apply(residual,levels,w,h,cancelled);
        for(int i=0;i<pixels.length;i++) {
            if((i&1023)==0)check(cancelled);
            float r=((completed[i]>>>16)&255)/255f,g=((completed[i]>>>8)&255)/255f,b=(completed[i]&255)/255f;
            float y=r*.2126f+g*.7152f+b*.0722f;
            double visibility=smooth(0,.04,y)*smooth(0,.04,1-y);
            int result=completed[i]&0xff000000;
            for(int c=0;c<3;c++) {
                float channel=((completed[i]>>>(16-c*8))&255)/255f;
                result|=(int)Math.rint(clamp(channel+residual[i*3+c]*visibility)*255)<<(16-c*8);
            }
            completed[i]=result;
        }
        check(cancelled);System.arraycopy(completed,0,pixels,0,pixels.length);
    }
}
