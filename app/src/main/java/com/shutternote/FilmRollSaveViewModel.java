package com.shutternote;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Survives screen recreation without submitting the same roll twice. */
public class FilmRollSaveViewModel extends AndroidViewModel {
    enum State { IDLE, SAVING, SAVED, FAILED }
    final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    public FilmRollSaveViewModel(Application application) { super(application); }

    void save(FilmRollRecord record) {
        save(record, null);
    }
    void save(FilmRollRecord record, String folderId) {
        if (state.getValue() == State.SAVING || state.getValue() == State.SAVED) return;
        state.setValue(State.SAVING);
        worker.execute(() -> {
            boolean saved = new FilmRollStore(getApplication()).save(record, folderId);
            state.postValue(saved ? State.SAVED : State.FAILED);
        });
    }

    @Override protected void onCleared() { worker.shutdown(); }
}
