package com.shutternote;
import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class FilmFrameDeleteViewModel extends AndroidViewModel {
    enum State { IDLE, DELETING, DELETED, FAILED }
    final MutableLiveData<State> state=new MutableLiveData<>(State.IDLE);
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    int number;
    public FilmFrameDeleteViewModel(Application app){super(app);}
    void delete(String roll,int cut,String captureId){
        if(state.getValue()!=State.IDLE)return;
        number=cut; state.setValue(State.DELETING);
        worker.execute(()->state.postValue(new FilmRollStore(getApplication()).deleteFrame(roll,cut,captureId)?State.DELETED:State.FAILED));
    }
    void acknowledge(){state.setValue(State.IDLE);}
    @Override protected void onCleared(){worker.shutdown();}
}
