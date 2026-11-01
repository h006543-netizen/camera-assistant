package com.shutternote;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

/** Reuses one preview across record refreshes and disposes it when its cut leaves the page. */
public final class FilmNoteLocationView extends LinearLayout {
    private final TextView coordinates, edit, accuracy;
    private final FrameLayout preview;
    private FilmNoteMap map;
    private FilmFrameRecord frame;
    private AppCompatActivity activity;
    private boolean active;
    private String shownCapture = "";
    private FilmFrameLocation shownPosition;

    public FilmNoteLocationView(Context context, AttributeSet attrs) {
        super(context, attrs); setOrientation(VERTICAL);
        View line = new View(context); line.setBackgroundColor(ContextCompat.getColor(context, R.color.note_gallery_rule));
        LayoutParams rule = new LayoutParams(-1, dp(1)); rule.topMargin = dp(20); rule.bottomMargin = dp(12); addView(line, rule);
        TextView heading = text(12, true); heading.setText(R.string.note_location); ViewCompat.setAccessibilityHeading(heading, true); addView(heading);
        coordinates = text(16, false); coordinates.setMinHeight(dp(48)); coordinates.setGravity(Gravity.CENTER_VERTICAL);
        coordinates.setFocusable(true); addView(coordinates);
        accuracy = text(12, true); addView(accuracy);
        preview = new FrameLayout(context); addView(preview, new LayoutParams(-1, dp(200)));
        edit = text(14, false); edit.setMinHeight(dp(48)); edit.setGravity(Gravity.CENTER_VERTICAL); edit.setFocusable(true); addView(edit);
    }
    void bind(AppCompatActivity activity, String rollId, FilmFrameRecord frame, boolean active) {
        this.activity = activity; this.frame = frame; this.active = active;
        coordinates.setText(frame.location == null ? getContext().getString(R.string.note_no_location) : frame.location.coordinates());
        coordinates.setContentDescription(frame.location == null ? getContext().getString(R.string.note_no_location)
                : getContext().getString(R.string.note_open_google_maps) + ", " + frame.location.coordinates());
        coordinates.setOnClickListener(frame.location == null ? null : v -> FilmNoteMap.openGoogleMaps(getContext(), this.frame.location));
        accuracy.setVisibility(frame.location != null && frame.location.accuracyMeters >= 0 ? VISIBLE : GONE);
        if (accuracy.getVisibility() == VISIBLE) accuracy.setText(getContext().getString(R.string.note_location_accuracy, Math.round(frame.location.accuracyMeters)));
        edit.setText(frame.location == null ? R.string.note_location_add : R.string.note_location_edit);
        edit.setOnClickListener(v -> FilmLocationEditorDialog.show(activity.getSupportFragmentManager(), rollId, this.frame));
        preview.setVisibility(frame.location == null ? GONE : VISIBLE);
        updateMap();
    }
    void setActive(boolean active) { this.active = active; updateMap(); }
    private void updateMap() {
        if (frame == null || frame.location == null || !active) { clearMap(); return; }
        if (map != null && (!shownCapture.equals(frame.captureId) || shownPosition.latitude != frame.location.latitude || shownPosition.longitude != frame.location.longitude)) clearMap();
        if (map == null) {
            map = new FilmNoteMap(getContext(), activity, false, frame.location, ignored -> { });
            preview.addView(map, new FrameLayout.LayoutParams(-1, -1));
            shownCapture = frame.captureId; shownPosition = frame.location;
        }
    }
    private TextView text(int size, boolean muted) {
        TextView view = new TextView(getContext()); view.setTextSize(size);
        view.setTextColor(ContextCompat.getColor(getContext(), muted ? R.color.note_gallery_muted : R.color.note_gallery_text));
        return view;
    }
    void clearMap() { if (map != null) map.close(); map = null; preview.removeAllViews(); }
    void lowMemory() { if (map != null) map.lowMemory(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
