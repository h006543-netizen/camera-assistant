package com.shutternote;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Small local metadata records. Explicit backup allowlists exclude this preference file. */
final class FilmRollStore {
    static final Object LOCK = new Object();
    private final Context context;
    private final SharedPreferences preferences;
    private final java.io.File photoDirectory;
    private final FilmScanFiles scanFiles;
    private final FilmNoteIsoStore noteIsoStore;
    private final FilmNoteExposureStore noteExposureStore;
    private final FilmNoteFieldOfViewStore noteFieldOfViewStore;
    private final CameraUsageStore cameraUsage;
    private final FilmUsageStore filmUsage;
    private final FilmRollDefaultsStore rollDefaults;

    FilmRollStore(Context context) {
        this.context = context.getApplicationContext();
        noteIsoStore = new FilmNoteIsoStore(context);
        noteExposureStore = new FilmNoteExposureStore(context);
        noteFieldOfViewStore = new FilmNoteFieldOfViewStore(context);
        cameraUsage = new CameraUsageStore(context);
        filmUsage = new FilmUsageStore(context);
        rollDefaults = new FilmRollDefaultsStore(context);
        preferences = context.getApplicationContext().getSharedPreferences("film_roll_records", Context.MODE_PRIVATE);
        photoDirectory = new java.io.File(context.getFilesDir(), "film_notes_photos");
        scanFiles = new FilmScanFiles(context.getFilesDir());
    }

    List<FilmRollRecord> load() throws JSONException {
        synchronized (LOCK) {
            JSONArray data = new JSONArray(preferences.getString("rolls", "[]"));
            List<FilmRollRecord> records = new ArrayList<>();
            for (int i = 0; i < data.length(); i++) records.add(FilmRollRecord.fromJson(data.getJSONObject(i)));
            cleanDisabledCorrections(records);
            return records;
        }
    }

    private void cleanDisabledCorrections(List<FilmRollRecord> records) throws JSONException {
        Set<String> originals = new java.util.HashSet<>();
        for (FilmRollRecord roll : records) for (FilmFrameRecord frame : roll.frames) originals.add(photo(frame).getName());
        boolean changed = false;
        for (FilmRollRecord roll : records) for (FilmFrameRecord frame : roll.frames) {
            java.io.File old = correction(frame);
            if (old == null || frame.correctionEnabled || originals.contains(frame.correctionPhoto) || !photo(frame).isFile()) continue;
            if (!old.exists() || old.delete()) { frame.correctionPhoto = ""; changed = true; }
        }
        if (changed) commit(records);
    }

    FilmRollRecord find(String id) throws JSONException {
        for (FilmRollRecord record : load()) if (record.id.equals(id)) return record;
        return null;
    }

    boolean reorder(List<String> ids) {
        synchronized (LOCK) {
            try {
                return commit(FilmRollOrder.apply(load(), ids));
            } catch (JSONException exception) { return false; }
        }
    }

    boolean save(FilmRollRecord record) {
        return save(record, null);
    }

    boolean save(FilmRollRecord record, String folderId) {
        synchronized (LOCK) {
            try {
                List<FilmRollRecord> records = load(); // Never replace unreadable existing data.
                cameraUsage.seed(records);
                filmUsage.seed(records); rollDefaults.seed(records);
                int existing = -1;
                for (int i = 0; i < records.size(); i++) if (records.get(i).id.equals(record.id)) existing = i;
                boolean cameraChanged = existing < 0 || !records.get(existing).cameraId.equals(record.cameraId)
                        || !records.get(existing).cameraName.equals(record.cameraName);
                boolean filmChanged = existing < 0 || FilmUsageStore.changed(records.get(existing).filmId, records.get(existing).filmName, record.filmId, record.filmName);
                boolean focalChanged = existing < 0 || !records.get(existing).focalLength.equals(record.focalLength);
                boolean countChanged = existing < 0 || records.get(existing).plannedFrames != record.plannedFrames || records.get(existing).countMode != record.countMode;
                if (existing < 0) records.add(0, record); else {
                    // A roll edit opened before a capture must not overwrite newer cut records.
                    if (!mergeRollEdit(records.get(existing), record)) return false;
                    records.set(existing, record);
                }
                String foldersJson = null;
                if (existing < 0 && folderId != null && !folderId.isEmpty()) {
                    FilmRollFolders folders = FilmRollFolders.read(preferences.getString("folders", "{}"));
                    Set<String> currentIds = new java.util.HashSet<>();
                    for (FilmRollRecord item : records) currentIds.add(item.id);
                    if (!folders.move(java.util.Collections.singleton(record.id), folderId,
                            currentIds)) return false;
                    foldersJson = folders.json();
                }
                boolean saved = commit(preferences, records, foldersJson);
                if (saved && cameraChanged) cameraUsage.record(record.cameraId, record.cameraName);
                if (saved && filmChanged) filmUsage.record(record.filmId, record.filmDisplayName());
                if (saved && focalChanged) rollDefaults.recordFocal(record.focalLength);
                if (saved && countChanged) rollDefaults.recordCount(record.plannedFrames, record.countMode);
                return saved;
            } catch (JSONException exception) {
                return false;
            }
        }
    }

    static boolean mergeRollEdit(FilmRollRecord stored, FilmRollRecord edited) {
        if (edited.plannedFrames < 1 || edited.plannedFrames > 999) return false;
        for (FilmFrameRecord frame : stored.frames) if (frame.number > edited.plannedFrames) return false;
        edited.frames = stored.frames;
        return true;
    }

    private boolean commit(List<FilmRollRecord> records) throws JSONException {
        return commit(preferences, records);
    }

    static boolean commit(SharedPreferences preferences, List<FilmRollRecord> records) throws JSONException {
        return commit(preferences, records, null);
    }

    static boolean commit(SharedPreferences preferences, List<FilmRollRecord> records, String folders) throws JSONException {
        String original = preferences.getString("rolls", "[]");
        String oldFolders = preferences.getString("folders", "{}");
        JSONArray data = new JSONArray();
        for (FilmRollRecord item : records) data.put(item.toJson());
        SharedPreferences.Editor edit = preferences.edit().putString("rolls", data.toString());
        if (folders != null) edit.putString("folders", folders);
        boolean saved = edit.commit();
        if (!saved) {
            SharedPreferences.Editor restore = preferences.edit().putString("rolls", original);
            if (folders != null) restore.putString("folders", oldFolders);
            restore.commit();
        }
        return saved;
    }

    boolean hasCapture(String rollId, String captureId) {
        synchronized (LOCK) {
            try {
                FilmRollRecord roll = find(rollId);
                if (roll != null) for (FilmFrameRecord frame : roll.frames)
                    if (frame.captureId.equals(captureId)) return true;
            } catch (JSONException ignored) { }
            return false;
        }
    }

    /** Called on the result screen's save worker. A cut is committed only after durable image output. */
    boolean saveFrame(String rollId, FilmFrameRecord frame, android.graphics.Bitmap bitmap) {
        android.graphics.Bitmap corrected = null;
        try {
            if (frame.correctionEnabled) {
                if (!FilmCorrectionProfiles.supported(frame.correctionProfile)) return false;
                corrected = FilmColorCorrection.apply(context, bitmap,frame.correctionProfile, () -> false,null,frame.sceneCaptureHour);
            }
            return saveFrame(rollId, frame, bitmap, corrected);
        } catch (Exception | OutOfMemoryError error) { return false; }
        finally { if (corrected != null) corrected.recycle(); }
    }
    boolean saveFrame(String rollId, FilmFrameRecord frame, android.graphics.Bitmap bitmap, android.graphics.Bitmap corrected) {
        synchronized (LOCK) {
            java.io.File file = null;
            java.io.File derived = null;
            try {
                List<FilmRollRecord> records = load();
                FilmRollRecord roll = null;
                for (FilmRollRecord item : records) if (item.id.equals(rollId)) roll = item;
                if (roll == null || frame.number < 1 || frame.number > roll.plannedFrames) return false;
                for (FilmFrameRecord existing : roll.frames) if (existing.captureId.equals(frame.captureId)) return true;
                // Never silently replace an already recorded physical frame.
                if (roll.frame(frame.number) != null) return false;
                if (!photoDirectory.isDirectory() && !photoDirectory.mkdirs()) return false;
                frame.photo = java.util.UUID.randomUUID() + ".jpg";
                file = new java.io.File(photoDirectory, frame.photo);
                try (java.io.FileOutputStream stream = new java.io.FileOutputStream(file)) {
                    if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, stream)) throw new java.io.IOException("JPEG failed");
                    stream.flush(); stream.getFD().sync();
                }
                frame.correctionPhoto = "";
                if (frame.correctionEnabled) {
                    if (corrected == null || !FilmCorrectionProfiles.supported(frame.correctionProfile)) throw new java.io.IOException("Correction missing");
                    frame.correctionPhoto = java.util.UUID.randomUUID() + ".jpg";
                    derived = correction(frame); writeCorrection(corrected, derived);
                }
                roll.frames.add(frame);
                if (!commit(records)) { file.delete(); if (derived != null) derived.delete(); return false; }
                return true;
            } catch (Exception | OutOfMemoryError error) {
                if (file != null) file.delete();
                if (derived != null) derived.delete();
                return false;
            }
        }
    }

    boolean saveFrameMemo(String rollId, int number, String memo) {
        if (memo.length() > 1000) return false;
        synchronized (LOCK) {
            try {
                List<FilmRollRecord> records = load();
                for (FilmRollRecord roll : records) if (roll.id.equals(rollId)) {
                    FilmFrameRecord frame = roll.frame(number);
                    if (frame == null) return false;
                    frame.memo = memo; return commit(records);
                }
            } catch (JSONException ignored) { }
            return false;
        }
    }

    int editRecord(String rollId, int cut, String capture, FilmRecordEdits.Field field, String[] values) {
        synchronized (LOCK) {
            try {
                List<FilmRollRecord> records = load();
                for (FilmRollRecord roll : records) if (roll.id.equals(rollId)) {
                    String oldCameraId = roll.cameraId, oldCameraName = roll.cameraName;
                    String oldFilmId=roll.filmId,oldFilmName=roll.filmName,oldFocal=roll.focalLength;
                    int oldCount=roll.plannedFrames,oldMode=roll.countMode;
                    filmUsage.seed(records); rollDefaults.seed(records);
                    if (field == FilmRecordEdits.Field.CAMERA) cameraUsage.seed(records);
                    int error = FilmRecordEdits.apply(roll, cut, capture, field, values);
                    if (error != 0) return error;
                    if (!commit(records)) return R.string.roll_error_save;
                    if (field == FilmRecordEdits.Field.CAMERA && (!oldCameraId.equals(roll.cameraId) || !oldCameraName.equals(roll.cameraName))) {
                        cameraUsage.record(roll.cameraId, roll.cameraName);
                    }
                    if (field == FilmRecordEdits.Field.FILM && FilmUsageStore.changed(oldFilmId,oldFilmName,roll.filmId,roll.filmName)) filmUsage.record(roll.filmId,roll.filmDisplayName());
                    if (field == FilmRecordEdits.Field.FOCAL && !oldFocal.equals(roll.focalLength)) rollDefaults.recordFocal(roll.focalLength);
                    if (field == FilmRecordEdits.Field.COUNT && (oldCount!=roll.plannedFrames || oldMode!=roll.countMode)) rollDefaults.recordCount(roll.plannedFrames,roll.countMode);
                    return 0;
                }
            } catch (JSONException ignored) { }
            return R.string.roll_error_save;
        }
    }

    boolean rotateFrame(String rollId, int number, String capture) {
        synchronized (LOCK) {
            try {
                List<FilmRollRecord> records = load();
                for (FilmRollRecord roll : records) if (roll.id.equals(rollId)) {
                    return FilmFrameRecord.rotate(roll, number, capture) && commit(records);
                }
            } catch (JSONException ignored) { }
            return false;
        }
    }

    boolean saveFrameLocation(String rollId, int number, String capture, FilmFrameLocation position) {
        synchronized (LOCK) {
            try {
                List<FilmRollRecord> records = load();
                for (FilmRollRecord roll : records) if (roll.id.equals(rollId))
                    return FilmFrameLocation.apply(roll, number, capture, position) && commit(records);
            } catch (JSONException ignored) { }
            return false;
        }
    }

    /** Adds an automatic fix only to the original, still-unedited capture. */
    boolean saveCapturedFrameLocation(String rollId, int number, String capture, FilmFrameLocation position) {
        synchronized (LOCK) {
            try {
                List<FilmRollRecord> records = load();
                for (FilmRollRecord roll : records) if (roll.id.equals(rollId))
                    return FilmFrameLocation.applyCaptured(roll, number, capture, position) && commit(records);
            } catch (JSONException ignored) { }
            return false;
        }
    }

    java.io.File photo(FilmFrameRecord frame) {
        // Persist basenames only, never expose arbitrary filesystem paths from stored metadata.
        return new java.io.File(photoDirectory, new java.io.File(frame.photo).getName());
    }

    java.io.File correction(FilmFrameRecord frame) {
        if (!FilmFrameRecord.validCorrectionName(frame.correctionPhoto)) return null;
        java.io.File file = new java.io.File(photoDirectory, frame.correctionPhoto);
        return file.equals(photo(frame)) ? null : file;
    }

    boolean saveCorrection(String rollId, int number, String capture, long revision, boolean enabled,
                           String profile) {
        return saveCorrection(rollId, number, capture, revision, enabled, profile, null);
    }
    boolean saveCorrection(String rollId, int number, String capture, long revision, boolean enabled,
                           String profile, android.graphics.Bitmap bitmap) {
        synchronized (LOCK) {
            java.io.File added = null;
            try {
                List<FilmRollRecord> records = load();
                for (FilmRollRecord roll : records) if (roll.id.equals(rollId)) {
                    FilmFrameRecord frame = roll.frame(number);
                    if (frame == null || !frame.captureId.equals(capture) || frame.correctionRevision != revision) return false;
                    if (!photo(frame).isFile() || enabled && !FilmCorrectionProfiles.supported(profile)) return false;
                    java.io.File previous = correction(frame);
                    if (enabled) {
                        if (bitmap != null) {
                            frame.correctionPhoto = java.util.UUID.randomUUID() + ".jpg";
                            added = correction(frame); writeCorrection(bitmap, added);
                        } else if (previous == null || !previous.isFile() || !profile.equals(frame.correctionProfile)) return false;
                    } else frame.correctionPhoto = "";
                    boolean selectionChanged = frame.correctionEnabled != enabled || enabled && !profile.equals(frame.correctionProfile);
                    frame.correctionProfile = enabled ? profile : frame.correctionProfile;
                    frame.correctionEnabled = enabled; if (selectionChanged) frame.correctionRevision++;
                    if (!commit(records)) { if (added != null) added.delete(); return false; }
                    if (previous != null && (added != null || !enabled)) deleteDerivedFile(previous, records);
                    return true;
                }
            } catch (Exception | OutOfMemoryError error) { if (added != null) added.delete(); }
            return false;
        }
    }
    private static void writeCorrection(android.graphics.Bitmap bitmap, java.io.File file) throws java.io.IOException {
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(file)) {
            if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, stream)) throw new java.io.IOException("Correction JPEG failed");
            stream.flush(); stream.getFD().sync();
        }
    }

    java.io.File scan(FilmFrameRecord frame) { return scanFiles.scan(frame.scanPhoto); }

    void cleanScanFiles(String keep) throws JSONException {
        synchronized (LOCK) {
            Set<String> linked = new java.util.HashSet<>();
            for (FilmRollRecord roll : load()) for (FilmFrameRecord frame : roll.frames) linked.add(frame.scanPhoto);
            scanFiles.cleanOrphans(linked, keep, System.currentTimeMillis());
        }
    }

    /** Publish the new file before metadata; delete the previous copy only after a successful commit. */
    boolean saveScan(String rollId, int number, String capture, long revision, java.io.File draft) {
        synchronized (LOCK) {
            java.io.File added = null;
            try {
                List<FilmRollRecord> records = load();
                for (FilmRollRecord roll : records) if (roll.id.equals(rollId)) {
                    FilmFrameRecord frame = roll.frame(number);
                    if (frame == null || !frame.captureId.equals(capture) || frame.scanRevision != revision) return false;
                    java.io.File previous = scan(frame);
                    if (draft != null && FilmScanRules.completedDraft(frame, draft.getName()) && previous != null && previous.isFile()) return true;
                    if (draft != null) added = scanFiles.copyPermanent(draft);
                    if (!FilmScanRules.apply(roll, number, capture, revision, added == null ? "" : added.getName())
                            || !commit(records)) {
                        if (added != null) added.delete();
                        return false;
                    }
                    if (previous != null && !previous.equals(added)) previous.delete();
                    return true;
                }
            } catch (Exception error) { if (added != null) added.delete(); }
            return false;
        }
    }

    boolean deleteFrame(String rollId, int number, String captureId) {
        synchronized (LOCK) {
            try {
                List<FilmRollRecord> records = load();
                for (FilmRollRecord roll : records) if (roll.id.equals(rollId)) {
                    FilmFrameRecord removed = removeFrame(roll, number, captureId);
                    if (removed == null || !commit(records)) return false;
                    photo(removed).delete();
                    deleteDerivedCopy(removed, records);
                    java.io.File scan = scan(removed); if (scan != null) scan.delete(); return true;
                }
            } catch (JSONException ignored) { }
            return false;
        }
    }
    static FilmFrameRecord removeFrame(FilmRollRecord roll, int number, String captureId) {
        FilmFrameRecord target = roll.frame(number);
        if (target == null || !target.captureId.equals(captureId)) return null;
        roll.frames.remove(target); return target;
    }

    boolean delete(Set<String> selectedIds) {
        if (selectedIds.isEmpty()) return true;
        synchronized (LOCK) {
            try {
                // Read again under the shared lock so later creations/edits are preserved.
                List<FilmRollRecord> all = load();
                String original = preferences.getString("rolls", "[]");
                List<FilmRollRecord> remaining = remainingAfterDelete(all, selectedIds);
                JSONArray data = new JSONArray();
                for (FilmRollRecord record : remaining) data.put(record.toJson());
                boolean committed = preferences.edit().putString("rolls", data.toString()).commit();
                if (!committed) {
                    // A failed SharedPreferences commit can still change its memory copy.
                    preferences.edit().putString("rolls", original).commit();
                } else {
                    noteIsoStore.remove(selectedIds);
                    noteExposureStore.remove(selectedIds);
                    noteFieldOfViewStore.remove(selectedIds);
                    for (FilmRollRecord roll : all) if (selectedIds.contains(roll.id))
                        for (FilmFrameRecord frame : roll.frames) {
                            photo(frame).delete();
                            deleteDerivedCopy(frame, all);
                            java.io.File scan = scan(frame); if (scan != null) scan.delete();
                        }
                }
                return committed;
            } catch (JSONException exception) {
                return false;
            }
        }
    }

    private void deleteDerivedCopy(FilmFrameRecord frame, List<FilmRollRecord> records) {
        java.io.File derived = correction(frame);
        if (derived == null) return;
        deleteDerivedFile(derived, records);
    }
    private void deleteDerivedFile(java.io.File derived, List<FilmRollRecord> records) {
        for (FilmRollRecord roll : records) for (FilmFrameRecord other : roll.frames)
            if (derived.equals(photo(other))) return;
        derived.delete();
    }

    static List<FilmRollRecord> remainingAfterDelete(List<FilmRollRecord> records, Set<String> selectedIds) {
        List<FilmRollRecord> remaining = new ArrayList<>();
        for (FilmRollRecord record : records) if (!selectedIds.contains(record.id)) remaining.add(record);
        return remaining;
    }
}
