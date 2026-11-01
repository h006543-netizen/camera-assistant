package com.shutternote;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Streaming photo-only export. Roll records and source JPEGs are never changed. */
final class FilmRollPhotoArchive {
    interface OrientationWriter { void write(File copy, int degrees) throws IOException; }
    interface Progress { void update(int completed, int total); }
    interface RecipeRenderer { void render(File original, FilmFrameRecord frame, File output) throws IOException; }

    static String folderName(String title) {
        String value = title == null ? "" : title.replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_").trim();
        value = value.replaceAll("^[. ]+|[. ]+$", "");
        if (value.length() > 64) {
            int end = 64;
            if (Character.isHighSurrogate(value.charAt(end-1))) end--;
            value = value.substring(0, end).replaceAll("[. ]+$", "");
        }
        if (value.isEmpty()) value = "ShutterNote_roll";
        if (value.matches("(?i)(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])(\\..*)?")) value = "roll_"+value;
        return value;
    }

    static int write(File photos, FilmRollRecord roll, File work, OutputStream destination,
                     OrientationWriter orientation, Progress progress) throws IOException {
        return write(photos, roll, work, destination, orientation, progress,
                (source, frame, output) -> { throw new IOException("Recipe renderer required"); });
    }
    static int write(File photos, FilmRollRecord roll, File work, OutputStream destination,
                     OrientationWriter orientation, Progress progress, RecipeRenderer renderer) throws IOException {
        List<FilmFrameRecord> frames = new ArrayList<>(roll.frames);
        frames.sort(Comparator.comparingInt(f -> f.number));
        if (frames.isEmpty()) throw new IOException("No photos in roll");
        Set<Integer> numbers = new HashSet<>();
        File directory = photos.getCanonicalFile();
        for (FilmFrameRecord frame : frames) {
            if (frame.number < 1 || frame.number > 999 || !numbers.add(frame.number))
                throw new IOException("Invalid or duplicate cut number");
            source(directory, frame);
        }
        if (!work.isDirectory() && !work.mkdirs()) throw new IOException("Cannot create export work directory");
        String prefix = folderName(roll.title())+"/";
        progress.update(0, frames.size());
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(destination))) {
            for (int i = 0; i < frames.size(); i++) {
                checkCancelled();
                FilmFrameRecord frame = frames.get(i);
                File original = source(directory, frame), prepared = original;
                boolean temporary = false;
                try {
                    int degrees = FilmFrameRecord.normalizedRotation(frame.rotation);
                    if (frame.correctionEnabled) {
                        File cached = FilmFrameRecord.validCorrectionName(frame.correctionPhoto)
                                ? new File(directory, frame.correctionPhoto).getCanonicalFile() : null;
                        if (cached != null && directory.equals(cached.getParentFile()) && cached.isFile() && cached.length() > 0) prepared = cached;
                        else {
                            prepared = File.createTempFile("rendered-", ".jpg", work); temporary = true;
                            renderer.render(original, frame, prepared);
                            checkCancelled();
                            if (!prepared.isFile() || prepared.length() == 0) throw new IOException("Recipe render missing");
                        }
                    }
                    if (degrees != 0 && !temporary) {
                        File selected = prepared;
                        prepared = File.createTempFile("oriented-", ".jpg", work);
                        temporary = true;
                        try (InputStream in = new FileInputStream(selected); OutputStream out = new FileOutputStream(prepared)) {
                            copy(in, out);
                        }
                    }
                    if (degrees != 0) orientation.write(prepared, degrees);
                    ZipEntry entry = new ZipEntry(prefix+String.format(Locale.ROOT, "frame_%03d.jpg", frame.number));
                    if (frame.capturedAt > 0) entry.setTime(frame.capturedAt);
                    zip.putNextEntry(entry);
                    try (InputStream in = new FileInputStream(prepared)) { copy(in, zip); }
                    zip.closeEntry();
                } finally {
                    if (temporary) prepared.delete();
                }
                progress.update(i+1, frames.size());
            }
            checkCancelled();
        }
        return frames.size();
    }

    private static File source(File directory, FilmFrameRecord frame) throws IOException {
        String name = frame.photo;
        if (!name.matches("[A-Za-z0-9_-][A-Za-z0-9._-]*\\.jpg")) throw new IOException("Unsafe photo name");
        File file = new File(directory, name).getCanonicalFile();
        if (!directory.equals(file.getParentFile()) || !file.isFile() || file.length() == 0)
            throw new IOException("Photo missing or unsafe path");
        return file;
    }

    static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[64*1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            checkCancelled();
            output.write(buffer, 0, count);
        }
        checkCancelled();
    }
    private static void checkCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Export cancelled");
    }
}
