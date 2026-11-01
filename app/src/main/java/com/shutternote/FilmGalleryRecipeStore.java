package com.shutternote;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import org.json.JSONObject;

/** One gallery original and at most one current corrected JPEG in app storage. */
@android.annotation.SuppressLint("ApplySharedPref") // Save/rollback must be durable before reporting success or failure.
final class FilmGalleryRecipeStore {
    private static SharedPreferences records(Context context) { return context.getSharedPreferences("capture_saves", Context.MODE_PRIVATE); }
    static boolean save(Context context, String capture, Uri output, boolean corrected) {
        return save(context, capture, output, corrected, null);
    }
    static synchronized boolean save(Context context, String capture, Uri output, boolean corrected, Bitmap bitmap) {
        File added = null; Bitmap rendered = null;
        try {
            if (corrected) {
                if (bitmap == null) { rendered = render(context, output); bitmap = rendered; }
                added = write(context, bitmap);
            }
            File previous = correction(context, capture);
            if (!saveMetadata(context, capture, output, corrected, added)) { if (added != null) added.delete(); return false; }
            if (previous != null && !previous.equals(added)) previous.delete();
            return true;
        } catch (Exception | OutOfMemoryError error) { if (added != null) added.delete(); return false; }
        finally { if (rendered != null) rendered.recycle(); }
    }
    private static boolean saveMetadata(Context context, String capture, Uri output, boolean corrected, File file) {
        SharedPreferences records = records(context);
        String recipe = recipe(corrected, file);
        boolean hadCapture = records.contains(capture), previousSaved = records.getBoolean(capture, false);
        String previousOutput = records.getString("output:" + capture, null), previousRecipe = records.getString("recipe:" + output, null);
        if (records.edit().putBoolean(capture, true).putString("output:" + capture, output.toString())
                .putString("recipe:" + output, recipe).commit()) return true;
        SharedPreferences.Editor restore = records.edit();
        if (hadCapture) restore.putBoolean(capture, previousSaved); else restore.remove(capture);
        restore(restore, "output:" + capture, previousOutput); restore(restore, "recipe:" + output, previousRecipe);
        restore.commit(); return false;
    }
    static synchronized boolean select(Context context, String capture, boolean corrected) {
        SharedPreferences records = records(context);
        String output = records.getString("output:" + capture, null);
        // Older two-file gallery saves are user files and are not rewritten or deleted.
        if (output == null) return !corrected;
        File cached = correction(context, capture);
        if (corrected && corrected(context, capture) && cached != null && cached.isFile()) return true;
        File added = null; Bitmap rendered = null;
        try {
            if (corrected) { rendered = render(context, Uri.parse(output)); added = write(context, rendered); }
        String previous = records.getString("recipe:" + output, null);
        if (records.edit().putString("recipe:" + output, recipe(corrected, added)).commit()) {
            if (cached != null && !cached.equals(added)) cached.delete();
            return true;
        }
        SharedPreferences.Editor restore = records.edit(); restore(restore, "recipe:" + output, previous); restore.commit(); return false;
        } catch (Exception | OutOfMemoryError error) { return false; }
        finally {
            if (rendered != null) rendered.recycle();
            if (added != null && !added.equals(correction(context, capture))) added.delete();
        }
    }
    static boolean corrected(Context context, String capture) {
        SharedPreferences records = records(context);
        String output = records.getString("output:" + capture, null);
        if (output == null) return false;
        try {
            JSONObject recipe = new JSONObject(records.getString("recipe:" + output, "{}"));
            return recipe.optInt("schema") == 1 && recipe.optBoolean("enabled")
                    && FilmColorCorrection.E100D.equals(recipe.optString("profile"));
        } catch (Exception ignored) { return false; }
    }
    static File correction(Context context, String capture) {
        try {
            SharedPreferences records = records(context); String output = records.getString("output:" + capture, null);
            if (output == null) return null;
            JSONObject recipe = new JSONObject(records.getString("recipe:" + output, "{}"));
            String name = recipe.optString("correctionPhoto");
            if (!recipe.optBoolean("enabled") || !name.matches("[A-Za-z0-9_-]+\\.jpg")) return null;
            return new File(new File(context.getFilesDir(), "capture_corrections"), name);
        } catch (Exception ignored) { return null; }
    }
    private static String recipe(boolean corrected, File file) {
        try { return new JSONObject().put("schema", 1).put("profile", corrected ? FilmColorCorrection.E100D : "")
                .put("enabled", corrected).put("correctionPhoto", file == null ? "" : file.getName()).toString(); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private static File write(Context context, Bitmap bitmap) throws Exception {
        File directory = new File(context.getFilesDir(), "capture_corrections");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("Cannot create correction directory");
        File file = new File(directory, java.util.UUID.randomUUID() + ".jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) throw new java.io.IOException("Correction JPEG failed");
            out.flush(); out.getFD().sync(); return file;
        } catch (Exception | OutOfMemoryError error) { file.delete(); throw error; }
    }
    private static Bitmap render(Context context, Uri output) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(output)) { BitmapFactory.decodeStream(in, null, bounds); }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new java.io.IOException("Gallery original missing");
        Runtime runtime = Runtime.getRuntime(); BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = ImageMemoryBudget.sampleSize(bounds.outWidth, bounds.outHeight, runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()));
        Bitmap original;
        try (InputStream in = context.getContentResolver().openInputStream(output)) { original = BitmapFactory.decodeStream(in, null, options); }
        if (original == null) throw new java.io.IOException("Cannot decode original");
        try { return FilmColorCorrection.apply(context, original, () -> false); }
        finally { original.recycle(); }
    }
    private static void restore(SharedPreferences.Editor editor, String key, String value) {
        if (value == null) editor.remove(key); else editor.putString(key, value);
    }
}
