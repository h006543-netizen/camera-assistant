package com.shutternote;

import android.content.Context;
import android.graphics.Bitmap;
import java.io.File;
import java.io.IOException;

/** Creates at most the active film's JPEG; subsequent viewers only decode the saved file. */
final class FilmNoteCorrectionCache {
    static synchronized File source(Context context, FilmRollStore store, FilmFrameRecord snapshot) throws Exception {
        if (!snapshot.correctionEnabled) return store.photo(snapshot);
        FilmRollRecord owner = null; FilmFrameRecord current = null;
        java.util.List<FilmRollRecord> records = store.load();
        for (FilmRollRecord roll : records) for (FilmFrameRecord frame : roll.frames)
            if (frame.number == snapshot.number && frame.captureId.equals(snapshot.captureId)) { owner = roll; current = frame; }
        if (current == null || !current.correctionEnabled || !current.correctionProfile.equals(snapshot.correctionProfile))
            throw new java.util.concurrent.CancellationException();
        File cached = store.correction(current);
        boolean aliasesOriginal = false;
        for (FilmRollRecord roll : records) for (FilmFrameRecord frame : roll.frames)
            if (store.photo(frame).equals(cached)) aliasesOriginal = true;
        if (cached != null && cached.isFile() && !aliasesOriginal) return cached;
        Bitmap rendered = FilmRecipeImages.read(context, store.photo(current), Integer.MAX_VALUE, 0,
                FilmRecipeImages.profile(current), () -> Thread.currentThread().isInterrupted(), current.sceneCaptureHour);
        try {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            if (!store.saveCorrection(owner.id, current.number, current.captureId, current.correctionRevision,
                    true, current.correctionProfile, rendered)) throw new IOException("Correction changed");
        } finally { rendered.recycle(); }
        File saved = store.correction(store.find(owner.id).frame(current.number));
        if (saved == null || !saved.isFile()) throw new IOException("Correction missing");
        return saved;
    }
}
