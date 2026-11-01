package com.shutternote;

import org.json.JSONObject;
import org.json.JSONException;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import static com.shutternote.FilmRenderMath.*;
import static com.shutternote.Cine800Geometry.smooth;

/** User-approved night tuner settings, with the exact previous daylight branch. */
final class Vision500ApprovedProfile {
    private final Vision500SceneProfile previous;
    private final LearnedFilmProfile night;
    private final LearnedFilmGrain grain;
    private final double nativeGrain,position,grainPercent,brightness,contrast,shadows,highlights,tungsten,temperature,tint,saturation;
    private final double[] channelGain=new double[3];
    // The tuner uses double-precision encoded-sRGB weights, unlike the legacy float coordinate.
    private static double tone(double r,double g,double b){return r*.2126+g*.7152+b*.0722;}
    private static double bounded(JSONObject p,String key,double low,double high) throws JSONException {
        Object value=p.get(key);if(!(value instanceof Number))throw new JSONException("Invalid 500T tuning value: "+key);
        double v=((Number)value).doubleValue();if(!Double.isFinite(v)||v<low||v>high)throw new JSONException("Invalid 500T tuning range: "+key);return v;
    }
    Vision500ApprovedProfile(JSONObject p,LearnedFilmGrain.Noise noise) throws JSONException {
        if(p.getInt("schema_version")!=31||!p.getString("method").equals("vision500t_approved_night_tuning_v1")||
            !p.getString("color_space").equals("sRGB_D65_encoded")||!p.getString("input_pipeline_version").equals("shutternote-exposure-jpeg-srgb-v1"))
            throw new JSONException("Unsupported approved 500T contract");
        JSONObject base=p.getJSONObject("base_profile");
        if(base.getInt("schema_version")!=30||!base.getString("model_version").equals("552f290f-8336-53b0-bdbf-d9ce02ceb54b"))
            throw new JSONException("Unsupported approved 500T baseline");
        previous=new Vision500SceneProfile(base,noise);night=new LearnedFilmProfile(base.getJSONObject("night_profile").toString(),noise);
        grain=new LearnedFilmGrain(base.getJSONObject("grain_profile"),base.getJSONObject("android_runtime"),noise);
        nativeGrain=bounded(base,"night_grain_percent",0,200)/100;
        JSONObject values=p.getJSONObject("night_tuning");
        if(values.length()!=17)throw new JSONException("Invalid 500T tuning keys");
        position=bounded(values,"base_position",0,100);grainPercent=bounded(values,"grain_percent",0,300)/100;
        // The approved report explicitly turns protection off. Reject unsupported masks.
        if(bounded(values,"light_protection",0,100)!=0)throw new JSONException("Approved 500T requires disabled light protection");
        bounded(values,"light_threshold",10,78);bounded(values,"mask_softness",0,100);
        if(!(values.get("protect_adjustments") instanceof Boolean))throw new JSONException("Invalid 500T protection flag");
        brightness=bounded(values,"brightness",-50,50);contrast=bounded(values,"contrast",50,150)/100;
        shadows=bounded(values,"shadows",-50,50);highlights=bounded(values,"highlights",-50,50);
        tungsten=bounded(values,"tungsten",0,600)/100;temperature=bounded(values,"temperature",-100,100)/100;
        tint=bounded(values,"tint",-100,100)/100;saturation=bounded(values,"saturation",0,200)/100;
        channelGain[0]=Math.log1p(bounded(values,"red",-50,50)/100);
        channelGain[1]=Math.log1p(bounded(values,"green",-50,50)/100);
        channelGain[2]=Math.log1p(bounded(values,"blue",-50,50)/100);
    }
    int analysisEdge(){return previous.analysisEdge();}
    double dayWeight(int[] source,int w,int h,BooleanSupplier cancelled) throws JSONException{return previous.dayWeight(source,w,h,cancelled);}

    /** A complete tuner night branch. Source is read-only until the wrapper commits. */
    int[] renderNight(int[] source,int w,int h,BooleanSupplier cancelled) throws IOException {
        int[] clean=source.clone();night.render(clean,w,h,cancelled);
        float[] residual=new float[source.length*3],levels=new float[source.length];
        for(int i=0;i<clean.length;i++)levels[i]=(float)luma(((clean[i]>>>16)&255)/255f,((clean[i]>>>8)&255)/255f,(clean[i]&255)/255f);
        if(nativeGrain>0&&grain.active())grain.apply(residual,levels,w,h,cancelled);
        int[] completed=new int[source.length];double[] sample=new double[3],graded=new double[3];
        double blend=Math.min(position/50,1);
        for(int i=0;i<source.length;i++) {
            if((i&1023)==0)check(cancelled);
            double visibility=smooth(0,.04,levels[i])*smooth(0,.04,1-levels[i]);
            for(int c=0;c<3;c++) {
                int shift=16-c*8,old=(clean[i]>>>shift)&255,original=(source[i]>>>shift)&255;
                float noisy=(float)clamp(old/255f+residual[i*3+c]*(nativeGrain*visibility));
                int full=(int)Math.rint(noisy*255f);
                // Protection=0 makes the middle anchor identical to the native anchor.
                double value=original/255.+blend*(old/255.-original/255.);
                sample[c]=clamp(value+grainPercent*(full-old)/255.);
            }
            grade(sample[0],sample[1],sample[2],graded);
            int packed=source[i]&0xff000000;
            for(int c=0;c<3;c++)packed|=((int)Math.rint(clamp(graded[c])*255))<<(16-c*8);
            completed[i]=packed;
        }
        check(cancelled);return completed;
    }

    void grade(double r,double g,double b,double[] out) {
        double y=tone(r,g,b),mid=smooth(.015,.10,y)*(1-smooth(.50,.92,y)),high=smooth(.35,.85,y)*(1-smooth(.92,1,y));
        double rr=r*Math.exp(mid*(-.065)*tungsten+high*.045*tungsten+.11*temperature+.05*tint+channelGain[0]);
        double gg=g*Math.exp(mid*.008*tungsten+high*.008*tungsten+.008*temperature-.10*tint+channelGain[1]);
        double bb=b*Math.exp(mid*.055*tungsten+high*(-.045)*tungsten-.11*temperature+.05*tint+channelGain[2]);
        double ty=tone(rr,gg,bb),normalize=ty>0?y/ty:1;rr*=normalize;gg*=normalize;bb*=normalize;
        double cr=(rr-y)*saturation,cg=(gg-y)*saturation,cb=(bb-y)*saturation;
        double exposure=brightness/50+(shadows/100)*(1-smooth(.12,.55,y))+(highlights/100)*smooth(.45,.90,y);
        double k=Math.pow(2,exposure),target=k*y/(1+(k-1)*y),safe=Math.max(1e-9,Math.min(1-1e-9,target));
        double odds=Math.pow(safe/(1-safe),contrast);target=odds/(1+odds);if(y==0)target=0;if(y==1)target=1;
        double lift=y>0?target/y:1;cr*=lift;cg*=lift;cb*=lift;
        rr=target+cr;gg=target+cg;bb=target+cb;double drift=tone(rr,gg,bb);cr=rr-drift;cg=gg-drift;cb=bb-drift;
        double scale=1;
        scale=Math.min(scale,cr>0?(1-target)/Math.max(cr,1e-12):target/Math.max(-cr,1e-12));
        scale=Math.min(scale,cg>0?(1-target)/Math.max(cg,1e-12):target/Math.max(-cg,1e-12));
        scale=Math.min(scale,cb>0?(1-target)/Math.max(cb,1e-12):target/Math.max(-cb,1e-12));scale=Math.max(0,scale);
        out[0]=clamp(target+cr*scale);out[1]=clamp(target+cg*scale);out[2]=clamp(target+cb*scale);
    }
    void render(int[] pixels,int w,int h,BooleanSupplier cancelled,int[] evidence,int ew,int eh) throws IOException {
        render(pixels,w,h,cancelled,evidence,ew,eh,false,Double.NaN);
    }
    void render(int[] pixels,int w,int h,BooleanSupplier cancelled,int[] evidence,int ew,int eh,
                boolean sceneTime,double captureHour) throws IOException {
        render(pixels,w,h,cancelled,evidence,ew,eh,sceneTime,captureHour,Double.NaN);
    }
    void renderWithDayWeight(int[] pixels,int w,int h,BooleanSupplier cancelled,int[] evidence,int ew,int eh,double dayWeight) throws IOException {
        if(!Double.isFinite(dayWeight)||dayWeight<0||dayWeight>1)throw new IllegalArgumentException("Invalid external scene weight");
        render(pixels,w,h,cancelled,evidence,ew,eh,false,Double.NaN,dayWeight);
    }
    private void render(int[] pixels,int w,int h,BooleanSupplier cancelled,int[] evidence,int ew,int eh,
                boolean sceneTime,double captureHour,double externalWeight) throws IOException {
        if(w<=0||h<=0||(long)w*h!=pixels.length||pixels.length>Integer.MAX_VALUE/3)throw new IllegalArgumentException("Invalid image");
        if(evidence!=null&&(ew<=0||eh<=0||(long)ew*eh!=evidence.length))throw new IllegalArgumentException("Invalid source analysis");
        check(cancelled);
        try {
            int[] source=evidence==null?pixels:evidence;int sw=evidence==null?w:ew,sh=evidence==null?h:eh;
            double weight=Double.isFinite(externalWeight)?externalWeight:sceneTime?Vision500SceneTime.dayWeight(previous.similarityDayWeight(source,sw,sh,cancelled),captureHour)
                    :dayWeight(source,sw,sh,cancelled);
            if(weight==1){
                if(!sceneTime&&!Double.isFinite(externalWeight)){previous.render(pixels,w,h,cancelled,evidence,ew,eh);return;}
                int[] completed=previous.renderDayComplete(pixels,w,h,cancelled);
                check(cancelled);System.arraycopy(completed,0,pixels,0,pixels.length);return;
            }
            int[] completed=renderNight(pixels,w,h,cancelled);
            if(weight>0) {
                int[] day=previous.renderDayComplete(pixels,w,h,cancelled);
                for(int i=0;i<pixels.length;i++) {
                    if((i&1023)==0)check(cancelled);int packed=pixels[i]&0xff000000;
                    for(int c=0;c<3;c++){int shift=16-c*8;double n=(completed[i]>>>shift)&255,d=(day[i]>>>shift)&255;packed|=((int)Math.rint(n+weight*(d-n)))<<shift;}
                    completed[i]=packed;
                }
            }
            check(cancelled);System.arraycopy(completed,0,pixels,0,pixels.length);
        }catch(JSONException e){throw new IOException("Invalid approved 500T profile",e);}
    }
}
