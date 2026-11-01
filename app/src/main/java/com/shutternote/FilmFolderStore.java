package com.shutternote;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import java.util.HashSet;
import java.util.Set;

final class FilmFolderStore {
    enum Operation { CREATE, RENAME, REMOVE, MOVE }
    private final Context context;
    private final SharedPreferences preferences;
    FilmFolderStore(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences("film_roll_records", Context.MODE_PRIVATE);
    }
    FilmRollFolders load() throws JSONException {
        synchronized (FilmRollStore.LOCK) { return FilmRollFolders.read(preferences.getString("folders", "{}")); }
    }
    boolean update(Operation operation, String id, String name, Set<String> rolls) {
        synchronized (FilmRollStore.LOCK) {
            try {
                FilmRollFolders folders = load();
                Set<String> existing = new HashSet<>();
                for (FilmRollRecord roll : new FilmRollStore(context).load()) existing.add(roll.id);
                boolean changed;
                switch (operation) {
                    case CREATE:
                        changed = folders.create(id, name);
                        if (changed && !rolls.isEmpty()) changed = folders.move(rolls, id, existing);
                        break;
                    case RENAME: changed = folders.rename(id, name); break;
                    case REMOVE: changed = folders.remove(id); break;
                    default: changed = folders.move(rolls, id, existing);
                }
                return changed && commit(preferences, folders.json());
            } catch (JSONException error) { return false; }
        }
    }
    // Runs on the save worker; durable success is needed before reporting a completed move.
    @android.annotation.SuppressLint("ApplySharedPref")
    static boolean commit(SharedPreferences preferences, String json) {
        String previous = preferences.getString("folders", "{}");
        boolean saved = preferences.edit().putString("folders", json).commit();
        if (!saved) preferences.edit().putString("folders", previous).commit();
        return saved;
    }
}
