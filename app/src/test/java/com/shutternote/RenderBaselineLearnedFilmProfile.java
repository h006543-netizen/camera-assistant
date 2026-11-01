package com.shutternote;

import org.json.JSONObject;
import org.json.JSONException;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import static com.shutternote.RenderBaselineFilmRenderMath.*;

/** Pinned schema-9 color, optical response and native grain. Original pixels are caller-owned. */
final class RenderBaselineLearnedFilmProfile {
    private final RenderBaselineLearnedFilmColor color;
    private final RenderBaselineLearnedFilmLights lights;
    private final RenderBaselineLearnedFilmGrain grain;
    private final RenderBaselineE100dManualProfile manual;
    private final RenderBaselineCine800SceneProfile scene;
    RenderBaselineLearnedFilmProfile(String json,RenderBaselineLearnedFilmGrain.Noise noise) throws JSONException {
        JSONObject profile=new JSONObject(json);
        if(profile.getInt("schema_version")==25||profile.getInt("schema_version")==26||profile.getInt("schema_version")==27) {
            scene=new RenderBaselineCine800SceneProfile(profile,noise);manual=null;color=null;lights=null;grain=null;return;
        }
        scene=null;
        if(profile.getInt("schema_version")==24) {
            manual=new RenderBaselineE100dManualProfile(profile);color=null;lights=null;grain=null;return;
        }
        manual=null;
        if(profile.getInt("schema_version")!=9 || !profile.getString("color_space").equals("sRGB_D65_encoded") ||
                !profile.getString("input_pipeline_version").equals("shutternote-exposure-jpeg-srgb-v1"))
            throw new JSONException("Unsupported learned profile contract");
        JSONObject effects=profile.getJSONObject("effects"),runtime=profile.getJSONObject("android_runtime");
        if(!effects.getString("effects_contract").equals("source_adaptive_lights_texture_v4") ||
                !runtime.getString("renderer").equals("shutternote-rbf-lights-v1"))throw new JSONException("Unsupported effects contract");
        color=new RenderBaselineLearnedFilmColor(effects.getJSONObject("separated_color"));
        lights=new RenderBaselineLearnedFilmLights(effects.getJSONObject("halation"),effects.getDouble("reference_edge"));
        grain=new RenderBaselineLearnedFilmGrain(effects.getJSONObject("grain"),runtime,noise);
    }
    void render(int[] pixels,int w,int h,BooleanSupplier cancelled) throws IOException {
        if(scene!=null){scene.render(pixels,w,h,cancelled);return;}
        if(manual!=null){manual.render(pixels,w,h,cancelled);return;}
        if(w<=0 || h<=0 || (long)w*h!=pixels.length || pixels.length>Integer.MAX_VALUE/3)throw new IllegalArgumentException("Invalid image");
        check(cancelled);
        float[] result=color.render(pixels,w,h,cancelled),levels=grain.active()?new float[pixels.length]:null;
        if(levels!=null)for(int i=0;i<pixels.length;i++)levels[i]=(float)luma(result[i*3],result[i*3+1],result[i*3+2]);
        lights.apply(pixels,result,w,h,cancelled);
        if(levels!=null)grain.apply(result,levels,w,h,cancelled);
        for(int i=0;i<pixels.length;i++) {
            if((i&1023)==0)check(cancelled);
            int r=(int)Math.rint(clamp(result[i*3])*255),g=(int)Math.rint(clamp(result[i*3+1])*255),b=(int)Math.rint(clamp(result[i*3+2])*255);
            pixels[i]=(pixels[i]&0xff000000)|(r<<16)|(g<<8)|b;
        }
    }
    int analysisEdge(){return scene==null?0:scene.analysisEdge();}
    void render(int[] pixels,int w,int h,BooleanSupplier cancelled,int[] analysis,int aw,int ah) throws IOException {
        if(scene!=null){scene.render(pixels,w,h,cancelled,analysis,aw,ah);return;}render(pixels,w,h,cancelled);
    }
}
