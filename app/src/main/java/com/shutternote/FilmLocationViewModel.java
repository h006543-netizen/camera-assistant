package com.shutternote;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FilmLocationViewModel extends AndroidViewModel {
    enum State { IDLE, SAVING, SAVED, FAILED }
    final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    public FilmLocationViewModel(Application app) { super(app); }
    void save(String roll, int cut, String capture, FilmFrameLocation position) {
        if (state.getValue() == State.SAVING || state.getValue() == State.SAVED) return;
        state.setValue(State.SAVING);
        worker.execute(() -> state.postValue(new FilmRollStore(getApplication())
                .saveFrameLocation(roll, cut, capture, position) ? State.SAVED : State.FAILED));
    }
    @Override protected void onCleared() { worker.shutdown(); }
}
