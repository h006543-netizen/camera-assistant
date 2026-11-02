package com.shutternote;

import android.graphics.Bitmap;
import android.widget.ImageView;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Owns only this detail page's decoded comparison pair; switching performs no IO. */
final class FilmFramePreview {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private final ImageView target;
    private Bitmap original, corrected;
    private Future<?> task;
    private int generation;
    private boolean viewingOriginal;
    private String originalKey="";
    private String correctedProfile="", correctedRequest="", failedKey="";

    FilmFramePreview(ImageView target) { this.target = target; }
    boolean ready() { return original != null; }
    boolean correctionReady(FilmFrameRecord frame) {
        return frame.currentPreviewRenderer() && corrected != null && correctedProfile.equals(frame.selectedProfile())
                && (!frame.pendingCorrection() || correctedRequest.equals(frame.correctionRequest));
    }
    boolean waitingForCorrection(FilmFrameRecord frame) {
        return frame.correctionEnabled && !frame.correctionFailed && !failedKey.equals(pairKey(frame))
                && !correctionReady(frame);
    }
    private String pairKey = "";
    static String pairKey(FilmFrameRecord frame) {
        return frame.photo + ":" + frame.correctionPhoto + ":" + frame.correctionProfile + ":" + frame.rotation + ":" + frame.correctionRequest + ":" + frame.correctionPreview;
    }
    boolean reuse(FilmFrameRecord frame) {
        if (original == null || !pairKey.equals(pairKey(frame))) return false;
        if (frame.correctionEnabled && !correctionReady(frame)) return true;
        return show(!frame.correctionEnabled);
    }
    boolean show(boolean raw) {
        viewingOriginal = raw;
        Bitmap image = raw || corrected == null ? original : corrected;
        if (image == null || !raw && corrected == null) return false;
        target.setImageBitmap(image);
        if (target instanceof FilmComparisonImageView) ((FilmComparisonImageView) target).refresh();
        return true;
    }
    void load(FilmRollStore store, FilmFrameRecord frame, boolean raw, Runnable complete) {
        String sourceKey=frame.photo+":"+frame.rotation;
        boolean retained=original!=null&&originalKey.equals(sourceKey);
        if(!retained)clear();
        else {
            generation++;if(task!=null){task.cancel(true);task=null;}
        }
        viewingOriginal = raw; pairKey = pairKey(frame);originalKey=sourceKey;
        failedKey = "";
        int request = generation;
        android.content.Context context = target.getContext().getApplicationContext();
        task = WORKER.submit(() -> {
            Bitmap source = null, result = null;
            boolean failed = false;
            try {
                File originalFile = store.photo(frame);
                if(!retained)source = FilmRecipeImages.read(context, originalFile, 2400, frame.rotation, "",
                        () -> Thread.currentThread().isInterrupted());
                File interim=store.correctionPreview(frame);
                if ((!frame.pendingCorrection() || interim!=null&&interim.isFile())
                        && (frame.correctionEnabled || frame.pendingCorrection() || FilmFrameRecord.validCorrectionName(frame.correctionPhoto))) {
                    result = FilmNoteCorrectionCache.savedPreview(context, store, frame, 2400, frame.rotation,
                            () -> Thread.currentThread().isInterrupted());
                }
            } catch (Exception | OutOfMemoryError error) { failed = true; }
            if (Thread.currentThread().isInterrupted()) {
                recycle(source); recycle(result); return;
            }
            Bitmap loadedOriginal = source, loadedCorrection = result;
            boolean error = failed;
            target.post(() -> {
                if (generation != request) { recycle(loadedOriginal); recycle(loadedCorrection); return; }
                if(loadedOriginal!=null)original = loadedOriginal;
                Bitmap previous = corrected;
                if (loadedCorrection != null) {
                    corrected = loadedCorrection;
                    correctedProfile = frame.selectedProfile();
                    correctedRequest = frame.correctionRequest;
                }
                if (error) failedKey = pairKey;
                target.setTag(R.id.noteDetailPhoto, pairKey);
                // Keep the visible correction until its replacement is ready, including final JPEG swaps.
                if (viewingOriginal || !frame.correctionEnabled) show(true);
                else if (loadedCorrection != null) show(false);
                else if (target.getDrawable() == null) show(true);
                if (previous != corrected) recycle(previous);
                complete.run();
                if (error && target.isAttachedToWindow()) android.widget.Toast.makeText(context,
                        original != null ? R.string.correction_render_error : R.string.photo_load_error,
                        android.widget.Toast.LENGTH_SHORT).show();
            });
        });
    }
    void clear() {
        generation++;
        if (task != null) { task.cancel(true); task = null; }
        target.setImageDrawable(null);
        target.setTag(R.id.noteDetailPhoto, null);
        pairKey = "";
        originalKey="";
        correctedProfile=""; correctedRequest=""; failedKey="";
        recycle(original); recycle(corrected); original = null; corrected = null;
    }
    private static void recycle(Bitmap bitmap) { if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle(); }
}
