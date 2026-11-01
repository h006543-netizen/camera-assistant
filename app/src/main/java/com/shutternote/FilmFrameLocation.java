package com.shutternote;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.Locale;

/** A position sampled during capture or explicitly selected by the user. */
final class FilmFrameLocation {
    final double latitude, longitude;
    final double accuracyMeters; // -1 for a point selected manually.

    FilmFrameLocation(double latitude, double longitude, double accuracyMeters) {
        if (!valid(latitude, longitude)) throw new IllegalArgumentException("Invalid coordinates");
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = Double.isFinite(accuracyMeters) && accuracyMeters >= 0 ? accuracyMeters : -1;
    }
    static boolean valid(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }
    static FilmFrameLocation parse(String latitude, String longitude) {
        try { return new FilmFrameLocation(Double.parseDouble(latitude.trim().replace(',', '.')), Double.parseDouble(longitude.trim().replace(',', '.')), -1); }
        catch (IllegalArgumentException error) { return null; }
    }
    String coordinates() { return String.format(Locale.US, "%.6f, %.6f", latitude, longitude); }
    String googleMapsUrl() {
        return "https://www.google.com/maps/search/?api=1&query="
                + Double.toString(latitude) + "%2C" + Double.toString(longitude);
    }
    JSONObject toJson() throws JSONException {
        JSONObject data = new JSONObject().put("latitude", latitude).put("longitude", longitude);
        if (accuracyMeters >= 0) data.put("accuracyMeters", accuracyMeters);
        return data;
    }
    static FilmFrameLocation fromJson(JSONObject data) {
        if (data == null || !data.has("latitude") || !data.has("longitude")) return null;
        double latitude = data.optDouble("latitude", Double.NaN), longitude = data.optDouble("longitude", Double.NaN);
        return valid(latitude, longitude) ? new FilmFrameLocation(latitude, longitude, data.optDouble("accuracyMeters", -1)) : null;
    }
    static boolean apply(FilmRollRecord roll, int cut, String capture, FilmFrameLocation position) {
        FilmFrameRecord frame = roll.frame(cut);
        if (frame == null || !frame.captureId.equals(capture)) return false;
        frame.location = position;
        if (frame.locationRevision < Long.MAX_VALUE) frame.locationRevision++;
        return true;
    }
    static boolean applyCaptured(FilmRollRecord roll, int cut, String capture, FilmFrameLocation position) {
        FilmFrameRecord frame = roll.frame(cut);
        if (position == null || frame == null || !frame.captureId.equals(capture)
                || frame.location != null || frame.locationRevision != 0) return false;
        frame.location = position;
        return true;
    }
}
