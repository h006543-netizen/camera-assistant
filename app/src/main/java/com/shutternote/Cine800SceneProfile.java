package com.shutternote;

import org.json.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import static com.shutternote.FilmRenderMath.*;
import static com.shutternote.Cine800Geometry.smooth;

/** Two approved exterior presets, selected from input brightness distributions. */
final class Cine800SceneProfile {
    private final JSONObject scene,halation,sourceProfile,day,night,router;
    private final LearnedFilmColor color;
    private final LearnedFilmGrain grain;
    private final double reference;
    private final int analysisEdge;
    Cine800SceneProfile(JSONObject profile,LearnedFilmGrain.Noise noise) throws JSONException {
        int schema=profile.getInt("schema_version");
        if((schema!=25&&schema!=26&&schema!=27)||!profile.getString("film_name").equals("씨네스틸 800t")||
           !profile.getString("color_space").equals("sRGB_D65_encoded")||!profile.getString("input_pipeline_version").equals("shutternote-exposure-jpeg-srgb-v1"))throw new JSONException("Unsupported 800T scene contract");
        JSONObject effects=profile.getJSONObject("effects");scene=effects.getJSONObject("cine800t_scene");
        if(!effects.getString("effects_contract").equals("source_selective_lights_texture_v5")||!scene.getString("method").equals(schema==27?"cine800t_day_night_manual_v3":schema==26?"cine800t_day_night_manual_v2":"cine800t_day_night_manual_v1"))throw new JSONException("Unsupported 800T controls");
        analysisEdge=schema==27?scene.getInt("source_analysis_edge"):0;if(schema==27&&analysisEdge!=1024)throw new JSONException("Invalid optical analysis edge");
        reference=effects.getDouble("reference_edge");sourceProfile=scene.getJSONObject("source_profile");
        if(!sourceProfile.getString("method").equals("paired_source_tungsten_v4"))throw new JSONException("Unsupported exterior response");
        if((schema>=26)!=sourceProfile.has("halo_detection_guard"))throw new JSONException("Version differs from halo guard");
        if(schema>=26){JSONObject guard=sourceProfile.getJSONObject("halo_detection_guard");
            if(!guard.getString("method").equals("positive_support_and_margin_v1")||guard.length()!=5)throw new JSONException("Unsupported halo guard");
            double lo=guard.getDouble("minimum_support"),hi=guard.getDouble("full_support"),a=guard.getDouble("minimum_margin"),b=guard.getDouble("full_margin");
            if(!(0<=lo&&lo<hi&&hi<=1&&0<=a&&a<b&&b<=1))throw new JSONException("Invalid halo evidence range");}
        color=new LearnedFilmColor(effects.getJSONObject("separated_color"));halation=effects.getJSONObject("halation");
        grain=new LearnedFilmGrain(effects.getJSONObject("grain"),profile.getJSONObject("android_runtime"),noise);
        day=scene.getJSONObject("day_parameters");night=scene.getJSONObject("night_parameters");validate(day);validate(night);
        router=scene.getJSONObject("router");
        if(!router.getString("method").equals("source_brightness_nearest_support_v1")||router.getInt("analysis_edge")!=64)throw new JSONException("Unsupported brightness router");
        double[] scales=array(router,"feature_scale",7);for(double v:scales)if(v<.025)throw new JSONException("Invalid router scale");
        if(router.getJSONArray("day").length()==0||router.getJSONArray("night").length()==0||router.getDouble("margin")<=0||router.getDouble("margin")>1)throw new JSONException("Invalid scene support");
    }
    private static void validate(JSONObject p) throws JSONException {
        if(!p.getString("pipeline").equals("components_v4")||!p.getString("halo_color").matches("#[0-9A-Fa-f]{6}"))throw new JSONException("Exterior preset required");
        String[] keys={"base_percent","grain_percent","brightness","tungsten","temperature","halo_strength","halo_saturation","halo_hue","halo_depth","halo_width"};
        double[] lows={0,0,-40,0,-100,0,0,-100,0,25},highs={100,200,60,600,100,400,100,100,100,100};
        for(int i=0;i<keys.length;i++){double v=p.getDouble(keys[i]);if(!Double.isFinite(v)||v<lows[i]||v>highs[i])throw new JSONException("Invalid approved preset: "+keys[i]);}
    }
    static double[] features(int[] pixels,int w,int h,BooleanSupplier cancel) {
        double s=Math.min(1,64.0/Math.max(w,h));int ww=Math.max(1,(int)Math.rint(w*s)),hh=Math.max(1,(int)Math.rint(h*s));float[] values=new float[ww*hh];double[] rows=new double[hh];double total=0,top=0;
        for(int y=0;y<hh;y++){
            check(cancel);double y0=y*h/(double)hh,y1=(y+1)*h/(double)hh;
            for(int x=0;x<ww;x++){
                double x0=x*w/(double)ww,x1=(x+1)*w/(double)ww,area=(x1-x0)*(y1-y0);double[] sum=new double[3];
                for(int yy=(int)y0;yy<Math.ceil(y1)&&yy<h;yy++)for(int xx=(int)x0;xx<Math.ceil(x1)&&xx<w;xx++){
                    double weight=(Math.min(xx+1,x1)-Math.max(xx,x0))*(Math.min(yy+1,y1)-Math.max(yy,y0))/area;int p=pixels[yy*w+xx];
                    for(int c=0;c<3;c++)sum[c]+=((p>>>(16-c*8))&255)/255f*weight;
                }
                float v=(float)luma((float)sum[0],(float)sum[1],(float)sum[2]);values[y*ww+x]=v;total+=v;rows[y]+=v/ww;if(y<Math.max(1,hh/3))top+=v;
            }
        }
        Arrays.sort(values);Arrays.sort(rows);double peak=0;int count=Math.max(1,hh/4);for(int i=hh-count;i<hh;i++)peak+=rows[i]/count;
        double[] out={total/values.length,0,0,0,0,top/(ww*Math.max(1,hh/3)),peak};double[] qs={.25,.5,.75,.9};
        for(int i=0;i<4;i++){double at=qs[i]*(values.length-1);int lo=(int)at;out[i+1]=values[lo]+(values[Math.min(lo+1,values.length-1)]-values[lo])*(at-lo);}return out;
    }
    double dayWeight(int[] pixels,int w,int h,BooleanSupplier cancel) throws JSONException {
        double[] f=features(pixels,w,h,cancel),scale=array(router,"feature_scale",7),dist=new double[2];String[] keys={"day","night"};
        for(int k=0;k<2;k++){JSONArray support=router.getJSONArray(keys[k]);double[] proto=array(router,keys[k],support.length()*7);dist[k]=Double.POSITIVE_INFINITY;for(int i=0;i<support.length();i++){double d=0;for(int j=0;j<7;j++){double z=(f[j]-proto[i*7+j])/scale[j];d+=z*z/7;}dist[k]=Math.min(dist[k],d);}}
        double margin=(dist[1]-dist[0])/Math.max(dist[1]+dist[0],1e-9),m=router.getDouble("margin"),weight=smooth(-m,m,margin);
        if(f[0]<.015)weight=0;if(f[0]>.40)weight=1;return weight;
    }
    private static float[] grade(float[] image,JSONObject p) throws JSONException {
        float[] out=new float[image.length];double amount=p.getDouble("tungsten")/100,temp=p.getDouble("temperature")/100,k=Math.pow(2,p.getDouble("brightness")/50);
        double[] midGain={-.065,.008,.055},highGain={.045,.008,-.045},tempGain={.11,.008,-.11};
        for(int at=0;at<image.length;at+=3){double y=luma(image[at],image[at+1],image[at+2]),mid=smooth(.015,.10,y)*(1-smooth(.50,.92,y)),high=smooth(.35,.85,y)*(1-smooth(.92,1,y)),gate=smooth(.01,.08,y)*(1-smooth(.94,1,y));double[] tinted=new double[3];
            for(int c=0;c<3;c++)tinted[c]=(float)(image[at+c]*Math.exp(mid*midGain[c]*amount+high*highGain[c]*amount+gate*tempGain[c]*temp));
            double ty=luma(tinted[0],tinted[1],tinted[2]),target=k*y/(1+(k-1)*y),limit=1;double[] delta=new double[3];
            for(int c=0;c<3;c++){double value=tinted[c]*(ty>0?y/ty:1)*(y>0?target/y:1);delta[c]=value-target;limit=Math.min(limit,delta[c]>0?(1-target)/Math.max(delta[c],1e-8):target/Math.max(-delta[c],1e-8));}
            for(int c=0;c<3;c++)out[at+c]=(float)clamp(target+delta[c]*limit);
            if(Math.max(image[at],Math.max(image[at+1],image[at+2]))==0||Math.min(image[at],Math.min(image[at+1],image[at+2]))==1)System.arraycopy(image,at,out,at,3);
        }return out;
    }
    private float[] endpoint(float[] original,float[] clean,float[] full,Cine800Lights.Fields fields,JSONObject p) throws JSONException {
        double base=p.getDouble("base_percent")/100,actual=base*p.getDouble("grain_percent")/100;float[] noisy=new float[clean.length],mixed=new float[clean.length];
        for(int i=0;i<mixed.length;i++){mixed[i]=(float)(original[i]+base*(clean[i]-original[i]));noisy[i]=(float)clamp(mixed[i]+actual*(full[i]-clean[i]));}
        float[] out=grade(noisy,p);double strength=p.getDouble("halo_strength")/100,depth=p.getDouble("halo_depth")/100,saturation=p.getDouble("halo_saturation")/100,hue=p.getDouble("halo_hue");
        String hex=p.getString("halo_color");double[] colour=new double[3];for(int c=0;c<3;c++)colour[c]=Integer.parseInt(hex.substring(1+c*2,3+c*2),16)/255.;
        if(hue>0)colour[1]=Math.max(colour[1],.45*hue/100);if(hue<0)colour[2]=Math.max(colour[2],.20*-hue/100);
        for(int i=0;i<fields.red.length;i++){
            int at=i*3;double weight=fields.weight[i]*base;double[] reference=new double[3];
            for(int c=0;c<3;c++){out[at+c]=(float)(out[at+c]+(fields.target[at+c]-out[at+c])*weight);reference[c]=mixed[at+c]+(fields.target[at+c]-mixed[at+c])*weight;}
            if(strength==0||fields.red[i]<=0)continue;
            double energy=fields.red[i]*sourceProfile.getDouble("red_gain")*.5*strength;energy/=1+4*depth*energy;
            double red=linear(out[at]),room=Math.max(1-red,0),added=room*(-Math.expm1(-energy/Math.max(room,1e-7)));out[at]=(float)srgb(clamp(red+added));
            double white=Math.min(reference[1],reference[2])/Math.max(reference[0],1e-7),cover=-Math.expm1(-energy/.04),transmission=1-.8*depth*smooth(.12,.50,white)*cover;
            for(int c=1;c<3;c++)if(transmission<1)out[at+c]=(float)srgb(linear(out[at+c])*transmission);
            double alpha=saturation*cover;for(int c=0;c<3;c++)out[at+c]=(float)clamp(out[at+c]+(colour[c]-out[at+c])*alpha);
        }return out;
    }
    int analysisEdge(){return analysisEdge;}
    void render(int[] pixels,int w,int h,BooleanSupplier cancel) throws IOException {render(pixels,w,h,cancel,null,0,0);}
    void render(int[] pixels,int w,int h,BooleanSupplier cancel,int[] evidence,int ew,int eh) throws IOException {
        if(w<=0||h<=0||(long)w*h!=pixels.length||pixels.length>Integer.MAX_VALUE/3)throw new IllegalArgumentException("Invalid image");
        check(cancel);
        try {
            Cine800Analysis.Image analysis=analysisEdge==0?new Cine800Analysis.Image(pixels,w,h):Cine800Analysis.normalize(evidence==null?pixels:evidence,evidence==null?w:ew,evidence==null?h:eh,analysisEdge,cancel);
            double weight=dayWeight(analysis.pixels,analysis.w,analysis.h,cancel);
            Cine800Lights.Fields dayFields=null,nightFields=null;
            if(analysisEdge>0){
                if(weight>0)dayFields=Cine800Analysis.resize(Cine800Lights.paired(analysis.pixels,analysis.w,analysis.h,sourceProfile,day.getDouble("halo_width"),cancel),analysis.w,analysis.h,w,h,cancel);
                if(weight<1)nightFields=Cine800Analysis.resize(Cine800Lights.paired(analysis.pixels,analysis.w,analysis.h,sourceProfile,night.getDouble("halo_width"),cancel),analysis.w,analysis.h,w,h,cancel);
            }
            float[] original=Cine800Lights.encoded(pixels),clean=color.render(pixels,w,h,cancel),levels=new float[pixels.length];
            for(int i=0;i<levels.length;i++)levels[i]=(float)luma(clean[i*3],clean[i*3+1],clean[i*3+2]);
            Cine800Lights.selective(pixels,clean,w,h,halation,reference,cancel);float[] full=clean.clone();
            if(grain.active()&&((weight>0&&day.getDouble("grain_percent")>0)||(weight<1&&night.getDouble("grain_percent")>0))){grain.apply(full,levels,w,h,cancel);for(int i=0;i<full.length;i++)full[i]=(float)clamp(full[i]);}
            float[] out=null;
            if(weight>0){Cine800Lights.Fields f=dayFields==null?Cine800Lights.paired(pixels,w,h,sourceProfile,day.getDouble("halo_width"),cancel):dayFields;out=endpoint(original,clean,full,f,day);}
            if(weight<1){Cine800Lights.Fields f=nightFields==null?Cine800Lights.paired(pixels,w,h,sourceProfile,night.getDouble("halo_width"),cancel):nightFields;float[] b=endpoint(original,clean,full,f,night);if(out==null)out=b;else for(int i=0;i<out.length;i++)out[i]=(float)(b[i]+weight*(out[i]-b[i]));}
            check(cancel);int[] completed=new int[pixels.length];for(int i=0;i<pixels.length;i++){if((i&1023)==0)check(cancel);int px=pixels[i]&0xff000000;for(int c=0;c<3;c++)px|=((int)Math.rint(clamp(out[i*3+c])*255))<<(16-c*8);completed[i]=px;}check(cancel);System.arraycopy(completed,0,pixels,0,pixels.length);
        }catch(JSONException e){throw new IOException("Invalid 800T correction values",e);}
    }
}
