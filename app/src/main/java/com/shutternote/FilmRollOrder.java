package com.shutternote;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reorders only IDs seen by the user, retaining the latest records and newly created rolls. */
final class FilmRollOrder {
    static List<FilmRollRecord> apply(List<FilmRollRecord> latest, List<String> requested) {
        Map<String, FilmRollRecord> remaining = new LinkedHashMap<>();
        for (FilmRollRecord roll : latest) remaining.put(roll.id, roll);
        List<FilmRollRecord> moved = new ArrayList<>();
        for (String id : requested) {
            FilmRollRecord roll = remaining.remove(id);
            if (roll != null) moved.add(roll);
        }
        List<FilmRollRecord> result = new ArrayList<>(remaining.values());
        result.addAll(moved);
        return result;
    }
}
