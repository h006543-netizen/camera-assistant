package com.shutternote;

import org.json.JSONException;
import org.json.JSONObject;

final class FilmRollRecord {
    String id = "", filmId = "", filmName = "", cameraId = "", cameraName = "";
    String name = "", focalLength = "", memo = "", labName = "", labMemo = "";
    int plannedFrames = 36, countMode = 36, labStatus = 0;
    long start = FilmRollRules.NO_DATE, end = FilmRollRules.NO_DATE;
    long received = FilmRollRules.NO_DATE, completed = FilmRollRules.NO_DATE;
    java.util.List<FilmFrameRecord> frames = new java.util.ArrayList<>();

    FilmFrameRecord frame(int number) {
        for (FilmFrameRecord frame : frames) if (frame.number == number) return frame;
        return null;
    }

    String filmDisplayName() {
        String brand = filmId.startsWith("kodak-") ? "KODAK" : filmId.startsWith("fujifilm-") ? "FUJIFILM" : "";
        String prefix = brand + " ";
        return !brand.isEmpty() && filmName.regionMatches(true, 0, prefix, 0, prefix.length())
                ? filmName.substring(prefix.length()) : filmName;
    }

    String title() { return name.trim().isEmpty() ? filmDisplayName() : name; }

    JSONObject toJson() throws JSONException {
        org.json.JSONArray cuts = new org.json.JSONArray();
        for (FilmFrameRecord frame : frames) cuts.put(frame.toJson());
        return new JSONObject().put("frames", cuts).put("id", id).put("filmId", filmId).put("filmName", filmName)
                .put("cameraId", cameraId).put("cameraName", cameraName).put("name", name)
                .put("focalLength", focalLength).put("memo", memo).put("labName", labName)
                .put("labMemo", labMemo).put("plannedFrames", plannedFrames).put("countMode", countMode)
                .put("labStatus", labStatus).put("start", start).put("end", end)
                .put("received", received).put("completed", completed);
    }

    static FilmRollRecord fromJson(JSONObject object) throws JSONException {
        FilmRollRecord record = new FilmRollRecord();
        record.id = object.getString("id");
        record.filmId = object.optString("filmId"); record.filmName = object.optString("filmName");
        record.cameraId = object.optString("cameraId"); record.cameraName = object.optString("cameraName");
        record.name = object.optString("name"); record.focalLength = object.optString("focalLength");
        record.memo = object.optString("memo"); record.labName = object.optString("labName");
        record.labMemo = object.optString("labMemo"); record.plannedFrames = object.optInt("plannedFrames", 36);
        record.countMode = object.optInt("countMode", 36); record.labStatus = object.optInt("labStatus", 0);
        record.start = object.optLong("start", FilmRollRules.NO_DATE);
        record.end = object.optLong("end", FilmRollRules.NO_DATE);
        record.received = object.optLong("received", FilmRollRules.NO_DATE);
        record.completed = object.optLong("completed", FilmRollRules.NO_DATE);
        org.json.JSONArray cuts = object.optJSONArray("frames");
        if (cuts != null) for (int i = 0; i < cuts.length(); i++) record.frames.add(FilmFrameRecord.fromJson(cuts.getJSONObject(i)));
        return record;
    }
}
