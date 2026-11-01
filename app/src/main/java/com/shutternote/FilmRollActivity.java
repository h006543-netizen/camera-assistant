package com.shutternote;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.core.util.Pair;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.datepicker.MaterialDatePicker;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** XML category screen. Film and camera data are roll metadata only. */
public class FilmRollActivity extends AppCompatActivity {
    static final String EXTRA_ROLL_ID = "roll_id";
    static final String EXTRA_FOLDER_ID = "note_folder_id";
    private static final String PERIOD_PICKER = "roll_period_picker";
    private static final String RECEIVED_PICKER = "roll_received_picker";
    private static final String COMPLETED_PICKER = "roll_completed_picker";
    private final int[] categories = {R.id.rollCategoryFilm, R.id.rollCategoryCamera, R.id.rollCategoryInfo, R.id.rollCategoryLab};
    private final int[] panes = {R.id.rollFilmPane, R.id.rollCameraPane, R.id.rollInfoPane, R.id.rollLabPane};
    private final Map<String, View> filmRows = new LinkedHashMap<>();
    private FilmRollRecord record;
    private FilmRollCatalog catalog;
    private FilmRollSaveViewModel saving;
    private int category;
    private boolean customFilm, customCamera, editing, filmFavorites;
    private boolean locationPermissionHandled, locationPermissionPending, rollCreationSaved;

    private final ActivityResultLauncher<String[]> rollLocationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                locationPermissionPending = false;
                locationPermissionHandled = true;
                // Creation succeeds regardless of the choice. A denied location never blocks photos.
                finishSavedRoll();
            });

    private final ActivityResultLauncher<Intent> cameraPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                FilmRollCatalog.Item item = catalog.findCamera(result.getData().getStringExtra(CameraSelectionActivity.EXTRA_CAMERA_ID));
                if (item == null) {
                    String name = result.getData().getStringExtra(CameraSelectionActivity.EXTRA_CAMERA_NAME);
                    if (name == null || name.isEmpty()) return;
                    record.cameraId = ""; record.cameraName = name; customCamera = true;
                    setText(R.id.rollCustomCamera, name); updateCamera(); return;
                }
                record.cameraId = item.id; record.cameraName = item.fullName(); customCamera = false;
                updateCamera();
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_add_film_roll);
        SystemBarInsets.applyMainScreen(this, findViewById(R.id.rollStatusSpace), findViewById(R.id.rollNavigationSpace));
        applyKeyboardInsets(findViewById(R.id.rollRoot));
        markHeadings(findViewById(R.id.rollRoot));
        catalog = new FilmRollCatalog(this);
        editing = getIntent().hasExtra(EXTRA_ROLL_ID);
        if (state != null) {
            locationPermissionHandled = state.getBoolean("locationPermissionHandled");
            locationPermissionPending = state.getBoolean("locationPermissionPending");
            rollCreationSaved = state.getBoolean("rollCreationSaved");
        }
        record = new FilmRollRecord(); record.id = UUID.randomUUID().toString();
        record.focalLength = "35";
        try {
            java.util.List<FilmRollRecord> existingRolls = new FilmRollStore(this).load();
            FilmUsageStore films = new FilmUsageStore(this); films.seed(existingRolls);
            FilmRollDefaultsStore defaults = new FilmRollDefaultsStore(this); defaults.seed(existingRolls);
            filmFavorites = state == null ? !films.top().isEmpty() : state.getBoolean("filmFavorites",false);
            if (state != null) {
                record = FilmRollRecord.fromJson(new JSONObject(state.getString("draft")));
                category = state.getInt("category", 0);
                customFilm = state.getBoolean("customFilm"); customCamera = state.getBoolean("customCamera");
            } else if (editing) {
                FilmRollRecord saved = new FilmRollStore(this).find(getIntent().getStringExtra(EXTRA_ROLL_ID));
                if (saved == null) throw new JSONException("Missing roll");
                record = saved; customFilm = record.filmId.isEmpty();
                customCamera = record.cameraId.isEmpty() && !record.cameraName.isEmpty();
            } else {
                CameraUsageStore history = new CameraUsageStore(this);
                history.seed(existingRolls);
                defaults.restore(record);
                CameraUsageStore.Entry last = history.last();
                if (last.id.isEmpty() || catalog.findCamera(last.id) != null) {
                    record.cameraId = last.id; record.cameraName = last.name;
                    customCamera = last.id.isEmpty() && !last.name.isEmpty();
                }
            }
        } catch (JSONException exception) {
            Toast.makeText(this, R.string.roll_error_load, Toast.LENGTH_LONG).show();
            finish(); return;
        }
        ((TextView) findViewById(R.id.rollTitle)).setText(editing ? R.string.roll_edit : R.string.roll_new);
        ((TextView) findViewById(R.id.rollSave)).setText(editing ? R.string.roll_save_changes : R.string.roll_create);
        findViewById(R.id.rollBack).setOnClickListener(view -> getOnBackPressedDispatcher().onBackPressed());
        findViewById(R.id.rollLanguage).setOnClickListener(view -> showLanguageDialog());
        for (int i = 0; i < categories.length; i++) {
            final int selected = i;
            findViewById(categories[i]).setOnClickListener(view -> selectCategory(selected));
        }
        bindInputs(state);
        findViewById(R.id.rollFocal).setOnClickListener(view -> showFocalLengthPicker());
        buildFilmList();
        findViewById(R.id.rollFilmCustomButton).setOnClickListener(view -> {
            customFilm = true; record.filmId = ""; updateFilm();
            findViewById(R.id.rollCustomFilm).requestFocus();
        });
        findViewById(R.id.rollChooseCamera).setOnClickListener(view -> cameraPicker.launch(
                new Intent(this, CameraSelectionActivity.class).putExtra(CameraSelectionActivity.EXTRA_CAMERA_ID, record.cameraId)));
        findViewById(R.id.rollCameraCustomButton).setOnClickListener(view -> {
            customCamera = true; record.cameraId = ""; updateCamera();
            findViewById(R.id.rollCustomCamera).requestFocus();
        });
        findViewById(R.id.rollPeriod).setOnClickListener(view -> openPeriodPicker());
        findViewById(R.id.rollClearPeriod).setOnClickListener(view -> {
            record.start = record.end = FilmRollRules.NO_DATE; updateDates();
        });
        findViewById(R.id.rollReceived).setOnClickListener(view -> openDayPicker(RECEIVED_PICKER));
        findViewById(R.id.rollCompleted).setOnClickListener(view -> openDayPicker(COMPLETED_PICKER));
        findViewById(R.id.rollClearReceived).setOnClickListener(view -> { record.received = FilmRollRules.NO_DATE; updateDates(); });
        findViewById(R.id.rollClearCompleted).setOnClickListener(view -> { record.completed = FilmRollRules.NO_DATE; updateDates(); });
        for (String tag : new String[]{PERIOD_PICKER, RECEIVED_PICKER, COMPLETED_PICKER}) {
            Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
            if (fragment instanceof MaterialDatePicker) attachDateListener((MaterialDatePicker<?>) fragment, tag);
        }
        selectCategory(category); updateFilm(); updateCamera(); updateDates();
        saving = new ViewModelProvider(this).get(FilmRollSaveViewModel.class);
        saving.state.observe(this, value -> {
            findViewById(R.id.rollSave).setEnabled(!rollCreationSaved && !locationPermissionPending
                    && value != FilmRollSaveViewModel.State.SAVING && value != FilmRollSaveViewModel.State.SAVED);
            if (value == FilmRollSaveViewModel.State.SAVED) {
                rollCreationSaved = true;
                finishSavedRoll();
            } else if (value == FilmRollSaveViewModel.State.FAILED) showError(R.string.roll_error_save, category);
        });
        findViewById(R.id.rollSave).setOnClickListener(view -> saveRoll());
        if (rollCreationSaved) finishSavedRoll();
    }

    private void finishSavedRoll() {
        if (!rollCreationSaved || locationPermissionPending || isFinishing()) return;
        if (!editing && !locationPermissionHandled && !FilmCaptureLocation.hasPermission(this)) {
            locationPermissionHandled = true;
            locationPermissionPending = true;
            findViewById(R.id.rollSave).setEnabled(false);
            rollLocationPermission.launch(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION});
            return;
        }
        Toast.makeText(this, R.string.roll_saved, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK); finish();
    }

    static void applyKeyboardInsets(View root) {
        // Keep the fixed save button above the keyboard while content remains scrollable.
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars() | WindowInsetsCompat.Type.systemGestures()).bottom;
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), Math.max(0, ime - nav));
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    static void markHeadings(View view) {
        if ("roll_heading".equals(view.getTag())) ViewCompat.setAccessibilityHeading(view, true);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) markHeadings(group.getChildAt(i));
        }
    }

    private void bindInputs(Bundle state) {
        setText(R.id.rollName, record.name); updateFocalLength();
        setText(R.id.rollMemo, record.memo); setText(R.id.rollLabName, record.labName); setText(R.id.rollLabMemo, record.labMemo);
        setText(R.id.rollCustomFilm, state == null ? (customFilm ? record.filmName : "") : state.getString("customFilmText", ""));
        setText(R.id.rollCustomCamera, state == null ? (customCamera ? record.cameraName : "") : state.getString("customCameraText", ""));
        setText(R.id.rollCustomCount, state == null ? (record.countMode == 0 ? String.valueOf(record.plannedFrames) : "") : state.getString("customCountText", ""));
        RadioGroup counts = findViewById(R.id.rollCountGroup);
        counts.check(record.countMode == 24 ? R.id.rollCount24 : record.countMode == 36 ? R.id.rollCount36 : R.id.rollCountOther);
        findViewById(R.id.rollCustomCount).setVisibility(record.countMode == 0 ? View.VISIBLE : View.GONE);
        counts.setOnCheckedChangeListener((group, checked) -> {
            record.countMode = checked == R.id.rollCount24 ? 24 : checked == R.id.rollCount36 ? 36 : 0;
            findViewById(R.id.rollCustomCount).setVisibility(record.countMode == 0 ? View.VISIBLE : View.GONE);
        });
        ((RadioGroup) findViewById(R.id.rollStatusGroup)).check(record.labStatus == 1 ? R.id.rollStatusProcessing : record.labStatus == 2 ? R.id.rollStatusDone : R.id.rollStatusNew);
        ((EditText) findViewById(R.id.rollMemo)).addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateMemoCount(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        updateMemoCount();
    }

    private void selectCategory(int index) {
        category = Math.max(0, Math.min(index, categories.length - 1));
        for (int i = 0; i < categories.length; i++) {
            findViewById(categories[i]).setSelected(i == category);
            findViewById(panes[i]).setVisibility(i == category ? View.VISIBLE : View.GONE);
        }
    }

    private void buildFilmList() {
        LinearLayout root = findViewById(R.id.rollFilmList);
        LinearLayout tabs = new LinearLayout(this);
        TextView frequent = new TextView(this); frequent.setText(R.string.roll_film_frequent);
        TextView all = new TextView(this); all.setText(R.string.roll_film_all);
        for(TextView tab : new TextView[]{frequent,all}) {
            tab.setTextSize(13);tab.setTextColor(android.graphics.Color.rgb(21,21,21));tab.setMinHeight(Math.round(48*getResources().getDisplayMetrics().density));
            tab.setGravity(android.view.Gravity.CENTER);tab.setFocusable(true);
            android.util.TypedValue ripple=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackground,ripple,true);tab.setBackgroundResource(ripple.resourceId);
            tabs.addView(tab,new LinearLayout.LayoutParams(0,-2,1));
        }
        root.addView(tabs);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);
        TextView frequentTab=frequent;
        Runnable render=()->{
            list.removeAllViews();filmRows.clear();
            frequentTab.setSelected(filmFavorites);all.setSelected(!filmFavorites);
            frequentTab.setTypeface(null,filmFavorites?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
            all.setTypeface(null,!filmFavorites?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
            if(filmFavorites) {
                java.util.List<FilmUsageStore.Entry> favorites=new FilmUsageStore(this).top();
                for(FilmUsageStore.Entry entry:favorites) {
                    View row = addFilmRow(list,entry.id,entry.name);
                    FrequentItemRemoval.attach(row, () -> new FilmUsageStore(this).remove(entry.id,entry.name),
                            () -> frequentTab.performClick());
                }
                if(favorites.isEmpty()) {
                    TextView empty=(TextView)getLayoutInflater().inflate(R.layout.item_roll_speed,list,false);empty.setText(R.string.roll_film_no_history);list.addView(empty);
                }
            } else {
                String previousBrand="",previousSpeed="";
                for(FilmRollCatalog.Item item:catalog.films) {
                    if(!previousBrand.equals(item.brand)) {
                        TextView header=(TextView)getLayoutInflater().inflate(R.layout.item_roll_heading,list,false);header.setText(item.brand);ViewCompat.setAccessibilityHeading(header,true);list.addView(header);previousBrand=item.brand;previousSpeed="";
                    }
                    String speed=(item.ei?"EI ":"ISO ")+item.speed;
                    if(!speed.equals(previousSpeed)) {
                        TextView header=(TextView)getLayoutInflater().inflate(R.layout.item_roll_speed,list,false);header.setText(speed);ViewCompat.setAccessibilityHeading(header,true);list.addView(header);previousSpeed=speed;
                    }
                    addFilmRow(list,item.id,item.name);
                }
            }
            updateFilm();
        };
        frequentTab.setOnClickListener(v->{filmFavorites=true;render.run();});all.setOnClickListener(v->{filmFavorites=false;render.run();});render.run();
    }
    private View addFilmRow(LinearLayout list,String id,String name) {
        View row=getLayoutInflater().inflate(R.layout.item_roll_choice,list,false);
        ((TextView)row.findViewById(R.id.rollChoiceTitle)).setText(name);row.findViewById(R.id.rollChoiceDetail).setVisibility(View.GONE);
        row.setOnClickListener(v->{
            record.filmId=id;record.filmName=name;customFilm=id.isEmpty();
            if(customFilm)setText(R.id.rollCustomFilm,name);updateFilm();
        });
        list.addView(row);filmRows.put(id.isEmpty()?"custom:"+name:id,row);
        return row;
    }

    private void updateFilm() {
        findViewById(R.id.rollCustomFilm).setVisibility(customFilm ? View.VISIBLE : View.GONE);
        findViewById(R.id.rollFilmCustomButton).setSelected(customFilm);
        for (Map.Entry<String, View> row : filmRows.entrySet()) {
            boolean selected = row.getKey().equals(customFilm ? "custom:"+record.filmName : record.filmId);
            row.getValue().setSelected(selected);
            row.getValue().findViewById(R.id.rollChoiceCheck).setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void showFocalLengthPicker() {
        String[] values = {"35", "40", "50"};
        String[] labels = {"35mm", "40mm", "50mm"};
        new AlertDialog.Builder(this).setTitle(R.string.fov_label)
                .setSingleChoiceItems(labels, java.util.Arrays.asList(values).indexOf(record.focalLength), (dialog, which) -> {
                    record.focalLength = values[which]; updateFocalLength(); dialog.dismiss();
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private void updateFocalLength() {
        ((TextView) findViewById(R.id.rollFocal)).setText(record.focalLength.isEmpty()
                ? getString(R.string.choose_fov) : record.focalLength + "mm");
    }

    private void updateCamera() {
        findViewById(R.id.rollCustomCamera).setVisibility(customCamera ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.rollChooseCamera)).setText(customCamera || record.cameraName.isEmpty()
                ? getString(R.string.roll_choose_camera) : record.cameraName);
        FilmRollCatalog.Item item = catalog.findCamera(record.cameraId);
        ((TextView) findViewById(R.id.rollSelectedCameraDetail)).setText(item == null ? "" : item.cameraDetail(this));
    }

    private void updateMemoCount() {
        ((TextView) findViewById(R.id.rollMemoCounter)).setText(getString(R.string.roll_memo_counter, text(R.id.rollMemo).length()));
    }

    private void updateDates() {
        String period = FilmRollRules.period(record.start, record.end);
        ((TextView) findViewById(R.id.rollPeriod)).setText(period.isEmpty() ? getString(R.string.roll_select_period) : period);
        ((TextView) findViewById(R.id.rollReceived)).setText(record.received == FilmRollRules.NO_DATE ? getString(R.string.roll_select_date) : FilmRollRules.date(record.received));
        ((TextView) findViewById(R.id.rollCompleted)).setText(record.completed == FilmRollRules.NO_DATE ? getString(R.string.roll_select_date) : FilmRollRules.date(record.completed));
        findViewById(R.id.rollClearPeriod).setVisibility(record.start == FilmRollRules.NO_DATE ? View.GONE : View.VISIBLE);
        findViewById(R.id.rollClearReceived).setVisibility(record.received == FilmRollRules.NO_DATE ? View.GONE : View.VISIBLE);
        findViewById(R.id.rollClearCompleted).setVisibility(record.completed == FilmRollRules.NO_DATE ? View.GONE : View.VISIBLE);
    }

    private void openPeriodPicker() {
        if (getSupportFragmentManager().findFragmentByTag(PERIOD_PICKER) != null) return;
        MaterialDatePicker.Builder<Pair<Long, Long>> builder = MaterialDatePicker.Builder.dateRangePicker().setTitleText(R.string.roll_period);
        if (record.start != FilmRollRules.NO_DATE && record.end != FilmRollRules.NO_DATE) builder.setSelection(new Pair<>(record.start, record.end));
        MaterialDatePicker<Pair<Long, Long>> picker = builder.build();
        attachDateListener(picker, PERIOD_PICKER); picker.show(getSupportFragmentManager(), PERIOD_PICKER);
    }

    private void openDayPicker(String tag) {
        if (getSupportFragmentManager().findFragmentByTag(tag) != null) return;
        long current = RECEIVED_PICKER.equals(tag) ? record.received : record.completed;
        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker()
                .setTitleText(RECEIVED_PICKER.equals(tag) ? R.string.roll_received : R.string.roll_completed);
        if (current != FilmRollRules.NO_DATE) builder.setSelection(current);
        MaterialDatePicker<Long> picker = builder.build();
        attachDateListener(picker, tag); picker.show(getSupportFragmentManager(), tag);
    }

    @SuppressWarnings("unchecked")
    private void attachDateListener(MaterialDatePicker<?> picker, String tag) {
        if (PERIOD_PICKER.equals(tag)) {
            ((MaterialDatePicker<Pair<Long, Long>>) picker).addOnPositiveButtonClickListener(value -> {
                if (value == null || value.first == null || value.second == null) return;
                record.start = value.first; record.end = value.second; updateDates();
            });
        } else {
            ((MaterialDatePicker<Long>) picker).addOnPositiveButtonClickListener(value -> {
                if (value == null) return;
                if (RECEIVED_PICKER.equals(tag)) record.received = value; else record.completed = value;
                updateDates();
            });
        }
    }

    private void captureInputs() {
        record.name = text(R.id.rollName).trim();
        record.memo = text(R.id.rollMemo); record.labName = text(R.id.rollLabName).trim(); record.labMemo = text(R.id.rollLabMemo);
        if (customFilm) { record.filmId = ""; record.filmName = text(R.id.rollCustomFilm).trim(); }
        if (customCamera) { record.cameraId = ""; record.cameraName = text(R.id.rollCustomCamera).trim(); }
        record.plannedFrames = record.countMode == 0 ? FilmRollRules.parseFrameCount(text(R.id.rollCustomCount)) : record.countMode;
        int status = ((RadioGroup) findViewById(R.id.rollStatusGroup)).getCheckedRadioButtonId();
        record.labStatus = status == R.id.rollStatusProcessing ? 1 : status == R.id.rollStatusDone ? 2 : 0;
    }

    private void saveRoll() {
        if (locationPermissionPending || rollCreationSaved) return;
        captureInputs();
        if (record.filmName.isEmpty()) { showError(R.string.roll_error_film, 0); return; }
        if (record.plannedFrames < 1 || record.plannedFrames > 999) { showError(R.string.roll_error_count, 2); return; }
        if (!FilmRollRules.validFocalLength(record.focalLength)) { showError(R.string.roll_error_focal, 2); return; }
        if (!FilmRollRules.validPeriod(record.start, record.end)) { showError(R.string.roll_error_period, 2); return; }
        if (!FilmRollRules.validLabDates(record.received, record.completed)) { showError(R.string.roll_error_lab_dates, 3); return; }
        findViewById(R.id.rollError).setVisibility(View.GONE);
        // Hand the worker a snapshot so draft changes never alter an in-flight save.
        try { saving.save(FilmRollRecord.fromJson(record.toJson()), editing ? null : getIntent().getStringExtra(EXTRA_FOLDER_ID)); }
        catch (JSONException exception) { showError(R.string.roll_error_save, category); }
    }

    private void showError(int message, int index) {
        selectCategory(index);
        TextView error = findViewById(R.id.rollError); error.setText(message); error.setVisibility(View.VISIBLE);
    }

    private String text(int id) { return ((EditText) findViewById(id)).getText().toString(); }
    private void setText(int id, String value) { ((EditText) findViewById(id)).setText(value); }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("locationPermissionHandled", locationPermissionHandled);
        out.putBoolean("locationPermissionPending", locationPermissionPending);
        out.putBoolean("rollCreationSaved", rollCreationSaved);
        if (record != null) {
            captureInputs();
            out.putBoolean("filmFavorites",filmFavorites);
            try { out.putString("draft", record.toJson().toString()); } catch (JSONException exception) { throw new IllegalStateException(exception); }
            out.putInt("category", category); out.putBoolean("customFilm", customFilm); out.putBoolean("customCamera", customCamera);
            out.putString("customFilmText", text(R.id.rollCustomFilm)); out.putString("customCameraText", text(R.id.rollCustomCamera));
            out.putString("customCountText", text(R.id.rollCustomCount));
        }
        super.onSaveInstanceState(out);
    }

    private void showLanguageDialog() {
        String[] tags = {"", "ko", "en", "fr", "ja"};
        String[] labels = {getString(R.string.language_system), "한국어", "English", "Français", "日本語"};
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        String language = locales.isEmpty() ? "" : locales.get(0).getLanguage();
        new AlertDialog.Builder(this).setTitle(R.string.language_title)
                .setSingleChoiceItems(labels, Math.max(0, java.util.Arrays.asList(tags).indexOf(language)), (dialog, which) -> {
                    dialog.dismiss(); AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags[which]));
                }).setNegativeButton(R.string.cancel, null).show();
    }
}
