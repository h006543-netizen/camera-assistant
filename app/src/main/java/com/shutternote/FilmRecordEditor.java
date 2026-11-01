package com.shutternote;

import android.os.Bundle;
import android.graphics.Rect;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Pair;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.datepicker.MaterialDatePicker;
import java.text.DateFormat;
import java.util.*;
import com.shutternote.FilmRecordEdits.Field;

/** One compact, inline category editor. The photo stays in the same feed. */
final class FilmRecordEditor {
    private static final String PERIOD_PICKER = "note_record_period_picker";
    private final AppCompatActivity activity;
    private final FilmRecordEditViewModel model;
    private final FilmRollCatalog catalog;
    private final Runnable refresh;
    private final OnBackPressedCallback back;
    private LinearLayout activePanel;
    private TextView errorText;
    private final String rollId;
    private boolean restoreTextFocus;

    FilmRecordEditor(AppCompatActivity activity, String rollId, Bundle saved, Runnable refresh) {
        this.activity = activity; this.rollId = rollId; this.refresh = refresh;
        catalog = new FilmRollCatalog(activity);
        model = new ViewModelProvider(activity).get(FilmRecordEditViewModel.class);
        if (model.draft == null && saved != null) model.draft = saved.getBundle("recordDraft");
        if (model.draft != null && Arrays.asList("STATUS", "LAB", "RECEIVED", "COMPLETED", "LAB_MEMO").contains(model.draft.getString("field"))) model.draft = null;
        restoreTextFocus = saved != null && model.draft != null && model.draft.getBoolean("textFocused");
        back = new OnBackPressedCallback(model.draft != null) {
            @Override public void handleOnBackPressed() { cancel(); }
        };
        activity.getOnBackPressedDispatcher().addCallback(activity, back);
        model.busy.observe(activity, busy -> { if (activePanel != null) enabled(activePanel, !Boolean.TRUE.equals(busy)); });
        model.result.observe(activity, error -> {
            if (error == null) return;
            if (error == 0) { close(); model.acknowledge(); refresh.run(); }
            else {
                model.acknowledge();
                if (model.draft != null) model.draft.putInt("error", error);
                if (errorText != null) { errorText.setText(error); errorText.setVisibility(View.VISIBLE); }
                else Toast.makeText(activity, error, Toast.LENGTH_LONG).show();
            }
        });
        androidx.fragment.app.Fragment picker = activity.getSupportFragmentManager().findFragmentByTag(PERIOD_PICKER);
        if (picker instanceof MaterialDatePicker) attachPeriodListener((MaterialDatePicker<?>) picker);
    }
    void saveState(Bundle state) {
        if (model.draft == null) return;
        EditText input = focusedInput();
        model.draft.putBoolean("textFocused", input != null);
        if (input != null) {
            model.draft.putInt("textStart", input.getSelectionStart());
            model.draft.putInt("textEnd", input.getSelectionEnd());
        }
        state.putBundle("recordDraft", new Bundle(model.draft));
    }
    void detach(LinearLayout records) {
        if (activePanel != null && activePanel.getParent() != null && ((View)activePanel.getParent()).getParent() == records) {
            activePanel = null; errorText = null;
        }
    }
    boolean cancel() {
        if (model.draft == null) return false;
        if (!Boolean.TRUE.equals(model.busy.getValue())) close();
        return true;
    }
    private void close() {
        restoreTextFocus = false;
        hideKeyboard(); model.draft = null; back.setEnabled(false);
        if (activePanel != null) { activePanel.removeAllViews(); activePanel.setVisibility(View.GONE); }
        activePanel = null; errorText = null;
    }
    private void hideKeyboard() {
        View focus = activity.getCurrentFocus();
        if (focus != null) ((InputMethodManager)activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }

    void bind(LinearLayout records, FilmRollRecord roll, FilmFrameRecord frame) {
        EditText focused = focusedInput();
        boolean keepFocus = focused != null && descendantOf(focused, records);
        int start = keepFocus ? focused.getSelectionStart() : -1, end = keepFocus ? focused.getSelectionEnd() : -1;
        detach(records);
        records.removeAllViews();
        row(records, roll, frame, Field.CUT_MEMO, R.string.note_frame_memo, frame.memo);
        String fov = ExposureSettingsStore.FIELD_OF_VIEW_DEFAULT.equals(frame.fieldOfView) ? activity.getString(R.string.fov_default) : frame.fieldOfView;
        row(records, roll, frame, Field.EXPOSURE, R.string.note_exposure, activity.getString(R.string.note_exposure_values, frame.iso, frame.aperture, frame.shutter, fov));
        row(records, roll, frame, Field.SHOT_FOCAL, R.string.roll_focal, fov);
        heading(records, R.string.note_roll_info);
        row(records, roll, frame, Field.NAME, R.string.roll_name_hint, roll.name);
        row(records, roll, frame, Field.FILM, R.string.roll_film_name, roll.filmDisplayName());
        row(records, roll, frame, Field.CAMERA, R.string.roll_camera, roll.cameraName);
        row(records, roll, frame, Field.COUNT, R.string.roll_count, String.valueOf(roll.plannedFrames));
        readOnlyRow(records, R.string.note_capture_time, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT,
                activity.getResources().getConfiguration().getLocales().get(0)).format(new Date(frame.capturedAt)));
        row(records, roll, frame, Field.PERIOD, R.string.note_period_title, FilmRollRules.period(roll.start, roll.end));
        row(records, roll, frame, Field.MEMO, R.string.note_roll_memo, roll.memo);
        if (activePanel != null && descendantOf(activePanel, records) && (keepFocus || restoreTextFocus)) {
            EditText input = firstInput(activePanel);
            if (input != null) {
                focusInput(input, false, keepFocus ? start : model.draft.getInt("textStart", -1),
                        keepFocus ? end : model.draft.getInt("textEnd", -1));
                restoreTextFocus = false;
            }
        }
    }
    private void heading(LinearLayout records, int title) {
        View line = new View(activity); line.setBackgroundColor(color(R.color.note_gallery_rule));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1)); lp.topMargin = dp(20); lp.bottomMargin = dp(12); records.addView(line, lp);
        records.addView(text(activity.getString(title), 12, true));
    }
    private void readOnlyRow(LinearLayout records, int title, String value) {
        View row = LayoutInflater.from(activity).inflate(R.layout.item_note_record, records, false);
        ((TextView)row.findViewById(R.id.recordTitle)).setText(title);
        ((TextView)row.findViewById(R.id.recordValue)).setText(value);
        View hit = row.findViewById(R.id.recordHit);
        hit.setClickable(false); hit.setFocusable(false); hit.setBackground(null);
        row.findViewById(R.id.recordArrow).setVisibility(View.GONE);
        records.addView(row);
    }
    private void row(LinearLayout records, FilmRollRecord roll, FilmFrameRecord frame, Field field, int title, String value) {
        View row = LayoutInflater.from(activity).inflate(R.layout.item_note_record, records, false);
        ((TextView)row.findViewById(R.id.recordTitle)).setText(title);
        TextView body = row.findViewById(R.id.recordValue);
        body.setText(value.isEmpty() ? activity.getString(R.string.note_empty_add) : value);
        if (value.isEmpty()) body.setTextColor(color(R.color.note_gallery_muted));
        LinearLayout panel = row.findViewById(R.id.recordEditor);
        row.findViewById(R.id.recordHit).setOnClickListener(v -> {
            if (Boolean.TRUE.equals(model.busy.getValue())) return;
            if (matches(frame, field)) {
                if (field == Field.PERIOD) openPeriodPicker(model.draft);
                else if (!beginTextEditing(field)) close();
                return;
            }
            close();
            Bundle draft = new Bundle(); draft.putInt("cut", frame.number); draft.putString("capture", frame.captureId);
            draft.putString("field", field.name()); draft.putStringArray("values", FilmRecordEdits.values(roll, frame, field));
            model.draft = draft; back.setEnabled(true); show(panel, field);
            if (field == Field.PERIOD) openPeriodPicker(draft);
            else beginTextEditing(field);
        });
        records.addView(row);
        if (matches(frame, field)) show(panel, field);
    }
    private boolean matches(FilmFrameRecord frame, Field field) {
        return model.draft != null && model.draft.getInt("cut") == frame.number && frame.captureId.equals(model.draft.getString("capture")) && field.name().equals(model.draft.getString("field"));
    }
    private void show(LinearLayout panel, Field field) {
        activePanel = panel; panel.removeAllViews(); panel.setVisibility(View.VISIBLE);
        switch (field) {
            case FILM: case CAMERA: catalogInput(panel, field); break;
            case COUNT: countInput(panel); break;
            case EXPOSURE:
                choice(panel, 0, "ISO", ExposureSettingsStore.isoValues(), ExposureSettingsStore.isoValues());
                choice(panel, 1, activity.getString(R.string.note_aperture_title), ExposureSettingsStore.apertureValues(), ExposureSettingsStore.apertureValues());
                choice(panel, 2, activity.getString(R.string.note_shutter_title), ExposureSettingsStore.shutterValues(), ExposureSettingsStore.shutterValues());
                String[] fovs = ExposureSettingsStore.fieldOfViewValues(); String[] labels = fovs.clone(); labels[0] = activity.getString(R.string.fov_default);
                choice(panel, 3, activity.getString(R.string.roll_focal), fovs, labels); break;
            case STATUS: choice(panel, 0, activity.getString(R.string.roll_status), new String[]{"0","1","2"}, statusLabels()); break;
            case FOCAL: choice(panel, 0, activity.getString(R.string.roll_focal), focalOptions(), focalOptions()); break;
            case SHOT_FOCAL:
                String[] shotFovs = ExposureSettingsStore.fieldOfViewValues(), shotLabels = shotFovs.clone();
                shotLabels[0] = activity.getString(R.string.fov_default);
                choice(panel, 0, activity.getString(R.string.roll_focal), shotFovs, shotLabels); break;
            case PERIOD: periodInput(panel); break;
            case RECEIVED: case COMPLETED: input(panel, 0, activity.getString(R.string.note_date_hint), false, true, false); break;
            default: input(panel, 0, activity.getString(field == Field.NAME ? R.string.roll_name_hint
                            : field == Field.CUT_MEMO ? R.string.note_memo_hint : R.string.roll_memo_hint),
                    field == Field.CUT_MEMO || field == Field.MEMO || field == Field.LAB_MEMO, false, field == Field.COUNT);
        }
        errorText = text("", 12, true); errorText.setVisibility(View.GONE); panel.addView(errorText);
        if (model.draft.getInt("error", 0) != 0) {
            errorText.setText(model.draft.getInt("error")); errorText.setVisibility(View.VISIBLE);
        }
        LinearLayout buttons = new LinearLayout(activity); buttons.setGravity(android.view.Gravity.END);
        TextView cancel = action(R.string.cancel), save = action(R.string.save);
        cancel.setOnClickListener(v -> cancel()); save.setOnClickListener(v -> { hideKeyboard(); model.save(rollId); });
        buttons.addView(cancel); buttons.addView(save); panel.addView(buttons);
        enabled(panel, !Boolean.TRUE.equals(model.busy.getValue()));
    }
    private String[] focalOptions() {
        String old = model.draft.getStringArray("values")[0];
        return old.isEmpty() || Arrays.asList("35", "40", "50").contains(old) ? new String[]{"", "35", "40", "50"} : new String[]{old, "35", "40", "50", ""};
    }
    private void periodInput(LinearLayout panel) {
        Bundle target = model.draft;
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        String period = periodLabel(target);
        TextView date = action(R.string.roll_select_period);
        date.setText(period.isEmpty() ? activity.getString(R.string.roll_select_period) : period);
        date.setGravity(android.view.Gravity.CENTER_VERTICAL);
        date.setPadding(dp(8), dp(4), dp(8), dp(4));
        date.setOnClickListener(v -> openPeriodPicker(target));
        row.addView(date, new LinearLayout.LayoutParams(0, -2, 1));
        TextView clear = action(R.string.roll_clear_date);
        clear.setVisibility(period.isEmpty() ? View.GONE : View.VISIBLE);
        clear.setOnClickListener(v -> {
            if (model.draft != target || Boolean.TRUE.equals(model.busy.getValue())) return;
            target.putStringArray("values", new String[]{"", ""});
            show(panel, Field.PERIOD);
        });
        row.addView(clear); panel.addView(row);
    }
    private String periodLabel(Bundle target) {
        String[] values = target.getStringArray("values");
        try { return FilmRollRules.period(FilmRecordEdits.parseDate(values[0]), FilmRecordEdits.parseDate(values[1])); }
        catch (IllegalArgumentException error) { return ""; }
    }
    private void openPeriodPicker(Bundle target) {
        androidx.fragment.app.FragmentManager manager = activity.getSupportFragmentManager();
        if (model.draft != target || Boolean.TRUE.equals(model.busy.getValue())
                || manager.isStateSaved() || manager.findFragmentByTag(PERIOD_PICKER) != null) return;
        hideKeyboard();
        MaterialDatePicker.Builder<Pair<Long, Long>> builder = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(R.string.roll_period).setInputMode(MaterialDatePicker.INPUT_MODE_CALENDAR);
        String[] values = target.getStringArray("values");
        try {
            long start = FilmRecordEdits.parseDate(values[0]), end = FilmRecordEdits.parseDate(values[1]);
            if (start != FilmRollRules.NO_DATE && end != FilmRollRules.NO_DATE && FilmRollRules.validPeriod(start, end))
                builder.setSelection(new Pair<>(start, end));
        } catch (IllegalArgumentException error) { /* An older text draft can be replaced with calendar dates. */ }
        MaterialDatePicker<Pair<Long, Long>> picker = builder.build();
        String token = UUID.randomUUID().toString();
        target.putString("periodPickerTarget", token);
        picker.requireArguments().putString("periodPickerTarget", token);
        attachPeriodListener(picker); picker.show(manager, PERIOD_PICKER);
    }
    @SuppressWarnings("unchecked")
    private void attachPeriodListener(MaterialDatePicker<?> restored) {
        MaterialDatePicker<Pair<Long, Long>> picker = (MaterialDatePicker<Pair<Long, Long>>) restored;
        // A restored picker must use the current editor and its original draft only.
        picker.clearOnPositiveButtonClickListeners();
        Bundle target = model.draft;
        String token = picker.requireArguments().getString("periodPickerTarget");
        if (target == null || !Field.PERIOD.name().equals(target.getString("field"))
                || token == null || !token.equals(target.getString("periodPickerTarget"))) {
            picker.dismiss(); return;
        }
        picker.addOnPositiveButtonClickListener(value -> {
            if (model.draft != target || Boolean.TRUE.equals(model.busy.getValue())
                    || !token.equals(target.getString("periodPickerTarget"))
                    || value == null || value.first == null || value.second == null) return;
            target.putStringArray("values", new String[]{FilmRollRules.date(value.first), FilmRollRules.date(value.second)});
            if (activePanel != null) show(activePanel, Field.PERIOD);
        });
    }
    private void catalogInput(LinearLayout panel, Field field) {
        Bundle target = model.draft;
        List<FilmRollCatalog.Item> items = field == Field.FILM ? catalog.films : catalog.cameras;
        LinkedHashMap<String, List<String[]>> groups = new LinkedHashMap<>();
        String frequent = activity.getString(R.string.roll_camera_frequent), custom = activity.getString(R.string.roll_custom);
        if (field == Field.CAMERA) {
            List<String[]> favorites = new ArrayList<>();
            for (CameraUsageStore.Entry e : new CameraUsageStore(activity).top()) favorites.add(new String[]{e.id, e.name, e.name});
            if (!favorites.isEmpty()) groups.put(frequent, favorites);
        }
        if (field == Field.FILM) {
            List<String[]> favorites = new ArrayList<>();
            for (FilmUsageStore.Entry e : new FilmUsageStore(activity).top()) favorites.add(new String[]{e.id,e.name,e.name});
            if (!favorites.isEmpty()) groups.put(frequent,favorites);
        }
        String initial = null;
        for (FilmRollCatalog.Item item : items) {
            String value = field == Field.FILM ? item.name : item.fullName();
            groups.computeIfAbsent(item.brand, key -> new ArrayList<>()).add(new String[]{item.id, value, item.name});
            if (item.id.equals(target.getStringArray("values")[0])) initial = item.brand;
        }
        groups.put(custom, new ArrayList<>());
        if (initial == null) initial = target.getStringArray("values")[1].isEmpty() ? groups.keySet().iterator().next() : custom;
        String currentCategory = target.getString("category", initial);
        if (!groups.containsKey(currentCategory)) currentCategory = initial;
        target.putString("category", currentCategory);

        LinearLayout columns = new LinearLayout(activity);
        ScrollView categoryScroll = new ScrollView(activity); categoryScroll.setFillViewport(false);
        LinearLayout categories = new LinearLayout(activity); categories.setOrientation(LinearLayout.VERTICAL); categoryScroll.addView(categories);
        columns.addView(categoryScroll, new LinearLayout.LayoutParams(dp(96), dp(240)));
        LinearLayout right = new LinearLayout(activity); right.setOrientation(LinearLayout.VERTICAL); right.setPadding(dp(8), 0, 0, 0);
        columns.addView(right, new LinearLayout.LayoutParams(0, -2, 1)); panel.addView(columns);
        List<TextView> categoryViews = new ArrayList<>();
        Runnable[] rerender = new Runnable[1];
        Runnable render = () -> {
            right.removeAllViews();
            String category = target.getString("category");
            for (TextView label : categoryViews) categoryStyle(label, category.equals(label.getTag()));
            if (custom.equals(category)) {
                input(right, 1, activity.getString(field == Field.FILM ? R.string.roll_film_name : R.string.roll_camera_name), false, false, false);
                return;
            }
            EditText search = new EditText(activity); search.setTextSize(14); search.setSingleLine(true); search.setMinHeight(dp(48));
            search.setTextColor(color(R.color.note_gallery_text)); search.setHintTextColor(color(R.color.note_gallery_muted));
            search.setHint(R.string.note_category_search); search.setText(target.getString("search", "")); right.addView(search);
            ScrollView optionsScroll = new ScrollView(activity); optionsScroll.setNestedScrollingEnabled(true);
            LinearLayout options = new LinearLayout(activity); options.setOrientation(LinearLayout.VERTICAL); optionsScroll.addView(options);
            right.addView(optionsScroll, new LinearLayout.LayoutParams(-1, dp(192)));
            Runnable filter = () -> {
                options.removeAllViews(); String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
                for (String[] item : groups.get(category)) {
                    if (!item[1].toLowerCase(Locale.ROOT).contains(query)) continue;
                    TextView option = text(item[2], 14, false); option.setMinHeight(dp(48)); option.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    option.setPadding(dp(8), dp(4), dp(8), dp(4)); option.setFocusable(true); ripple(option);
                    boolean selected = item[0].equals(target.getStringArray("values")[0]) && item[1].equals(target.getStringArray("values")[1]);
                    option.setSelected(selected); option.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                    option.setOnClickListener(v -> {
                        change(target, 0, item[0]); change(target, 1, item[1]);
                        for (int i=0; i<options.getChildCount(); i++) {
                            TextView child=(TextView)options.getChildAt(i); child.setSelected(child==option);
                            child.setTypeface(null, child==option ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                        }
                    });
                    if (frequent.equals(category)) {
                        FrequentItemRemoval.attach(option,
                                () -> field == Field.FILM
                                        ? new FilmUsageStore(activity).remove(item[0], item[1])
                                        : new CameraUsageStore(activity).remove(item[0], item[1]),
                                () -> {
                                    List<String[]> refreshed = new ArrayList<>();
                                    if (field == Field.FILM) {
                                        for (FilmUsageStore.Entry e : new FilmUsageStore(activity).top()) refreshed.add(new String[]{e.id,e.name,e.name});
                                    } else {
                                        for (CameraUsageStore.Entry e : new CameraUsageStore(activity).top()) refreshed.add(new String[]{e.id,e.name,e.name});
                                    }
                                    groups.put(frequent, refreshed);
                                    rerender[0].run();
                                });
                    }
                    options.addView(option);
                }
                if (options.getChildCount()==0) options.addView(text(activity.getString(R.string.roll_camera_no_results), 13, true));
                enabled(right, !Boolean.TRUE.equals(model.busy.getValue()));
            };
            watch(search, () -> { target.putString("search", search.getText().toString()); filter.run(); }); filter.run();
        };
        rerender[0] = render;
        for (String category : groups.keySet()) {
            TextView label=text(category, 12, true); label.setTag(category); label.setMinHeight(dp(48)); label.setGravity(android.view.Gravity.CENTER_VERTICAL);
            label.setPadding(dp(4), dp(4), dp(4), dp(4)); label.setFocusable(true); ripple(label);
            label.setOnClickListener(v -> {
                hideKeyboard(); target.putString("category", category); target.putString("search", ""); render.run();
                if (custom.equals(category)) focusInput(firstInput(right), true, -1, -1);
            });
            categories.addView(label); categoryViews.add(label);
        }
        render.run();
    }
    private void categoryStyle(TextView label, boolean selected) {
        label.setSelected(selected); label.setTextColor(color(selected ? R.color.note_gallery_text : R.color.note_gallery_muted));
        label.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }
    private void countInput(LinearLayout panel) {
        Bundle target=model.draft;
        RadioGroup choices=new RadioGroup(activity); choices.setOrientation(RadioGroup.HORIZONTAL);
        int[] ids={View.generateViewId(), View.generateViewId(), View.generateViewId()};
        int[] labels={R.string.roll_count_24, R.string.roll_count_36, R.string.roll_custom};
        for(int i=0;i<3;i++) {
            RadioButton button=new RadioButton(activity); button.setId(ids[i]); button.setText(labels[i]); button.setTextSize(13);
            button.setTextColor(color(R.color.note_gallery_text)); button.setMinHeight(dp(48));
            button.setButtonTintList(android.content.res.ColorStateList.valueOf(color(R.color.note_gallery_muted)));
            choices.addView(button, new RadioGroup.LayoutParams(0,-2,1));
        }
        panel.addView(choices);
        LinearLayout custom=new LinearLayout(activity); custom.setOrientation(LinearLayout.VERTICAL); panel.addView(custom);
        String old=target.getStringArray("values")[0]; String mode=target.getString("countCategory", "24".equals(old)||"36".equals(old)?old:"custom");
        choices.check(ids["24".equals(mode)?0:"36".equals(mode)?1:2]);
        if ("custom".equals(mode)) input(custom, 0, activity.getString(R.string.roll_count_hint), false, false, true);
        choices.setOnCheckedChangeListener((group,id) -> {
            hideKeyboard(); String value=id==ids[0]?"24":id==ids[1]?"36":"custom";
            if ("custom".equals(target.getString("countCategory",mode))) target.putString("customCount",target.getStringArray("values")[0]);
            target.putString("countCategory",value); custom.removeAllViews();
            if ("custom".equals(value)) {
                change(target,0,target.getString("customCount", "")); input(custom,0,activity.getString(R.string.roll_count_hint),false,false,true);
                focusInput(firstInput(custom), true, -1, -1);
            } else change(target,0,value);
        });
    }
    private void ripple(View view) {
        android.util.TypedValue value=new android.util.TypedValue(); activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,value,true);view.setBackgroundResource(value.resourceId);
    }
    private void input(LinearLayout panel, int index, String hint, boolean multiline, boolean date, boolean numeric) {
        EditText input = new EditText(activity); input.setTextColor(color(R.color.note_gallery_text));
        input.setHintTextColor(color(R.color.note_gallery_muted)); input.setTextSize(16); input.setMinHeight(dp(48));
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(numeric ? 3 : date ? 10 : 1000)});
        input.setInputType(numeric ? android.text.InputType.TYPE_CLASS_NUMBER : android.text.InputType.TYPE_CLASS_TEXT | (multiline ? android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        input.setSingleLine(!multiline); if (multiline) { input.setMinLines(2); input.setMaxLines(4); }
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setHint(date ? hint + " · " + activity.getString(R.string.note_date_hint) : hint);
        input.setText(model.draft.getStringArray("values")[index]); Bundle target = model.draft;
        watch(input, () -> {
            change(target, index, input.getText().toString());
            String field=target.getString("field");
            if (index==1 && (Field.FILM.name().equals(field) || Field.CAMERA.name().equals(field))) change(target,0,"");
        }); panel.addView(input);
    }
    private boolean beginTextEditing(Field field) {
        boolean text = field == Field.NAME || field == Field.CUT_MEMO || field == Field.MEMO
                || field == Field.LAB || field == Field.LAB_MEMO || field == Field.RECEIVED || field == Field.COMPLETED
                || field == Field.COUNT
                || (field == Field.FILM || field == Field.CAMERA)
                && activity.getString(R.string.roll_custom).equals(model.draft.getString("category"));
        EditText input = text ? firstInput(activePanel) : null;
        if (input == null) return false;
        focusInput(input, true, input.hasFocus() ? input.getSelectionStart() : -1,
                input.hasFocus() ? input.getSelectionEnd() : -1);
        return true;
    }

    private EditText firstInput(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = firstInput(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean descendantOf(View view, View parent) {
        for (View current = view; current != null;
                current = current.getParent() instanceof View ? (View) current.getParent() : null)
            if (current == parent) return true;
        return false;
    }

    private EditText focusedInput() {
        View focus = activity.getCurrentFocus();
        return focus instanceof EditText && activePanel != null && descendantOf(focus, activePanel) ? (EditText) focus : null;
    }

    private void focusInput(EditText input, boolean showKeyboard, int start, int end) {
        if (input == null || activePanel == null || !descendantOf(input, activePanel) || !input.isEnabled()) return;
        Bundle target = model.draft;
        // Keep focus immediately, including if a location refresh rebinds this field before layout.
        input.requestFocus();
        int length = input.length();
        input.setSelection(start < 0 ? length : Math.min(start, length), end < 0 ? length : Math.min(end, length));
        // A click can be followed by a different field, save or page recycle before the next layout.
        OneShotPreDrawListener.add(input, () -> {
            if (model.draft != target || activePanel == null || !descendantOf(input, activePanel)
                    || !ViewCompat.isAttachedToWindow(input) || !input.isEnabled() || !input.hasFocus()) return;
            if (showKeyboard)
                androidx.core.view.WindowCompat.getInsetsController(activity.getWindow(), input).show(WindowInsetsCompat.Type.ime());
            revealFocusedInput();
        });
    }

    /** Called again after IME insets resize the page; requesting focus alone uses its old height. */
    void revealFocusedInput() {
        EditText input = focusedInput();
        if (input == null || !input.isShown()) return;
        Rect visible = new Rect(input.getScrollX(), input.getScrollY() - dp(8),
                input.getScrollX() + input.getWidth(), input.getScrollY() + input.getHeight() + dp(56));
        View parent = input;
        while (parent.getParent() instanceof View) {
            parent = (View) parent.getParent();
            if (parent instanceof androidx.core.widget.NestedScrollView) {
                if (visible.height() > parent.getHeight() && input.getLayout() != null) {
                    int line = input.getLayout().getLineForOffset(Math.max(0, input.getSelectionEnd()));
                    visible.top = input.getTotalPaddingTop() + input.getLayout().getLineTop(line) - dp(8);
                    visible.bottom = input.getTotalPaddingTop() + input.getLayout().getLineBottom(line) + dp(8);
                }
                break;
            }
        }
        input.requestRectangleOnScreen(visible, false);
    }

    private void choice(LinearLayout panel, int index, String title, String[] values, String[] labels) {
        LinearLayout row = new LinearLayout(activity); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView label = text(title, 12, true); row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        Spinner spinner = new Spinner(activity, Spinner.MODE_DROPDOWN);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, labels) {
            @Override public View getView(int position, View convert, ViewGroup parent) {
                TextView view = (TextView)super.getView(position, convert, parent); view.setTextColor(color(R.color.note_gallery_text)); view.setTextSize(15); return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter);
        String current = model.draft.getStringArray("values")[index]; int selected = Arrays.asList(values).indexOf(current);
        spinner.setSelection(Math.max(0, selected)); Bundle target = model.draft;
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { change(target, index, values[position]); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        row.addView(spinner, new LinearLayout.LayoutParams(0, dp(48), 1)); panel.addView(row);
    }
    private void change(Bundle target, int index, String value) {
        if (model.draft != target) return;
        String[] values = target.getStringArray("values").clone(); values[index] = value; target.putStringArray("values", values);
    }
    private void watch(EditText input, Runnable changed) {
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) { changed.run(); }
            @Override public void afterTextChanged(Editable s) { }
        });
    }
    private String[] statusLabels() { return new String[]{activity.getString(R.string.roll_status_new), activity.getString(R.string.roll_status_processing), activity.getString(R.string.roll_status_done)}; }
    private TextView action(int resource) {
        TextView view = text(activity.getString(resource), 14, false); view.setMinHeight(dp(48)); view.setMinWidth(dp(64)); view.setGravity(android.view.Gravity.CENTER); view.setFocusable(true);
        android.util.TypedValue ripple = new android.util.TypedValue(); activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true); view.setBackgroundResource(ripple.resourceId); return view;
    }
    private TextView text(String value, int size, boolean muted) {
        TextView view = new TextView(activity); view.setText(value); view.setTextSize(size); view.setTextColor(color(muted ? R.color.note_gallery_muted : R.color.note_gallery_text)); return view;
    }
    private void enabled(View view, boolean enabled) { view.setEnabled(enabled); if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) enabled(((ViewGroup)view).getChildAt(i), enabled); }
    private int color(int res) { return androidx.core.content.ContextCompat.getColor(activity, res); }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
