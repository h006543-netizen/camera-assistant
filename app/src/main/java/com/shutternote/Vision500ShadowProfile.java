package com.shutternote;

import org.json.JSONObject;
import org.json.JSONException;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import static com.shutternote.FilmRenderMath.*;

/** Versioned deep-shadow finish over the unchanged 500T response. */
final class Vision500ShadowProfile {
    private final LearnedFilmProfile base;
    private final double target,cutoff,maxShape,activationStart,activationFull;

    Vision500ShadowProfile(JSONObject profile,LearnedFilmGrain.Noise noise) throws JSONException {
        if(profile.getInt("schema_version")!=29 ||
                !profile.getString("method").equals("vision500t_deep_shadow_floor_v1") ||
                !profile.getString("color_space").equals("sRGB_D65_encoded") ||
                !profile.getString("input_pipeline_version").equals("shutternote-exposure-jpeg-srgb-v1"))
            throw new JSONException("Unsupported 500T shadow contract");
        JSONObject previous=profile.getJSONObject("base_profile");
        if(previous.getInt("schema_version")!=9 ||
                !previous.getString("model_version").equals("afea45eb-458b-4c64-a391-3790e680e276"))
            throw new JSONException("Unsupported 500T shadow baseline");
        base=new LearnedFilmProfile(previous.toString(),noise);
        JSONObject p=profile.getJSONObject("parameters");
        target=bounded(p,"target_black_8bit",0,8)/255;
        cutoff=bounded(p,"protected_luma_8bit",32,80)/255;
        maxShape=bounded(p,"max_shape",1,20);
        activationStart=bounded(p,"activation_start_8bit",3,16)/255;
        activationFull=bounded(p,"activation_full_8bit",8,24)/255;
        if(target<=0 || target>=cutoff || activationStart<target || activationFull<=activationStart)
            throw new JSONException("Invalid shadow range");
    }

    private static double bounded(JSONObject p,String key,double low,double high) throws JSONException {
        Object value=p.get(key);
        if(!(value instanceof Number))throw new JSONException("Invalid shadow value: "+key);
        double v=((Number)value).doubleValue();
        if(!Double.isFinite(v)||v<low||v>high)throw new JSONException("Invalid shadow value: "+key);
        return v;
    }

    static double shape(double black,double target,double cutoff,double maxShape) {
        if(black<=target || black>=cutoff)return 0;
        return Math.min(maxShape,Math.log(black/target)/Math.pow(1-black/cutoff,2));
    }

    static double activation(double black,double start,double full) {
        double t=clamp((black-start)/(full-start));return t*t*(3-2*t);
    }

    /** Strictly increasing below cutoff, with value and slope matching identity at cutoff. */
    static double gain(double y,double cutoff,double shape) {
        if(y>=cutoff || shape==0)return 1;
        double q=1-y/cutoff;return Math.exp(-shape*q*q);
    }

    void render(int[] pixels,int w,int h,BooleanSupplier cancelled) throws IOException {
        if(w<=0||h<=0||(long)w*h!=pixels.length||pixels.length>Integer.MAX_VALUE/3)
            throw new IllegalArgumentException("Invalid image");
        check(cancelled);
        double black=base.blackLevel(pixels,w,h,cancelled);
        double a=shape(black,target,cutoff,maxShape)*activation(black,activationStart,activationFull);
        float[] color=base.renderLegacyFloat(pixels,w,h,cancelled);
        int[] completed=new int[pixels.length];
        for(int i=0;i<completed.length;i++) {
            if((i&1023)==0)check(cancelled);
            double fr=clamp(color[i*3]),fg=clamp(color[i*3+1]),fb=clamp(color[i*3+2]);
            int r=(int)Math.rint(fr*255),g=(int)Math.rint(fg*255),b=(int)Math.rint(fb*255);
            int p=(pixels[i]&0xff000000)|(r<<16)|(g<<8)|b;completed[i]=p;
            double y=luma(r/255.,g/255.,b/255.);
            if(y>=cutoff || a==0)continue;
            double multiplier=gain(luma(fr,fg,fb),cutoff,a);
            completed[i]=(p&0xff000000)|((int)Math.rint(fr*multiplier*255)<<16)|
                    ((int)Math.rint(fg*multiplier*255)<<8)|(int)Math.rint(fb*multiplier*255);
        }
        check(cancelled);System.arraycopy(completed,0,pixels,0,pixels.length);
    }
}
