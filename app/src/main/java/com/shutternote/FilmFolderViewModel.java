package com.shutternote;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FilmFolderViewModel extends AndroidViewModel {
    enum State { IDLE, SAVING, SAVED, FAILED }
    final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    boolean movedRolls;
    public FilmFolderViewModel(Application application) { super(application); }
    void update(FilmFolderStore.Operation operation, String id, String name, Set<String> rolls) {
        if (state.getValue() != State.IDLE) return;
        Set<String> selected = new HashSet<>(rolls);
        movedRolls = !selected.isEmpty(); state.setValue(State.SAVING);
        worker.execute(() -> state.postValue(new FilmFolderStore(getApplication()).update(operation, id, name, selected) ? State.SAVED : State.FAILED));
    }
    void acknowledge() {
        if (state.getValue() == State.SAVED || state.getValue() == State.FAILED) state.setValue(State.IDLE);
    }
    @Override protected void onCleared() { worker.shutdown(); }
}
