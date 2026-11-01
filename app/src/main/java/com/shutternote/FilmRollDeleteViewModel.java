package com.shutternote;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FilmRollDeleteViewModel extends AndroidViewModel {
    enum State { IDLE, DELETING, DELETED, FAILED }
    final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    public FilmRollDeleteViewModel(Application application) { super(application); }

    void delete(Set<String> ids) {
        if (ids.isEmpty() || state.getValue() != State.IDLE) return;
        Set<String> selected = new HashSet<>(ids);
        state.setValue(State.DELETING);
        worker.execute(() -> {
            boolean deleted = new FilmRollStore(getApplication()).delete(selected);
            state.postValue(deleted ? State.DELETED : State.FAILED);
        });
    }

    void acknowledge() {
        if (state.getValue() == State.DELETED || state.getValue() == State.FAILED) state.setValue(State.IDLE);
    }

    @Override protected void onCleared() { worker.shutdown(); }
}
