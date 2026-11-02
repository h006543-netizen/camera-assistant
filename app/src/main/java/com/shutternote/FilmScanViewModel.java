package com.shutternote;

import android.app.Application;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Holds display copies and a durable uncommitted scan across activity recreation. */
public class FilmScanViewModel extends AndroidViewModel {
    enum State { LOADING, READY, IMPORTING, SAVING, MISSING }
    final MutableLiveData<State> state = new MutableLiveData<>(State.LOADING);
    final MutableLiveData<Integer> message = new MutableLiveData<>();
    Bitmap noteBitmap, scanBitmap;
    FilmFrameRecord frame;
    FilmRollRecord roll;
    String rollTitle = "";
    private final SavedStateHandle saved;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final FilmScanFiles files;
    private String rollId, capture;
    private int cut;
    private long revision;
    private boolean initialized, hasRevision;
    private volatile boolean closed;

    public FilmScanViewModel(Application app, SavedStateHandle saved) {
        super(app); this.saved = saved; files = new FilmScanFiles(app.getFilesDir());
        Long previous = saved.get("scanRevision");
        hasRevision = previous != null; revision = previous == null ? 0 : previous;
    }

    void initialize(String roll, int number, String id) {
        if (initialized) return;
        initialized = true; rollId = roll; cut = number; capture = id;
        load(false);
    }

    boolean busy() { return state.getValue() == State.LOADING || state.getValue() == State.IMPORTING || state.getValue() == State.SAVING; }
    String draftName() { return saved.get("scanDraft"); }
    boolean hasDraft() { return draftName() != null; }
    boolean hasLinkedScan() { return frame != null && !frame.scanPhoto.isEmpty(); }
    void refreshCorrection() {
        if (!initialized || closed || frame == null || busy()) return;
        String shown=frame.imageKey();
        worker.execute(() -> {
            Bitmap updated=null;
            try {
                FilmRollStore store=new FilmRollStore(getApplication()); FilmRollRecord owner=store.find(rollId);
                FilmFrameRecord current=owner==null?null:owner.frame(cut);
                if(current==null||!current.captureId.equals(capture)||shown.equals(current.imageKey()))return;
                updated=FilmNoteCorrectionCache.preview(getApplication(),store,current,2000,current.rotation,()->closed||Thread.currentThread().isInterrupted());
                Bitmap image=updated;
                main.post(() -> {
                    if(closed||busy()||frame==null||!shown.equals(frame.imageKey())){image.recycle();return;}
                    Bitmap previous=noteBitmap;noteBitmap=image;frame=current;state.setValue(State.READY);
                    if(previous!=null&&previous!=image)previous.recycle();
                });
            } catch(Exception|OutOfMemoryError error){if(updated!=null)updated.recycle();}
        });
    }

    private void load(boolean adoptRevision) {
        String pending = draftName();
        state.setValue(State.LOADING);
        worker.execute(() -> {
            try {
                FilmRollStore store = new FilmRollStore(getApplication());
                store.cleanScanFiles(pending);
                FilmRollRecord roll = store.find(rollId);
                FilmFrameRecord current = roll == null ? null : roll.frame(cut);
                if (current == null || !current.captureId.equals(capture)) {
                    main.post(() -> { if (!closed) state.setValue(State.MISSING); }); return;
                }
                Bitmap note;
                boolean correctionFailed = false;
                try {
                    note = FilmNoteCorrectionCache.preview(getApplication(), store, current, 2000, current.rotation,
                            () -> closed || Thread.currentThread().isInterrupted());
                } catch (java.util.concurrent.CancellationException cancelled) { throw cancelled; }
                catch (Exception | OutOfMemoryError error) {
                    note = FilmRecipeImages.read(getApplication(), store.photo(current), 2000, current.rotation, "",
                            () -> closed || Thread.currentThread().isInterrupted());
                    correctionFailed = current.correctionEnabled;
                }
                File linked = store.scan(current), pendingFile = files.draft(pending);
                boolean completed = FilmScanRules.completedDraft(current, pending) && linked != null && linked.isFile();
                boolean lostDraft = pending != null && (pendingFile == null || !pendingFile.isFile());
                File selected = pending == null || completed || lostDraft ? linked : pendingFile;
                Bitmap scan = null;
                boolean unreadable = false;
                if (selected != null) {
                    try { scan = FilmScanImages.read(selected, 0, true); }
                    catch (Exception | OutOfMemoryError error) { unreadable = true; }
                }
                Bitmap displayScan = scan, displayNote = note; boolean failed = unreadable, failedCorrection = correctionFailed;
                main.post(() -> {
                    if (closed) return;
                    this.roll = roll; frame = current; rollTitle = roll.title(); noteBitmap = displayNote; scanBitmap = displayScan;
                    if (completed || lostDraft) {
                        saved.remove("scanDraft"); if (pendingFile != null) pendingFile.delete();
                    }
                    if (!hasRevision || adoptRevision || completed || lostDraft) { revision = current.scanRevision; hasRevision = true; saved.set("scanRevision", revision); }
                    if (failed) message.setValue(R.string.scan_read_error);
                    else if (failedCorrection) message.setValue(R.string.correction_render_error);
                    state.setValue(State.READY);
                });
            } catch (Exception | OutOfMemoryError error) {
                main.post(() -> { if (!closed) { message.setValue(R.string.scan_read_error); state.setValue(State.READY); } });
            }
        });
    }

    void importScan(Uri uri) {
        if (busy()) return;
        state.setValue(State.IMPORTING);
        worker.execute(() -> {
            File draft = null;
            boolean persisted = false;
            try {
                try { getApplication().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); persisted = true; }
                catch (SecurityException ignored) { /* Copy using the temporary picker grant. */ }
                try (InputStream input = getApplication().getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new java.io.IOException("No scan stream");
                    draft = files.copyDraft(input);
                }
                Bitmap scan = FilmScanImages.read(draft, 0, true);
                File copied = draft;
                main.post(() -> {
                    if (closed) { copied.delete(); return; }
                    File oldDraft = files.draft(draftName()); if (oldDraft != null) oldDraft.delete();
                    saved.set("scanDraft", copied.getName()); scanBitmap = scan;
                    state.setValue(State.READY);
                });
            } catch (Exception | OutOfMemoryError error) {
                if (draft != null) draft.delete();
                main.post(() -> { if (!closed) { message.setValue(R.string.scan_read_error); state.setValue(State.READY); } });
            } finally {
                if (persisted) try { getApplication().getContentResolver().releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                catch (SecurityException ignored) { }
            }
        });
    }

    void save(boolean unlink) {
        if (busy() || frame == null || (!unlink && (!hasDraft() || scanBitmap == null))) return;
        File pending = files.draft(draftName());
        if (!unlink && (pending == null || !pending.isFile())) { message.setValue(R.string.scan_read_error); return; }
        long expected = revision;
        state.setValue(State.SAVING);
        worker.execute(() -> {
            boolean success = new FilmRollStore(getApplication()).saveScan(rollId, cut, capture, expected, unlink ? null : pending);
            main.post(() -> {
                if (closed) { if (pending != null) pending.delete(); return; }
                if (success) {
                    if (pending != null) pending.delete(); saved.remove("scanDraft");
                } else message.setValue(R.string.scan_save_error);
                // A retry requires a new explicit confirmation against the refreshed connection.
                load(true);
            });
        });
    }

    void discardDraft() {
        if (busy()) return;
        File pending = files.draft(draftName()); if (pending != null) pending.delete();
        saved.remove("scanDraft"); load(true);
    }

    @Override protected void onCleared() {
        closed = true;
        if (state.getValue() == State.SAVING) worker.shutdown(); else worker.shutdownNow();
        File pending = files.draft(draftName());
        // A save worker must finish reading the draft before it is removed.
        if (pending != null && state.getValue() != State.SAVING) pending.delete();
    }
}
