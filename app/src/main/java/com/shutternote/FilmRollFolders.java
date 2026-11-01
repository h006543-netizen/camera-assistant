package com.shutternote;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Membership is independent of roll edits, so an old edit screen cannot undo a folder move. */
final class FilmRollFolders {
    final Map<String, String> names = new LinkedHashMap<>();
    private final Map<String, String> members = new LinkedHashMap<>();

    static FilmRollFolders read(String json) throws JSONException {
        FilmRollFolders result = new FilmRollFolders();
        JSONObject data = new JSONObject(json);
        JSONArray folders = data.has("folders") ? data.getJSONArray("folders") : new JSONArray();
        for (int i = 0; i < folders.length(); i++) {
            JSONObject folder = folders.getJSONObject(i);
            String id = folder.getString("id"), name = folder.getString("name");
            if (id.isEmpty() || !validName(name) || result.names.containsKey(id)) throw new JSONException("Invalid folder");
            result.names.put(id, name);
        }
        JSONObject members = data.has("members") ? data.getJSONObject("members") : new JSONObject();
        java.util.Iterator<String> keys = members.keys();
        while (keys.hasNext()) {
            String id = keys.next(), folder = members.getString(id);
            if (result.names.containsKey(folder)) result.members.put(id, folder);
        }
        return result;
    }

    static boolean validName(String name) { return name != null && !name.trim().isEmpty() && name.trim().length() <= 80; }
    boolean create(String id, String name) {
        if (id == null || id.isEmpty() || names.containsKey(id) || !validName(name)) return false;
        names.put(id, name.trim()); return true;
    }
    boolean rename(String id, String name) {
        if (!names.containsKey(id) || !validName(name)) return false;
        names.put(id, name.trim()); return true;
    }
    boolean remove(String id) {
        if (names.remove(id) == null) return false;
        members.entrySet().removeIf(entry -> id.equals(entry.getValue())); return true;
    }
    String folderOf(String roll) { return members.getOrDefault(roll, ""); }
    boolean move(Set<String> rolls, String destination, Set<String> existing) {
        if (destination == null || !destination.isEmpty() && !names.containsKey(destination)) return false;
        for (String id : rolls) if (existing.contains(id)) {
            if (destination.isEmpty()) members.remove(id); else members.put(id, destination);
        }
        members.keySet().retainAll(existing);
        return true;
    }
    String json() throws JSONException {
        JSONArray folders = new JSONArray();
        for (Map.Entry<String, String> folder : names.entrySet())
            folders.put(new JSONObject().put("id", folder.getKey()).put("name", folder.getValue()));
        JSONObject membership = new JSONObject();
        for (Map.Entry<String, String> entry : members.entrySet()) membership.put(entry.getKey(), entry.getValue());
        return new JSONObject().put("folders", folders).put("members", membership).toString();
    }
}
