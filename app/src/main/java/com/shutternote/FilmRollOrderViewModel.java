package com.shutternote;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One background commit survives Activity recreation without replaying the drag. */
public class FilmRollOrderViewModel extends AndroidViewModel {
    enum State { IDLE, SAVING, SAVED, FAILED }
    final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    public FilmRollOrderViewModel(Application application) { super(application); }
    void save(List<String> ids) {
        if (state.getValue() != State.IDLE) return;
        List<String> order = new ArrayList<>(ids);
        state.setValue(State.SAVING);
        worker.execute(() -> {
            boolean saved = new FilmRollStore(getApplication()).reorder(order);
            state.postValue(saved ? State.SAVED : State.FAILED);
        });
    }
    void acknowledge() {
        if (state.getValue() == State.SAVED || state.getValue() == State.FAILED) state.setValue(State.IDLE);
    }
    @Override protected void onCleared() { worker.shutdown(); }
}
