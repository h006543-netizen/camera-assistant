package com.shutternote;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Set;

/** User choices inside note capture only, one key per roll, excluded from backup. */
final class FilmNoteFieldOfViewStore {
    private final SharedPreferences preferences;

    FilmNoteFieldOfViewStore(Context context) {
        this(context.getApplicationContext().getSharedPreferences("film_note_fov", Context.MODE_PRIVATE));
    }
    FilmNoteFieldOfViewStore(SharedPreferences preferences) { this.preferences = preferences; }

    String load(String rollId, String focalLength) {
        return FilmNoteCaptureSettings.fieldOfView(focalLength, preferences.getString(rollId, null));
    }
    void save(String rollId, String value) {
        if (rollId != null && !rollId.isEmpty() && ExposureSettingsStore.isValidFieldOfView(value)) {
            preferences.edit().putString(rollId, value).apply();
        }
    }
    void remove(Set<String> ids) {
        SharedPreferences.Editor editor = preferences.edit();
        for (String id : ids) editor.remove(id);
        editor.apply();
    }
}
