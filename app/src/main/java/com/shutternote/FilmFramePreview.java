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

    FilmFramePreview(ImageView target) { this.target = target; }
    boolean ready() { return original != null; }
    boolean keepOriginal() {
        if (original == null) return false;
        generation++;
        if (task != null) { task.cancel(true); task = null; }
        show(true); recycle(corrected); corrected = null;
        return true;
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
        clear(); viewingOriginal = raw;
        int request = generation;
        android.content.Context context = target.getContext().getApplicationContext();
        task = WORKER.submit(() -> {
            Bitmap source = null, result = null;
            boolean failed = false;
            try {
                File originalFile = store.photo(frame);
                source = FilmRecipeImages.read(context, originalFile, 2400, frame.rotation, "",
                        () -> Thread.currentThread().isInterrupted());
                if (frame.correctionEnabled) {
                    File file = FilmNoteCorrectionCache.source(context, store, frame);
                    result = FilmRecipeImages.read(context, file, 2400, frame.rotation, "",
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
                original = loadedOriginal; corrected = loadedCorrection;
                if (!show(viewingOriginal || !frame.correctionEnabled)) show(true);
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
        recycle(original); recycle(corrected); original = null; corrected = null;
    }
    private static void recycle(Bitmap bitmap) { if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle(); }
}
