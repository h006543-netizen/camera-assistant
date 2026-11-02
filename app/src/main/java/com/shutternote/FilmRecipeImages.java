package com.shutternote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import java.io.File;
import java.io.IOException;
import java.util.function.BooleanSupplier;

/** A recipe is rendered from the original at the requested size, before display rotation. */
final class FilmRecipeImages {
    static Bitmap read(Context context, File source, int edge, int rotation, String profile,
                       BooleanSupplier cancelled) throws Exception {
        return read(context,source,edge,rotation,profile,cancelled,Double.NaN);
    }
    static Bitmap read(Context context, File source, int edge, int rotation, String profile,
                       BooleanSupplier cancelled,double captureHour) throws Exception {
        if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException();
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Original missing");
        Runtime runtime = Runtime.getRuntime();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = edge == Integer.MAX_VALUE ? 1 : ImageMemoryBudget.sampleSize(bounds.outWidth, bounds.outHeight,
                runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()),
                FilmCorrectionProfiles.workingBytes(profile));
        // Decode enough detail for the target, then filter down instead of undersampling below it.
        while (Math.max(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2L) >= edge)
            options.inSampleSize *= 2;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap image = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
        if (image == null) throw new IOException("Cannot decode original");
        try {
            int longest = Math.max(image.getWidth(), image.getHeight());
            if (longest > edge) {
                float scale = edge / (float) longest;
                Bitmap scaled = Bitmap.createScaledBitmap(image,
                        Math.max(1, Math.round(image.getWidth() * scale)),
                        Math.max(1, Math.round(image.getHeight() * scale)), true);
                if (scaled != image) image.recycle();
                image = scaled;
            }
            if (profile != null && !profile.isEmpty()) {
                if (!FilmCorrectionProfiles.supported(profile)) throw new IOException("Unsupported recipe version");
                Bitmap optical=FilmColorCorrection.analysisSource(context,source,profile,cancelled);
                Bitmap rendered;
                try {rendered=FilmColorCorrection.apply(context,image,profile,cancelled,optical,
                        FilmCorrectionProfiles.usesSceneTime(profile)?FilmSceneMetadata.captureHour(source,captureHour):Double.NaN);}
                finally {if(optical!=null)optical.recycle();}
                if (rendered != image) image.recycle();
                image = rendered;
            }
            if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException();
            int degrees = FilmFrameRecord.normalizedRotation(rotation);
            if (degrees != 0) {
                Matrix matrix = new Matrix(); matrix.postRotate(degrees);
                Bitmap rotated = Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), matrix, true);
                if (rotated != image) image.recycle();
                image = rotated;
            }
            return image;
        } catch (Exception | OutOfMemoryError error) { image.recycle(); throw error; }
    }
    static String profile(FilmFrameRecord frame) {
        return frame.correctionEnabled ? (frame.selectedProfile().isEmpty() ? "unsupported-legacy" : frame.selectedProfile()) : "";
    }
    /** Screen-size interim only. Never passed to the original-size save path. */
    static Bitmap fastPreview(Context context, File source, int edge, String profile, BooleanSupplier cancelled, double hour) throws Exception {
        Bitmap original = read(context, source, edge, 0, "", cancelled);
        try {
            if(FilmCorrectionProfiles.completePreview(profile)) {
                Bitmap optical=FilmColorCorrection.analysisSource(context,source,profile,cancelled);
                try {return FilmColorCorrection.apply(context,original,profile,cancelled,optical,hour);}
                finally {if(optical!=null)optical.recycle();}
            }
            return FilmColorCorrection.preview(context, original, profile, cancelled,
                    FilmCorrectionProfiles.usesSceneTime(profile) ? FilmSceneMetadata.captureHour(source, hour) : Double.NaN);
        } finally { original.recycle(); }
    }
    /** Compare encoded dimensions before the note's separate display rotation. No pixel allocation. */
    static boolean sameDimensions(File original, File corrected) {
        if (original == null || corrected == null) return false;
        BitmapFactory.Options source = bounds(original), copy = bounds(corrected);
        return source.outWidth > 0 && source.outHeight > 0
                && source.outWidth == copy.outWidth && source.outHeight == copy.outHeight;
    }
    static boolean sameDimensions(File original, Bitmap corrected) {
        if (original == null || corrected == null) return false;
        BitmapFactory.Options source = bounds(original);
        return source.outWidth == corrected.getWidth() && source.outHeight == corrected.getHeight();
    }
    private static BitmapFactory.Options bounds(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds); return bounds;
    }
}
