package com.shutternote;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.lifecycle.ViewModelProvider;

public class MainActivity extends AppCompatActivity {

    /*
        메인 화면에서 사용하는 View 변수들입니다.

        boxIso       : ISO 선택 박스
        boxAperture  : 조리개 선택 박스
        boxShutter   : 셔터속도 선택 박스

        tvIsoValue       : 현재 선택된 ISO 값 표시
        tvApertureValue  : 현재 선택된 조리개 값 표시
        tvShutterValue   : 현재 선택된 셔터속도 값 표시

        btnExposureCamera : 노출계 카메라 실행 버튼
        btnDistanceCamera : 거리계 카메라 실행 버튼
    */
    private LinearLayout boxIso;
    private LinearLayout boxAperture;
    private LinearLayout boxShutter;

    private TextView tvIsoValue;
    private TextView tvApertureValue;
    private TextView tvShutterValue;

    private Button btnExposureCamera;
    private Button btnDistanceCamera;
    private ExposureSettingsStore exposureSettingsStore;
    private static final String MAIN_SCREEN_PREFERENCES = "main_screen_settings";
    private SharedPreferences mainScreenPreferences;
    private static final String STATE_MAIN_TAB = "main_tab";
    private boolean showingFilmNotes;
    private static final String STATE_CATEGORIES_OPEN = "categories_open";
    private boolean categoriesOpen;
    private OnBackPressedCallback categoryBackCallback;
    private OnBackPressedCallback rollSelectionBackCallback;
    private final FilmRollSelection rollSelection = new FilmRollSelection();
    private final java.util.Map<String, View> rollCards = new java.util.LinkedHashMap<>();
    private FilmRollDeleteViewModel rollDeletion;
    private FilmRollOrderViewModel rollOrdering;
    private FilmRollGroupDrag rollDrag;
    private FilmFolderViewModel folderSaving;
    private FilmRollFolders folders = new FilmRollFolders();
    private String currentFolder = "";
    private OnBackPressedCallback folderBackCallback;
    private final java.util.Map<String, View> folderCards = new java.util.LinkedHashMap<>();

    /*
        실제 선택 가능한 값 배열입니다.

        ISO는 필름카메라에서 자주 쓰는 값 위주로 구성했습니다.
        조리개는 일반적인 렌즈 조리개 단계입니다.
        셔터속도는 필름카메라에서 흔한 셔터속도 단계입니다.

        AlertDialog는 항목이 많아지면 자동으로 스크롤 가능한 목록처럼 동작합니다.
    */
    private final String[] isoValues = ExposureSettingsStore.isoValues();
    private final String[] apertureValues = ExposureSettingsStore.apertureValues();
    private final String[] shutterValues = ExposureSettingsStore.shutterValues();

    /*
        현재 선택된 값을 저장하는 변수입니다.

        초기값은 XML 화면과 맞춰서
        ISO 200
        조리개 f/2.8
        셔터속도 1/125
        로 설정했습니다.
    */
    private String selectedIso = ExposureSettingsStore.DEFAULT_ISO;
    private String selectedAperture = ExposureSettingsStore.DEFAULT_APERTURE;
    private String selectedShutter = ExposureSettingsStore.DEFAULT_SHUTTER;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CameraSessionCache.clearExpired(this);
        setContentView(R.layout.activity_main);
        SystemBarInsets.applyMainScreen(
                this,
                findViewById(R.id.mainStatusBarSpace),
                findViewById(R.id.mainNavigationBarSpace)
        );
        exposureSettingsStore = new ExposureSettingsStore(this);
        mainScreenPreferences = getSharedPreferences(MAIN_SCREEN_PREFERENCES, MODE_PRIVATE);
        findViewById(R.id.btnLanguage).setOnClickListener(view -> showLanguageDialog());
        findViewById(R.id.btnPrivacyPolicy).setOnClickListener(view -> PrivacyUi.openPolicy(this));
        findViewById(R.id.btnArPrivacy).setOnClickListener(view -> PrivacyUi.showArNotice(this, null));
        findViewById(R.id.btnAddFilmRoll).setOnClickListener(view ->
                startActivity(new Intent(this, FilmRollActivity.class).putExtra(FilmRollActivity.EXTRA_FOLDER_ID, currentFolder)));
        initializeRollSelection(savedInstanceState);

        /*
            XML에 있는 View들을 Java 변수와 연결합니다.
        */
        boxIso = findViewById(R.id.boxIso);
        boxAperture = findViewById(R.id.boxAperture);
        boxShutter = findViewById(R.id.boxShutter);

        tvIsoValue = findViewById(R.id.tvIsoValue);
        tvApertureValue = findViewById(R.id.tvApertureValue);
        tvShutterValue = findViewById(R.id.tvShutterValue);

        btnExposureCamera = findViewById(R.id.btnExposureCamera);
        btnDistanceCamera = findViewById(R.id.btnDistanceCamera);

        categoryBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                setCategoriesOpen(false);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, categoryBackCallback);
        findViewById(R.id.btnMainCategories).setOnClickListener(view -> setCategoriesOpen(!categoriesOpen));
        findViewById(R.id.categoryScrim).setOnClickListener(view -> setCategoriesOpen(false));
        findViewById(R.id.mainContentContainer).addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        updateCategoryMenuWidth());
        findViewById(R.id.tabShootingAssist).setOnClickListener(view -> selectMainCategory(false));
        findViewById(R.id.tabFilmNotes).setOnClickListener(view -> selectMainCategory(true));
        boolean lastMainTab = mainScreenPreferences.getBoolean(STATE_MAIN_TAB, false);
        showMainTab(savedInstanceState != null
                ? savedInstanceState.getBoolean(STATE_MAIN_TAB, lastMainTab) : lastMainTab);
        setCategoriesOpen(savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_CATEGORIES_OPEN, false));

        /*
        마지막으로 저장한 노출값을 읽어 화면에 표시합니다.
        저장값이 없거나 유효하지 않으면 기본값을 사용합니다.
        */
        restoreExposureSettings();

        /*
            ISO 박스를 누르면 ISO 선택 다이얼로그를 띄웁니다.
        */
        boxIso.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showIsoDialog();
            }
        });

        /*
            조리개 박스를 누르면 조리개 선택 다이얼로그를 띄웁니다.
        */
        boxAperture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showApertureDialog();
            }
        });

        /*
            셔터속도 박스를 누르면 셔터속도 선택 다이얼로그를 띄웁니다.
        */
        boxShutter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showShutterDialog();
            }
        });

        /*
            노출계 카메라 실행 버튼입니다.

            현재 선택한 ISO, 조리개, 셔터속도 값을
            ExposureCameraActivity로 넘깁니다.

            아직 ExposureCameraActivity를 만들지 않았다면
            이 부분은 빨간 줄이 뜨는 게 정상입니다.
            나중에 해당 Activity를 만들면 해결됩니다.
        */
        btnExposureCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, ExposureCameraActivity.class);

                intent.putExtra("iso", selectedIso);
                intent.putExtra("aperture", selectedAperture);
                intent.putExtra("shutter", selectedShutter);

                startActivity(intent);
            }
        });

        /*
            거리계 카메라 실행 버튼입니다.

            거리계는 ISO, 조리개, 셔터속도 값이 필요 없으므로
            별도 데이터 없이 DistanceCameraActivity로 이동합니다.

            아직 DistanceCameraActivity를 만들지 않았다면
            이 부분도 빨간 줄이 뜨는 게 정상입니다.
        */
        btnDistanceCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, DistanceCameraActivity.class);
                PrivacyUi.beforeRangefinder(MainActivity.this, () -> startActivity(intent));
            }
        });
    }

    /*
        ISO 선택 다이얼로그입니다.

        setItems()를 사용하면 목록 형태의 다이얼로그가 뜹니다.
        ISO 값이 많아지면 자동으로 스크롤됩니다.
    */
    private void showLanguageDialog() {
        String[] tags = {"", "ko", "en", "fr", "ja"};
        String[] labels = {getString(R.string.language_system), "한국어", "English", "Français", "日本語"};
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        String language = locales.isEmpty() ? "" : locales.get(0).getLanguage();
        int selected = Math.max(0, java.util.Arrays.asList(tags).indexOf(language));
        new AlertDialog.Builder(this)
                .setTitle(R.string.language_title)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    dialog.dismiss();
                    AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(tags[which]));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showMainTab(boolean filmNotes) {
        if (!filmNotes && rollDrag != null) rollDrag.cancel();
        if (!filmNotes && rollSelection.isActive()) endRollSelection();
        showingFilmNotes = filmNotes;
        findViewById(R.id.contentScrollView).setVisibility(filmNotes ? View.GONE : View.VISIBLE);
        findViewById(R.id.filmNotesScrollView).setVisibility(filmNotes ? View.VISIBLE : View.GONE);
        findViewById(R.id.tabShootingAssist).setSelected(!filmNotes);
        findViewById(R.id.tabFilmNotes).setSelected(filmNotes);
    }

    private void selectMainCategory(boolean filmNotes) {
        showMainTab(filmNotes);
        mainScreenPreferences.edit().putBoolean(STATE_MAIN_TAB, filmNotes).apply();
        setCategoriesOpen(false);
    }

    private void setCategoriesOpen(boolean open) {
        categoriesOpen = open;
        updateCategoryMenuWidth();
        findViewById(R.id.mainCategoryOverlay).setVisibility(open ? View.VISIBLE : View.GONE);
        findViewById(R.id.categoryChevron).setRotation(open ? 180f : 0f);
        View titleButton = findViewById(R.id.btnMainCategories);
        titleButton.setContentDescription(getString(open
                ? R.string.main_categories_close : R.string.main_categories_open));
        int accessibility = open ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
        findViewById(R.id.contentScrollView).setImportantForAccessibility(accessibility);
        findViewById(R.id.filmNotesScrollView).setImportantForAccessibility(accessibility);
        findViewById(R.id.mainLegalFooter).setImportantForAccessibility(accessibility);
        findViewById(R.id.btnPrivacyPolicy).setEnabled(!open);
        findViewById(R.id.btnArPrivacy).setEnabled(!open);
        categoryBackCallback.setEnabled(open);
        if (open) {
            findViewById(showingFilmNotes ? R.id.tabFilmNotes : R.id.tabShootingAssist).requestFocus();
        } else {
            titleButton.requestFocus();
        }
    }

    private void updateCategoryMenuWidth() {
        int available = findViewById(R.id.mainContentContainer).getWidth();
        if (available <= 0) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        TextView assist = findViewById(R.id.mainCategoryAssistLabel);
        TextView notes = findViewById(R.id.mainCategoryNotesLabel);
        float textWidth = Math.max(assist.getPaint().measureText(assist.getText().toString()),
                notes.getPaint().measureText(notes.getText().toString()));
        int desired = Math.max(Math.round(224 * density), (int) Math.ceil(textWidth + 64 * density));
        int width = Math.min(desired, Math.max(1, available - Math.round(40 * density)));
        View menu = findViewById(R.id.mainCategoryMenu);
        android.view.ViewGroup.LayoutParams params = menu.getLayoutParams();
        if (params.width != width) {
            params.width = width;
            menu.setLayoutParams(params);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (rollDrag != null) rollDrag.cancel();
        outState.putBoolean(STATE_MAIN_TAB, showingFilmNotes);
        outState.putBoolean(STATE_CATEGORIES_OPEN, categoriesOpen);
        outState.putBoolean("roll_selection_active", rollSelection.isActive());
        outState.putString("note_folder", currentFolder);
        outState.putStringArrayList("roll_selection_ids", new java.util.ArrayList<>(rollSelection.snapshot()));
        super.onSaveInstanceState(outState);
    }

    private void showIsoDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.choose_iso));

        builder.setItems(isoValues, (dialogInterface, position) -> {
            selectedIso = isoValues[position];
            tvIsoValue.setText(selectedIso);
            saveExposureSettings();
        });

        builder.show();
    }

    /*
        조리개 선택 다이얼로그입니다.
    */
    private void showApertureDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.choose_aperture));

        builder.setItems(apertureValues, (dialogInterface, position) -> {
            selectedAperture = apertureValues[position];
            tvApertureValue.setText(selectedAperture);
            saveExposureSettings();
        });

        builder.show();
    }

    /*
        셔터속도 선택 다이얼로그입니다.
    */
    private void showShutterDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.choose_shutter));

        builder.setItems(shutterValues, (dialogInterface, position) -> {
            selectedShutter = shutterValues[position];
            tvShutterValue.setText(selectedShutter);
            saveExposureSettings();
        });

        builder.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFilmRolls();
        if (exposureSettingsStore != null && tvIsoValue != null) {
            restoreExposureSettings();
        }
    }

    private void refreshFilmRolls() {
        if (rollDrag != null) rollDrag.cancel();
        LinearLayout list = findViewById(R.id.filmNotesRollList);
        list.removeAllViews();
        rollCards.clear();
        rollDrag.clearTargets();
        LinearLayout folderList = findViewById(R.id.filmNotesFolderList);
        folderList.removeAllViews(); folderCards.clear();
        try {
            java.util.List<FilmRollRecord> rolls = new FilmRollStore(this).load();
            folders = new FilmFolderStore(this).load();
            if (!currentFolder.isEmpty() && !folders.names.containsKey(currentFolder)) currentFolder = "";
            ((TextView)findViewById(R.id.filmNotesHeading)).setText(currentFolder.isEmpty()?getString(R.string.main_tab_notes):folders.names.get(currentFolder));
            findViewById(R.id.noteFolderBack).setVisibility(currentFolder.isEmpty()?View.GONE:View.VISIBLE);
            View folderPath = findViewById(R.id.noteFolderPath);
            LinearLayout.LayoutParams pathParams = (LinearLayout.LayoutParams) folderPath.getLayoutParams();
            pathParams.setMarginStart(currentFolder.isEmpty() ? 0
                    : -Math.round(20 * getResources().getDisplayMetrics().density));
            folderPath.setLayoutParams(pathParams);
            folderBackCallback.setEnabled(!currentFolder.isEmpty());
            if (!currentFolder.isEmpty()) rollDrag.target(findViewById(R.id.noteFolderPath), "");
            else for (java.util.Map.Entry<String,String> entry : folders.names.entrySet()) {
                String id = entry.getKey();
                View card = getLayoutInflater().inflate(R.layout.item_note_folder, folderList, false);
                ((TextView)card.findViewById(R.id.noteFolderTitle)).setText(entry.getValue());
                int count=0; for (FilmRollRecord roll:rolls) if (id.equals(folders.folderOf(roll.id))) count++;
                ((TextView)card.findViewById(R.id.noteFolderCount)).setText(getString(R.string.note_folder_count,count));
                card.setContentDescription(getString(R.string.note_folder_open,entry.getValue(),count));
                card.setOnClickListener(view -> { if (!isChangingRolls() && !rollSelection.isActive()) openFolder(id); });
                card.setOnLongClickListener(view -> { if (!isChangingRolls() && !rollSelection.isActive()) folderActions(id); return true; });
                folderList.addView(card); folderCards.put(id,card); rollDrag.target(card,id);
            }
            folderList.setVisibility(folderCards.isEmpty()?View.GONE:View.VISIBLE);
            java.util.List<FilmRollRecord> visible = new java.util.ArrayList<>();
            for (FilmRollRecord roll:rolls) if (currentFolder.equals(folders.folderOf(roll.id))) visible.add(roll);
            java.util.Set<String> availableIds = new java.util.HashSet<>();
            for (FilmRollRecord roll : visible) availableIds.add(roll.id);
            rollSelection.retainAvailable(availableIds);
            findViewById(R.id.filmNotesLoadError).setVisibility(View.GONE);
            findViewById(R.id.filmNotesEmptyState).setVisibility(visible.isEmpty() && folderCards.isEmpty() ? View.VISIBLE : View.GONE);
            list.setVisibility(visible.isEmpty() ? View.GONE : View.VISIBLE);
            for (FilmRollRecord roll : visible) {
                View card = getLayoutInflater().inflate(R.layout.item_film_roll, list, false);
                ((TextView) card.findViewById(R.id.filmRollCardTitle)).setText(roll.title());
                TextView film = card.findViewById(R.id.filmRollCardFilm);
                String description = roll.title().equals(roll.filmDisplayName()) ? "" : roll.filmDisplayName();
                if (!roll.cameraName.isEmpty()) {
                    description += (description.isEmpty() ? "" : "\n") + roll.cameraName;
                }
                film.setText(description);
                film.setVisibility(description.isEmpty() ? View.GONE : View.VISIBLE);
                ((TextView) card.findViewById(R.id.filmRollCardDetail)).setText(getString(R.string.note_record_count,
                        roll.frames.size(), roll.plannedFrames));
                TextView period = card.findViewById(R.id.filmRollCardPeriod);
                String dates = FilmRollRules.period(roll.start, roll.end);
                period.setText(dates); period.setVisibility(dates.isEmpty() ? View.GONE : View.VISIBLE);
                card.setTag(roll);
                card.setOnClickListener(view -> {
                    if (isChangingRolls()) return;
                    if (rollSelection.isActive()) toggleRollSelection(roll.id);
                    else startActivity(new Intent(this, FilmRollFramesActivity.class)
                            .putExtra(FilmRollActivity.EXTRA_ROLL_ID, roll.id));
                });
                rollDrag.attach(card);
                FilmRollReorderHandle handle=card.findViewById(R.id.filmRollReorder);
                rollDrag.attachHandle(card,handle);
                androidx.appcompat.widget.TooltipCompat.setTooltipText(handle,getString(R.string.roll_reorder));
                androidx.core.view.ViewCompat.addAccessibilityAction(handle, getString(R.string.roll_move_up),
                        (view, args) -> { rollDrag.moveOne(card, -1); return true; });
                androidx.core.view.ViewCompat.addAccessibilityAction(handle, getString(R.string.roll_move_down),
                        (view, args) -> { rollDrag.moveOne(card, 1); return true; });
                androidx.core.view.ViewCompat.addAccessibilityAction(card, getString(R.string.roll_select),
                        (view, args) -> {
                            if (isChangingRolls()) return false;
                            rollSelection.begin(roll.id); updateRollSelectionUi(); return true;
                        });
                card.findViewById(R.id.filmRollCardCheck).setOnClickListener(view -> toggleRollSelection(roll.id));
                list.addView(card);
                rollCards.put(roll.id, card);
            }
        } catch (org.json.JSONException exception) {
            findViewById(R.id.filmNotesEmptyState).setVisibility(View.GONE);
            list.setVisibility(View.GONE);
            folderList.setVisibility(View.GONE);
            findViewById(R.id.filmNotesLoadError).setVisibility(View.VISIBLE);
        }
        updateRollSelectionUi();
    }

    private void initializeRollSelection(Bundle savedState) {
        currentFolder = savedState == null ? "" : savedState.getString("note_folder", "");
        folderBackCallback = new OnBackPressedCallback(!currentFolder.isEmpty()) {
            @Override public void handleOnBackPressed() { if (!isChangingRolls()) openFolder(""); }
        };
        getOnBackPressedDispatcher().addCallback(this, folderBackCallback);
        if (savedState != null) rollSelection.restore(savedState.getBoolean("roll_selection_active"),
                savedState.getStringArrayList("roll_selection_ids"));
        rollSelectionBackCallback = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { if (rollDrag != null && rollDrag.active()) rollDrag.cancel(); else endRollSelection(); }
        };
        // Registered before the category callback: Back closes an open category menu first.
        getOnBackPressedDispatcher().addCallback(this, rollSelectionBackCallback);
        rollDeletion = new ViewModelProvider(this).get(FilmRollDeleteViewModel.class);
        rollOrdering = new ViewModelProvider(this).get(FilmRollOrderViewModel.class);
        folderSaving = new ViewModelProvider(this).get(FilmFolderViewModel.class);
        rollDrag = new FilmRollGroupDrag(findViewById(R.id.filmNotesRollList), findViewById(R.id.filmNotesViewport),
                () -> showingFilmNotes && !categoriesOpen && !isChangingRolls(), rollSelection, this::updateRollSelectionUi,
                ids -> rollOrdering.save(ids), this::moveRollsToFolder);
        findViewById(R.id.noteFolderBack).setOnClickListener(view -> { if (!isChangingRolls()) openFolder(""); });
        findViewById(R.id.btnAddNoteFolder).setOnClickListener(view -> editFolderName(null, false));
        findViewById(R.id.btnMoveFilmRolls).setOnClickListener(view -> chooseFolder());
        getSupportFragmentManager().setFragmentResultListener(FilmFolderNameDialog.RESULT, this, (key, value) -> {
            if (isChangingRolls()) return;
            java.util.ArrayList<String> selected = value.getStringArrayList("rolls");
            folderSaving.update(value.getBoolean("create")?FilmFolderStore.Operation.CREATE:FilmFolderStore.Operation.RENAME,
                    value.getString("id"), value.getString("name"), selected==null?java.util.Collections.emptySet():new java.util.HashSet<>(selected));
        });
        findViewById(R.id.btnSelectFilmRolls).setOnClickListener(view -> {
            if (isChangingRolls()) return;
            rollSelection.restore(true, java.util.Collections.emptyList()); updateRollSelectionUi();
        });
        findViewById(R.id.btnCancelRollSelection).setOnClickListener(view -> endRollSelection());
        findViewById(R.id.btnDeleteFilmRolls).setOnClickListener(view -> rollDeletion.delete(rollSelection.snapshot()));
        rollDeletion.state.observe(this, state -> {
            if (state == FilmRollDeleteViewModel.State.DELETED) {
                rollSelection.clear(); refreshFilmRolls(); rollDeletion.acknowledge();
            } else if (state == FilmRollDeleteViewModel.State.FAILED) {
                Toast.makeText(this, R.string.roll_error_delete, Toast.LENGTH_LONG).show();
                rollDeletion.acknowledge();
            }
            updateRollSelectionUi();
        });
        rollOrdering.state.observe(this, state -> {
            if (state == FilmRollOrderViewModel.State.SAVED || state == FilmRollOrderViewModel.State.FAILED) {
                if (state == FilmRollOrderViewModel.State.FAILED)
                    Toast.makeText(this, R.string.roll_order_error, Toast.LENGTH_LONG).show();
                refreshFilmRolls(); rollOrdering.acknowledge();
            }
            updateRollSelectionUi();
        });
        folderSaving.state.observe(this, state -> {
            if (state == FilmFolderViewModel.State.SAVED || state == FilmFolderViewModel.State.FAILED) {
                if (state == FilmFolderViewModel.State.SAVED && folderSaving.movedRolls) rollSelection.clear();
                if (state == FilmFolderViewModel.State.FAILED) Toast.makeText(this,R.string.note_folder_error,Toast.LENGTH_LONG).show();
                refreshFilmRolls(); folderSaving.acknowledge();
            }
            updateRollSelectionUi();
        });
    }

    private boolean isChangingRolls() {
        return rollDeletion != null && rollDeletion.state.getValue() == FilmRollDeleteViewModel.State.DELETING
                || rollOrdering != null && rollOrdering.state.getValue() == FilmRollOrderViewModel.State.SAVING
                || folderSaving != null && folderSaving.state.getValue() == FilmFolderViewModel.State.SAVING;
    }

    private void openFolder(String id) {
        if (rollDrag != null) rollDrag.cancel();
        currentFolder=id; rollSelection.clear(); refreshFilmRolls();
        ((android.widget.ScrollView)findViewById(R.id.filmNotesViewport)).scrollTo(0,0);
    }

    private void editFolderName(String id, boolean moveSelection) {
        if (isChangingRolls() || getSupportFragmentManager().isStateSaved()
                || getSupportFragmentManager().findFragmentByTag(FilmFolderNameDialog.TAG)!=null) return;
        FilmFolderNameDialog.create(id==null?java.util.UUID.randomUUID().toString():id,
                id==null?"":folders.names.get(id), id==null,
                new java.util.ArrayList<>(moveSelection?rollSelection.snapshot():java.util.Collections.emptySet()))
                .show(getSupportFragmentManager(),FilmFolderNameDialog.TAG);
    }

    private void folderActions(String id) {
        new AlertDialog.Builder(this).setTitle(folders.names.get(id))
                .setItems(new String[]{getString(R.string.note_folder_rename),getString(R.string.note_folder_remove)},(dialog,which) -> {
                    if (isChangingRolls()) return;
                    if (which==0) editFolderName(id,false);
                    else folderSaving.update(FilmFolderStore.Operation.REMOVE,id,"",java.util.Collections.emptySet());
                }).show();
    }

    private void chooseFolder() {
        if (isChangingRolls() || rollSelection.count()==0) return;
        java.util.List<String> ids=new java.util.ArrayList<>(), labels=new java.util.ArrayList<>();
        ids.add(""); labels.add(getString(R.string.note_folder_outside));
        for (java.util.Map.Entry<String,String> folder:folders.names.entrySet()) { ids.add(folder.getKey()); labels.add(folder.getValue()); }
        labels.add(getString(R.string.note_folder_new));
        new AlertDialog.Builder(this).setTitle(R.string.note_folder_move)
                .setItems(labels.toArray(new String[0]),(dialog,which) -> {
                    if (which==ids.size()) editFolderName(null,true);
                    else moveRollsToFolder(rollSelection.snapshot(),ids.get(which));
                }).setNegativeButton(R.string.cancel,null).show();
    }

    private void moveRollsToFolder(java.util.Set<String> ids,String destination) {
        if (isChangingRolls() || ids.isEmpty()) return;
        folderSaving.update(FilmFolderStore.Operation.MOVE,destination,"",ids);
    }

    private void toggleRollSelection(String id) {
        if (isChangingRolls()) return;
        rollSelection.toggle(id); updateRollSelectionUi();
    }

    private void endRollSelection() {
        rollSelection.clear(); updateRollSelectionUi();
    }

    private void updateRollSelectionUi() {
        boolean active = rollSelection.isActive();
        boolean busy = isChangingRolls();
        View selectRolls = findViewById(R.id.btnSelectFilmRolls);
        selectRolls.setVisibility(!active && !rollCards.isEmpty() ? View.VISIBLE : View.GONE);
        selectRolls.setEnabled(!busy);
        findViewById(R.id.btnAddNoteFolder).setVisibility(!active && currentFolder.isEmpty()?View.VISIBLE:View.GONE);
        findViewById(R.id.btnAddNoteFolder).setEnabled(!busy);
        findViewById(R.id.noteFolderBack).setEnabled(!busy);
        for (View card:folderCards.values()) card.setEnabled(!busy);
        findViewById(R.id.filmRollSelectionBar).setVisibility(active ? View.VISIBLE : View.GONE);
        findViewById(R.id.filmNotesHeading).setVisibility(active && currentFolder.isEmpty() ? View.GONE : View.VISIBLE);
        View body = findViewById(R.id.filmNotesBody);
        float density = getResources().getDisplayMetrics().density;
        body.setPadding(body.getPaddingLeft(), Math.round(8 * density),
                body.getPaddingRight(), body.getPaddingBottom());
        LinearLayout.LayoutParams listParams = (LinearLayout.LayoutParams)
                findViewById(R.id.filmNotesRollList).getLayoutParams();
        listParams.topMargin = Math.round((active ? 0 : 8) * density);
        findViewById(R.id.filmNotesRollList).setLayoutParams(listParams);
        ((TextView) findViewById(R.id.filmRollSelectionCount)).setText(getString(R.string.roll_selection_count, rollSelection.count()));
        findViewById(R.id.btnDeleteFilmRolls).setEnabled(active && rollSelection.count() > 0 && !busy);
        findViewById(R.id.btnMoveFilmRolls).setEnabled(active && rollSelection.count()>0 && !busy);
        findViewById(R.id.btnCancelRollSelection).setEnabled(!busy);
        findViewById(R.id.btnAddFilmRoll).setVisibility(active ? View.GONE : View.VISIBLE);
        findViewById(R.id.btnAddFilmRoll).setEnabled(!busy);
        if (rollSelectionBackCallback != null) rollSelectionBackCallback.setEnabled(active || rollDrag != null && rollDrag.active());
        for (java.util.Map.Entry<String, View> entry : rollCards.entrySet()) {
            View card = entry.getValue();
            FilmRollRecord roll = (FilmRollRecord) card.getTag();
            CheckBox checkbox = card.findViewById(R.id.filmRollCardCheck);
            checkbox.setVisibility(active ? View.VISIBLE : View.GONE);
            checkbox.setChecked(rollSelection.contains(entry.getKey()));
            checkbox.setEnabled(!busy);
            checkbox.setContentDescription(getString(R.string.roll_select_item, roll.title()));
            View handle=card.findViewById(R.id.filmRollReorder);
            boolean canReorder=!busy && !active && rollCards.size()>1;
            handle.setEnabled(canReorder); handle.setAlpha(canReorder?1f:.35f);
            handle.setContentDescription(getString(R.string.roll_reorder_item,roll.title()));
            card.setEnabled(!busy);
            card.setSelected(active && rollSelection.contains(entry.getKey()));
            card.setContentDescription(getString(active ? R.string.roll_select_item : R.string.note_open_roll, roll.title()));
        }
    }

    @Override protected void onPause() {
        if (rollDrag != null) rollDrag.cancel();
        super.onPause();
    }

    private void restoreExposureSettings() {
        ExposureSettingsStore.Values values = exposureSettingsStore.load();
        selectedIso = values.getIso();
        selectedAperture = values.getAperture();
        selectedShutter = values.getShutter();

        tvIsoValue.setText(selectedIso);
        tvApertureValue.setText(selectedAperture);
        tvShutterValue.setText(selectedShutter);
    }

    private void saveExposureSettings() {
        exposureSettingsStore.save(
                selectedIso,
                selectedAperture,
                selectedShutter
        );
    }
}
