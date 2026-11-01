package com.shutternote;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Short foreground requests outlive a result screen. Jobs never reference an Activity. */
final class FilmCaptureLocationJobs implements Application.ActivityLifecycleCallbacks {
    private static FilmCaptureLocationJobs instance;
    private final Application application;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, Job> jobs = new LinkedHashMap<>(); // Main-thread only.
    private final Set<Activity> visible = Collections.newSetFromMap(new IdentityHashMap<>());
    private final MutableLiveData<String> changes = new MutableLiveData<>();
    private final Runnable background = () -> {
        if (!visible.isEmpty()) return;
        for (Job job : new ArrayList<>(jobs.values())) resolve(job, null);
    };

    static synchronized FilmCaptureLocationJobs get(Context context) {
        if (instance == null) instance = new FilmCaptureLocationJobs((Application) context.getApplicationContext());
        return instance;
    }
    private FilmCaptureLocationJobs(Application application) {
        this.application = application;
        application.registerActivityLifecycleCallbacks(this);
    }
    LiveData<String> changes() { return changes; }

    static final class Job {
        final String roll, capture;
        final int cut;
        final long photoReadyNanos;
        final FilmPostPhotoLocation state;
        final FilmCaptureLocation tracker;
        boolean started;
        Job(Context context, String roll, int cut, String capture, long photoReadyNanos,
                FilmFrameLocation initial, boolean restoredComplete) {
            this.roll = roll; this.cut = cut; this.capture = capture; this.photoReadyNanos = photoReadyNanos;
            state = new FilmPostPhotoLocation(photoReadyNanos, initial, restoredComplete);
            tracker = new FilmCaptureLocation(context);
        }
    }

    Job obtain(String roll, int cut, String capture, long photoReadyNanos,
            FilmFrameLocation initial, boolean restoredComplete) {
        Job job = jobs.get(capture);
        if (job == null) {
            job = new Job(application, roll, cut, capture, photoReadyNanos, initial, restoredComplete);
            jobs.put(capture, job);
        }
        return job;
    }
    void start(Job job) {
        if (job.started || job.state.isComplete() || job.state.abandoned()) return;
        job.started = true;
        if (job.state.remainingNanos(SystemClock.elapsedRealtimeNanos()) == 0) { resolve(job, null); return; }
        job.tracker.start();
        job.tracker.capture(job.photoReadyNanos, position -> resolve(job, position));
    }
    private void resolve(Job job, FilmFrameLocation position) {
        job.state.complete(position, SystemClock.elapsedRealtimeNanos());
        job.tracker.stop(); // Stop GPS immediately, even if image saving is still underway.
        attachIfReady(job);
    }
    void saving(Job job) { job.state.saving(); }
    void saved(Job job) { main.post(() -> { job.state.saved(); attachIfReady(job); }); }
    void failed(Job job) {
        main.post(() -> {
            job.state.failed();
            if (job.state.abandoned()) resolve(job, null);
            else cleanup(job);
        });
    }
    void release(Job job) {
        job.state.release();
        if (job.state.abandoned()) resolve(job, null);
        else cleanup(job);
    }
    private void attachIfReady(Job job) {
        FilmFrameLocation position = job.state.claimAttachment();
        if (position != null) worker.execute(() -> {
            if (new FilmRollStore(application).saveCapturedFrameLocation(job.roll, job.cut, job.capture, position))
                main.post(() -> changes.setValue(job.roll));
        });
        cleanup(job);
    }
    private void cleanup(Job job) {
        if (job.state.removable() && jobs.get(job.capture) == job) jobs.remove(job.capture);
    }

    @Override public void onActivityStarted(Activity activity) {
        visible.add(activity); main.removeCallbacks(background);
    }
    @Override public void onActivityStopped(Activity activity) {
        visible.remove(activity);
        if (visible.isEmpty()) {
            main.removeCallbacks(background);
            main.postDelayed(background, activity.isChangingConfigurations() ? 300 : 0);
        }
    }
    @Override public void onActivityDestroyed(Activity activity) { visible.remove(activity); }
    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityResumed(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
}
