package com.shutternote;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Set;

/** Aperture and shutter belong to a roll; ISO and field of view retain their existing stores. */
final class FilmNoteExposureStore {
    private final SharedPreferences preferences;

    FilmNoteExposureStore(Context context) {
        this(context.getApplicationContext().getSharedPreferences("film_note_exposure", Context.MODE_PRIVATE));
    }
    FilmNoteExposureStore(SharedPreferences preferences) { this.preferences = preferences; }

    static final class Values {
        final String aperture, shutter;
        Values(String aperture, String shutter) { this.aperture=aperture; this.shutter=shutter; }
    }

    Values load(String rollId) {
        if (rollId == null || rollId.isEmpty())
            return new Values(ExposureSettingsStore.DEFAULT_APERTURE, ExposureSettingsStore.DEFAULT_SHUTTER);
        return new Values(ExposureSettingsStore.normalizeAperture(preferences.getString("aperture/"+rollId, null)),
                ExposureSettingsStore.normalizeShutter(preferences.getString("shutter/"+rollId, null)));
    }

    void save(String rollId, String aperture, String shutter) {
        if (rollId == null || rollId.isEmpty() || !ExposureSettingsStore.isValidAperture(aperture)
                || !ExposureSettingsStore.isValidShutter(shutter)) return;
        preferences.edit().putString("aperture/"+rollId, aperture).putString("shutter/"+rollId, shutter).apply();
    }

    void remove(Set<String> ids) {
        SharedPreferences.Editor editor=preferences.edit();
        for (String id:ids) editor.remove("aperture/"+id).remove("shutter/"+id);
        editor.apply();
    }
}
