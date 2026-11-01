package com.shutternote;

import org.json.JSONException;
import org.json.JSONObject;

/** A saved cut. Exposure values are snapshots, independent of current camera settings. */
final class FilmFrameRecord {
    int number;
    int rotation;
    long capturedAt;
    double sceneCaptureHour = Double.NaN;
    FilmFrameLocation location;
    long locationRevision;
    String scanPhoto = "";
    long scanRevision;
    String correctionPhoto = "", correctionProfile = "";
    boolean correctionEnabled;
    long correctionRevision;
    String photo = "", captureId = "", iso = "", aperture = "", shutter = "", fieldOfView = "", memo = "";

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("number", number).put("rotation", normalizedRotation(rotation)).put("capturedAt", capturedAt)
                .put("sceneCaptureHour", Vision500SceneTime.validHour(sceneCaptureHour)?sceneCaptureHour:JSONObject.NULL)
                .put("photo", photo).put("captureId", captureId).put("iso", iso)
                .put("aperture", aperture).put("shutter", shutter).put("fieldOfView", fieldOfView).put("memo", memo)
                .put("location", location == null ? JSONObject.NULL : location.toJson())
                .put("locationRevision", locationRevision).put("scanPhoto", scanPhoto).put("scanRevision", scanRevision)
                .put("correctionPhoto", correctionPhoto).put("correctionProfile", correctionProfile)
                .put("correctionEnabled", correctionEnabled).put("correctionRevision", correctionRevision);
    }

    static FilmFrameRecord fromJson(JSONObject data) throws JSONException {
        FilmFrameRecord frame = new FilmFrameRecord();
        frame.number = data.getInt("number"); frame.capturedAt = data.optLong("capturedAt");
        double hour=data.optDouble("sceneCaptureHour",Double.NaN);
        frame.sceneCaptureHour=Vision500SceneTime.validHour(hour)?hour:Double.NaN;
        frame.rotation = normalizedRotation(data.optInt("rotation", 0));
        frame.photo = data.optString("photo"); frame.captureId = data.optString("captureId");
        frame.iso = data.optString("iso"); frame.aperture = data.optString("aperture");
        frame.shutter = data.optString("shutter"); frame.fieldOfView = data.optString("fieldOfView");
        frame.memo = data.optString("memo");
        frame.location = FilmFrameLocation.fromJson(data.optJSONObject("location"));
        frame.locationRevision = Math.max(0, data.optLong("locationRevision", 0));
        String scan = data.optString("scanPhoto", "");
        frame.correctionPhoto = validCorrectionName(data.optString("correctionPhoto")) ? data.optString("correctionPhoto") : "";
        frame.correctionProfile = data.optString("correctionProfile");
        if (frame.correctionPhoto.equals(frame.photo)) frame.correctionPhoto = "";
        frame.correctionEnabled = data.optBoolean("correctionEnabled")
                && (!frame.correctionProfile.isEmpty() || !frame.correctionPhoto.isEmpty());
        frame.correctionRevision = Math.max(0, data.optLong("correctionRevision"));
        frame.scanPhoto = FilmScanRules.validName(scan) ? scan : "";
        frame.scanRevision = Math.max(0, data.optLong("scanRevision", 0)); return frame;
    }
    static int normalizedRotation(int degrees) {
        return degrees % 90 == 0 ? Math.floorMod(degrees, 360) : 0;
    }
    static boolean validCorrectionName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-][A-Za-z0-9._-]*\\.jpg");
    }
    String displayPhotoName() { return correctionEnabled && validCorrectionName(correctionPhoto) ? correctionPhoto : photo; }
    String imageKey() { return photo + ":" + correctionEnabled + ":" + correctionProfile + ":" + correctionRevision; }

    static boolean rotate(FilmRollRecord roll, int cut, String capture) {
        FilmFrameRecord frame = roll.frame(cut);
        if (frame == null || !frame.captureId.equals(capture)) return false;
        // Subtract a quarter turn for counterclockwise rotation; retain stored orientations.
        frame.rotation = normalizedRotation(normalizedRotation(frame.rotation) - 90);
        return true;
    }
}
