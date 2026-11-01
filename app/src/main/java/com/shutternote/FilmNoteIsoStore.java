package com.shutternote;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Set;

/** Roll-specific ISO selections; deliberately separate from general exposure settings and backup. */
final class FilmNoteIsoStore {
    private final SharedPreferences preferences;

    FilmNoteIsoStore(Context context) {
        this(context.getApplicationContext().getSharedPreferences("film_note_iso", Context.MODE_PRIVATE));
    }

    FilmNoteIsoStore(SharedPreferences preferences) { this.preferences = preferences; }

    String load(String rollId, int filmSpeed) {
        String saved = preferences.getString(rollId, null);
        return ExposureSettingsStore.isValidIso(saved) ? saved
                : ExposureSettingsStore.normalizeIso(Integer.toString(filmSpeed));
    }

    void save(String rollId, String iso) {
        if (rollId != null && !rollId.isEmpty() && ExposureSettingsStore.isValidIso(iso)) {
            preferences.edit().putString(rollId, iso).apply();
        }
    }

    void remove(Set<String> ids) {
        SharedPreferences.Editor editor = preferences.edit();
        for (String id : ids) editor.remove(id);
        editor.apply();
    }
}
