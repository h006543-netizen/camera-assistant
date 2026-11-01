package com.shutternote;

import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.concurrent.CancellationException;
import org.json.*;
import static org.junit.Assert.*;

/** Frozen pre-change renderer versus production: exact pixels, never a tolerance. */
final class RenderPerformanceCases {
    interface Files { InputStream open(String name) throws IOException; }
    static byte[] bytes(InputStream in) throws IOException {
        try(InputStream src=in;ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buf=new byte[8192];int n;while((n=src.read(buf))!=-1)out.write(buf,0,n);return out.toByteArray();
        }
    }
    static float[] noise(Files assets,String path) throws IOException {
        byte[] b=bytes(new GZIPInputStream(assets.open(path)));float[] values=new float[b.length/4];
        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(values);return values;
    }
    static final class Image {
        int w,h,aw,ah;int[] source,analysis;
        Image(Files fixtures,int number) throws IOException {
            try(DataInputStream in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(fixtures.open("render-speed/"+number+".bin.gz"))))) {
                w=in.readInt();h=in.readInt();aw=in.readInt();ah=in.readInt();source=new int[w*h];analysis=new int[aw*ah];
                for(int i=0;i<source.length;i++)source[i]=in.readInt();for(int i=0;i<analysis.length;i++)analysis[i]=in.readInt();
            }
        }
    }
    static void geometry() {
        Random random=new Random(892341);
        for(int[] shape:new int[][]{{1,1},{3,17},{19,2},{41,31}})for(double probability:new double[]{0,.01,.5,.99,1}) {
            boolean[] src=new boolean[shape[0]*shape[1]];for(int i=0;i<src.length;i++)src[i]=random.nextDouble()<probability;
            for(int radius:new int[]{1,2,8,12})for(boolean ellipse:new boolean[]{false,true})for(boolean dilate:new boolean[]{false,true})
                assertArrayEquals(RenderBaselineCine800Geometry.morph(src,shape[0],shape[1],radius,dilate,ellipse,()->false),Cine800Geometry.morph(src,shape[0],shape[1],radius,dilate,ellipse,()->false));
        }
    }
    static void filters() {
        Random random=new Random(27155);
        for(int[] shape:new int[][]{{1,1},{1,23},{31,1},{3,2},{41,31}})for(double sigma:new double[]{.1,1,5,12}) {
            int n=shape[0]*shape[1];float[] source=new float[n];for(int i=0;i<n;i++)source[i]=random.nextFloat()*2-1;
            float[] old=new float[n],now=new float[n];double[] kernel=RenderBaselineFilmRenderMath.gaussian(sigma);
            RenderBaselineFilmRenderMath.filter(source,shape[0],shape[1],kernel,old,new float[n],()->false);
            FilmRenderMath.filter(source,shape[0],shape[1],kernel,now,new float[n],()->false);
            assertArrayEquals(old,now,0);
        }
        assertThrows(CancellationException.class,()->FilmRenderMath.filter(new float[100],10,10,new double[]{1},new float[100],new float[100],()->true));
    }
    static JSONObject run(Files assets,Files fixtures,int repeats) throws Exception {
        JSONObject report=new JSONObject();JSONArray rows=new JSONArray();report.put("rows",rows);report.put("pixel_tolerance",0);
        String[] keys={"vision3-50d","e100d","vision3-250d","cine800t","vision3-500t","e100d-previous","cine800t-previous","cine800t-scenes-v1","cine800t-source-guard-v1"};
        for(String key:keys) {
            String json=new String(bytes(assets.open("film_corrections/latest/"+key+".json")),java.nio.charset.StandardCharsets.UTF_8);
            long start=System.nanoTime();RenderBaselineLearnedFilmProfile baseline=new RenderBaselineLearnedFilmProfile(json,a->noise(assets,a));double baselineLoad=(System.nanoTime()-start)/1e6;
            start=System.nanoTime();LearnedFilmProfile optimized=new LearnedFilmProfile(json,a->noise(assets,a));double optimizedLoad=(System.nanoTime()-start)/1e6;
            for(int number:new int[]{8,15,7,4}) {
                Image im=new Image(fixtures,number);int count=key.endsWith("-v1")||key.endsWith("-previous")?1:repeats;
                double[] oldTimes=new double[count],newTimes=new double[count];
                for(int run=0;run<count;run++) {
                    int[] old=im.source.clone(),now=im.source.clone();
                    for(int pass=0;pass<2;pass++) {
                        boolean first=(run&1)==0?pass==0:pass==1;long t=System.nanoTime();
                        if(first){baseline.render(old,im.w,im.h,()->false,im.analysis,im.aw,im.ah);oldTimes[run]=(System.nanoTime()-t)/1e6;}
                        else{optimized.render(now,im.w,im.h,()->false,im.analysis,im.aw,im.ah);newTimes[run]=(System.nanoTime()-t)/1e6;}
                    }
                    assertArrayEquals(key+" / photo "+number+" / run "+run,old,now);
                }
                JSONObject row=new JSONObject().put("film",key).put("photo",number).put("width",im.w).put("height",im.h).put("baseline_ms",new JSONArray(oldTimes)).put("optimized_ms",new JSONArray(newTimes)).put("identical",true).put("baseline_construct_ms",baselineLoad).put("optimized_construct_ms",optimizedLoad);
                rows.put(row);System.out.println("RENDER_SPEED "+row);
            }
            int[] synthetic=new int[256*32];Random random=new Random(78);for(int i=0;i<synthetic.length;i++)synthetic[i]=i<256?(i<<24)|(i<<16)|(i<<8)|i:random.nextInt();
            int[] expected=synthetic.clone(),actual=synthetic.clone();baseline.render(expected,256,32,()->false);optimized.render(actual,256,32,()->false);assertArrayEquals(key+" synthetic alpha/random/ramp",expected,actual);
            actual=synthetic.clone();int[] cancelled=actual;assertThrows(CancellationException.class,()->optimized.render(cancelled,256,32,()->true));assertArrayEquals(synthetic,actual);
        }
        report.put("synthetic_cases",keys.length).put("cancel_cases",keys.length);return report;
    }
}
