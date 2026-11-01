package com.shutternote;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import androidx.exifinterface.media.ExifInterface;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One export survives configuration changes; only a completed ZIP is copied to the picked file. */
public class FilmRollExportViewModel extends AndroidViewModel {
    enum State { IDLE, PICKING, EXPORTING, COPYING, COMPLETE, FAILED, CANCELLED, PARTIAL }
    final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);
    final MutableLiveData<Integer> completed = new MutableLiveData<>(0);
    int total;
    private final SavedStateHandle saved;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean cancelled, closed;
    private volatile Thread taskThread;

    public FilmRollExportViewModel(Application app, SavedStateHandle saved) {
        super(app); this.saved = saved;
        // A process death cannot be reported as success or silently restart an export.
        String pending = saved.get("exportDocument");
        if (pending != null) {
            state.setValue(State.COPYING);
            worker.execute(() -> {
                boolean removed = removeDocument(Uri.parse(pending));
                main.post(() -> { if (!closed) { saved.remove("exportDocument"); state.setValue(removed ? State.FAILED : State.PARTIAL); } });
            });
        } else if (Boolean.TRUE.equals(saved.get("exportPicking"))) state.setValue(State.PICKING);
    }

    boolean busy() { State value=state.getValue(); return value==State.PICKING || value==State.EXPORTING || value==State.COPYING; }
    boolean picking() { return state.getValue()==State.PICKING; }
    void pickingStarted() { saved.set("exportPicking",true); state.setValue(State.PICKING); }
    void pickerCancelled() { saved.remove("exportPicking"); state.setValue(State.IDLE); }
    void acknowledge() { if (!busy()) state.setValue(State.IDLE); }

    void export(String rollId, Uri destination) {
        if (!picking()) return;
        saved.remove("exportPicking"); saved.set("exportDocument",destination.toString());
        cancelled=false; total=0; completed.setValue(0); state.setValue(State.EXPORTING);
        worker.execute(() -> {
            taskThread=Thread.currentThread();
            File folder=null;
            State result=State.FAILED;
            try {
                if (cancelled || closed) throw new InterruptedIOException();
                FilmRollStore store=new FilmRollStore(getApplication());
                FilmRollRecord roll=store.find(rollId);
                if (roll==null || roll.frames.isEmpty()) throw new IOException("Roll missing or empty");
                for (FilmFrameRecord frame : roll.frames) if (frame.correctionEnabled)
                    FilmNoteCorrectionCache.source(getApplication(), store, frame);
                roll = store.find(rollId);
                folder=new File(getApplication().getCacheDir(),"roll-export-"+java.util.UUID.randomUUID());
                if (!folder.mkdirs()) throw new IOException("Cannot create export cache");
                File archive=new File(folder,"photos.zip");
                try (OutputStream out=new FileOutputStream(archive)) {
                    FilmRollPhotoArchive.write(new File(getApplication().getFilesDir(),"film_notes_photos"),roll,folder,out,
                            FilmRollExportViewModel::orientCopy,
                            (done,count)->main.post(()-> { if (!closed) { total=count; completed.setValue(done); } }),
                            (source, frame, output) -> renderRecipe(getApplication(), source, frame, output));
                }
                if (cancelled || closed) throw new InterruptedIOException();
                main.post(()-> { if (!closed) state.setValue(State.COPYING); });
                try (InputStream in=new FileInputStream(archive);
                     OutputStream out=getApplication().getContentResolver().openOutputStream(destination,"wt")) {
                    if (out==null) throw new IOException("Cannot open export destination");
                    FilmRollPhotoArchive.copy(in,out); out.flush();
                }
                if (cancelled || closed) throw new InterruptedIOException();
                result=State.COMPLETE;
            } catch (Exception | OutOfMemoryError error) {
                // Clear the interrupt only for provider cleanup; do not touch any other document.
                Thread.interrupted();
                result=removeDocument(destination) ? (cancelled || closed ? State.CANCELLED : State.FAILED) : State.PARTIAL;
            } finally {
                if (folder!=null) { File[] files=folder.listFiles(); if (files!=null) for(File file:files) file.delete(); folder.delete(); }
                taskThread=null;
            }
            State outcome=result;
            if (outcome==State.COMPLETE && !closed) FilmRollExportNotification.show(getApplication(), destination);
            main.post(()-> { if (!closed) { saved.remove("exportDocument"); state.setValue(outcome); } });
        });
    }

    void cancel() { cancelled=true; Thread thread=taskThread; if (thread!=null) thread.interrupt(); }
    static void renderRecipe(android.content.Context context, File source, FilmFrameRecord frame, File output) throws IOException {
        android.graphics.Bitmap rendered = null;
        try {
            rendered = FilmRecipeImages.read(context, source, Integer.MAX_VALUE, 0, FilmRecipeImages.profile(frame),
                    () -> Thread.currentThread().isInterrupted(), frame.sceneCaptureHour);
            try (OutputStream out = new FileOutputStream(output)) {
                if (!rendered.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)) throw new IOException("Export JPEG failed");
            }
        } catch (IOException error) { throw error; }
        catch (Exception error) { throw new IOException("Recipe export failed", error); }
        finally { if (rendered != null) rendered.recycle(); }
    }
    static void orientCopy(File file, int degrees) throws IOException {
        ExifInterface exif=new ExifInterface(file);
        // Note JPEGs are already upright; stored rotation is a separate display setting.
        exif.setAttribute(ExifInterface.TAG_ORIENTATION,Integer.toString(degrees==90?ExifInterface.ORIENTATION_ROTATE_90:
                degrees==180?ExifInterface.ORIENTATION_ROTATE_180:ExifInterface.ORIENTATION_ROTATE_270));
        exif.saveAttributes();
    }
    private boolean removeDocument(Uri uri) {
        try { return DocumentsContract.deleteDocument(getApplication().getContentResolver(),uri); }
        catch (Exception error) { return false; }
    }
    @Override protected void onCleared() {
        closed=true; cancel(); worker.shutdown();
    }
}
