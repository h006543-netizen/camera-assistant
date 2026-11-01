package com.shutternote;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.*;
import java.util.Date;
import java.util.UUID;

/** Copies the selected note JPEG without decoding or recompressing it. */
final class FilmNoteGallerySaver {
    static final class PartialSaveException extends IOException {
        PartialSaveException(Throwable cause) { super("Could not remove incomplete gallery photo", cause); }
    }
    static Uri save(Context context, FilmFrameRecord snapshot) throws Exception {
        FilmRollStore store = new FilmRollStore(context);
        File source = FilmNoteCorrectionCache.source(context, store, snapshot);
        File copy = File.createTempFile("note-gallery-", ".jpg", context.getCacheDir());
        try {
            try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(copy)) {
                FilmRollPhotoArchive.copy(in, out);
            }
            int rotation = FilmFrameRecord.normalizedRotation(snapshot.rotation);
            if (rotation != 0) FilmRollExportViewModel.orientCopy(copy, rotation);
            String base = GalleryFileNameBuilder.build(snapshot.iso, snapshot.aperture, snapshot.shutter,
                    snapshot.fieldOfView, new Date(snapshot.capturedAt));
            String name = base.substring(0, base.length() - 4) + "_Note_" + UUID.randomUUID() + ".jpg";
            return publish(context, copy, name);
        } finally { copy.delete(); }
    }

    static Uri publish(Context context, File source, String name) throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            android.content.ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera/");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Cannot create gallery photo");
            try {
                try (InputStream in = new FileInputStream(source); OutputStream out = resolver.openOutputStream(uri)) {
                    if (out == null) throw new IOException("Cannot open gallery photo");
                    FilmRollPhotoArchive.copy(in, out);
                }
                ContentValues ready = new ContentValues(); ready.put(MediaStore.Images.Media.IS_PENDING, 0);
                if (resolver.update(uri, ready, null, null) != 1) throw new IOException("Cannot publish gallery photo");
                return uri;
            } catch (IOException | RuntimeException | OutOfMemoryError error) {
                boolean removed = false;
                try { removed = resolver.delete(uri, null, null) > 0; } catch (RuntimeException cleanup) { error.addSuppressed(cleanup); }
                if (!removed) throw new PartialSaveException(error);
                throw error;
            }
        }
        File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create gallery directory");
        File target = new File(directory, name);
        for (int suffix = 1; !target.createNewFile(); suffix++) target = new File(directory, GalleryFileNameBuilder.numberedName(name, suffix));
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(target)) {
            FilmRollPhotoArchive.copy(in, out);
        } catch (IOException | RuntimeException | OutOfMemoryError error) {
            if (!target.delete()) throw new PartialSaveException(error);
            throw error;
        }
        MediaScannerConnection.scanFile(context, new String[]{target.getAbsolutePath()}, new String[]{"image/jpeg"}, null);
        return Uri.fromFile(target);
    }
}
