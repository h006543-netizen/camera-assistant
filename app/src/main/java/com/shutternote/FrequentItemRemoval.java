package com.shutternote;

import android.view.View;
import android.widget.Toast;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

/** Removes personal picker history without selecting or changing a roll. */
final class FrequentItemRemoval {
    interface Removal { boolean remove(); }

    static void attach(View row, Removal removal, Runnable refresh) {
        row.setOnLongClickListener(v -> {
            boolean removed = removal.remove();
            if (removed) refresh.run();
            Toast.makeText(row.getContext(), removed ? R.string.roll_frequent_removed
                    : R.string.roll_frequent_remove_error, Toast.LENGTH_SHORT).show();
            return true;
        });
        ViewCompat.replaceAccessibilityAction(row,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
                row.getContext().getString(R.string.roll_frequent_remove),
                (view, args) -> view.performLongClick());
    }
}
