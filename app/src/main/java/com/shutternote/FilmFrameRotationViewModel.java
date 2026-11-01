package com.shutternote;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FilmFrameRotationViewModel extends AndroidViewModel {
    enum State { IDLE, SAVING, SAVED, FAILED }
    final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    public FilmFrameRotationViewModel(Application app) { super(app); }
    void rotate(String roll, int number, String capture) {
        if (state.getValue() != State.IDLE) return;
        state.setValue(State.SAVING);
        worker.execute(() -> state.postValue(new FilmRollStore(getApplication()).rotateFrame(roll, number, capture) ? State.SAVED : State.FAILED));
    }
    void acknowledge() { state.setValue(State.IDLE); }
    @Override protected void onCleared() { worker.shutdown(); }
}
