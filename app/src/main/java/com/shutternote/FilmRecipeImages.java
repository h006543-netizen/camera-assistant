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
        options.inSampleSize = ImageMemoryBudget.sampleSize(bounds.outWidth, bounds.outHeight,
                runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()),
                FilmCorrectionProfiles.workingBytes(profile));
        while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > edge) options.inSampleSize *= 2;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap image = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
        if (image == null) throw new IOException("Cannot decode original");
        try {
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
        return frame.correctionEnabled ? (frame.correctionProfile.isEmpty() ? "unsupported-legacy" : frame.correctionProfile) : "";
    }
}
