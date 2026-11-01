package com.shutternote;

import android.view.Menu;
import android.widget.TextView;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.ViewCompat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Shared compact chooser. Add future supported films to the choices, using stable keys. */
final class FilmCorrectionCategory {
    static final String ORIGINAL = "original", E100D = "e100d";
    private static final class Choice {
        final String key; final int label, menuId;
        Choice(String key, int label, int menuId) { this.key=key; this.label=label; this.menuId=menuId; }
    }
    private static final List<Choice> CHOICES = Collections.unmodifiableList(Arrays.asList(
            new Choice(ORIGINAL, R.string.correction_original, 1),
            new Choice("vision3-50d", R.string.correction_50d, 2),
            new Choice(E100D, R.string.correction_e100d, 3),
            new Choice("vision3-250d", R.string.correction_250d, 4),
            new Choice("cine800t", R.string.correction_800t, 5),
            new Choice("vision3-500t", R.string.correction_500t, 6),
            new Choice("portra400", R.string.correction_portra400, 7)));
    private final TextView anchor;
    private PopupMenu popup;
    private String selected = ORIGINAL;
    private Predicate<String> available = key -> false;
    private Consumer<String> select = key -> { };

    FilmCorrectionCategory(TextView anchor) {
        this.anchor = anchor;
        anchor.setOnClickListener(view -> show());
    }
    void bind(String key, boolean enabled, boolean busy, Predicate<String> available, Consumer<String> select) {
        if (!selected.equals(key) || !enabled) dismiss();
        selected=key; this.available=available; this.select=select;
        Choice current=CHOICES.get(0);
        for (Choice choice:CHOICES) if (choice.key.equals(key)) current=choice;
        anchor.setText(current.label);
        anchor.setEnabled(enabled); anchor.setAlpha(enabled ? 1f : .45f);
        anchor.setContentDescription(anchor.getContext().getString(R.string.correction_category, anchor.getText()));
        ViewCompat.setStateDescription(anchor, busy ? anchor.getContext().getString(R.string.correction_processing) : null);
    }
    private void show() {
        if (!anchor.isEnabled()) return;
        dismiss();
        PopupMenu menu = new PopupMenu(new ContextThemeWrapper(anchor.getContext(), R.style.FilmCorrectionPopupTheme), anchor);
        for (Choice choice:CHOICES) if(available.test(choice.key)) menu.getMenu().add(Menu.NONE,choice.menuId,Menu.NONE,choice.label)
                .setCheckable(true).setChecked(choice.key.equals(selected));
        menu.getMenu().setGroupCheckable(Menu.NONE,true,true);
        menu.setOnMenuItemClickListener(item -> {
            for (Choice choice:CHOICES) if (choice.menuId==item.getItemId() && anchor.isEnabled() && available.test(choice.key)) {
                select.accept(choice.key); return true;
            }
            return false;
        });
        menu.setOnDismissListener(ignored -> { if (popup==menu) popup=null; });
        popup=menu; menu.show();
    }
    void dismiss() { if (popup!=null) { popup.dismiss(); popup=null; } }
}
