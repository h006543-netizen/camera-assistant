package com.shutternote;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;

/** The app owns lookup/attachment after a saved result closes; the result owns its draft. */
public class FilmResultLocationViewModel extends AndroidViewModel {
    private final FilmCaptureLocationJobs jobs;
    private FilmCaptureLocationJobs.Job job;

    public FilmResultLocationViewModel(Application application) {
        super(application); jobs = FilmCaptureLocationJobs.get(application);
    }

    void configure(String roll, int cut, String capture, long photoReadyNanos,
            FilmFrameLocation initial, boolean restoredComplete) {
        if (job == null) job = jobs.obtain(roll, cut, capture, photoReadyNanos, initial, restoredComplete);
    }
    void start() { if (job != null) jobs.start(job); }
    boolean isComplete() { return job != null && job.state.isComplete(); }
    FilmFrameLocation position() { return job == null ? null : job.state.position(); }
    void saving() { if (job != null) jobs.saving(job); }
    void saved() { if (job != null) jobs.saved(job); }
    void failed() { if (job != null) jobs.failed(job); }
    @Override protected void onCleared() { if (job != null) jobs.release(job); }
}
