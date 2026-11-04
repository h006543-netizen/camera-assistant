package com.shutternote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.BooleanSupplier;
import java.util.HashMap;
import java.util.Map;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.GZIPInputStream;

/** Applies the bundled, pinned E100D profile to a copy of the exposure-adjusted source. */
final class FilmColorCorrection {
    static final String E100D = "e100d-" + E100dColorProfile.VERSION;
    private static volatile E100dColorProfile profile;
    private static final Map<String,LearnedFilmProfile> learned = new HashMap<>();
    private static final java.util.concurrent.atomic.AtomicBoolean preparing=new java.util.concurrent.atomic.AtomicBoolean();
    static void preparePreviews(Context context) {
        if(!preparing.compareAndSet(false,true))return;
        Context app=context.getApplicationContext();
        java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
        worker.execute(()->{
            try{learned(app,FilmCorrectionProfiles.latest("cine800t"));}catch(Exception error){android.util.Log.w("FilmCorrection","800T preparation deferred",error);}
            for(String id:FilmCorrectionProfiles.IDS)try{learned(app,id);}catch(Exception error){android.util.Log.w("FilmCorrection","Profile preparation deferred",error);}
        });worker.shutdown();
    }

    private static byte[] verified(Context context,String asset,String expected) throws Exception {
        byte[] bytes;
        try(InputStream input=context.getAssets().open(asset);ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int n;
            while((n=input.read(buffer))!=-1)output.write(buffer,0,n);
            bytes=output.toByteArray();
        }
        StringBuilder hash=new StringBuilder();
        for(byte value:MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
        if(!hash.toString().equals(expected))throw new IOException("Correction values changed");
        return bytes;
    }
    private static synchronized LearnedFilmProfile learned(Context context,String id) throws Exception {
        id=FilmCorrectionProfiles.assetId(id);
        LearnedFilmProfile existing=learned.get(id);
        if(existing!=null)return existing;
        int index=FilmCorrectionProfiles.index(id);
        boolean previous=FilmCorrectionProfiles.OLD_E100D.equals(id);
        boolean previous250=FilmCorrectionProfiles.OLD_250D.equals(id);
        boolean previous250Recovery=FilmCorrectionProfiles.OLD_250D_RECOVERY.equals(id);
        boolean previous800=FilmCorrectionProfiles.OLD_800T.equals(id);
        boolean previousScene=FilmCorrectionProfiles.OLD_800T_SCENE.equals(id);
        boolean previousGuard=FilmCorrectionProfiles.OLD_800T_GUARD.equals(id);
        boolean previous800Current=FilmCorrectionProfiles.OLD_800T_CURRENT.equals(id);
        boolean previous500=FilmCorrectionProfiles.OLD_500T.equals(id);
        boolean previous500Shadow=FilmCorrectionProfiles.OLD_500T_SHADOW.equals(id);
        boolean previous500Scene=FilmCorrectionProfiles.OLD_500T_SCENE.equals(id);
        boolean previous500Approved=FilmCorrectionProfiles.OLD_500T_APPROVED.equals(id);
        boolean previous500Content=FilmCorrectionProfiles.OLD_500T_CONTENT.equals(id);
        boolean previous500Confidence=FilmCorrectionProfiles.OLD_500T_CONFIDENCE.equals(id);
        if(index<0&&!previous&&!previous250&&!previous250Recovery&&!previous800&&!previousScene&&!previousGuard&&!previous800Current&&!previous500&&!previous500Shadow&&!previous500Scene&&!previous500Approved&&!previous500Content&&!previous500Confidence)throw new IOException("Unsupported recipe version");
        byte[] bytes=verified(context,previous?FilmCorrectionProfiles.OLD_E100D_ASSET:previous250?FilmCorrectionProfiles.OLD_250D_ASSET:previous250Recovery?FilmCorrectionProfiles.OLD_250D_RECOVERY_ASSET:previous800?FilmCorrectionProfiles.OLD_800T_ASSET:previousScene?FilmCorrectionProfiles.OLD_800T_SCENE_ASSET:previousGuard?FilmCorrectionProfiles.OLD_800T_GUARD_ASSET:previous800Current?FilmCorrectionProfiles.OLD_800T_CURRENT_ASSET:previous500?FilmCorrectionProfiles.OLD_500T_ASSET:previous500Shadow?FilmCorrectionProfiles.OLD_500T_SHADOW_ASSET:previous500Scene?FilmCorrectionProfiles.OLD_500T_SCENE_ASSET:previous500Approved?FilmCorrectionProfiles.OLD_500T_APPROVED_ASSET:previous500Content?FilmCorrectionProfiles.OLD_500T_CONTENT_ASSET:previous500Confidence?FilmCorrectionProfiles.OLD_500T_CONFIDENCE_ASSET:FilmCorrectionProfiles.ASSETS[index],
                previous?FilmCorrectionProfiles.OLD_E100D_HASH:previous250?FilmCorrectionProfiles.OLD_250D_HASH:previous250Recovery?FilmCorrectionProfiles.OLD_250D_RECOVERY_HASH:previous800?FilmCorrectionProfiles.OLD_800T_HASH:previousScene?FilmCorrectionProfiles.OLD_800T_SCENE_HASH:previousGuard?FilmCorrectionProfiles.OLD_800T_GUARD_HASH:previous800Current?FilmCorrectionProfiles.OLD_800T_CURRENT_HASH:previous500?FilmCorrectionProfiles.OLD_500T_HASH:previous500Shadow?FilmCorrectionProfiles.OLD_500T_SHADOW_HASH:previous500Scene?FilmCorrectionProfiles.OLD_500T_SCENE_HASH:previous500Approved?FilmCorrectionProfiles.OLD_500T_APPROVED_HASH:previous500Content?FilmCorrectionProfiles.OLD_500T_CONTENT_HASH:previous500Confidence?FilmCorrectionProfiles.OLD_500T_CONFIDENCE_HASH:FilmCorrectionProfiles.HASHES[index]);
        Context app=context.getApplicationContext();
        LearnedFilmProfile renderer=new LearnedFilmProfile(new String(bytes,StandardCharsets.UTF_8),asset -> {
            try {
                int i=-1;
                for(int k=0;k<FilmCorrectionProfiles.GRAIN_ASSETS.length;k++)if(FilmCorrectionProfiles.GRAIN_ASSETS[k].equals(asset))i=k;
                if(i<0)throw new IOException("Unsupported grain atlas");
                byte[] compressed=verified(app,asset,FilmCorrectionProfiles.GRAIN_HASHES[i]);
                byte[] raw=new byte[512*512*3*4];
                try(InputStream input=new GZIPInputStream(new java.io.ByteArrayInputStream(compressed))) {
                    int at=0,count;while(at<raw.length && (count=input.read(raw,at,raw.length-at))!=-1)at+=count;
                    if(at!=raw.length || input.read()!=-1)throw new IOException("Invalid grain atlas length");
                }
                float[] values=new float[raw.length/4];ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(values);
                return values;
            } catch(IOException e) { throw e; } catch(Exception e) { throw new IOException(e); }
        });
        learned.put(id,renderer);return renderer;
    }

    private static E100dColorProfile profile(Context context) throws Exception {
        if (profile != null) return profile;
        synchronized (FilmColorCorrection.class) {
            if (profile == null) {
                byte[] bytes;
                try (InputStream input = context.getAssets().open("film_corrections/e100d/profile.json");
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    bytes = output.toByteArray();
                }
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
                StringBuilder hex = new StringBuilder();
                for (byte value : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
                if (!hex.toString().equals("7ac4f28a037f94142c51e26a978c57604d1a85c09d7a693a5ce835365298d84f"))
                    throw new IOException("Correction values changed");
                profile = new E100dColorProfile(new String(bytes, StandardCharsets.UTF_8));
            }
            return profile;
        }
    }
    static Bitmap apply(Context context, Bitmap original, BooleanSupplier cancelled) throws Exception {
        E100dColorProfile renderer = profile(context);
        int w = original.getWidth(), h = original.getHeight();
        int[] pixels = new int[w*h]; original.getPixels(pixels, 0, w, 0, 0, w, h);
        renderer.render(pixels, w, h, cancelled);
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
    }
    static Bitmap apply(Context context,Bitmap original,String version,BooleanSupplier cancelled) throws Exception {
        return apply(context,original,version,cancelled,null);
    }
    static Bitmap preview(Context context, Bitmap original, String version, BooleanSupplier cancelled, double hour) throws Exception {
        if (E100D.equals(version)) return apply(context, original, cancelled);
        if (FilmCorrectionProfiles.completePreview(version))
            return apply(context, original, version, cancelled, null, hour);
        int w=original.getWidth(), h=original.getHeight(); int[] pixels=new int[w*h];
        original.getPixels(pixels,0,w,0,0,w,h);
        learned(context,version).renderPreview(pixels,w,h,cancelled,hour);
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
    }
    static Bitmap analysisSource(Context context,File source,String version,BooleanSupplier cancelled) throws Exception {
        if(E100D.equals(version))return null;int edge=learned(context,version).analysisEdge();if(edge==0)return null;
        FilmRenderMath.check(cancelled);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(source.getAbsolutePath(),bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("Optical source missing");
        int sample=1;while(Math.max(bounds.outWidth,bounds.outHeight)/(sample*2)>=edge)sample*=2;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap decoded=BitmapFactory.decodeFile(source.getAbsolutePath(),options);if(decoded==null)throw new IOException("Optical source decode failed");
        try {int longest=Math.max(decoded.getWidth(),decoded.getHeight());if(longest!=edge){Bitmap normalized=Bitmap.createScaledBitmap(decoded,Math.max(1,(int)Math.rint(decoded.getWidth()*edge/(double)longest)),Math.max(1,(int)Math.rint(decoded.getHeight()*edge/(double)longest)),true);if(normalized!=decoded){decoded.recycle();decoded=normalized;}}
            FilmRenderMath.check(cancelled);return decoded;
        }catch(Exception|OutOfMemoryError error){decoded.recycle();throw error;}
    }
    static Bitmap apply(Context context,Bitmap original,String version,BooleanSupplier cancelled,Bitmap opticalSource) throws Exception {
        return apply(context,original,version,cancelled,opticalSource,Double.NaN);
    }
    static Bitmap apply(Context context,Bitmap original,String version,BooleanSupplier cancelled,Bitmap opticalSource,double captureHour) throws Exception {
        if(E100D.equals(version))return apply(context,original,cancelled);
        FilmRenderMath.check(cancelled);
        LearnedFilmProfile renderer=learned(context,version);
        Bitmap normalizedAnalysis=null;
        try {
            int[] analysisPixels=null;int aw=0,ah=0,edge=renderer.analysisEdge();
            if(edge>0){Bitmap reference=opticalSource==null?original:opticalSource;int longest=Math.max(reference.getWidth(),reference.getHeight());
                Bitmap analysis=reference;if(longest!=edge){normalizedAnalysis=Bitmap.createScaledBitmap(reference,Math.max(1,(int)Math.rint(reference.getWidth()*edge/(double)longest)),Math.max(1,(int)Math.rint(reference.getHeight()*edge/(double)longest)),true);analysis=normalizedAnalysis;}
                aw=analysis.getWidth();ah=analysis.getHeight();analysisPixels=new int[aw*ah];analysis.getPixels(analysisPixels,0,aw,0,0,aw,ah);
            }
            // The caller chooses preview vs original size. Never shrink a render that may be saved.
            int w=original.getWidth(),h=original.getHeight();
            int[] pixels=new int[w*h];original.getPixels(pixels,0,w,0,0,w,h);
            if(FilmCorrectionProfiles.usesSceneTime(version))renderer.renderWithSceneTime(pixels,w,h,cancelled,analysisPixels,aw,ah,captureHour);
            else renderer.render(pixels,w,h,cancelled,analysisPixels,aw,ah);
            return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
        } finally {if(normalizedAnalysis!=null&&normalizedAnalysis!=original&&normalizedAnalysis!=opticalSource)normalizedAnalysis.recycle();}
    }
}
