package com.shutternote;

import org.json.JSONObject;
import org.json.JSONException;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import static com.shutternote.FilmRenderMath.*;
import static com.shutternote.Cine800Geometry.smooth;

/** Daylight B with smooth cool-colour protection, preserved night and explicit scan-reference grain. */
final class Vision500SceneProfile {
    private final LearnedFilmProfile night;
    private final LearnedFilmColor day;
    private final LearnedFilmLights lights;
    private final LearnedFilmGrain grain;
    private final JSONObject router;
    private final double[] tone,tint;
    private final double strength,protection,redLow,redHigh,greenLow,greenHigh,dayGrain,nightGrain;

    Vision500SceneProfile(JSONObject p,LearnedFilmGrain.Noise noise) throws JSONException {
        if(p.getInt("schema_version")!=30 || !p.getString("method").equals("vision500t_day_b_sky_grain_v1") ||
                !p.getString("color_space").equals("sRGB_D65_encoded") ||
                !p.getString("input_pipeline_version").equals("shutternote-exposure-jpeg-srgb-v1"))
            throw new JSONException("Unsupported 500T scene contract");
        JSONObject previous=p.getJSONObject("night_profile");
        if(previous.getInt("schema_version")!=29 || !previous.getString("model_version").equals("7238369c-3982-5f1f-ab94-6bfa4569d791"))
            throw new JSONException("Unsupported 500T night baseline");
        night=new LearnedFilmProfile(previous.toString(),noise);
        JSONObject color=p.getJSONObject("day_color_response");day=new LearnedFilmColor(color);
        lights=new LearnedFilmLights(p.getJSONObject("halation"),p.getDouble("reference_edge"));
        grain=new LearnedFilmGrain(p.getJSONObject("grain_profile"),p.getJSONObject("android_runtime"),noise);
        strength=bounded(p,"day_strength",0,1);
        dayGrain=bounded(p,"day_grain_percent",0,200)/100;nightGrain=bounded(p,"night_grain_percent",0,200)/100;
        JSONObject sky=p.getJSONObject("sky_protection");protection=bounded(sky,"amount",0,1);
        redLow=bounded(sky,"blue_red_low",-.5,0);redHigh=bounded(sky,"blue_red_high",.001,.5);
        greenLow=bounded(sky,"blue_green_low",-.5,0);greenHigh=bounded(sky,"blue_green_high",.001,.5);
        router=p.getJSONObject("router");
        if(!router.getString("method").equals("source_brightness_nearest_support_v1") || router.getInt("analysis_edge")!=64)
            throw new JSONException("Unsupported 500T scene router");
        for(double s:array(router,"feature_scale",7))if(s<.025)throw new JSONException("Invalid scene feature scale");
        bounded(router,"margin",.001,1);
        for(String key:new String[]{"day","night"}) {
            int count=router.getJSONArray(key).length();if(count<1||count>32)throw new JSONException("Invalid scene support");
            array(router,key,count*7);
        }
        double[] neutral=array(color,"neutral_common",9);tone=new double[257];tint=new double[257*3];
        // The daylight response is deliberately fixed, never conditioned on an image ID.
        for(double v:array(color,"neutral_kernel",color.getJSONArray("descriptors").length()*9))
            if(v!=0)throw new JSONException("Daylight neutral response must be fixed");
        for(int i=0;i<257;i++) {
            double level=i/256.;double r=level+neutral[0]+neutral[3]*level+neutral[6]*level*level;
            double g=level+neutral[1]+neutral[4]*level+neutral[7]*level*level;
            double b=level+neutral[2]+neutral[5]*level+neutral[8]*level*level;
            double y=luma(r,g,b);tone[i]=y;tint[i*3]=r-y;tint[i*3+1]=g-y;tint[i*3+2]=b-y;
        }
        double[] monotone=monotone(tone);for(int i=0;i<tone.length;i++)tone[i]=clamp(monotone[i]);
    }

    private static double bounded(JSONObject p,String key,double low,double high) throws JSONException {
        Object value=p.get(key);if(!(value instanceof Number))throw new JSONException("Invalid 500T value: "+key);
        double v=((Number)value).doubleValue();
        if(!Double.isFinite(v)||v<low||v>high)throw new JSONException("Invalid 500T value: "+key);
        return v;
    }

    double dayWeight(int[] pixels,int w,int h,BooleanSupplier cancel) throws JSONException {
        return dayWeight(pixels,w,h,cancel,true);
    }
    double similarityDayWeight(int[] pixels,int w,int h,BooleanSupplier cancel) throws JSONException {
        return dayWeight(pixels,w,h,cancel,false);
    }
    private double dayWeight(int[] pixels,int w,int h,BooleanSupplier cancel,boolean legacyBrightnessLimits) throws JSONException {
        double[] f=Cine800SceneProfile.features(pixels,w,h,cancel),scale=array(router,"feature_scale",7),distance=new double[2];
        String[] keys={"day","night"};
        for(int k=0;k<2;k++) {
            int count=router.getJSONArray(keys[k]).length();double[] prototypes=array(router,keys[k],count*7);distance[k]=Double.POSITIVE_INFINITY;
            for(int i=0;i<count;i++){double d=0;for(int j=0;j<7;j++){double z=(f[j]-prototypes[i*7+j])/scale[j];d+=z*z/7;}distance[k]=Math.min(distance[k],d);}
        }
        double margin=(distance[1]-distance[0])/Math.max(distance[1]+distance[0],1e-9),m=router.getDouble("margin");
        double weight=smooth(-m,m,margin);
        if(legacyBrightnessLimits){if(f[0]<.015)weight=0;if(f[0]>.40)weight=1;}
        return weight;
    }

    private float[] daylight(int[] pixels,int w,int h,BooleanSupplier cancel) {
        float[] result=day.render(pixels,w,h,cancel),safe=new float[3];
        for(int i=0;i<pixels.length;i++) {
            if((i&1023)==0)check(cancel);int p=pixels[i],at=i*3;
            float r=((p>>>16)&255)/255f,g=((p>>>8)&255)/255f,b=(p&255)/255f,level=(float)luma(r,g,b);
            double t=curve(tone,level,0,1);
            gamut(t+r-level+curve(tint,level,0,3),t+g-level+curve(tint,level,1,3),t+b-level+curve(tint,level,2,3),safe,0);
            double weight=protection*smooth(redLow,redHigh,b-r)*smooth(greenLow,greenHigh,b-g);
            for(int c=0;c<3;c++) {
                float protectedValue=(float)(result[at+c]+weight*(safe[c]-result[at+c]));
                float original=c==0?r:c==1?g:b;
                result[at+c]=(float)(original+strength*(protectedValue-original));
            }
        }
        lights.apply(pixels,result,w,h,cancel);return result;
    }

    int analysisEdge(){return 1024;}
    /** A complete pure-day branch for the newer approved-night wrapper. */
    int[] renderDayComplete(int[] pixels,int w,int h,BooleanSupplier cancel) throws IOException {
        float[] color=daylight(pixels,w,h,cancel);
        if(dayGrain>0 && grain.active()) {
            float[] levels=new float[pixels.length],noisy=color.clone();
            for(int i=0;i<levels.length;i++)levels[i]=(float)luma(color[i*3],color[i*3+1],color[i*3+2]);
            grain.apply(noisy,levels,w,h,cancel);
            for(int i=0;i<levels.length;i++) {
                if((i&1023)==0)check(cancel);double visibility=smooth(0,.04,levels[i])*smooth(0,.04,1-levels[i]);
                for(int c=0;c<3;c++){int at=i*3+c;color[at]=(float)clamp(color[at]+dayGrain*visibility*(noisy[at]-color[at]));}
            }
        }
        int[] completed=new int[pixels.length];
        for(int i=0;i<pixels.length;i++) {
            if((i&1023)==0)check(cancel);int p=pixels[i]&0xff000000;
            for(int c=0;c<3;c++)p|=((int)Math.rint(clamp(color[i*3+c])*255))<<(16-c*8);completed[i]=p;
        }
        return completed;
    }
    void render(int[] pixels,int w,int h,BooleanSupplier cancel,int[] evidence,int ew,int eh) throws IOException {
        if(w<=0||h<=0||(long)w*h!=pixels.length||pixels.length>Integer.MAX_VALUE/3)throw new IllegalArgumentException("Invalid image");
        if(evidence!=null&&(ew<=0||eh<=0||(long)ew*eh!=evidence.length))throw new IllegalArgumentException("Invalid source analysis");
        check(cancel);
        try {
            double weight=dayWeight(evidence==null?pixels:evidence,evidence==null?w:ew,evidence==null?h:eh,cancel);
            float[] color=null;
            if(weight>0)color=daylight(pixels,w,h,cancel);
            if(weight<1) {
                int[] old=pixels.clone();night.render(old,w,h,cancel);
                if(color==null){color=new float[pixels.length*3];for(int i=0;i<old.length;i++)for(int c=0;c<3;c++)color[i*3+c]=((old[i]>>>(16-c*8))&255)/255f;}
                else for(int i=0;i<old.length;i++)for(int c=0;c<3;c++){float n=((old[i]>>>(16-c*8))&255)/255f;color[i*3+c]=(float)(n+weight*(color[i*3+c]-n));}
            }
            double scale=nightGrain+weight*(dayGrain-nightGrain);
            if(scale>0 && grain.active()) {
                float[] levels=new float[pixels.length],noisy=color.clone();
                for(int i=0;i<levels.length;i++)levels[i]=(float)luma(color[i*3],color[i*3+1],color[i*3+2]);
                grain.apply(noisy,levels,w,h,cancel);
                for(int i=0;i<levels.length;i++) {
                    if((i&1023)==0)check(cancel);double visibility=smooth(0,.04,levels[i])*smooth(0,.04,1-levels[i]);
                    for(int c=0;c<3;c++){int at=i*3+c;color[at]=(float)clamp(color[at]+scale*visibility*(noisy[at]-color[at]));}
                }
            }
            check(cancel);int[] completed=new int[pixels.length];
            for(int i=0;i<pixels.length;i++) {
                if((i&1023)==0)check(cancel);int p=pixels[i]&0xff000000;
                for(int c=0;c<3;c++)p|=((int)Math.rint(clamp(color[i*3+c])*255))<<(16-c*8);completed[i]=p;
            }
            check(cancel);System.arraycopy(completed,0,pixels,0,pixels.length);
        }catch(JSONException e){throw new IOException("Invalid 500T scene profile",e);}
    }
}
