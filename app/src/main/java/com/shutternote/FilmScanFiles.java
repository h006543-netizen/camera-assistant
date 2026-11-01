package com.shutternote;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** Original bytes are copied, never recompressed. Only generated basenames are accepted. */
final class FilmScanFiles {
    static final long MAX_BYTES = 128L * 1024 * 1024;
    private static final long DRAFT_AGE = 24L * 60 * 60 * 1000;
    private final File directory;

    FilmScanFiles(File appFiles) { directory = new File(appFiles, "film_notes_scans"); }

    File scan(String name) { return FilmScanRules.validName(name) ? new File(directory, name) : null; }

    File draft(String name) {
        return name != null && name.startsWith("pending-") && FilmScanRules.validName(name.substring(8))
                ? new File(directory, name) : null;
    }

    File copyDraft(InputStream input) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create scan folder");
        File file = new File(directory, "pending-" + UUID.randomUUID() + ".img");
        copy(input, file);
        return file;
    }

    File copyPermanent(File draft) throws IOException {
        File expected = draft(draft.getName());
        if (expected == null || !expected.getCanonicalFile().equals(draft.getCanonicalFile()))
            throw new IOException("Invalid draft");
        // Retain the draft identity so a restored screen can recognize a completed save.
        File file = new File(directory, draft.getName().substring(8));
        try (InputStream input = new java.io.FileInputStream(draft)) { copy(input, file); }
        return file;
    }

    private static void copy(InputStream input, File file) throws IOException {
        boolean complete = false;
        try {
            try (FileOutputStream output = new FileOutputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Interrupted");
                    total += count;
                    if (total > MAX_BYTES) throw new IOException("Scan too large");
                    output.write(buffer, 0, count);
                }
                if (total == 0) throw new IOException("Empty scan");
                output.flush(); output.getFD().sync();
            }
            complete = true;
        } finally { if (!complete) file.delete(); }
    }

    void cleanDrafts(String keep, long now) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) if (draft(file.getName()) != null && !file.getName().equals(keep)
                && now - file.lastModified() >= DRAFT_AGE) file.delete();
    }

    void cleanOrphans(java.util.Set<String> linked, String keep, long now) {
        cleanDrafts(keep, now);
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) if (FilmScanRules.validName(file.getName()) && !linked.contains(file.getName())
                && now - file.lastModified() >= DRAFT_AGE) file.delete();
    }
}
