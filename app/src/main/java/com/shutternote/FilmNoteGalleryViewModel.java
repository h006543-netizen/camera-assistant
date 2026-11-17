package com.shutternote;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Permission and completion for the invoking screen, with application-wide in-flight saves. */
public class FilmNoteGalleryViewModel extends AndroidViewModel {
    enum State { IDLE, PERMISSION, SAVING, SAVED, FAILED, DENIED, PARTIAL }
    final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final java.util.Set<String> pending = new java.util.HashSet<>();
    static final MutableLiveData<Long> queueChanges = new MutableLiveData<>();
    private final SavedStateHandle saved;
    private String observedCapture;
    Uri lastOutput;
    public FilmNoteGalleryViewModel(Application app, SavedStateHandle saved) {
        super(app); this.saved = saved;
        if (saved.contains("galleryFrame")) state.setValue(State.PERMISSION);
    }
    boolean busy() { return state.getValue() == State.SAVING || state.getValue() == State.PERMISSION; }
    boolean saving(FilmFrameRecord frame) { return frame != null && pending.contains(frame.captureId); }
    boolean requestingPermission() { return state.getValue() == State.PERMISSION; }
    void save(FilmFrameRecord frame, boolean needsPermission) {
        if (requestingPermission() || saving(frame)) return;
        try {
            // Snapshot the tapped cut and selected profile before paging or recreation.
            observedCapture = frame.captureId;
            saved.set("galleryFrame", frame.toJson().toString());
            if (needsPermission) state.setValue(State.PERMISSION); else permissionResult(true);
        } catch (org.json.JSONException error) {
            saved.remove("galleryFrame");
            showResult(State.FAILED);
            state.setValue(State.FAILED);
        }
    }
    void permissionResult(boolean granted) {
        String json = saved.get("galleryFrame");
        if (json == null) return;
        saved.remove("galleryFrame");
        if (!granted) { state.setValue(State.DENIED); return; }
        FilmFrameRecord frame;
        try {
            frame = FilmFrameRecord.fromJson(new org.json.JSONObject(json));
        } catch (org.json.JSONException error) {
            showResult(State.FAILED);
            state.setValue(State.FAILED);
            return;
        }
        if (!pending.add(frame.captureId)) { state.setValue(State.IDLE); return; }
        observedCapture = frame.captureId;
        FilmCorrectionJobs.prioritizeGallerySave(frame);
        state.setValue(State.SAVING);
        queueChanges.setValue(System.nanoTime());
        worker.execute(() -> {
            State outcome;
            try {
                lastOutput = FilmNoteGallerySaver.save(getApplication(), frame);
                outcome = State.SAVED;
            } catch (FilmNoteGallerySaver.PartialSaveException error) { outcome = State.PARTIAL; }
            catch (Exception | OutOfMemoryError error) {
                android.util.Log.e("FilmCorrection", "Gallery save failed", error);
                outcome = State.FAILED;
            }
            finally { FilmCorrectionJobs.finishGallerySave(frame); }
            State result = outcome;
            main.post(() -> {
                pending.remove(frame.captureId);
                queueChanges.setValue(System.nanoTime());
                showResult(result);
                if (frame.captureId.equals(observedCapture)) state.setValue(result);
            });
        });
    }
    private void showResult(State result) {
        int message = result == State.SAVED ? R.string.save_success
                : result == State.PARTIAL ? R.string.correction_save_partial : R.string.save_error;
        Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show();
    }
    void acknowledge() { if (!busy()) state.setValue(State.IDLE); }
}
