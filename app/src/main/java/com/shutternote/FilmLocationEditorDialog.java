package com.shutternote;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.os.CancellationSignal;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

/** Select a point on the map, enter coordinates, or explicitly obtain the device's current position. */
public class FilmLocationEditorDialog extends DialogFragment {
    static final String TAG = "note-location-editor", RESULT = "note-location-changed";
    private FilmNoteMap map;
    private FilmFrameLocation point;
    private EditText latitude, longitude;
    private TextView status, current;
    private LinearLayout content;
    private FilmLocationViewModel model;
    private boolean updating, viewAlive;
    private CancellationSignal cancellation;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeout;
    private final ActivityResultLauncher<String[]> permission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (!viewAlive) return;
                if (hasPermission()) locate(); else status.setText(R.string.note_location_permission_denied);
            });

    static void show(FragmentManager manager, String roll, FilmFrameRecord frame) {
        if (manager.isStateSaved() || manager.findFragmentByTag(TAG) != null) return;
        FilmLocationEditorDialog dialog = new FilmLocationEditorDialog();
        Bundle args = new Bundle(); args.putString("roll", roll); args.putInt("cut", frame.number);
        args.putString("capture", frame.captureId);
        if (frame.location != null) {
            args.putDouble("lat", frame.location.latitude); args.putDouble("lon", frame.location.longitude);
            args.putDouble("accuracy", frame.location.accuracyMeters);
        }
        dialog.setArguments(args); dialog.show(manager, TAG);
    }

    @NonNull @Override public Dialog onCreateDialog(Bundle saved) {
        Bundle source = saved != null ? saved : requireArguments();
        if (source.containsKey("lat")) point = new FilmFrameLocation(source.getDouble("lat"), source.getDouble("lon"), source.getDouble("accuracy", -1));
        model = new ViewModelProvider(this).get(FilmLocationViewModel.class);
        content = new LinearLayout(requireContext()); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(8));
        TextView hint = new TextView(requireContext()); hint.setText(R.string.note_location_select_hint);
        content.addView(hint);
        map = new FilmNoteMap(requireContext(), this, true, point, next -> {
            if (model.state.getValue() == FilmLocationViewModel.State.SAVING) return;
            cancelLocation(); setPoint(next); status.setText("");
        });
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(-1, dp(240)); mapParams.topMargin = dp(12);
        content.addView(map, mapParams);
        current = new TextView(requireContext()); current.setText(R.string.note_location_current);
        current.setMinHeight(dp(48)); current.setGravity(android.view.Gravity.CENTER_VERTICAL);
        current.setTextColor(ContextCompat.getColor(requireContext(), R.color.note_ink));
        current.setOnClickListener(v -> {
            if (hasPermission()) locate();
            else permission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        });
        content.addView(current);
        latitude = coordinateInput(R.string.note_location_latitude, View.generateViewId());
        longitude = coordinateInput(R.string.note_location_longitude, View.generateViewId());
        status = new TextView(requireContext()); status.setTextSize(12); status.setPadding(0, dp(8), 0, dp(8));
        content.addView(status);
        if (point != null) setPoint(point);
        if (saved != null) {
            updating = true;
            latitude.setText(saved.getString("latText", latitude.getText().toString()));
            longitude.setText(saved.getString("lonText", longitude.getText().toString())); updating = false;
        }
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (updating) return;
                cancelLocation();
                point = FilmFrameLocation.parse(latitude.getText().toString(), longitude.getText().toString());
                if (point != null) { map.showPoint(point, true); status.setText(""); }
                else status.setText(R.string.note_location_invalid);
                updateButtons();
            }
            @Override public void afterTextChanged(Editable s) { }
        };
        latitude.addTextChangedListener(watcher); longitude.addTextChangedListener(watcher);
        viewAlive = true;
        ScrollView scroll = new ScrollView(requireContext()); scroll.addView(content);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.note_location).setView(scroll)
                .setPositiveButton(R.string.save, null).setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.note_location_remove, null).create();
        model.state.observe(this, state -> {
            if (state == FilmLocationViewModel.State.SAVED) {
                getParentFragmentManager().setFragmentResult(RESULT, new Bundle()); dismiss();
            } else {
                boolean saving = state == FilmLocationViewModel.State.SAVING;
                setCancelable(!saving); enable(content, !saving);
                if (state == FilmLocationViewModel.State.FAILED) status.setText(R.string.note_location_save_error);
                updateButtons();
            }
        });
        return dialog;
    }
    private EditText coordinateInput(int label, int id) {
        TextInputLayout layout = new TextInputLayout(requireContext()); layout.setHint(getString(label));
        EditText input = new com.google.android.material.textfield.TextInputEditText(layout.getContext()); input.setId(id);
        input.setSingleLine(true); input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789-+.,"));
        input.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setSaveEnabled(false); input.setMinHeight(dp(48)); layout.addView(input);
        content.addView(layout, new LinearLayout.LayoutParams(-1, -2)); return input;
    }
    @Override public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) requireDialog();
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> save(point));
        if (requireArguments().containsKey("lat")) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> save(null));
        else dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE);
        updateButtons();
    }
    private void save(FilmFrameLocation position) {
        cancelLocation();
        Bundle args = requireArguments(); model.save(args.getString("roll"), args.getInt("cut"), args.getString("capture"), position);
    }
    private void updateButtons() {
        if (!(getDialog() instanceof AlertDialog) || !((AlertDialog) getDialog()).isShowing()) return;
        AlertDialog dialog = (AlertDialog) getDialog();
        boolean idle = model.state.getValue() != FilmLocationViewModel.State.SAVING;
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(idle && point != null);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(idle);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(idle);
    }
    @android.annotation.SuppressLint("SetTextI18n") // Editable coordinates retain full precision; parser also accepts decimal commas.
    private void setPoint(FilmFrameLocation next) {
        point = next; updating = true;
        latitude.setText(Double.toString(next.latitude)); longitude.setText(Double.toString(next.longitude)); updating = false;
        map.showPoint(next, true); updateButtons();
    }
    private boolean hasPermission() {
        Context context = getContext();
        return context != null && (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
    }
    @android.annotation.SuppressLint("MissingPermission")
    private void locate() {
        if (!viewAlive || !hasPermission() || model.state.getValue() == FilmLocationViewModel.State.SAVING) return;
        cancelLocation();
        LocationManager manager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        boolean precise = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        String provider;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 31 && manager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) provider = LocationManager.FUSED_PROVIDER;
            else if (precise && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) provider = LocationManager.GPS_PROVIDER;
            else if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) provider = LocationManager.NETWORK_PROVIDER;
            else { status.setText(R.string.note_location_unavailable); return; }
            CancellationSignal request = new CancellationSignal(); cancellation = request;
            current.setEnabled(false); status.setText(R.string.note_location_locating);
            timeout = () -> { if (cancellation == request) { cancelLocation(); status.setText(R.string.note_location_unavailable); } };
            handler.postDelayed(timeout, 20000);
            LocationManagerCompat.getCurrentLocation(manager, provider, request, ContextCompat.getMainExecutor(requireContext()), location -> {
                if (!viewAlive || cancellation != request) return;
                cancelLocation();
                long age = location == null ? Long.MAX_VALUE : SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos();
                if (location == null || age < 0 || age > 120_000_000_000L || !FilmFrameLocation.valid(location.getLatitude(), location.getLongitude())) {
                    status.setText(R.string.note_location_unavailable); return;
                }
                setPoint(new FilmFrameLocation(location.getLatitude(), location.getLongitude(), location.hasAccuracy() ? location.getAccuracy() : -1));
                status.setText(location.hasAccuracy() ? getString(R.string.note_location_accuracy, Math.round(location.getAccuracy())) : "");
            });
        } catch (SecurityException | IllegalArgumentException error) { cancelLocation(); status.setText(R.string.note_location_unavailable); }
    }
    private void cancelLocation() {
        CancellationSignal old = cancellation; cancellation = null;
        if (old != null) old.cancel();
        if (timeout != null) handler.removeCallbacks(timeout); timeout = null;
        if (current != null) current.setEnabled(model == null || model.state.getValue() != FilmLocationViewModel.State.SAVING);
    }
    @Override public void onStop() { cancelLocation(); super.onStop(); }
    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        if (point != null) { state.putDouble("lat", point.latitude); state.putDouble("lon", point.longitude); state.putDouble("accuracy", point.accuracyMeters); }
        if (latitude != null) { state.putString("latText", latitude.getText().toString()); state.putString("lonText", longitude.getText().toString()); }
    }
    @Override public void onDestroyView() { viewAlive = false; cancelLocation(); if (map != null) map.close(); map = null; super.onDestroyView(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (map != null) map.lowMemory(); }
    private void enable(View v, boolean enabled) {
        v.setEnabled(enabled);
        if (v instanceof android.view.ViewGroup) for (int i = 0; i < ((android.view.ViewGroup) v).getChildCount(); i++) enable(((android.view.ViewGroup) v).getChildAt(i), enabled);
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
