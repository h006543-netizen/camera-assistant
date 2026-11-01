package com.shutternote;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import static com.shutternote.FilmRenderMath.*;

/** Continuous source-statistics interpolation of bounded, versioned recovery curves.
 * Reference photographs and target pixels are never available to this renderer.
 */
final class Vision250RecoveryProfile {
    private final LearnedFilmProfile base;
    private final int count;
    private final double[] center,scale,descriptors,strength,curves;
    private final int[] modes;
    private final double radius;

    Vision250RecoveryProfile(JSONObject p,LearnedFilmGrain.Noise noise) throws JSONException {
        if(p.getInt("schema_version")!=32 || !p.getString("method").equals("vision250_bounded_scene_recovery_v1") ||
                !p.getString("color_space").equals("sRGB_D65_encoded") ||
                !p.getString("input_pipeline_version").equals("shutternote-exposure-jpeg-srgb-v1"))
            throw new JSONException("Unsupported 250D recovery contract");
        JSONObject previous=p.getJSONObject("base_profile");
        if(previous.getInt("schema_version")!=9 || !previous.getString("model_version").equals("vision3-250d-shadow-color-studio-20261023"))
            throw new JSONException("Unsupported 250D baseline");
        base=new LearnedFilmProfile(previous.toString(),noise);
        JSONObject q=p.getJSONObject("recovery");
        if(!q.getString("interpolation").equals("compact_shepard4_source8_v1"))throw new JSONException("Invalid scene interpolation");
        radius=q.getDouble("radius");if(!Double.isFinite(radius)||radius<.1||radius>2)throw new JSONException("Invalid scene support");
        center=array(q,"center",8);scale=array(q,"scale",8);
        for(double s:scale)if(s<.049999)throw new JSONException("Invalid scene scale");
        JSONArray refs=q.getJSONArray("references");count=refs.length();
        if(count<1||count>32)throw new JSONException("Invalid scene count");
        descriptors=new double[count*8];strength=new double[count];modes=new int[count];curves=new double[count*257*3];
        for(int i=0;i<count;i++) {
            JSONObject ref=refs.getJSONObject(i);double[] d=array(ref,"descriptor",8),c=array(ref,"curves",257*3);
            System.arraycopy(d,0,descriptors,i*8,8);System.arraycopy(c,0,curves,i*257*3,c.length);
            modes[i]=ref.getInt("mode");strength[i]=ref.getDouble("strength");
            if(modes[i]<0||modes[i]>3||!Double.isFinite(strength[i])||strength[i]<0||strength[i]>1||modes[i]==0&&strength[i]!=0)
                throw new JSONException("Invalid recovery mode");
            for(int k=0;k<257;k++)for(int channel=0;channel<3;channel++) {
                double v=c[k*3+channel];
                if(v<0||v>1||k==0&&Math.abs(v)>1e-6||k==256&&Math.abs(v-1)>1e-6)
                    throw new JSONException("Invalid recovery range");
                if(k>0) {double slope=(v-c[(k-1)*3+channel])*256;
                    if(slope<.1998||slope>2.5002)throw new JSONException("Unbounded recovery slope");}
                if(modes[i]==3&&Math.abs(v-c[k*3])>1e-8)throw new JSONException("Tone curve must be neutral");
            }
        }
    }

    /** Positive, compact, C1 weights with the continuous cardinal limit at a reference. */
    double[] weights(int[] source,int w,int h,BooleanSupplier cancelled) {
        check(cancelled);double[] d=E100dColorProfile.descriptor(source,w,h),out=new double[count];
        double sum=0,support=0;
        for(int i=0;i<count;i++) {
            double distance=0;
            for(int j=0;j<8;j++){double v=(d[j]-descriptors[i*8+j])/scale[j];distance+=v*v/8;}
            double normalized=Math.sqrt(distance)/radius;
            if(normalized>=1)continue;
            double k=Math.pow(1-normalized,4)*(1+4*normalized);
            support=Math.max(support,k);out[i]=k/Math.max(distance*distance,1e-24);sum+=out[i];
        }
        if(sum>0)for(int i=0;i<count;i++)out[i]=out[i]/sum*support*strength[i];
        check(cancelled);return out;
    }

    void render(int[] pixels,int w,int h,BooleanSupplier cancelled,int[] analysis,int aw,int ah) throws IOException {
        if(w<=0||h<=0||(long)w*h!=pixels.length||pixels.length>Integer.MAX_VALUE/3)throw new IllegalArgumentException("Invalid image");
        check(cancelled);
        if(analysis!=null&&(aw<=0||ah<=0||(long)aw*ah!=analysis.length))throw new IllegalArgumentException("Invalid scene source");
        double[] weights=weights(analysis==null?pixels:analysis,analysis==null?w:aw,analysis==null?h:ah,cancelled);
        double active=0;for(int i=0;i<count;i++)if(modes[i]!=0)active+=weights[i];
        // Inactive sources use the exact original path, including its quantization.
        if(active<1e-12) {int[] completed=pixels.clone();base.render(completed,w,h,cancelled);check(cancelled);System.arraycopy(completed,0,pixels,0,pixels.length);return;}
        double[] direct=new double[257*3],finish=new double[257*3];
        double directWeight=0,finishWeight=0,toneWeight=0;
        for(int i=0;i<count;i++) {
            if(modes[i]==1)directWeight+=weights[i];
            if(modes[i]==2)finishWeight+=weights[i];
            if(modes[i]==3)toneWeight+=weights[i];
            if(modes[i]==1||modes[i]==2)for(int j=0;j<257*3;j++)
                (modes[i]==1?direct:finish)[j]+=weights[i]*curves[i*257*3+j];
        }
        float[] color=base.renderLegacyFloat(pixels,w,h,cancelled);int[] completed=new int[pixels.length];
        float[] toneResult=new float[3];double[] value=new double[3];
        double baseWeight=1-directWeight-finishWeight-toneWeight;
        for(int at=0;at<pixels.length;at++) {
            if((at&1023)==0)check(cancelled);
            int pixel=pixels[at];double r=color[at*3],g=color[at*3+1],b=color[at*3+2];
            for(int c=0;c<3;c++) {
                double old=c==0?r:c==1?g:b,original=((pixel>>>(16-c*8))&255)/255.;
                value[c]=baseWeight*old+curve(direct,original,c,3)+curve(finish,old,c,3);
            }
            if(toneWeight>0) {
                double y=luma(r,g,b);
                for(int i=0;i<count;i++)if(modes[i]==3&&weights[i]>0) {
                    int offset=i*257*3;double position=clamp(y)*256;int lo=Math.min((int)position,255);double f=position-lo;
                    double tone=curves[offset+lo*3]*(1-f)+curves[offset+(lo+1)*3]*f;
                    gamut(tone+r-y,tone+g-y,tone+b-y,toneResult,0);
                    for(int c=0;c<3;c++)value[c]+=weights[i]*toneResult[c];
                }
            }
            completed[at]=(pixel&0xff000000)|((int)Math.rint(clamp(value[0])*255)<<16)|
                ((int)Math.rint(clamp(value[1])*255)<<8)|(int)Math.rint(clamp(value[2])*255);
        }
        check(cancelled);System.arraycopy(completed,0,pixels,0,pixels.length);
    }
}
