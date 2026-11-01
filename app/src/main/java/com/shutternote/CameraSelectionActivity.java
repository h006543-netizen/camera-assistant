package com.shutternote;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class CameraSelectionActivity extends AppCompatActivity {
    static final String EXTRA_CAMERA_ID = "camera_id";
    static final String EXTRA_CAMERA_NAME = "camera_name";
    private static final String FREQUENT = "__frequent__";
    private CameraUsageStore history;
    private FilmRollCatalog catalog;
    private String brand;
    private String selectedId;
    private final Map<String, View> brandRows = new LinkedHashMap<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_selection);
        SystemBarInsets.applyMainScreen(this, findViewById(R.id.cameraPickerStatusSpace),
                findViewById(R.id.cameraPickerNavigationSpace));
        FilmRollActivity.applyKeyboardInsets(findViewById(R.id.cameraPickerRoot));
        FilmRollActivity.markHeadings(findViewById(R.id.cameraPickerRoot));
        findViewById(R.id.cameraPickerBack).setOnClickListener(view -> finish());
        catalog = new FilmRollCatalog(this);
        history = new CameraUsageStore(this);
        try { history.seed(new FilmRollStore(this).load()); } catch (org.json.JSONException ignored) { }
        selectedId = getIntent().getStringExtra(EXTRA_CAMERA_ID);
        FilmRollCatalog.Item selected = catalog.findCamera(selectedId);
        brand = savedInstanceState == null ? (!history.top().isEmpty() ? FREQUENT : (selected == null ? catalog.cameras.get(0).brand : selected.brand))
                : savedInstanceState.getString("brand", catalog.cameras.get(0).brand);
        LinearLayout brands = findViewById(R.id.cameraPickerBrands);
        TextView frequent = (TextView) getLayoutInflater().inflate(R.layout.item_roll_brand, brands, false);
        frequent.setText(R.string.roll_camera_frequent);
        frequent.setOnClickListener(v -> { brand = FREQUENT; showModels(); ((ScrollView) findViewById(R.id.cameraPickerModelsScroll)).scrollTo(0, 0); });
        brands.addView(frequent); brandRows.put(FREQUENT, frequent);
        for (FilmRollCatalog.Item item : catalog.cameras) {
            if (brandRows.containsKey(item.brand)) continue;
            TextView row = (TextView) getLayoutInflater().inflate(R.layout.item_roll_brand, brands, false);
            row.setText(item.brand);
            row.setOnClickListener(view -> {
                brand = item.brand;
                showModels();
                ((ScrollView) findViewById(R.id.cameraPickerModelsScroll)).scrollTo(0, 0);
            });
            brands.addView(row); brandRows.put(item.brand, row);
        }
        EditText search = findViewById(R.id.cameraPickerSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { showModels(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        showModels();
    }

    private void showModels() {
        for (Map.Entry<String, View> row : brandRows.entrySet()) row.getValue().setSelected(row.getKey().equals(brand));
        String query = ((EditText) findViewById(R.id.cameraPickerSearch)).getText().toString().trim().toLowerCase(Locale.ROOT);
        LinearLayout models = findViewById(R.id.cameraPickerModels);
        models.removeAllViews();
        if (FREQUENT.equals(brand)) {
            for (CameraUsageStore.Entry entry : history.top()) {
                if (!entry.name.toLowerCase(Locale.ROOT).contains(query)) continue;
                FilmRollCatalog.Item item = catalog.findCamera(entry.id);
                View row = getLayoutInflater().inflate(R.layout.item_roll_choice, models, false);
                ((TextView) row.findViewById(R.id.rollChoiceTitle)).setText(entry.name);
                row.findViewById(R.id.rollChoiceDetail).setVisibility(View.GONE);
                boolean selected = !entry.id.isEmpty() && entry.id.equals(selectedId);
                row.setSelected(selected); row.findViewById(R.id.rollChoiceCheck).setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
                row.setOnClickListener(v -> {
                    setResult(RESULT_OK, new Intent().putExtra(EXTRA_CAMERA_ID, item == null ? "" : item.id).putExtra(EXTRA_CAMERA_NAME, entry.name));
                    finish();
                });
                FrequentItemRemoval.attach(row, () -> history.remove(entry.id, entry.name), this::showModels);
                models.addView(row);
            }
        }
        for (FilmRollCatalog.Item item : catalog.cameras) {
            if (!item.brand.equals(brand) || !item.name.toLowerCase(Locale.ROOT).contains(query)) continue;
            View row = getLayoutInflater().inflate(R.layout.item_roll_choice, models, false);
            ((TextView) row.findViewById(R.id.rollChoiceTitle)).setText(item.name);
            ((TextView) row.findViewById(R.id.rollChoiceDetail)).setText(item.cameraDetail(this));
            boolean selected = item.id.equals(selectedId);
            row.setSelected(selected);
            row.findViewById(R.id.rollChoiceCheck).setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            row.setContentDescription(item.fullName() + ", " + item.cameraDetail(this));
            row.setOnClickListener(view -> {
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_CAMERA_ID, item.id));
                finish();
            });
            models.addView(row);
        }
        if (models.getChildCount() == 0) {
            TextView empty = (TextView) getLayoutInflater().inflate(R.layout.item_roll_brand, models, false);
            empty.setText(R.string.roll_camera_no_results); empty.setClickable(false); empty.setFocusable(false);
            models.addView(empty);
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("brand", brand);
        super.onSaveInstanceState(out);
    }
}
