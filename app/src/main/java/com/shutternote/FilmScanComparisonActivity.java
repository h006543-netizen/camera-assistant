package com.shutternote;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

/** One independent screen contains connection confirmation, paired comparison and enlarged switching. */
public class FilmScanComparisonActivity extends AppCompatActivity {
    static final String CAPTURE = "scanCapture", PICK = "scanPick";
    private static final int PAGER_BASE = 1_000_000_000;
    private FilmScanViewModel model;
    private RecyclerView zoomPager;
    private ScanPagerAdapter zoomAdapter;
    private PagerSnapHelper zoomSnap;
    private boolean enlarged, showScan, initialPickerLaunched, restorePagePending;
    private int pageScroll;
    private int currentPagerPosition = PAGER_BASE;
    private FilmRollRecord displayedRoll;
    private String displayedDraft;
    private float restoredZoom = 1, restoredX = .5f, restoredY = .5f;
    private final ActivityResultLauncher<PickVisualMediaRequest> picker = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), uri -> {
                acceptPhoto(uri);
            });
    private final ActivityResultLauncher<Intent> galleryPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> acceptPhoto(
                    result.getResultCode() == RESULT_OK && result.getData() != null ? result.getData().getData() : null));

    private void acceptPhoto(android.net.Uri uri) {
        if (uri != null) model.importScan(uri);
        else if (!model.hasDraft() && !model.hasLinkedScan()) finish();
    }

    static Intent intent(android.content.Context context, String roll, FilmFrameRecord frame, boolean pick) {
        return new Intent(context, FilmScanComparisonActivity.class).putExtra(FilmRollActivity.EXTRA_ROLL_ID, roll)
                .putExtra(FilmFrameCapture.NUMBER, frame.number).putExtra(CAPTURE, frame.captureId).putExtra(PICK, pick);
    }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved); setContentView(R.layout.activity_film_scan_comparison);
        // ImageView does not consume android:enabled from XML on all platform versions.
        findViewById(R.id.scanNoteImage).setEnabled(false);
        findViewById(R.id.scanFilmImage).setEnabled(false);
        SystemBarInsets.applyMainScreen(this, findViewById(R.id.scanStatusSpace), findViewById(R.id.scanNavigationSpace));
        if (saved != null) {
            enlarged = saved.getBoolean("enlarged"); showScan = saved.getBoolean("showScan");
            initialPickerLaunched = saved.getBoolean("pickerLaunched");
            pageScroll = saved.getInt("pageScroll"); restorePagePending = true;
            restoredZoom = saved.getFloat("zoom", 1); restoredX = saved.getFloat("centerX", .5f); restoredY = saved.getFloat("centerY", .5f);
        }
        currentPagerPosition = saved == null ? PAGER_BASE + (showScan ? 1 : 0)
                : saved.getInt("zoomPage", PAGER_BASE + (showScan ? 1 : 0));
        model = new ViewModelProvider(this).get(FilmScanViewModel.class);
        FilmCorrectionJobs.changes.observe(this, ignored -> model.refreshCorrection());
        displayedDraft = model.draftName();
        zoomPager = findViewById(R.id.scanZoomPager);
        zoomPager.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        zoomPager.setNestedScrollingEnabled(false);
        zoomAdapter = new ScanPagerAdapter();
        zoomPager.setAdapter(zoomAdapter);
        zoomSnap = new PagerSnapHelper();
        zoomSnap.attachToRecyclerView(zoomPager);
        zoomPager.scrollToPosition(currentPagerPosition);
        zoomPager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView view, int state) {
                if (state != RecyclerView.SCROLL_STATE_IDLE || !enlarged) return;
                View page = zoomSnap.findSnapView(zoomPager.getLayoutManager());
                if (page == null) return;
                int position = zoomPager.getChildAdapterPosition(page);
                if (position == RecyclerView.NO_POSITION) return;
                currentPagerPosition = position;
                boolean selectedScan = (position & 1) != 0;
                if (selectedScan != showScan) {
                    showScan = selectedScan;
                    FilmComparisonImageView photo = currentZoomPhoto();
                    if (photo != null) photo.restore(restoredZoom, restoredX, restoredY);
                    render();
                }
            }
        });
        findViewById(R.id.scanBack).setOnClickListener(view -> goBack());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { goBack(); }
        });
        findViewById(R.id.scanMenu).setOnClickListener(this::showMenu);
        findViewById(R.id.scanReselect).setOnClickListener(view -> choose());
        findViewById(R.id.scanChoose).setOnClickListener(view -> choose());
        findViewById(R.id.scanConfirm).setOnClickListener(view -> { enlarged = false; model.save(false); });
        findViewById(R.id.scanNoteImage).setOnClickListener(view -> enlarge(false));
        findViewById(R.id.scanFilmImage).setOnClickListener(view -> enlarge(true));
        findViewById(R.id.scanShowNote).setOnClickListener(view -> selectPhoto(false));
        findViewById(R.id.scanShowFilm).setOnClickListener(view -> selectPhoto(true));
        findViewById(R.id.scanZoomIn).setOnClickListener(view -> { FilmComparisonImageView photo = currentZoomPhoto(); if (photo != null) photo.stepZoom(true); });
        findViewById(R.id.scanZoomOut).setOnClickListener(view -> { FilmComparisonImageView photo = currentZoomPhoto(); if (photo != null) photo.stepZoom(false); });
        model.message.observe(this, message -> { if (message != null) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); model.message.setValue(null); } });
        model.state.observe(this, state -> {
            if (state == FilmScanViewModel.State.MISSING) { Toast.makeText(this, R.string.note_frame_missing, Toast.LENGTH_LONG).show(); finish(); return; }
            String draft = model.draftName();
            if (draft != null && !draft.equals(displayedDraft)) enlarged = false;
            displayedDraft = draft;
            render();
            if(state==FilmScanViewModel.State.READY)model.refreshCorrection();
            if (state == FilmScanViewModel.State.READY && model.frame != null && !initialPickerLaunched
                    && getIntent().getBooleanExtra(PICK, false) && !model.hasDraft()) {
                initialPickerLaunched = true; choose();
            }
        });
        model.initialize(getIntent().getStringExtra(FilmRollActivity.EXTRA_ROLL_ID),
                getIntent().getIntExtra(FilmFrameCapture.NUMBER, 0), getIntent().getStringExtra(CAPTURE));
    }

    private void choose() {
        if (model.busy()) return;
        try {
            // Let Android present its resolver (Once / Always) and manage the default app.
            galleryPicker.launch(new Intent(Intent.ACTION_PICK)
                    .setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*"));
        } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
            try {
                picker.launch(new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
            } catch (android.content.ActivityNotFoundException | SecurityException error) {
                Toast.makeText(this, R.string.scan_picker_error, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void goBack() {
        if (model.state.getValue() == FilmScanViewModel.State.SAVING) return;
        if (enlarged) { enlarged = false; render(); showPhotos(); } else finish();
    }

    private void enlarge(boolean scan) {
        if (model.busy() || model.noteBitmap == null || model.scanBitmap == null) return;
        enlarged = true; showScan = scan; restoredZoom = 1; restoredX = restoredY = .5f;
        currentPagerPosition = PAGER_BASE + (scan ? 1 : 0);
        zoomAdapter.notifyDataSetChanged(); zoomPager.scrollToPosition(currentPagerPosition);
        render(); showPhotos();
    }

    private void showPhotos() {
        pageScroll = 0; restorePagePending = false;
        NestedScrollView scroll = findViewById(R.id.scanPageScroll);
        OneShotPreDrawListener.add(scroll, () -> scroll.scrollTo(0, 0));
    }

    private void selectPhoto(boolean scan) {
        if (model.busy() || scan == showScan || (scan ? model.scanBitmap : model.noteBitmap) == null) return;
        int target = currentPagerPosition + (scan ? 1 : -1);
        zoomPager.smoothScrollToPosition(target);
    }

    private FilmComparisonImageView currentZoomPhoto() {
        RecyclerView.ViewHolder holder = zoomPager.findViewHolderForAdapterPosition(currentPagerPosition);
        return holder instanceof ScanPhotoHolder ? ((ScanPhotoHolder) holder).photo : null;
    }

    private void showMenu(View anchor) {
        if (model.busy()) return;
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.scan_reselect);
        if (model.hasDraft()) menu.getMenu().add(0, 2, 1, R.string.scan_discard);
        if (model.hasLinkedScan()) menu.getMenu().add(0, 3, 2, R.string.scan_unlink);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) choose();
            else if (item.getItemId() == 2) { enlarged = false; model.discardDraft(); }
            else if (item.getItemId() == 3) { enlarged = false; model.save(true); }
            return true;
        }); menu.show();
    }

    private void render() {
        boolean busy = model.busy(), draft = model.hasDraft();
        zoomAdapter.setPhotos(model.noteBitmap, model.scanBitmap, busy);
        TextView title = findViewById(R.id.scanTitle);
        int cut = getIntent().getIntExtra(FilmFrameCapture.NUMBER, 0);
        title.setText(getString(enlarged ? R.string.scan_zoom_title : draft ? R.string.scan_confirm_title : R.string.scan_compare_title, cut));
        title.setContentDescription(model.rollTitle + ", " + title.getText());
        TextView rollTitle = findViewById(R.id.scanRollTitle);
        rollTitle.setText(model.rollTitle);
        rollTitle.setVisibility(model.rollTitle.isEmpty() ? View.GONE : View.VISIBLE);
        LinearLayout pair = findViewById(R.id.scanPair);
        Bitmap note = model.noteBitmap, scan = model.scanBitmap;
        boolean horizontal = note != null && scan != null && FilmScanRules.sideBySide(note.getWidth(), note.getHeight(), scan.getWidth(), scan.getHeight());
        pair.setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        pairCard(findViewById(R.id.scanNoteCard), horizontal, false);
        pairCard(findViewById(R.id.scanFilmCard), horizontal, true);
        ImageView noteImage=findViewById(R.id.scanNoteImage), filmImage=findViewById(R.id.scanFilmImage);
        noteImage.setImageBitmap(note); filmImage.setImageBitmap(scan);
        boolean pairReady=!busy && note!=null && scan!=null;
        noteImage.setEnabled(pairReady); filmImage.setEnabled(pairReady);
        noteImage.setContentDescription(getString(R.string.scan_note_photo)+", "+getString(R.string.scan_enlarge));
        filmImage.setContentDescription(getString(R.string.scan_film_photo)+", "+getString(R.string.scan_enlarge));
        pair.setVisibility(enlarged ? View.GONE : View.VISIBLE);
        zoomPager.setVisibility(enlarged ? View.VISIBLE : View.GONE);
        findViewById(R.id.scanZoomControls).setVisibility(enlarged ? View.VISIBLE : View.GONE);
        TextView progress = findViewById(R.id.scanProgress);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        progress.setText(model.state.getValue() == FilmScanViewModel.State.SAVING ? R.string.saving : R.string.scan_loading);
        findViewById(R.id.scanConfirmActions).setVisibility(!enlarged && draft ? View.VISIBLE : View.GONE);
        findViewById(R.id.scanCompareActions).setVisibility(!enlarged && !draft && scan == null ? View.VISIBLE : View.GONE);
        findViewById(R.id.scanSwitchActions).setVisibility(enlarged ? View.VISIBLE : View.GONE);
        findViewById(R.id.scanChoose).setVisibility(scan == null ? View.VISIBLE : View.GONE);
        int[] actions = {R.id.scanMenu, R.id.scanReselect, R.id.scanChoose, R.id.scanConfirm};
        for (int id : actions) findViewById(id).setEnabled(!busy);
        findViewById(R.id.scanConfirm).setEnabled(!busy && note != null && scan != null && draft);
        if (enlarged) zoomPager.post(() -> {
            if (zoomPager.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) {
                View page = zoomSnap.findSnapView(zoomPager.getLayoutManager());
                int visible = page == null ? RecyclerView.NO_POSITION : zoomPager.getChildAdapterPosition(page);
                if (visible != currentPagerPosition) zoomPager.scrollToPosition(currentPagerPosition);
            }
        });
        Button showNote = findViewById(R.id.scanShowNote), showFilm = findViewById(R.id.scanShowFilm);
        showNote.setSelected(!showScan); showFilm.setSelected(showScan);
        showNote.setEnabled(note != null && !busy); showFilm.setEnabled(scan != null && !busy);
        androidx.core.view.ViewCompat.setStateDescription(showNote, !showScan ? getString(R.string.scan_selected) : null);
        androidx.core.view.ViewCompat.setStateDescription(showFilm, showScan ? getString(R.string.scan_selected) : null);
        renderNotes();
        updateZoomControls();
    }

    private String exposure(FilmFrameRecord frame) {
        String fov = ExposureSettingsStore.FIELD_OF_VIEW_DEFAULT.equals(frame.fieldOfView) ? getString(R.string.fov_default) : frame.fieldOfView;
        return getString(R.string.note_exposure_values, frame.iso, frame.aperture, frame.shutter, fov);
    }

    private void renderNotes() {
        NestedScrollView scroll = findViewById(R.id.scanPageScroll);
        if (model.roll != null && model.frame != null && model.roll != displayedRoll) {
            // Importing/replacing a scan leaves these records and their scroll position intact.
            if (displayedRoll != null && !restorePagePending) pageScroll = scroll.getScrollY();
            displayedRoll = model.roll;
            LinearLayout records = findViewById(R.id.scanNotesRecords); records.removeAllViews();
            FilmFrameRecord frame = model.frame; FilmRollRecord roll = model.roll;
            noteRow(records, R.string.note_frame_memo, frame.memo);
            noteRow(records, R.string.note_exposure, exposure(frame));
            noteRow(records, R.string.note_capture_time, java.text.DateFormat.getDateTimeInstance(
                    java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, getResources().getConfiguration().getLocales().get(0))
                    .format(new java.util.Date(frame.capturedAt)));
            noteRow(records, R.string.note_location, frame.location == null ? getString(R.string.note_no_location) : frame.location.coordinates());
            if (frame.location != null && frame.location.accuracyMeters >= 0)
                noteRow(records, R.string.scan_location_accuracy, getString(R.string.note_location_accuracy, Math.round(frame.location.accuracyMeters)));
            noteRow(records, R.string.roll_name_hint, roll.name);
            noteRow(records, R.string.roll_film_name, roll.filmDisplayName());
            noteRow(records, R.string.roll_camera, roll.cameraName);
            noteRow(records, R.string.roll_count, String.valueOf(roll.plannedFrames));
            noteRow(records, R.string.note_period_title, FilmRollRules.period(roll.start, roll.end));
            noteRow(records, R.string.note_roll_memo, roll.memo);
            int offset = pageScroll;
            OneShotPreDrawListener.add(scroll, () -> {
                scroll.scrollTo(0, offset); restorePagePending = false;
            });
        }
    }

    private void noteRow(LinearLayout records, int title, String value) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_note_record, records, false);
        TextView heading = row.findViewById(R.id.recordTitle); heading.setText(title);
        ViewCompat.setAccessibilityHeading(heading, true);
        ((TextView) row.findViewById(R.id.recordValue)).setText(value.isEmpty() ? getString(R.string.scan_notes_empty) : value);
        View hit = row.findViewById(R.id.recordHit);
        hit.setClickable(false); hit.setFocusable(false); hit.setBackground(null);
        row.findViewById(R.id.recordArrow).setVisibility(View.GONE);
        records.addView(row);
    }

    private void pairCard(View card, boolean horizontal, boolean second) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(horizontal ? 0 : LinearLayout.LayoutParams.MATCH_PARENT,
                horizontal ? LinearLayout.LayoutParams.MATCH_PARENT : 0, 1);
        int gap = (int) (8 * getResources().getDisplayMetrics().density + .5f);
        if (second) { params.setMarginStart(horizontal ? gap : 0); params.topMargin = horizontal ? 0 : gap; }
        card.setLayoutParams(params);
    }

    private void updateZoomControls() {
        FilmComparisonImageView photo = currentZoomPhoto();
        if (photo == null) return;
        restoredZoom = photo.zoom(); restoredX = photo.centerX(); restoredY = photo.centerY();
        ((TextView) findViewById(R.id.scanZoomScale)).setText(getString(R.string.scan_zoom_percent, Math.round(photo.zoom() * 100)));
        findViewById(R.id.scanZoomOut).setEnabled(!model.busy() && photo.zoom() > 1);
        findViewById(R.id.scanZoomIn).setEnabled(!model.busy() && photo.zoom() < 5);
    }

    private final class ScanPagerAdapter extends RecyclerView.Adapter<ScanPhotoHolder> {
        private Bitmap note, scan;
        private boolean busy;
        void setPhotos(Bitmap note, Bitmap scan, boolean busy) {
            if (this.note == note && this.scan == scan && this.busy == busy) return;
            this.note = note; this.scan = scan; this.busy = busy; notifyDataSetChanged();
        }
        @NonNull @Override public ScanPhotoHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            FrameLayout page = new FrameLayout(parent.getContext());
            page.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            FilmComparisonImageView photo = new FilmComparisonImageView(parent.getContext(), null);
            photo.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            photo.setPhotoOnlyListener(new FilmComparisonImageView.PhotoOnlyListener() {
                @Override public boolean isActive() { return true; }
                @Override public void show() { }
            });
            photo.setChanged(() -> {
                if (photo == currentZoomPhoto()) updateZoomControls();
            });
            page.addView(photo);
            return new ScanPhotoHolder(page, photo);
        }
        @Override public void onBindViewHolder(@NonNull ScanPhotoHolder holder, int position) {
            boolean film = (position & 1) != 0;
            holder.photo.setImageBitmap(film ? scan : note);
            holder.photo.setEnabled(!busy);
            holder.photo.setContentDescription(getString(film ? R.string.scan_film_photo : R.string.scan_note_photo));
            holder.photo.restore(restoredZoom, restoredX, restoredY);
        }
        @Override public int getItemCount() { return Integer.MAX_VALUE; }
    }

    private static final class ScanPhotoHolder extends RecyclerView.ViewHolder {
        final FilmComparisonImageView photo;
        ScanPhotoHolder(View itemView, FilmComparisonImageView photo) { super(itemView); this.photo = photo; }
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle state) {
        state.putBoolean("enlarged", enlarged); state.putBoolean("showScan", showScan);
        state.putBoolean("pickerLaunched", initialPickerLaunched);
        state.putInt("pageScroll", restorePagePending ? pageScroll
                : ((NestedScrollView) findViewById(R.id.scanPageScroll)).getScrollY());
        state.putFloat("zoom", restoredZoom); state.putFloat("centerX", restoredX); state.putFloat("centerY", restoredY);
        state.putInt("zoomPage", currentPagerPosition);
        super.onSaveInstanceState(state);
    }
}
