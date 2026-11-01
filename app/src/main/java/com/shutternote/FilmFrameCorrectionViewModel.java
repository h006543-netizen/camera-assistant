package com.shutternote;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reuses the active copy, replaces it on film changes, and removes it when correction is cleared. */
public class FilmFrameCorrectionViewModel extends AndroidViewModel {
    enum State { IDLE, SAVING, SAVED, FAILED }
    final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    public FilmFrameCorrectionViewModel(Application app) { super(app); }

    void select(String rollId, int number, String capture, long revision, boolean enabled) {
        select(rollId,number,capture,revision,enabled,FilmColorCorrection.E100D);
    }
    void select(String rollId,int number,String capture,long revision,boolean enabled,String profile) {
        if (state.getValue() != State.IDLE) return;
        state.setValue(State.SAVING);
        worker.execute(() -> {
            boolean success = false;
            android.graphics.Bitmap rendered = null;
            try {
                FilmRollStore store = new FilmRollStore(getApplication());
                FilmRollRecord roll = store.find(rollId);
                FilmFrameRecord frame = roll == null ? null : roll.frame(number);
                if (frame == null || !frame.captureId.equals(capture) || frame.correctionRevision != revision) throw new IOException("Cut changed");
                java.io.File cached = store.correction(frame);
                if(enabled && !FilmCorrectionProfiles.supported(profile))throw new IOException("Unsupported film");
                if (enabled && (cached == null || !cached.isFile() || !profile.equals(frame.correctionProfile)))
                    rendered = FilmRecipeImages.read(getApplication(), store.photo(frame), Integer.MAX_VALUE, 0,
                            profile, () -> false, frame.sceneCaptureHour);
                success = store.saveCorrection(rollId, number, capture, revision, enabled, profile, rendered);
            } catch (Exception | OutOfMemoryError ignored) {
                // The previously selected image stays in place on any render/commit failure.
            } finally { if (rendered != null) rendered.recycle(); }
            state.postValue(success ? State.SAVED : State.FAILED);
        });
    }
    void acknowledge() { state.setValue(State.IDLE); }
    @Override protected void onCleared() { worker.shutdown(); }
}
