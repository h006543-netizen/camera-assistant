package com.shutternote;

import android.view.View;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.lifecycle.ViewModelProvider;
import java.util.function.IntConsumer;

/** Reveals an inline delete control; only tapping that control deletes a cut. */
final class FilmFrameDeletion {
    private final AppCompatActivity activity;
    private final FilmFrameDeleteViewModel model;
    private final OnBackPressedCallback back;
    private View selected;

    FilmFrameDeletion(AppCompatActivity activity, IntConsumer onDeleted) {
        this.activity = activity;
        model = new ViewModelProvider(activity).get(FilmFrameDeleteViewModel.class);
        back = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { dismiss(); }
        };
        activity.getOnBackPressedDispatcher().addCallback(activity, back);
        model.state.observe(activity, state -> {
            if (state == FilmFrameDeleteViewModel.State.DELETED) {
                dismiss(); onDeleted.accept(model.number); model.acknowledge();
            } else if (state == FilmFrameDeleteViewModel.State.FAILED) {
                if (selected != null) selected.setEnabled(true);
                Toast.makeText(activity, R.string.note_delete_error, Toast.LENGTH_LONG).show();
                model.acknowledge();
            }
        });
    }

    void clear(View button) {
        if (selected == button) dismiss();
        button.setVisibility(View.GONE);
        button.setOnClickListener(null);
    }

    void attach(View photo, View button, String roll, FilmFrameRecord frame) {
        clear(button);
        button.setEnabled(true);
        button.setOnClickListener(v -> {
            if (selected != button || model.state.getValue() != FilmFrameDeleteViewModel.State.IDLE) return;
            button.setEnabled(false);
            model.delete(roll, frame.number, frame.captureId);
        });
        photo.setOnLongClickListener(v -> { toggle(button); return true; });
        ViewCompat.replaceAccessibilityAction(photo,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
                activity.getString(R.string.note_delete_photo),
                (v, args) -> { toggle(button); return true; });
    }

    private void toggle(View button) {
        if (model.state.getValue() != FilmFrameDeleteViewModel.State.IDLE) return;
        boolean closing = selected == button;
        dismiss();
        if (closing) return;
        selected = button;
        button.setVisibility(View.VISIBLE);
        back.setEnabled(true);
    }

    void dismiss() {
        if (selected != null) selected.setVisibility(View.GONE);
        selected = null;
        back.setEnabled(false);
    }
}
