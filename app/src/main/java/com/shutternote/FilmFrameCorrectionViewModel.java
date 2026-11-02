package com.shutternote;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Original selection retains the copy; only a different film replaces it. */
public class FilmFrameCorrectionViewModel extends AndroidViewModel {
    enum State { IDLE, SAVING, SAVED, FAILED }
    final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    public FilmFrameCorrectionViewModel(Application app) { super(app); }

    void select(String rollId, int number, String capture, long revision, boolean enabled) {
        select(rollId,number,capture,revision,enabled,FilmColorCorrection.E100D);
    }
    void select(String rollId,int number,String capture,long revision,boolean enabled,String profile) {
        select(rollId,number,capture,revision,enabled,profile,FilmFrameRecord.NOTE_PREVIEW_EDGE);
    }
    void select(String rollId,int number,String capture,long revision,boolean enabled,String profile,int edge) {
        state.setValue(State.SAVING);
        worker.execute(() -> {
            FilmRollStore store=new FilmRollStore(getApplication());
            String previous="";
            try{FilmRollRecord roll=store.find(rollId);FilmFrameRecord frame=roll==null?null:roll.frame(number);if(frame!=null)previous=frame.correctionRequest;}catch(Exception ignored){}
            boolean success = store.requestCorrection(rollId,number,capture,enabled,profile,edge);
            if (success) try {
                FilmFrameRecord updated=store.find(rollId).frame(number);
                if(!previous.equals(updated.correctionRequest))FilmCorrectionJobs.selectionChanged(getApplication(),capture);
                else FilmCorrectionJobs.changed();
            } catch(Exception error){android.util.Log.e("FilmCorrection","Durable request will recover on next launch",error);}
            if (!closed) state.postValue(success ? State.SAVED : State.FAILED);
        });
    }
    void acknowledge() { state.setValue(State.IDLE); }
    @Override protected void onCleared() { closed=true; worker.shutdown(); }
}
