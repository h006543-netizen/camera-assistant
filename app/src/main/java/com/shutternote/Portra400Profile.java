package com.shutternote;

import org.json.JSONObject;
import org.json.JSONException;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import static com.shutternote.FilmRenderMath.*;

/** Fixed unpaired scan reference grade over the preserved 250D renderer. */
final class Portra400Profile {
    private final LearnedFilmColor color;
    private final LearnedFilmLights lights;
    private final LearnedFilmGrain grain;
    private final double strength,warmth,tint,saturation,contrast,shadows,highlights;

    Portra400Profile(JSONObject profile, LearnedFilmGrain.Noise noise) throws JSONException {
        if(profile.getInt("schema_version")!=28 || !profile.getString("film_name").equals("PORTRA 400") ||
                !profile.getString("color_space").equals("sRGB_D65_encoded") ||
                !profile.getString("input_pipeline_version").equals("shutternote-exposure-jpeg-srgb-v1") ||
                !profile.getString("method").equals("portra400_unpaired_scan_reference_v2"))
            throw new JSONException("Unsupported Portra reference contract");
        JSONObject base=profile.getJSONObject("base_profile");
        if(base.getInt("schema_version")!=9 || !base.getString("color_space").equals("sRGB_D65_encoded") ||
                !base.getString("input_pipeline_version").equals("shutternote-exposure-jpeg-srgb-v1"))
            throw new JSONException("Unsupported Portra baseline");
        JSONObject effects=base.getJSONObject("effects"),runtime=base.getJSONObject("android_runtime");
        if(!effects.getString("effects_contract").equals("source_adaptive_lights_texture_v4") ||
                !runtime.getString("renderer").equals("shutternote-rbf-lights-v1"))
            throw new JSONException("Unsupported Portra baseline effects");
        color=new LearnedFilmColor(effects.getJSONObject("separated_color"));
        lights=new LearnedFilmLights(effects.getJSONObject("halation"),effects.getDouble("reference_edge"));
        grain=new LearnedFilmGrain(effects.getJSONObject("grain"),runtime,noise);
        JSONObject p=profile.getJSONObject("parameters");
        strength=bounded(p,"base",0,100)/100;
        warmth=bounded(p,"warmth",-30,30)/100;tint=bounded(p,"tint",-30,30)/100;
        saturation=bounded(p,"saturation",0,150)/100;contrast=bounded(p,"contrast",50,150)/100;
        shadows=bounded(p,"shadows",-30,30)/100;highlights=bounded(p,"highlights",0,60)/100;
        // Reference scans cannot isolate excess film grain from scanner/subject noise.
        if(bounded(p,"grain",0,200)!=0)throw new JSONException("Unmeasured synthetic Portra grain is not deployed");
    }

    private static double bounded(JSONObject p,String key,double low,double high) throws JSONException {
        Object raw=p.get(key);
        if(!(raw instanceof Number))throw new JSONException("Invalid Portra value: "+key);
        double v=((Number)raw).doubleValue();
        if(!Double.isFinite(v)||v<low||v>high)throw new JSONException("Invalid Portra value: "+key);
        return v;
    }

    void render(int[] pixels,int w,int h,BooleanSupplier cancelled) throws IOException {
        if(w<=0||h<=0||(long)w*h!=pixels.length||pixels.length>Integer.MAX_VALUE/3)
            throw new IllegalArgumentException("Invalid image");
        check(cancelled);
        float[] result=color.render(pixels,w,h,cancelled);
        float[] levels=grain.active()?new float[pixels.length]:null;
        if(levels!=null)for(int i=0;i<pixels.length;i++)levels[i]=(float)luma(result[i*3],result[i*3+1],result[i*3+2]);
        lights.apply(pixels,result,w,h,cancelled);
        if(levels!=null)grain.apply(result,levels,w,h,cancelled);
        int[] completed=new int[pixels.length];
        for(int i=0;i<pixels.length;i++) {
            if((i&1023)==0)check(cancelled);
            double r=(float)(((pixels[i]>>>16)&255)/255f+(float)strength*(result[i*3]-((pixels[i]>>>16)&255)/255f));
            double g=(float)(((pixels[i]>>>8)&255)/255f+(float)strength*(result[i*3+1]-((pixels[i]>>>8)&255)/255f));
            double b=(float)((pixels[i]&255)/255f+(float)strength*(result[i*3+2]-(pixels[i]&255)/255f));
            double y=luma(r,g,b);
            double tone=y+(contrast-1)*y*(1-y)*(2*y-1)+shadows*4*y*(1-y)*(1-y)-highlights*.35*4*y*y*(1-y);
            r=clamp(r+tone-y);g=clamp(g+tone-y);b=clamp(b+tone-y);
            r+=warmth*r*(1-r);g-=tint*g*(1-g);b-=warmth*b*(1-b);
            y=luma(r,g,b);
            r=y+saturation*(r-y);g=y+saturation*(g-y);b=y+saturation*(b-y);
            completed[i]=(pixels[i]&0xff000000)|((int)Math.rint(clamp(r)*255)<<16)|
                    ((int)Math.rint(clamp(g)*255)<<8)|(int)Math.rint(clamp(b)*255);
        }
        check(cancelled);System.arraycopy(completed,0,pixels,0,pixels.length);
    }
}
