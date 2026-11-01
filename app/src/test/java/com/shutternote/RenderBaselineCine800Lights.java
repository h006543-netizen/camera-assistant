package com.shutternote;

import org.json.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static com.shutternote.RenderBaselineFilmRenderMath.*;
import static com.shutternote.RenderBaselineCine800Geometry.*;

/** Portable selective scatter and the approved paired-source exterior response. */
final class RenderBaselineCine800Lights {
    static float[] blur(float[] x,int w,int h,double sigma,BooleanSupplier cancel){float[] out=new float[x.length];filter(x,w,h,gaussian(sigma),out,new float[x.length],cancel);return out;}
    static float[] channel(float[] rgb,int c){float[] x=new float[rgb.length/3];for(int i=0;i<x.length;i++)x[i]=rgb[i*3+c];return x;}
    static float[] encoded(int[] pixels){float[] rgb=new float[pixels.length*3];for(int i=0;i<pixels.length;i++)for(int c=0;c<3;c++)rgb[i*3+c]=((pixels[i]>>>(16-8*c))&255)/255f;return rgb;}
    static double[] member(double[] f,JSONObject p,String centerKey,String scaleKey,String prototypesKey,String widthKey,boolean veto) throws JSONException {
        int d=f.length,n=p.getJSONArray(prototypesKey).length();double[] center=array(p,centerKey,d),scale=array(p,scaleKey,d),proto=array(p,prototypesKey,n*d),out=new double[n];
        double width=p.getDouble(widthKey),sum=0,max=0;for(int k=0;k<n;k++){double dist=0;for(int j=0;j<d;j++){double z=(f[j]-center[j])/scale[j]-proto[k*d+j];dist+=z*z/d;}out[k]=Math.exp(-dist/(2*width*width));sum+=out[k];max=Math.max(max,out[k]);}
        if(!veto)for(int k=0;k<n;k++)out[k]*=max/Math.max(sum,1e-12);return out;
    }
    static void selective(int[] original,float[] color,int w,int h,JSONObject p,double reference,BooleanSupplier cancel) throws JSONException {
        if(!p.getString("method").equals("source_selective_scatter_rbf_v5"))throw new JSONException("Unsupported selective light response");
        int n=original.length;float[] rgb=encoded(original),signal=new float[n];boolean[] bright=new boolean[n];double threshold=p.getDouble("threshold_linear"),scale=Math.max(w,h)/reference,scene=0;int count=0;
        for(int i=0;i<n;i++){for(int c=0;c<3;c++)rgb[i*3+c]=(float)linear(rgb[i*3+c]);signal[i]=Math.max(rgb[i*3],Math.max(rgb[i*3+1],rgb[i*3+2]));bright[i]=signal[i]>threshold;if(bright[i])count++;scene+=signal[i];}
        if(count==0||count==n)return;
        Labels labels=label(bright,w,h,cancel);int m=labels.components.size();float[] surround=blur(signal,w,h,Math.max(1,12*scale),cancel),local=blur(signal,w,h,Math.max(1,scale),cancel),energy=new float[n];
        int[] owners=labels.ids.clone();float[] distance=distance(bright,w,h,owners);double[][] stats=new double[m+1][12],outside=new double[m+1][8];
        for(int i=0;i<n;i++){
            double contrast=clamp((signal[i]-surround[i])/.25);energy[i]=(float)(Math.max(signal[i]-threshold,0)/(1-threshold)*contrast);int id=labels.ids[i];
            if(id>0){int x=i%w,y=i/w;double[] s=stats[id];s[0]++;s[1]+=signal[i];for(int c=0;c<3;c++)s[2+c]+=rgb[i*3+c];s[5]+=contrast;s[6]+=surround[i];
                s[7]+=(x==0||labels.ids[i-1]!=id?1:0)+(x==w-1||labels.ids[i+1]!=id?1:0)+(y==0||labels.ids[i-w]!=id?1:0)+(y==h-1||labels.ids[i+w]!=id?1:0);
                s[8]+=signal[i]>=.98?1:0;s[9]+=Math.abs(signal[i]-local[i]);
            }else{double d=distance[i]/scale;int b=d<=2?0:d<=8?1:d<=24?2:d<=64?3:-1;if(b>=0){outside[owners[i]][b*2]+=signal[i];outside[owners[i]][b*2+1]++;}}
        }
        int prototypes=p.getJSONArray("prototypes").length();double[][] weights=new double[m+1][prototypes];double[] gate=new double[m+1];
        double[] gates=array(p,"chromatic_gate",p.getJSONArray("chromatic_gate").length());double floor=p.getDouble("gate_floor");
        JSONObject gp=new JSONObject(p.toString());gp.put("prototypes",p.getJSONArray("gate_prototypes"));gp.put("kernel_width",p.optDouble("gate_kernel_width",p.getDouble("kernel_width")));
        for(int id=1;id<=m;id++){
            double[] s=stats[id],d=new double[14];double area=s[0],total=Math.max(s[2]+s[3]+s[4],1e-9);
            d[0]=s[1]/area;d[1]=s[2]/total;d[2]=s[4]/total;d[3]=Math.tanh(Math.log(Math.max(Math.sqrt(area/Math.PI)/scale,.25)/6)/2);d[4]=clamp(4*Math.PI*area/Math.max(s[7]*s[7],1));d[5]=s[5]/area;d[6]=s[6]/area;d[7]=scene/n;d[8]=s[8]/area;d[9]=s[9]/area;
            for(int b=0;b<4;b++)d[10+b]=outside[id][b*2]/Math.max(outside[id][b*2+1],1)/Math.max(d[0],.01);
            weights[id]=member(d,p,"descriptor_center","descriptor_scale","prototypes","kernel_width",false);
            double[] gw=member(d,gp,"descriptor_center","descriptor_scale","prototypes","kernel_width",false);double v=0;for(int k=0;k<gw.length;k++)v+=gw[k]*gates[k];gate[id]=clamp((v-floor)/Math.max(1-floor,1e-9));
        }
        float[] transmission=new float[n];Arrays.fill(transmission,1);
        if(p.optString("chromatic_destination_policy").equals("source_no_halo_neighborhood_fade_v1")){
            float[] confidence=new float[n];for(int i=0;i<n;i++)if(bright[i])confidence[i]=(float)(1-gate[labels.ids[i]]);
            confidence=blur(dilate(confidence,w,h,Math.max(1,(int)Math.ceil(p.getDouble("protected_rim_reference_px")*scale)),cancel),w,h,Math.max(.5,scale),cancel);
            for(int i=0;i<n;i++)transmission[i]=(float)clamp(1-confidence[i]);
        }
        double[] gains=array(p,"gain_rgb",prototypes*36),widths=array(p,"sigma_reference_px",4);float[] residual=new float[n*3];
        for(int k=0;k<prototypes;k++)for(int chroma=0;chroma<2;chroma++){
            float[] source=new float[n];for(int i=0;i<n;i++)source[i]=(float)(energy[i]*weights[labels.ids[i]][k]*(chroma==1?gate[labels.ids[i]]:1));
            for(int band=0;band<4;band++){
                check(cancel);double sigma=Math.max(widths[band]*scale,.1),radius=Math.max(.75,Math.min(4*sigma,56*scale/Math.sqrt(2)));int edge=(int)Math.ceil(radius);double[] kernel=new double[edge*2+1];double sum=0;
                for(int x=-edge;x<=edge;x++){kernel[x+edge]=Math.exp(-.5*x*x/(sigma*sigma))*Math.pow(Math.max(1-x*x/(radius*radius),0),2);sum+=kernel[x+edge];}for(int x=0;x<kernel.length;x++)kernel[x]/=sum;
                float[] spread=new float[n];filter(source,w,h,kernel,spread,new float[n],cancel);
                for(int i=0;i<n;i++)if(!bright[i]){float v=(float)(Math.max(spread[i]-source[i],0)*(chroma==1?transmission[i]:1));for(int c=0;c<3;c++)residual[i*3+c]+=v*(float)gains[k*36+chroma*18+(band+2)*3+c];}
            }
        }
        for(int i=0;i<n;i++)if(residual[i*3]!=0||residual[i*3+1]!=0||residual[i*3+2]!=0)for(int c=0;c<3;c++)color[i*3+c]=(float)clamp(srgb(clamp(linear(color[i*3+c])+residual[i*3+c])));
    }
    static final class Fields {final float[] red,weight,target;Fields(int n){red=new float[n];weight=new float[n];target=new float[n*3];}}
    private static final class Bucket {double[] values=new double[16];int count;void add(double v){if(count==values.length)values=Arrays.copyOf(values,count*2);values[count++]=v;}double value(int minimum){return count>=minimum?median(values,count):Double.NaN;}}
    private static double ring(float[] signal,int[] labels,float[] distance,boolean[] raw,int width,int x0,int y0,int ww,int hh,double scale,double low,double high,int sector,double cx,double cy) {
        double[] values=new double[distance.length];int count=0;
        for(int y=0;y<hh;y++)for(int x=0;x<ww;x++){int j=y*ww+x,i=(y+y0)*width+x+x0;double d=distance[j]/scale;
            if(labels[i]!=0||d<=low||d>high)continue;
            if(sector>=0&&((int)((Math.atan2(y+y0-cy,x+x0-cx)+Math.PI)*16/(2*Math.PI)))%16!=sector)continue;
            values[count++]=signal[i];}
        return count>=(sector<0?4:2)?median(values,count):Double.NaN;
    }
    static Fields paired(int[] original,int w,int h,JSONObject p,double width,BooleanSupplier cancel) throws JSONException {
        int n=original.length;Fields out=new Fields(n);float[] rgb=encoded(original),signal=new float[n];boolean[] bright=new boolean[n];double scale=Math.max(w,h)/p.getDouble("reference_edge"),threshold=p.getDouble("threshold_linear"),scene=0,fraction=0;int brightCount=0;
        for(int i=0;i<n;i++){signal[i]=Math.max(rgb[i*3],Math.max(rgb[i*3+1],rgb[i*3+2]));scene+=signal[i];if(signal[i]>.7)fraction++;bright[i]=linear(signal[i])>threshold;if(bright[i])brightCount++;}
        if(brightCount==0||brightCount==n)return out;
        Labels groups=grouped(bright,w,h,scale,p.getDouble("group_radius"),cancel);int[] ids=groups.ids;
        float[] mass=new float[n],responses=new float[n*3];JSONObject coreModel=p.getJSONObject("core_model"),gate=p.getJSONObject("gate"),policy=p.getJSONObject("bloom_red_policy");
        double[] widths=array(p,"group_red_widths",4),coeff=array(p,"group_red_coefficients",4),range=array(p,"core_red_fraction_range",2);
        double[] targets=array(coreModel,"targets",coreModel.getJSONArray("prototypes").length()*3),gateLabels=array(gate,"labels",gate.getJSONArray("prototypes").length());
        int padding=(int)Math.ceil(56*scale);
        for(Component c:groups.components){
            check(cancel);int x0=Math.max(0,c.x0-padding),x1=Math.min(w,c.x1+padding),y0=Math.max(0,c.y0-padding),y1=Math.min(h,c.y1+padding),ww=x1-x0,hh=y1-y0;
            boolean[] core=new boolean[ww*hh],raw=new boolean[ww*hh];int rawCount=0;
            for(int y=0;y<hh;y++)for(int x=0;x<ww;x++){int j=y*ww+x,i=(y+y0)*w+x+x0;core[j]=ids[i]==c.id;raw[j]=core[j]&&bright[i];if(raw[j])rawCount++;}
            double[][] pixels=new double[6][rawCount];int rawAt=0;
            for(int y=0;y<hh;y++)for(int x=0;x<ww;x++)if(raw[y*ww+x]){int i=(y+y0)*w+x+x0;for(int ch=0;ch<3;ch++){pixels[ch][rawAt]=rgb[i*3+ch];pixels[ch+3][rawAt]=linear(rgb[i*3+ch]);}rawAt++;}
            float[] distance=distance(core,ww,hh,null);double[] med=new double[3],lin=new double[3];for(int ch=0;ch<3;ch++){med[ch]=median(pixels[ch],rawCount);lin[ch]=median(pixels[ch+3],rawCount);}
            double[] rings=new double[4];double[][] bounds={{0,1.5},{1.5,3.5},{4,8},{10,16}};
            Bucket[][] buckets=new Bucket[17][4];for(int j=0;j<17;j++)for(int b=0;b<4;b++)buckets[j][b]=new Bucket();
            for(int y=0;y<hh;y++)for(int x=0;x<ww;x++){int j=y*ww+x,i=(y+y0)*w+x+x0;if(ids[i]!=0)continue;double d=distance[j]/scale;if(d<=0||d>16)continue;int sector=((int)((Math.atan2(y+y0-c.cy,x+x0-c.cx)+Math.PI)*16/(2*Math.PI)))%16;for(int b=0;b<4;b++)if(d>bounds[b][0]&&d<=bounds[b][1]){buckets[0][b].add(signal[i]);buckets[sector+1][b].add(signal[i]);}}
            for(int b=0;b<4;b++)rings[b]=buckets[0][b].value(4);
            double peak=Math.max(med[0],Math.max(med[1],med[2]));double[] values=new double[rawCount];int at=0;
            for(int y=0;y<hh;y++)for(int x=0;x<ww;x++)if(raw[y*ww+x])values[at++]=signal[(y+y0)*w+x+x0];peak=median(values,rawCount);
            boolean valid=true;for(double v:rings)valid&=Double.isFinite(v);double contrast=valid?Math.max(peak-rings[3],0):0,wing=valid?Math.max(0,rings[1]-rings[3])/Math.max(peak-rings[3],.05):0;
            double gateValue=valid?clamp((wing-.07)/.17)*clamp(((Math.max(0,rings[1]-rings[2])/Math.max(peak-rings[3],.05))-.015)/.075)*clamp(((Math.max(0,rings[0]-rings[1])/Math.max(peak-rings[3],.05))-.01)/.09):0;
            double[] sectors=new double[16];int sectorCount=0;
            for(int sector=0;sector<16;sector++){
                double[] v=new double[4];boolean ok=true;for(int b=0;b<4;b++){v[b]=buckets[sector+1][b].value(2);ok&=Double.isFinite(v[b]);}
                if(ok){double con=Math.max(peak-v[3],.05);sectors[sectorCount++]=clamp(((v[1]-v[3])/con-.06)/.14)*clamp(((v[1]-v[2])/con-.012)/.058)*clamp(((v[0]-v[1])/con-.005)/.075);}
            }
            double directional=sectorCount>=8?median(sectors,sectorCount):0,area=rawCount/(scale*scale),occupancy=rawCount/(double)c.count,aspect=Math.max(c.x1-c.x0,c.y1-c.y0)/(double)Math.min(c.x1-c.x0,c.y1-c.y0);
            gateValue*=directional*clamp((area-12)/28)*clamp((occupancy-.40)/.30)*(1-clamp((aspect-2.2)/1.3));
            double[] near=new double[3];
            for(int ch=0;ch<3;ch++){double[] vals=new double[ww*hh];int nn=0;for(int y=0;y<hh;y++)for(int x=0;x<ww;x++){int j=y*ww+x,i=(y+y0)*w+x+x0;if(distance[j]>0&&distance[j]/scale<=8&&ids[i]==0)vals[nn++]=rgb[i*3+ch];}near[ch]=median(vals,nn);}
            double[] feature={med[0],med[1],med[2],med[0]-Math.max(med[1],med[2]),Math.log1p(area)/8,occupancy,Math.log1p(aspect)/3,contrast,wing,directional,scene/n,fraction/n,near[0],near[1],near[2]};
            double[] veto=member(feature,gate,"center","scale","prototypes","width",true);double positive=0,negative=0;for(int j=0;j<veto.length;j++)if(gateLabels[j]>0)positive=Math.max(positive,veto[j]);else negative=Math.max(negative,veto[j]);
            double confidence=clamp(1-Math.max(negative-positive,0)/Math.max(1-positive,1e-6));
            JSONObject guard=p.optJSONObject("halo_detection_guard");
            if(guard!=null)confidence*=smooth(guard.getDouble("minimum_support"),guard.getDouble("full_support"),positive)*smooth(guard.getDouble("minimum_margin"),guard.getDouble("full_margin"),positive-negative);
            double con=smooth(policy.getDouble("minimum_contrast"),policy.getDouble("full_contrast"),contrast),evidence=gateValue*con;
            double gain=evidence*(1+(policy.getDouble("maximum_red_gain")-1)*evidence)*confidence,spectral=lin[0]/Math.max(Math.max(lin[0],Math.max(lin[1],lin[2])),1e-8);
            double[] memberships=member(feature,coreModel,"center","scale","prototypes","width",false);double support=0;double[] response=new double[3];for(int j=0;j<memberships.length;j++){support+=memberships[j];for(int ch=0;ch<3;ch++)response[ch]+=memberships[j]*targets[j*3+ch];}for(int ch=0;ch<3;ch++)response[ch]/=Math.max(support,1e-9);
            double massValue=clamp(support)*clamp(((feature[3]/Math.max(feature[0],.1))-range[0])/(range[1]-range[0]));
            for(int y=0;y<hh;y++)for(int x=0;x<ww;x++)if(core[y*ww+x]){int i=(y+y0)*w+x+x0;mass[i]=(float)massValue;for(int ch=0;ch<3;ch++)responses[i*3+ch]=(float)(response[ch]*mass[i]);}
            for(int y=0;y<hh;y++)for(int x=0;x<ww;x++){int j=y*ww+x,i=(y+y0)*w+x+x0;if(ids[i]>0)continue;double d=distance[j]/scale/(width/100),value=0;for(int b=0;b<4;b++){double t=d/(3.5*widths[b]);value+=Math.exp(-.5*Math.pow(d/widths[b],2))*Math.pow(Math.max(1-Math.pow(t,4),0),2)*coeff[b];}out.red[i]=Math.max(out.red[i],(float)(value*gain*spectral));}
        }
        double sigma=Math.max(.4,scale);float[] sm=blur(mass,w,h,sigma,cancel);float[][] response=new float[3][];for(int ch=0;ch<3;ch++)response[ch]=blur(channel(responses,ch),w,h,sigma,cancel);
        for(int i=0;i<n;i++){double brightness=smooth(.55,.85,rgb[i*3]),red=smooth(.06,.20,rgb[i*3]-Math.max(rgb[i*3+1],rgb[i*3+2]));out.weight[i]=(float)(brightness*sm[i]*red);out.target[i*3]=(float)clamp(rgb[i*3]*response[2][i]/Math.max(sm[i],1e-9));for(int ch=1;ch<3;ch++)out.target[i*3+ch]=(float)(out.target[i*3]*clamp(response[ch-1][i]/Math.max(sm[i],1e-9)));}
        return out;
    }
}
