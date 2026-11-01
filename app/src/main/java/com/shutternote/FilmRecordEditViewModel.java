package com.shutternote;

import android.app.Application;
import android.os.Bundle;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FilmRecordEditViewModel extends AndroidViewModel {
    final MutableLiveData<Integer> result = new MutableLiveData<>();
    final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    Bundle draft;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    public FilmRecordEditViewModel(Application app) { super(app); }
    void save(String rollId) {
        if (draft == null || Boolean.TRUE.equals(busy.getValue())) return;
        Bundle snapshot = new Bundle(draft);
        busy.setValue(true);
        worker.execute(() -> result.postValue(new FilmRollStore(getApplication()).editRecord(rollId,
                snapshot.getInt("cut"), snapshot.getString("capture"), FilmRecordEdits.Field.valueOf(snapshot.getString("field")), snapshot.getStringArray("values"))));
    }
    void acknowledge() { result.setValue(null); busy.setValue(false); }
    @Override protected void onCleared() { worker.shutdown(); }
}
