package com.shutternote;

import android.app.Application;
import android.net.Uri;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A single explicit gallery save survives Activity recreation. */
public class FilmNoteGalleryViewModel extends AndroidViewModel {
    enum State { IDLE, PERMISSION, SAVING, SAVED, FAILED, DENIED, PARTIAL }
    final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final SavedStateHandle saved;
    Uri lastOutput;
    public FilmNoteGalleryViewModel(Application app, SavedStateHandle saved) {
        super(app); this.saved = saved;
        if (saved.contains("galleryFrame")) state.setValue(State.PERMISSION);
    }
    boolean busy() { return state.getValue() == State.SAVING || state.getValue() == State.PERMISSION; }
    void save(FilmFrameRecord frame, boolean needsPermission) {
        if (state.getValue() != State.IDLE) return;
        try {
            // Snapshot the tapped cut and selected profile before paging or recreation.
            saved.set("galleryFrame", frame.toJson().toString());
            if (needsPermission) state.setValue(State.PERMISSION); else permissionResult(true);
        } catch (Exception error) { saved.remove("galleryFrame"); state.setValue(State.FAILED); }
    }
    void permissionResult(boolean granted) {
        String json = saved.get("galleryFrame");
        if (json == null || state.getValue() == State.SAVING) return;
        saved.remove("galleryFrame");
        if (!granted) { state.setValue(State.DENIED); return; }
        state.setValue(State.SAVING);
        worker.execute(() -> {
            State outcome;
            try {
                lastOutput = FilmNoteGallerySaver.save(getApplication(), FilmFrameRecord.fromJson(new org.json.JSONObject(json)));
                outcome = State.SAVED;
            } catch (FilmNoteGallerySaver.PartialSaveException error) { outcome = State.PARTIAL; }
            catch (Exception | OutOfMemoryError error) { outcome = State.FAILED; }
            state.postValue(outcome);
        });
    }
    void acknowledge() { if (!busy()) state.setValue(State.IDLE); }
    @Override protected void onCleared() { worker.shutdown(); }
}
