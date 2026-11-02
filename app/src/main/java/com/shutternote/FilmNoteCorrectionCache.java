package com.shutternote;

import android.content.Context;
import android.graphics.Bitmap;
import java.io.File;
import java.io.IOException;

/** Creates at most the active film's JPEG; subsequent viewers only decode the saved file. */
final class FilmNoteCorrectionCache {
    /** Display never waits for full-size repair or changes a saved JPEG/recipe. */
    static Bitmap preview(Context context, FilmRollStore store, FilmFrameRecord snapshot, int edge,
                          int rotation, java.util.function.BooleanSupplier cancelled) throws Exception {
        if (edge <= 0 || edge == Integer.MAX_VALUE) throw new IllegalArgumentException("Preview edge required");
        FilmRenderMath.check(cancelled);
        if (!snapshot.correctionEnabled)
            return FilmRecipeImages.read(context, store.photo(snapshot), edge, rotation, "", cancelled);
        return savedPreview(context, store, snapshot, edge, rotation, cancelled);
    }
    static Bitmap savedPreview(Context context, FilmRollStore store, FilmFrameRecord snapshot, int edge,
                               int rotation, java.util.function.BooleanSupplier cancelled) throws Exception {
        if (edge <= 0 || edge == Integer.MAX_VALUE) throw new IllegalArgumentException("Preview edge required");
        FilmRenderMath.check(cancelled);
        java.util.List<FilmRollRecord> records = store.load();
        FilmFrameRecord current = null;
        for (FilmRollRecord roll : records) for (FilmFrameRecord frame : roll.frames)
            if (frame.photo.equals(snapshot.photo) && frame.number == snapshot.number && frame.captureId.equals(snapshot.captureId)) current = frame;
        if (current == null || !current.selectedProfile().equals(snapshot.selectedProfile()))
            throw new java.util.concurrent.CancellationException();
        File cached = current.pendingCorrection() ? store.correctionPreview(current) : store.correction(current);
        if (current.pendingCorrection() && (cached == null || !cached.isFile()))
            return FilmRecipeImages.read(context, store.photo(current), edge, rotation, "", cancelled);
        boolean aliasesOriginal = false;
        for (FilmRollRecord roll : records) for (FilmFrameRecord frame : roll.frames)
            if (store.photo(frame).equals(cached)) aliasesOriginal = true;
        if (cached != null && cached.isFile() && cached.length() > 0 && !aliasesOriginal)
            return FilmRecipeImages.read(context, cached, edge, rotation, "", cancelled);
        throw new IOException("Saved correction missing");
    }

    /** Save/export copies existing JPEGs; only recipe-only records need their first saved copy. */
    static File source(Context context, FilmRollStore store, FilmFrameRecord snapshot) throws Exception {
        if (!snapshot.correctionEnabled) return store.photo(snapshot);
        if (snapshot.pendingCorrection()) return FilmCorrectionJobs.await(context, store, snapshot);
        FilmRollRecord owner = null; FilmFrameRecord current = null;
        java.util.List<FilmRollRecord> records = store.load();
        for (FilmRollRecord roll : records) for (FilmFrameRecord frame : roll.frames)
            if (frame.photo.equals(snapshot.photo) && frame.number == snapshot.number && frame.captureId.equals(snapshot.captureId)) { owner = roll; current = frame; }
        if (current == null || !current.correctionEnabled || !current.selectedProfile().equals(snapshot.selectedProfile()))
            throw new java.util.concurrent.CancellationException();
        if (current.pendingCorrection()) return FilmCorrectionJobs.await(context, store, current);
        File cached = store.correction(current);
        boolean aliasesOriginal = false;
        for (FilmRollRecord roll : records) for (FilmFrameRecord frame : roll.frames)
            if (store.photo(frame).equals(cached)) aliasesOriginal = true;
        if (cached != null && cached.isFile() && cached.length()>0 && !aliasesOriginal) return cached;
        // Recipe-only legacy records join the same queue; two exports cannot launch duplicate full renders.
        FilmFrameRecord queued;boolean created=false;
        synchronized(FilmRollStore.LOCK) {
            java.util.List<FilmRollRecord> latestRecords=store.load();queued=null;
            for(FilmRollRecord roll:latestRecords)if(roll.id.equals(owner.id))queued=roll.frame(current.number);
            if(queued==null||!queued.photo.equals(snapshot.photo)||!queued.captureId.equals(snapshot.captureId)
                    ||!queued.correctionEnabled||!queued.selectedProfile().equals(snapshot.selectedProfile()))
                throw new java.util.concurrent.CancellationException();
            File ready=store.correction(queued);
            if(!queued.pendingCorrection()&&ready!=null&&ready.isFile()&&ready.length()>0&&!store.isOriginalFile(ready,latestRecords))return ready;
            if(!queued.pendingCorrection()) {
                if(!store.requestCorrection(owner.id,queued.number,queued.captureId,true,queued.correctionProfile,FilmFrameRecord.NOTE_PREVIEW_EDGE))
                    throw new IOException("Cannot restore correction");
                queued=store.find(owner.id).frame(queued.number);created=true;
            }
        }
        if(created)FilmCorrectionJobs.selectionChanged(context,queued.captureId);
        return FilmCorrectionJobs.await(context,store,queued);
    }
}
