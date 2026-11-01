package com.shutternote;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FilmFrameMemoViewModel extends AndroidViewModel {
    enum State { IDLE, SAVING, SAVED, FAILED }
    final MutableLiveData<State> state=new MutableLiveData<>(State.IDLE);
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    public FilmFrameMemoViewModel(Application app){super(app);}
    void save(String rollId,int number,String memo){
        if(state.getValue()==State.SAVING)return;
        state.setValue(State.SAVING);
        worker.execute(()->state.postValue(new FilmRollStore(getApplication()).saveFrameMemo(rollId,number,memo)?State.SAVED:State.FAILED));
    }
    void acknowledge(){state.setValue(State.IDLE);}
    @Override protected void onCleared(){worker.shutdown();}
}
