package com.shutternote;

import android.graphics.Bitmap;
import android.widget.ImageView;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Bounded off-thread image reads. Recycled views cannot display an earlier cut's image. */
final class FilmFrameImages {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final class Read { Future<?> future; }
    static void clear(ImageView target) {
        Object previous = target.getTag();
        if (previous instanceof Read && ((Read) previous).future != null) ((Read) previous).future.cancel(true);
        target.setTag(null); target.setImageDrawable(null);
    }
    static void load(ImageView target, File file, int edge) {
        load(target, file, edge, 0);
    }
    static void load(ImageView target, File file, int edge, int rotation) {
        load(target, file, edge, rotation, "", null, null);
    }
    static void load(ImageView target, FilmRollStore store, FilmFrameRecord frame, int edge) {
        load(target, store.photo(frame), edge, frame.rotation, FilmRecipeImages.profile(frame), store, frame);
    }
    private static void load(ImageView target, File file, int edge, int rotation, String profile, FilmRollStore store, FilmFrameRecord frame) {
        clear(target);
        Read read = new Read(); target.setTag(read);
        read.future = WORKER.submit(() -> {
            Bitmap bitmap = null;
            try {
                if (Thread.currentThread().isInterrupted() || !file.isFile()) return;
                File selected = store == null ? file : FilmNoteCorrectionCache.source(target.getContext().getApplicationContext(), store, frame);
                bitmap = FilmRecipeImages.read(target.getContext().getApplicationContext(), selected, edge, rotation,
                        "", () -> Thread.currentThread().isInterrupted());
                Bitmap result = bitmap;
                if (Thread.currentThread().isInterrupted()) { if (result != null) result.recycle(); return; }
                target.post(() -> {
                    if (target.getTag() == read) {
                        target.setImageBitmap(result);
                        if (target instanceof FilmComparisonImageView) ((FilmComparisonImageView) target).refresh();
                    }
                    else if (result != null) result.recycle();
                });
            } catch (Exception | OutOfMemoryError error) {
                if (bitmap != null) bitmap.recycle();
                if (!(error instanceof java.util.concurrent.CancellationException) && !Thread.currentThread().isInterrupted()) {
                    Bitmap fallback = null;
                    if (!profile.isEmpty()) try {
                        fallback = FilmRecipeImages.read(target.getContext().getApplicationContext(), file, edge, rotation, "",
                                () -> Thread.currentThread().isInterrupted());
                    } catch (Exception | OutOfMemoryError ignored) { }
                    Bitmap original = fallback;
                    target.post(() -> {
                        if (target.getTag() == read) {
                            if (original != null) target.setImageBitmap(original);
                            if (target.isAttachedToWindow()) android.widget.Toast.makeText(target.getContext(),
                                    original != null ? R.string.correction_render_error : R.string.photo_load_error, android.widget.Toast.LENGTH_SHORT).show();
                        } else if (original != null) original.recycle();
                    });
                }
            }
        });
    }
}
