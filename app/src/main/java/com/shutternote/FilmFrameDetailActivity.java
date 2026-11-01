package com.shutternote;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/** Each horizontally paged cut contains its photograph and vertically scrollable records. */
public class FilmFrameDetailActivity extends AppCompatActivity {
    private String rollId;
    private int number, restoredScroll;
    private FilmRollRecord roll;
    private List<Integer> numbers = new ArrayList<>();
    private RecyclerView pager;
    private LinearLayoutManager layout;
    private final FilmFramePagerSnapHelper snap = new FilmFramePagerSnapHelper(() -> numbers.size());
    private final FeedAdapter adapter = new FeedAdapter();
    private FilmRecordEditor recordEditor;
    private FilmFrameDeletion deletion;
    private FilmFrameRotationViewModel rotationSaving;
    private FilmFrameCorrectionViewModel correctionSaving;
    private FilmNoteGalleryViewModel gallerySaving;
    private final androidx.activity.result.ActivityResultLauncher<String> galleryPermission = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            granted -> { if (gallerySaving != null) gallerySaving.permissionResult(granted); });
    private FilmCorrectionCategory correctionCategory;
    private boolean ready, resumed;
    private boolean photoOnly;
    private boolean showingNextRoll;
    private int clearingCut = -1;
    private boolean closeAfterEdits;
    private FilmNextRollNavigation.Target nextRoll;
    private Bundle restoredPhoto;
    private final androidx.activity.OnBackPressedCallback photoBack = new androidx.activity.OnBackPressedCallback(false) {
        @Override public void handleOnBackPressed() { setPhotoOnly(false); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_film_frame_detail);
        SystemBarInsets.applyMainScreen(this, findViewById(R.id.noteDetailStatusSpace), findViewById(R.id.noteDetailNavigationSpace));
        View root = findViewById(R.id.noteDetailRoot);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int keyboard = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom;
            int navigation = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()
                    | androidx.core.view.WindowInsetsCompat.Type.systemGestures()).bottom;
            // The separate navigation spacer already accounts for the system bar.
            int bottom = photoOnly ? 0 : Math.max(0, keyboard - navigation);
            if (view.getPaddingBottom() != bottom) {
                view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), bottom);
                if (keyboard > 0) OneShotPreDrawListener.add(view, () -> {
                    if (recordEditor != null) recordEditor.revealFocusedInput();
                });
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        rollId = getIntent().getStringExtra(FilmRollActivity.EXTRA_ROLL_ID);
        number = state == null ? getIntent().getIntExtra(FilmFrameCapture.NUMBER, 1) : state.getInt("number", 1);
        restoredScroll = state == null ? 0 : state.getInt("scroll", 0);
        photoOnly = state == null ? getIntent().getBooleanExtra("photoOnly", false) : state.getBoolean("photoOnly");
        showingNextRoll = state != null && state.getBoolean("showingNextRoll");
        restoredPhoto = state;
        clearingCut = state == null ? -1 : state.getInt("clearingCut", -1);
        closeAfterEdits = state != null && state.getBoolean("closeAfterEdits", state.getBoolean("closeAfterCorrection"));
        correctionCategory = new FilmCorrectionCategory(findViewById(R.id.noteCorrectionCategory));
        // Editor/delete callbacks register afterwards and take precedence when open.
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { closeAfterChanges(); }
        });
        findViewById(R.id.noteDetailBack).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        findViewById(R.id.noteDetailSave).setOnClickListener(v -> saveToGallery());
        androidx.appcompat.widget.TooltipCompat.setTooltipText(findViewById(R.id.noteDetailSave), getString(R.string.note_save_gallery));
        pager = findViewById(R.id.noteDetailPager);
        layout = new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false);
        pager.setLayoutManager(layout);
        pager.setAdapter(adapter);
        pager.setItemViewCacheSize(0);
        pager.setItemAnimator(null);
        pager.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            if (photoOnly && b - t != ob - ot) for (FeedHolder holder : adapter.holders) applyPhotoOnly(holder);
            if (r - l != or - ol && nextRoll != null) {
                View card = layout.findViewByPosition(numbers.size());
                if (card != null) { card.getLayoutParams().width = nextRollWidth(); card.requestLayout(); }
                if (showingNextRoll) layout.scrollToPositionWithOffset(numbers.size(), pager.getWidth() - nextRollWidth());
            }
        });
        snap.attachToRecyclerView(pager);
        pager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView view, int state) {
                if (state != RecyclerView.SCROLL_STATE_IDLE) {
                    correctionCategory.dismiss(); updateCorrectionCategory();
                    if (deletion != null) deletion.dismiss(); return;
                }
                View selected = snap.findSnapView(layout);
                if (selected == null) return;
                int index = layout.getPosition(selected);
                showingNextRoll = nextRoll != null && index == numbers.size();
                if (showingNextRoll) {
                    number = numbers.get(numbers.size() - 1);
                    updateIndex();
                    return;
                }
                if (index < 0 || index >= numbers.size()) return;
                int next = numbers.get(index);
                if (next != number) {
                    number = next;
                    ((NestedScrollView) selected).scrollTo(0, 0);
                    updateIndex();
                }
                updateCorrectionCategory();
            }
        });
        recordEditor = new FilmRecordEditor(this, rollId, state, () -> reload(false));
        getSupportFragmentManager().setFragmentResultListener(FilmLocationEditorDialog.RESULT, this, (key, result) -> {
            if (ready) reload(false);
        });
        FilmCaptureLocationJobs.get(this).changes().observe(this, changedRoll -> {
            if (ready && rollId.equals(changedRoll)) reload(false);
        });
        deletion = new FilmFrameDeletion(this, this::onCutDeleted);
        rotationSaving = new androidx.lifecycle.ViewModelProvider(this).get(FilmFrameRotationViewModel.class);
        correctionSaving = new androidx.lifecycle.ViewModelProvider(this).get(FilmFrameCorrectionViewModel.class);
        rotationSaving.state.observe(this, value -> {
            for (FeedHolder holder : adapter.holders) holder.itemView.findViewById(R.id.noteRotate).setEnabled(value != FilmFrameRotationViewModel.State.SAVING);
            updateGalleryButton();
            if (value == FilmFrameRotationViewModel.State.SAVED) {
                reload(false); rotationSaving.acknowledge(); finishPendingClose();
            }
            else if (value == FilmFrameRotationViewModel.State.FAILED) {
                closeAfterEdits = false;
                Toast.makeText(this, R.string.note_rotate_error, Toast.LENGTH_LONG).show(); rotationSaving.acknowledge();
            }
        });
        correctionSaving.state.observe(this, value -> {
            updateCorrectionCategory();
            if (value == FilmFrameCorrectionViewModel.State.SAVED) {
                clearingCut = -1; reload(false); correctionSaving.acknowledge();
                finishPendingClose();
            }
            else if (value == FilmFrameCorrectionViewModel.State.FAILED) {
                clearingCut = -1; closeAfterEdits = false;
                for (FeedHolder holder : adapter.holders) {
                    FilmFrameRecord frame = roll == null ? null : roll.frame(holder.cut);
                    if (frame != null) holder.preview.show(!frame.correctionEnabled);
                }
                Toast.makeText(this, R.string.correction_change_error, Toast.LENGTH_LONG).show();
                correctionSaving.acknowledge();
            }
        });
        gallerySaving = new androidx.lifecycle.ViewModelProvider(this).get(FilmNoteGalleryViewModel.class);
        gallerySaving.state.observe(this, value -> {
            updateGalleryButton();
            int message = value == FilmNoteGalleryViewModel.State.SAVED ? R.string.save_success
                    : value == FilmNoteGalleryViewModel.State.FAILED ? R.string.save_error
                    : value == FilmNoteGalleryViewModel.State.PARTIAL ? R.string.correction_save_partial
                    : value == FilmNoteGalleryViewModel.State.DENIED ? R.string.storage_permission : 0;
            if (message != 0) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); gallerySaving.acknowledge(); }
        });
        getOnBackPressedDispatcher().addCallback(this, photoBack);
        applyPhotoChrome();
        if (!isFinishing()) ready = reload(true);
    }

    private void setPhotoOnly(boolean enabled) {
        if (photoOnly == enabled) return;
        photoOnly = enabled;
        correctionCategory.dismiss();
        pager.stopScroll();
        deletion.dismiss();
        if (enabled) {
            View focus = getCurrentFocus();
            if (focus != null) focus.clearFocus();
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).hide(WindowInsetsCompat.Type.ime());
            findViewById(R.id.noteDetailRoot).setPadding(0, 0, 0, 0);
        }
        applyPhotoChrome();
        for (FeedHolder holder : adapter.holders) applyPhotoOnly(holder);
        View card = layout.findViewByPosition(numbers.size());
        if (card != null) card.setBackgroundColor(photoOnly ? android.graphics.Color.BLACK
                : androidx.core.content.ContextCompat.getColor(this, R.color.note_gallery_background));
    }

    private void applyPhotoChrome() {
        photoBack.setEnabled(photoOnly);
        int visibility = photoOnly ? View.GONE : View.VISIBLE;
        findViewById(R.id.noteDetailHeader).setVisibility(visibility);
        findViewById(R.id.noteDetailStatusSpace).setVisibility(visibility);
        findViewById(R.id.noteDetailNavigationSpace).setVisibility(visibility);
        findViewById(R.id.noteDetailRoot).setBackgroundColor(photoOnly ? android.graphics.Color.BLACK
                : androidx.core.content.ContextCompat.getColor(this, R.color.note_gallery_background));
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (photoOnly) {
            bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            bars.hide(WindowInsetsCompat.Type.systemBars());
        } else bars.show(WindowInsetsCompat.Type.systemBars());
    }

    private void applyPhotoOnly(FeedHolder holder) {
        if (photoOnly && !holder.photoOnly) holder.noteScroll = holder.pendingScroll >= 0 ? holder.pendingScroll : holder.scroll.getScrollY();
        holder.itemView.findViewById(R.id.noteFeedRecords).setVisibility(photoOnly ? View.GONE : View.VISIBLE);
        holder.area.setBackgroundColor(photoOnly ? android.graphics.Color.BLACK
                : androidx.core.content.ContextCompat.getColor(this, R.color.note_gallery_background));
        holder.area.setPhotoOnlyHeight(photoOnly ? Math.max(1, pager.getHeight()) : 0);
        holder.scroll.setFillViewport(photoOnly);
        if (photoOnly) {
            holder.pendingScroll = -1;
            holder.scroll.scrollTo(0, 0);
            holder.photo.setOnLongClickListener(null);
            ViewCompat.removeAccessibilityAction(holder.photo, androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_LONG_CLICK);
            holder.itemView.findViewById(R.id.frameDelete).setVisibility(View.GONE);
        } else {
            if (holder.photoOnly) {
                holder.photo.restore(1, .5f, .5f);
                holder.pendingScroll = holder.noteScroll;
                OneShotPreDrawListener.add(holder.itemView, holder::restoreScroll);
            }
            if (holder.cut >= 0 && roll != null && roll.frame(holder.cut) != null)
                deletion.attach(holder.photo, holder.itemView.findViewById(R.id.frameDelete), rollId, roll.frame(holder.cut));
        }
        holder.photoOnly = photoOnly;
        holder.photo.setContentDescription(photoOnly ? getString(R.string.note_photo_close) : getString(R.string.note_open_frame, holder.cut));
        holder.location.setActive(!photoOnly && holder.cut == number && holder.itemView.isAttachedToWindow());
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && photoOnly) applyPhotoChrome();
    }

    @Override protected void onResume() {
        super.onResume();
        if (resumed && ready) reload(false); // Includes edits to the shared roll information.
        resumed = true;
    }

    private boolean reload(boolean initial) {
        try { roll = new FilmRollStore(this).find(rollId); } catch (org.json.JSONException error) { roll = null; }
        FilmRollStore store = new FilmRollStore(this);
        if (roll == null || roll.frame(number) == null || !store.photo(roll.frame(number)).isFile()) {
            Toast.makeText(this, R.string.note_frame_missing, Toast.LENGTH_LONG).show(); finish(); return false;
        }
        List<Integer> available = new ArrayList<>();
        for (FilmFrameRecord frame : roll.frames) if (store.photo(frame).isFile()) available.add(frame.number);
        List<Integer> updated = FilmFrameNavigation.recordedNumbers(roll.plannedFrames, available);
        if (!updated.contains(number)) {
            Toast.makeText(this, R.string.note_frame_missing, Toast.LENGTH_LONG).show(); finish(); return false;
        }
        boolean hadNext = nextRoll != null;
        nextRoll = findNextRoll();
        if (initial || !updated.equals(numbers) || hadNext != (nextRoll != null)) {
            numbers = updated;
            adapter.notifyDataSetChanged();
            showingNextRoll = showingNextRoll && nextRoll != null && number == numbers.get(numbers.size() - 1);
            if (showingNextRoll) layout.scrollToPositionWithOffset(numbers.size(), pager.getWidth() - nextRollWidth());
            else layout.scrollToPositionWithOffset(numbers.indexOf(number), 0);
        } else {
            // Refresh records without clearing/reloading the photo or jumping vertical position.
            adapter.notifyItemRangeChanged(0, numbers.size(), "records");
        }
        updateIndex(); return true;
    }

    private void updateIndex() {
        setResult(RESULT_OK, new android.content.Intent()
                .putExtra(FilmRollActivity.EXTRA_ROLL_ID, rollId)
                .putExtra(FilmFrameCapture.NUMBER, number));
        ((TextView) findViewById(R.id.noteDetailIndex)).setText(getString(R.string.note_single_position, number, roll.plannedFrames));
        updateCorrectionCategory();
        for (FeedHolder holder : adapter.holders) holder.location.setActive(!photoOnly && holder.cut == number && holder.itemView.isAttachedToWindow());
    }

    private void onCutDeleted(int deleted) {
        try {
            FilmRollStore store = new FilmRollStore(this);
            FilmRollRecord current = store.find(rollId);
            List<Integer> available = new ArrayList<>();
            if (current != null) for (FilmFrameRecord frame : current.frames) if (store.photo(frame).isFile()) available.add(frame.number);
            List<Integer> remaining = FilmFrameNavigation.recordedNumbers(current == null ? 0 : current.plannedFrames, available);
            if (remaining.isEmpty()) { finish(); return; }
            if (!remaining.contains(number)) {
                number = remaining.get(remaining.size() - 1);
                for (int cut : remaining) if (cut > deleted) { number = cut; break; }
            }
            reload(false);
        } catch (org.json.JSONException error) { finish(); }
    }

    private void navigate(int direction) {
        int index = showingNextRoll ? numbers.size() : numbers.indexOf(number), next = index + direction;
        if (index >= 0 && next >= 0 && next < adapter.getItemCount()) pager.smoothScrollToPosition(next);
    }

    private int nextRollWidth() {
        return Math.max(1, Math.round(pager.getWidth() * FilmNextRollNavigation.REVEAL_FRACTION));
    }

    private FilmNextRollNavigation.Target findNextRoll() {
        try {
            FilmRollStore store = new FilmRollStore(this);
            return FilmNextRollNavigation.find(store.load(), new FilmFolderStore(this).load(), rollId,
                    frame -> store.photo(frame).isFile());
        } catch (org.json.JSONException error) { return null; }
    }

    private void openNextRoll() {
        if (recordEditor != null && recordEditor.cancel()) return;
        // Resolve again: a roll/photo may have changed while this screen was in the background.
        FilmNextRollNavigation.Target target = findNextRoll();
        if (target == null) { reload(false); return; }
        android.content.Intent frames = new android.content.Intent(this, FilmRollFramesActivity.class)
                .putExtra(FilmRollActivity.EXTRA_ROLL_ID, target.rollId)
                .putExtra(FilmFrameCapture.NUMBER, target.number)
                .putExtra("photoOnly", photoOnly)
                .putExtra(FilmRollFramesActivity.EXTRA_OPEN_DETAIL, true)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
        // Recreate the list at the target cut; it launches detail and receives the viewed cut.
        // Do not add SINGLE_TOP: the old list must not retain its previous roll ID/ViewModels.
        startActivity(frames);
        finish();
    }

    private final class FeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final java.util.Set<FeedHolder> holders = new java.util.HashSet<>();
        @Override public int getItemViewType(int position) { return position == numbers.size() ? 1 : 0; }
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            if (type == 1) {
                View card = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note_next_roll, parent, false);
                card.findViewById(R.id.noteNextRoll).setOnClickListener(v -> openNextRoll());
                ViewCompat.addAccessibilityAction(card.findViewById(R.id.noteNextRoll), getString(R.string.note_previous_photo),
                        (v, args) -> { navigate(-1); return true; });
                return new RecyclerView.ViewHolder(card) { };
            }
            FeedHolder holder = new FeedHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_film_frame_feed, parent, false));
            holders.add(holder);
            ViewCompat.addAccessibilityAction(holder.scroll, getString(R.string.note_previous_photo), (v, args) -> { navigate(-1); return true; });
            ViewCompat.addAccessibilityAction(holder.scroll, getString(R.string.note_next_photo), (v, args) -> { navigate(1); return true; });
            ViewCompat.addAccessibilityAction(holder.photo, getString(R.string.scan_zoom_in), (v, args) -> {
                setPhotoOnly(true); holder.photo.stepZoom(true); return true;
            });
            ViewCompat.addAccessibilityAction(holder.photo, getString(R.string.scan_zoom_out), (v, args) -> {
                setPhotoOnly(true); holder.photo.stepZoom(false); return true;
            });
            ViewCompat.addAccessibilityAction(holder.photo, getString(R.string.correction_original), (v, args) -> { selectCorrection(holder.cut, false); return true; });
            ViewCompat.addAccessibilityAction(holder.photo, getString(R.string.correction_e100d), (v, args) -> { selectCorrection(holder.cut, true); return true; });
            return holder;
        }
        @Override public int getItemCount() { return numbers.size() + (nextRoll == null ? 0 : 1); }
        @Override public long getItemId(int position) { return position == numbers.size() ? -1 : numbers.get(position); }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder page, int position) {
            if (!(page instanceof FeedHolder)) {
                page.itemView.getLayoutParams().width = nextRollWidth();
                page.itemView.setBackgroundColor(photoOnly ? android.graphics.Color.BLACK
                        : androidx.core.content.ContextCompat.getColor(FilmFrameDetailActivity.this, R.color.note_gallery_background));
                return;
            }
            FeedHolder holder = (FeedHolder) page;
            int cut = numbers.get(position);
            holder.photoOnly = false;
            holder.rotation = -1;
            bindRecords(holder, cut);
            holder.pendingScroll = cut == number ? restoredScroll : 0;
            if (cut == number) restoredScroll = 0;
            holder.scroll.scrollTo(0, 0);
            OneShotPreDrawListener.add(holder.itemView, holder::restoreScroll);
            holder.rotation = roll.frame(cut).rotation;
            holder.imageName = roll.frame(cut).imageKey();
            holder.photo.restore(1, .5f, .5f);
            if (restoredPhoto != null && cut == number) {
                holder.photo.restore(restoredPhoto.getFloat("photoZoom", 1), restoredPhoto.getFloat("photoX", .5f), restoredPhoto.getFloat("photoY", .5f));
                restoredPhoto = null;
            }
            holder.preview.load(new FilmRollStore(FilmFrameDetailActivity.this), roll.frame(cut),
                    !roll.frame(cut).correctionEnabled || clearingCut == cut, FilmFrameDetailActivity.this::updateCorrectionCategory);
            applyPhotoOnly(holder);
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
            if (payloads.isEmpty() || !(holder instanceof FeedHolder)) onBindViewHolder(holder, position);
            else { bindRecords((FeedHolder) holder, numbers.get(position)); applyPhotoOnly((FeedHolder) holder); }
        }
        private void bindRecords(FeedHolder holder, int cut) {
            FilmFrameRecord frame = roll.frame(cut);
            holder.cut = cut;
            holder.location.bind(FilmFrameDetailActivity.this, rollId, frame, !photoOnly && cut == number && holder.itemView.isAttachedToWindow());
            View rotate = holder.itemView.findViewById(R.id.noteRotate);
            rotate.setEnabled(rotationSaving.state.getValue() != FilmFrameRotationViewModel.State.SAVING);
            rotate.setOnClickListener(v -> rotationSaving.rotate(rollId, cut, frame.captureId));
            FilmRollStore store = new FilmRollStore(FilmFrameDetailActivity.this);
            if (holder.rotation >= 0 && (holder.rotation != frame.rotation || !holder.imageName.equals(frame.imageKey()))) {
                boolean reuseOriginal = holder.rotation == frame.rotation && holder.imageName.startsWith(frame.photo + ":") && !frame.correctionEnabled;
                holder.rotation = frame.rotation;
                holder.imageName = frame.imageKey();
                if (!reuseOriginal || !holder.preview.keepOriginal())
                    holder.preview.load(store, frame, !frame.correctionEnabled || clearingCut == cut, FilmFrameDetailActivity.this::updateCorrectionCategory);
            }
            holder.photo.setContentDescription(getString(R.string.note_open_frame, cut));
            holder.photo.setOnClickListener(v -> setPhotoOnly(!photoOnly));
            holder.photo.setPhotoOnlyListener(new FilmComparisonImageView.PhotoOnlyListener() {
                @Override public boolean isActive() { return photoOnly; }
                @Override public void show() { if (holder.cut == number) setPhotoOnly(true); }
            });
            holder.set(R.id.noteFeedTitle, roll.title());
            ViewCompat.setAccessibilityHeading(holder.itemView.findViewById(R.id.noteFeedTitle), true);
            recordEditor.bind(holder.itemView.findViewById(R.id.noteRecords), roll, frame);
            TextView comparison = holder.itemView.findViewById(R.id.noteScanComparison);
            comparison.setText(frame.scanPhoto.isEmpty() ? R.string.scan_link : R.string.scan_compare);
            comparison.setOnClickListener(view -> startActivity(FilmScanComparisonActivity.intent(
                    FilmFrameDetailActivity.this, rollId, frame, frame.scanPhoto.isEmpty())));
        }
        @Override public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
            if (holder instanceof FeedHolder) ((FeedHolder) holder).location.setActive(!photoOnly && ((FeedHolder) holder).cut == number);
        }
        @Override public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
            if (holder instanceof FeedHolder) ((FeedHolder) holder).location.setActive(false);
        }
        @Override public void onViewRecycled(@NonNull RecyclerView.ViewHolder page) {
            if (!(page instanceof FeedHolder)) return;
            FeedHolder holder = (FeedHolder) page;
            holder.location.setActive(false); holder.cut = -1;
            if (recordEditor != null) recordEditor.detach(holder.itemView.findViewById(R.id.noteRecords));
            if (deletion != null) deletion.clear(holder.itemView.findViewById(R.id.frameDelete));
            holder.preview.clear(); holder.pendingScroll = -1;
        }
    }

    private static final class FeedHolder extends RecyclerView.ViewHolder {
        final NestedScrollView scroll;
        final FilmComparisonImageView photo;
        final FilmFramePreview preview;
        final FilmNotePhotoLayout area;
        final FilmNoteLocationView location;
        int cut = -1;
        int pendingScroll = -1, rotation = -1;
        int noteScroll;
        boolean photoOnly;
        String imageName = "";
        FeedHolder(View view) {
            super(view); scroll = (NestedScrollView) view;
            photo = view.findViewById(R.id.noteDetailPhoto);
            preview = new FilmFramePreview(photo);
            area = view.findViewById(R.id.notePhotoArea);
            location = view.findViewById(R.id.noteLocationView);
        }
        void restoreScroll() {
            if (pendingScroll < 0) return;
            int restore = pendingScroll; pendingScroll = -1;
            scroll.scrollTo(0, restore);
        }
        void set(int id, String value) { ((TextView) itemView.findViewById(id)).setText(value); }
    }

    private void selectCorrection(int cut, boolean enabled) {
        selectCorrection(cut,enabled,FilmColorCorrection.E100D);
    }
    private void selectCorrection(int cut,String key) {
        FilmFrameRecord frame=roll==null?null:roll.frame(cut);
        boolean enabled=!FilmCorrectionCategory.ORIGINAL.equals(key);
        String profile=frame!=null && frame.correctionEnabled && FilmCorrectionProfiles.key(frame.correctionProfile).equals(key)
                && !FilmCorrectionProfiles.upgradeOnReselect(frame.correctionProfile)
                ?frame.correctionProfile:FilmCorrectionProfiles.latest(key);
        selectCorrection(cut,enabled,profile);
    }
    private void selectCorrection(int cut,boolean enabled,String profile) {
        if (roll == null || correctionSaving.state.getValue() != FilmFrameCorrectionViewModel.State.IDLE) return;
        FilmFrameRecord frame = roll.frame(cut);
        if (frame == null) return;
        if (frame.correctionEnabled == enabled && (!enabled || profile.equals(frame.correctionProfile))) {
            for (FeedHolder holder : adapter.holders) if (holder.cut == cut) holder.preview.show(!enabled);
            updateCorrectionCategory(); return;
        }
        if (!enabled) {
            clearingCut = cut;
            for (FeedHolder holder : adapter.holders) if (holder.cut == cut) holder.preview.show(true);
        }
        correctionSaving.select(rollId, cut, frame.captureId, frame.correctionRevision, enabled,profile);
    }

    private void updateCorrectionCategory() {
        updateGalleryButton();
        if (correctionCategory == null || correctionSaving == null || roll == null) return;
        FilmFrameRecord frame = roll.frame(number);
        if (frame == null) return;
        boolean enabled = frame.correctionEnabled && FilmCorrectionProfiles.supported(frame.correctionProfile);
        boolean busy = correctionSaving.state.getValue() == FilmFrameCorrectionViewModel.State.SAVING;
        boolean previewReady = false;
        for (FeedHolder holder : adapter.holders) if (holder.cut == number && holder.preview.ready()) previewReady = true;
        int cut = number;
        correctionCategory.bind(enabled && clearingCut != cut ? FilmCorrectionProfiles.key(frame.correctionProfile) : FilmCorrectionCategory.ORIGINAL,
                previewReady && !busy && !showingNextRoll && pager.getScrollState() == RecyclerView.SCROLL_STATE_IDLE, busy,
                key -> true, key -> selectCorrection(cut,key));
    }

    private void updateGalleryButton() {
        if (gallerySaving == null || pager == null) return;
        boolean busy = gallerySaving.busy();
        boolean ready = roll != null && roll.frame(number) != null && !showingNextRoll
                && pager.getScrollState() == RecyclerView.SCROLL_STATE_IDLE
                && correctionSaving != null && correctionSaving.state.getValue() != FilmFrameCorrectionViewModel.State.SAVING
                && rotationSaving != null && rotationSaving.state.getValue() != FilmFrameRotationViewModel.State.SAVING;
        View button = findViewById(R.id.noteDetailSave);
        button.setEnabled(ready && !busy);
        button.setAlpha(ready && !busy ? 1f : .45f);
        androidx.core.view.ViewCompat.setStateDescription(button, busy ? getString(R.string.save_progress) : null);
    }

    private void saveToGallery() {
        if (gallerySaving == null || gallerySaving.busy() || roll == null || showingNextRoll) return;
        FilmFrameRecord frame = roll.frame(number);
        if (frame == null) return;
        boolean needsPermission = android.os.Build.VERSION.SDK_INT < 29
                && androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED;
        gallerySaving.save(frame, needsPermission);
        if (needsPermission && gallerySaving.state.getValue() == FilmNoteGalleryViewModel.State.PERMISSION)
            galleryPermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private void closeAfterChanges() {
        if (recordEditor != null && recordEditor.cancel()) return;
        if (editsSaving()) {
            closeAfterEdits = true; return;
        }
        finish();
    }

    private boolean editsSaving() {
        return correctionSaving != null && correctionSaving.state.getValue() == FilmFrameCorrectionViewModel.State.SAVING
                || rotationSaving != null && rotationSaving.state.getValue() == FilmFrameRotationViewModel.State.SAVING;
    }
    private void finishPendingClose() { if (closeAfterEdits && !editsSaving()) finish(); }

    @Override protected void onSaveInstanceState(@NonNull Bundle state) {
        View current = layout.findViewByPosition(numbers.indexOf(number));
        if (recordEditor != null) recordEditor.saveState(state);
        state.putInt("number", number);
        state.putInt("clearingCut", clearingCut);
        state.putBoolean("closeAfterEdits", closeAfterEdits);
        int offset = current instanceof NestedScrollView ? current.getScrollY() : restoredScroll;
        if (current != null) {
            RecyclerView.ViewHolder holder = pager.getChildViewHolder(current);
            if (holder instanceof FeedHolder) {
                FeedHolder feed = (FeedHolder) holder;
                if (photoOnly) offset = feed.noteScroll;
                else if (feed.pendingScroll > 0) offset = feed.pendingScroll;
                state.putFloat("photoZoom", feed.photo.zoom());
                state.putFloat("photoX", feed.photo.centerX());
                state.putFloat("photoY", feed.photo.centerY());
            }
        }
        state.putInt("scroll", offset);
        state.putBoolean("photoOnly", photoOnly);
        state.putBoolean("showingNextRoll", showingNextRoll);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if (correctionCategory != null) correctionCategory.dismiss();
        for (FeedHolder holder : adapter.holders) { holder.location.clearMap(); holder.preview.clear(); }
        super.onDestroy();
    }
    @Override public void onLowMemory() {
        super.onLowMemory();
        for (FeedHolder holder : adapter.holders) holder.location.lowMemory();
    }
}
