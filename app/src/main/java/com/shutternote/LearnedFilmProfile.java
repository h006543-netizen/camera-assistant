package com.shutternote;

import org.json.JSONObject;
import org.json.JSONException;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import static com.shutternote.FilmRenderMath.*;

/** Pinned schema-9 color, optical response and native grain. Original pixels are caller-owned. */
final class LearnedFilmProfile {
    private final LearnedFilmColor color;
    private final LearnedFilmLights lights;
    private final LearnedFilmGrain grain;
    private final E100dManualProfile manual;
    private final Cine800SceneProfile scene;
    private final Portra400Profile portra;
    private final Vision500ShadowProfile shadow;
    private final Vision500SceneProfile vision500;
    private final Vision500ApprovedProfile approved500;
    private Vision250RecoveryProfile recovery250;
    private Vision500ContentProfile content500;
    private Vision250ReportProfile report250;
    LearnedFilmProfile(String json,LearnedFilmGrain.Noise noise) throws JSONException {
        JSONObject profile=new JSONObject(json);
        if(profile.getInt("schema_version")==34) {
            report250=new Vision250ReportProfile(profile,noise);approved500=null;vision500=null;shadow=null;portra=null;scene=null;manual=null;color=null;lights=null;grain=null;return;
        }
        if(profile.getInt("schema_version")==33) {
            content500=new Vision500ContentProfile(profile,noise);approved500=null;vision500=null;shadow=null;portra=null;scene=null;manual=null;color=null;lights=null;grain=null;return;
        }
        if(profile.getInt("schema_version")==32) {
            recovery250=new Vision250RecoveryProfile(profile,noise);approved500=null;vision500=null;shadow=null;portra=null;scene=null;manual=null;color=null;lights=null;grain=null;return;
        }
        if(profile.getInt("schema_version")==31) {
            approved500=new Vision500ApprovedProfile(profile,noise);vision500=null;shadow=null;portra=null;scene=null;manual=null;color=null;lights=null;grain=null;return;
        }
        approved500=null;
        if(profile.getInt("schema_version")==30) {
            vision500=new Vision500SceneProfile(profile,noise);shadow=null;portra=null;scene=null;manual=null;color=null;lights=null;grain=null;return;
        }
        vision500=null;
        if(profile.getInt("schema_version")==29) {
            shadow=new Vision500ShadowProfile(profile,noise);portra=null;scene=null;manual=null;color=null;lights=null;grain=null;return;
        }
        shadow=null;
        if(profile.getInt("schema_version")==28) {
            portra=new Portra400Profile(profile,noise);scene=null;manual=null;color=null;lights=null;grain=null;return;
        }
        portra=null;
        if(profile.getInt("schema_version")==25||profile.getInt("schema_version")==26||profile.getInt("schema_version")==27) {
            scene=new Cine800SceneProfile(profile,noise);manual=null;color=null;lights=null;grain=null;return;
        }
        scene=null;
        if(profile.getInt("schema_version")==24) {
            manual=new E100dManualProfile(profile);color=null;lights=null;grain=null;return;
        }
        manual=null;
        if(profile.getInt("schema_version")!=9 || !profile.getString("color_space").equals("sRGB_D65_encoded") ||
                !profile.getString("input_pipeline_version").equals("shutternote-exposure-jpeg-srgb-v1"))
            throw new JSONException("Unsupported learned profile contract");
        JSONObject effects=profile.getJSONObject("effects"),runtime=profile.getJSONObject("android_runtime");
        if(!effects.getString("effects_contract").equals("source_adaptive_lights_texture_v4") ||
                !runtime.getString("renderer").equals("shutternote-rbf-lights-v1"))throw new JSONException("Unsupported effects contract");
        color=new LearnedFilmColor(effects.getJSONObject("separated_color"));
        lights=new LearnedFilmLights(effects.getJSONObject("halation"),effects.getDouble("reference_edge"));
        grain=new LearnedFilmGrain(effects.getJSONObject("grain"),runtime,noise);
    }
    void render(int[] pixels,int w,int h,BooleanSupplier cancelled) throws IOException {
        if(report250!=null){report250.render(pixels,w,h,cancelled);return;}
        if(content500!=null){content500.render(pixels,w,h,cancelled,null,0,0,Double.NaN);return;}
        if(recovery250!=null){recovery250.render(pixels,w,h,cancelled,null,0,0);return;}
        if(approved500!=null){approved500.render(pixels,w,h,cancelled,null,0,0);return;}
        if(vision500!=null){vision500.render(pixels,w,h,cancelled,null,0,0);return;}
        if(shadow!=null){shadow.render(pixels,w,h,cancelled);return;}
        if(portra!=null){portra.render(pixels,w,h,cancelled);return;}
        if(scene!=null){scene.render(pixels,w,h,cancelled);return;}
        if(manual!=null){manual.render(pixels,w,h,cancelled);return;}
        float[] result=renderLegacyFloat(pixels,w,h,cancelled);
        for(int i=0;i<pixels.length;i++) {
            if((i&1023)==0)check(cancelled);
            int r=(int)Math.rint(clamp(result[i*3])*255),g=(int)Math.rint(clamp(result[i*3+1])*255),b=(int)Math.rint(clamp(result[i*3+2])*255);
            pixels[i]=(pixels[i]&0xff000000)|(r<<16)|(g<<8)|b;
        }
    }
    /** Keeps the original math/order; versioned finishes may run before the single quantization. */
    float[] renderLegacyFloat(int[] pixels,int w,int h,BooleanSupplier cancelled) throws IOException {
        if(color==null)throw new IllegalStateException("Float baseline requires a legacy profile");
        if(w<=0 || h<=0 || (long)w*h!=pixels.length || pixels.length>Integer.MAX_VALUE/3)throw new IllegalArgumentException("Invalid image");
        check(cancelled);
        float[] result=color.render(pixels,w,h,cancelled),levels=grain.active()?new float[pixels.length]:null;
        if(levels!=null)for(int i=0;i<pixels.length;i++)levels[i]=(float)luma(result[i*3],result[i*3+1],result[i*3+2]);
        lights.apply(pixels,result,w,h,cancelled);
        if(levels!=null)grain.apply(result,levels,w,h,cancelled);
        return result;
    }
    int analysisEdge(){return content500!=null||recovery250!=null?1024:approved500!=null?approved500.analysisEdge():vision500!=null?vision500.analysisEdge():scene==null?0:scene.analysisEdge();}
    double blackLevel(int[] pixels,int w,int h,BooleanSupplier cancelled) {
        if(color==null)throw new IllegalStateException("Black probe requires a legacy learned color profile");
        return color.blackLevel(pixels,w,h,cancelled);
    }
    void render(int[] pixels,int w,int h,BooleanSupplier cancelled,int[] analysis,int aw,int ah) throws IOException {
        if(content500!=null){content500.render(pixels,w,h,cancelled,analysis,aw,ah,Double.NaN);return;}
        if(recovery250!=null){recovery250.render(pixels,w,h,cancelled,analysis,aw,ah);return;}
        if(approved500!=null){approved500.render(pixels,w,h,cancelled,analysis,aw,ah);return;}
        if(vision500!=null){vision500.render(pixels,w,h,cancelled,analysis,aw,ah);return;}
        if(scene!=null){scene.render(pixels,w,h,cancelled,analysis,aw,ah);return;}render(pixels,w,h,cancelled);
    }
    void renderWithSceneTime(int[] pixels,int w,int h,BooleanSupplier cancelled,int[] analysis,int aw,int ah,double captureHour) throws IOException {
        if(content500!=null){content500.render(pixels,w,h,cancelled,analysis,aw,ah,captureHour);return;}
        if(approved500==null)throw new IOException("Scene-time policy requires approved 500T");
        approved500.render(pixels,w,h,cancelled,analysis,aw,ah,true,captureHour);
    }
}
