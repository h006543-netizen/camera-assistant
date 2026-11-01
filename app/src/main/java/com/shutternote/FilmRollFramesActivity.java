package com.shutternote;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.lifecycle.ViewModelProvider;
import java.util.ArrayList;
import java.util.Locale;

/** Photo-first two-row strip. A column always represents consecutive physical cut numbers. */
public class FilmRollFramesActivity extends AppCompatActivity {
    static final String EXTRA_OPEN_DETAIL = "openDetail";
    private static final int SCRUB_STEPS_PER_PAIR = 1000;
    private FilmRollRecord roll;
    private String rollId;
    private RecyclerView pager;
    private LinearLayoutManager layout;
    private FilmFrameScrubber scrubber;
    private FilmFrameDeletion deletion;
    private FilmRollExportViewModel export;
    private final PairAdapter adapter = new PairAdapter();
    private int pair, pendingNumber, pairWidth;
    private boolean initialized, scrubbing;

    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> launchExportPicker());

    private final ActivityResultLauncher<Intent> detail = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent viewed = result.getData();
                if (result.getResultCode() != RESULT_OK || viewed == null
                        || !rollId.equals(viewed.getStringExtra(FilmRollActivity.EXTRA_ROLL_ID))) return;
                int cut = viewed.getIntExtra(FilmFrameCapture.NUMBER, 0);
                if (cut > 0 && readRoll()) moveTo((cut - 1) / 2);
            });

    private final ActivityResultLauncher<String> exportFile = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"), uri -> {
                if (uri == null) export.pickerCancelled();
                else export.export(rollId, uri);
            });

    private final ActivityResultLauncher<Intent> capture = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                int savedNumber = pendingNumber;
                pendingNumber = 0;
                if (result.getResultCode() == RESULT_OK) {
                    Intent savedCut = result.getData();
                    if (savedCut != null && FilmFrameCapture.isNote(savedCut)) {
                        if (!rollId.equals(savedCut.getStringExtra(FilmFrameCapture.ROLL))) return;
                        savedNumber = savedCut.getIntExtra(FilmFrameCapture.NUMBER, 0);
                    }
                    if (readRoll()) {
                        moveTo((nextFrame() - 1) / 2);
                        FilmFrameRecord saved = roll.frame(savedNumber);
                        if (saved != null && new FilmRollStore(this).photo(saved).isFile()) open(savedNumber);
                    }
                }
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);setContentView(R.layout.activity_film_roll_frames);
        SystemBarInsets.applyMainScreen(this, findViewById(R.id.framesStatusSpace), findViewById(R.id.framesNavigationSpace));
        rollId = getIntent().getStringExtra(FilmRollActivity.EXTRA_ROLL_ID);
        export = new ViewModelProvider(this).get(FilmRollExportViewModel.class);
        if (!readRoll()) return;
        int initialCut = getIntent().getIntExtra(FilmFrameCapture.NUMBER, nextFrame());
        pair = state == null ? (initialCut - 1) / 2 : state.getInt("pair");
        pendingNumber = state == null ? 0 : state.getInt("pending");
        findViewById(R.id.framesBack).setOnClickListener(v -> finish());
        findViewById(R.id.framesInfo).setOnClickListener(v -> showRollInfo());
        findViewById(R.id.framesExport).setOnClickListener(v -> startExport());
        findViewById(R.id.framesFirst).setOnClickListener(v -> {
            if (export.busy()) return;
            scrubber.cancelTouch();
            pager.stopScroll();
            deletion.dismiss();
            // Travel from the actual current offset; do not jump or change the next capture.
            settleTo(0, 420);
        });
        androidx.appcompat.widget.TooltipCompat.setTooltipText(findViewById(R.id.framesFirst), getString(R.string.note_first_page));
        androidx.appcompat.widget.TooltipCompat.setTooltipText(findViewById(R.id.framesExport), getString(R.string.note_export_zip));
        findViewById(R.id.framesExportCancel).setOnClickListener(v -> {
            export.cancel(); findViewById(R.id.framesExportCancel).setEnabled(false);
        });
        pager = findViewById(R.id.framesPager);
        layout = new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false);
        pager.setLayoutManager(layout);pager.setAdapter(adapter);pager.setItemAnimator(null);
        LinearSnapHelper snap = new LinearSnapHelper() {
            @Override public int[] calculateDistanceToFinalSnap(@NonNull RecyclerView.LayoutManager manager, @NonNull View target) {
                // Start alignment leaves the next column visible instead of centering this column.
                return new int[]{manager.getDecoratedLeft(target) - manager.getPaddingLeft(), 0};
            }
            @Override public View findSnapView(RecyclerView.LayoutManager manager) {
                if (scrubbing) return null;
                View nearest = null;int distance = Integer.MAX_VALUE;
                for (int i = 0; i < manager.getChildCount(); i++) {
                    View candidate = manager.getChildAt(i);
                    int value = Math.abs(manager.getDecoratedLeft(candidate) - manager.getPaddingLeft());
                    if (value < distance) { distance = value; nearest = candidate; }
                }
                return nearest;
            }
        };
        snap.attachToRecyclerView(pager);
        scrubber = findViewById(R.id.framesScrubber);
        scrubber.setTouchListener(new FilmFrameScrubber.TouchListener() {
            @Override public float start() {
                scrubbing = true;
                pager.stopScroll();
                return pairWidth>0 ? currentOffset()/(float)pairWidth*SCRUB_STEPS_PER_PAIR : scrubber.getProgress();
            }
            @Override public void move(float progress) {
                float position=progress/SCRUB_STEPS_PER_PAIR;
                pager.scrollBy(Math.round(position*pairWidth)-currentOffset(),0);
                describePosition(Math.round(position));
            }
            @Override public void finish(boolean moved) {
                scrubbing = false;
                if (moved) settleTo(Math.round(scrubber.getProgress()/(float)SCRUB_STEPS_PER_PAIR));
            }
        });
        scrubber.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                // Only inherited keyboard/accessibility actions use absolute progress.
                if (fromUser && !scrubbing) settleTo(Math.round(progress/(float)SCRUB_STEPS_PER_PAIR));
            }
        });
        pager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                if (dx != 0 && deletion != null) deletion.dismiss();
                if (scrubbing) return;
                if (pairWidth <= 0) return;
                float position = currentOffset() / (float) pairWidth;
                describePosition(Math.round(position));
                scrubber.setProgress(Math.round(position * SCRUB_STEPS_PER_PAIR));
            }
        });
        pager.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            // The previous photograph itself occupies 10%; its trailing gap is additional.
            int leading = Math.round((r-l) * .10f) + dp(12);
            int width = Math.max(dp(96), r-l-leading-dp(24));
            if (pairWidth != width) {
                pairWidth = width;
                // Enough trailing space to start-align the last pair as well.
                pager.setPadding(leading,0,dp(24),0);
                adapter.notifyDataSetChanged();
                pager.post(() -> moveTo(pair));
            }
        });
        deletion = new FilmFrameDeletion(this, cut -> { if (readRoll()) refresh(); });
        initialized = true;refresh();
        export.state.observe(this, status -> {
            updateExportProgress(); adapter.notifyDataSetChanged();
            int message = status==FilmRollExportViewModel.State.PARTIAL ? R.string.note_export_partial
                    : status==FilmRollExportViewModel.State.FAILED ? R.string.note_export_failed
                    : 0;
            if (message != 0) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show(); export.acknowledge();
            } else if (status==FilmRollExportViewModel.State.COMPLETE || status==FilmRollExportViewModel.State.CANCELLED) {
                export.acknowledge();
            }
        });
        export.completed.observe(this, ignored -> updateExportProgress());
        if (state == null && getIntent().getBooleanExtra(EXTRA_OPEN_DETAIL, false))
            openDetail(initialCut, getIntent().getBooleanExtra("photoOnly", false));
    }

    @Override protected void onResume() {
        super.onResume();if (!initialized || !readRoll()) return;
        if (pendingNumber > 0 && roll.frame(pendingNumber) != null) { pair = (nextFrame()-1)/2;pendingNumber=0; }
        refresh();
    }
    @Override protected void onSaveInstanceState(@NonNull Bundle state) {
        if (scrubber!=null) scrubber.cancelTouch();
        state.putInt("pair", pair);state.putInt("pending", pendingNumber);super.onSaveInstanceState(state);
    }
    @Override protected void onPause() {
        if (scrubber!=null) scrubber.cancelTouch();
        super.onPause();
    }
    private boolean readRoll() {
        try { roll = new FilmRollStore(this).find(rollId); } catch (org.json.JSONException error) { roll = null; }
        if (roll == null) { Toast.makeText(this,R.string.roll_error_load,Toast.LENGTH_LONG).show();finish();return false; }
        return true;
    }
    private int nextFrame() {
        java.util.HashSet<Integer> recorded = new java.util.HashSet<>();for (FilmFrameRecord frame : roll.frames) recorded.add(frame.number);
        return FilmFrameNavigation.nextFrame(roll.plannedFrames, recorded);
    }
    private void startExport() {
        if (export.busy()) return;
        if (roll.frames.isEmpty()) { Toast.makeText(this,R.string.note_export_empty,Toast.LENGTH_LONG).show(); return; }
        deletion.dismiss();
        export.pickingStarted();
        android.content.SharedPreferences preferences=getSharedPreferences("export_notifications", MODE_PRIVATE);
        if (android.os.Build.VERSION.SDK_INT >= 33
                && androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED
                && !preferences.getBoolean("permissionAsked", false)) {
            preferences.edit().putBoolean("permissionAsked", true).apply();
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        launchExportPicker();
    }
    private void launchExportPicker() {
        if (!export.picking()) return;
        String stamp=new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT).format(new java.util.Date());
        try { exportFile.launch(FilmRollPhotoArchive.folderName(roll.title())+"_"+stamp+".zip"); }
        catch (RuntimeException error) {
            export.pickerCancelled(); Toast.makeText(this,R.string.note_export_failed,Toast.LENGTH_LONG).show();
        }
    }
    private void updateExportProgress() {
        boolean busy=export.busy(), copying=export.state.getValue()==FilmRollExportViewModel.State.COPYING;
        findViewById(R.id.framesExport).setEnabled(!busy);
        findViewById(R.id.framesInfo).setEnabled(!busy);
        updateFirstPageButton();
        findViewById(R.id.framesExportProgress).setVisibility(busy && !export.picking()?View.VISIBLE:View.GONE);
        int done=export.completed.getValue()==null?0:export.completed.getValue();
        ((TextView)findViewById(R.id.framesExportStatus)).setText(copying?getString(R.string.note_export_copying)
                :getString(R.string.note_export_progress,done,export.total));
        ProgressBar progress=findViewById(R.id.framesExportBar);
        progress.setIndeterminate(copying || export.total==0);
        progress.setMax(Math.max(1,export.total)); progress.setProgress(done);
        if (!busy) findViewById(R.id.framesExportCancel).setEnabled(true);
    }
    private void refresh() {
        ((TextView)findViewById(R.id.framesTitle)).setText(roll.title());
        scrubber.setMax((FilmFrameNavigation.pairCount(roll.plannedFrames)-1) * SCRUB_STEPS_PER_PAIR);
        scrubber.setKeyProgressIncrement(SCRUB_STEPS_PER_PAIR);
        scrubber.setEnabled(scrubber.getMax()>0);
        adapter.notifyDataSetChanged();moveTo(pair);
    }
    private void moveTo(int requested) {
        setPosition(requested);layout.scrollToPositionWithOffset(pair,0);
    }
    private void setPosition(int requested) {
        describePosition(requested);
        scrubber.setProgress(pair * SCRUB_STEPS_PER_PAIR);
    }
    private int currentOffset() {
        int first = layout.findFirstVisibleItemPosition();
        View child = layout.findViewByPosition(first);
        if (child == null || first == RecyclerView.NO_POSITION) return pair * pairWidth;
        return first * pairWidth + layout.getPaddingLeft() - layout.getDecoratedLeft(child);
    }
    private void settleTo(int requested) {
        settleTo(requested, 220);
    }
    private void settleTo(int requested, int duration) {
        int target = FilmFrameNavigation.clampPair(requested, roll.plannedFrames);
        int distance = target * pairWidth - currentOffset();
        if (distance == 0 || pairWidth <= 0) {
            setPosition(target);
            return;
        }
        // A short eased slide instead of instantly replacing the visible column.
        pager.smoothScrollBy(distance, 0, new android.view.animation.DecelerateInterpolator(), duration);
    }
    private void describePosition(int requested) {
        pair = FilmFrameNavigation.clampPair(requested,roll.plannedFrames);
        int first=FilmFrameNavigation.firstFrame(pair,roll.plannedFrames),last=FilmFrameNavigation.lastFrame(pair,roll.plannedFrames);
        String label=first==last?getString(R.string.note_single_position,first,roll.plannedFrames)
                :getString(R.string.note_position,first,last,roll.plannedFrames);
        scrubber.setContentDescription(getString(R.string.note_scrub_description)+", "+label);
        updateFirstPageButton();
    }
    private void updateFirstPageButton() {
        View button = findViewById(R.id.framesFirst);
        boolean enabled = pair > 0 && !export.busy();
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : .35f);
    }
    private void showRollInfo() {
        String info = roll.filmDisplayName()+(roll.cameraName.isEmpty()?"":"\n"+roll.cameraName)
                +"\n\n"+getString(R.string.note_record_count,roll.frames.size(),roll.plannedFrames)
                +(roll.memo.isEmpty()?"":"\n\n"+roll.memo);
        new AlertDialog.Builder(this).setTitle(roll.title()).setMessage(info)
                .setPositiveButton(R.string.roll_edit,(d,w)->startActivity(new Intent(this,FilmRollActivity.class)
                        .putExtra(FilmRollActivity.EXTRA_ROLL_ID,rollId))).setNegativeButton(R.string.cancel,null).show();
    }
    private void open(int number) {
        if (roll.frame(number) != null) openDetail(number, false);
        else {
            pendingNumber=number;
            capture.launch(new Intent(this,ExposureCameraActivity.class).putExtra(FilmFrameCapture.ROLL,rollId)
                    .putExtra(FilmFrameCapture.NUMBER,number));
        }
    }
    private void openDetail(int number, boolean photoOnly) {
        detail.launch(new Intent(this,FilmFrameDetailActivity.class)
                .putExtra(FilmRollActivity.EXTRA_ROLL_ID,rollId)
                .putExtra(FilmFrameCapture.NUMBER,number).putExtra("photoOnly",photoOnly));
    }
    private int dp(int number) {return Math.round(number*getResources().getDisplayMetrics().density);}

    private final class PairAdapter extends RecyclerView.Adapter<PairHolder> {
        @NonNull @Override public PairHolder onCreateViewHolder(@NonNull ViewGroup parent,int type) {
            return new PairHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_frame_pair,parent,false));
        }
        @Override public void onBindViewHolder(@NonNull PairHolder holder,int position) {
            holder.itemView.getLayoutParams().width=pairWidth>0?pairWidth:dp(300);
            bind(holder.top,position*2+1);bind(holder.bottom,position*2+2);
        }
        @Override public int getItemCount() {return roll==null?0:FilmFrameNavigation.pairCount(roll.plannedFrames);}
        @Override public void onViewRecycled(@NonNull PairHolder holder) {
            deletion.clear(holder.top.findViewById(R.id.frameDelete));
            deletion.clear(holder.bottom.findViewById(R.id.frameDelete));
            FilmFrameImages.clear(holder.top.findViewById(R.id.framePhoto));
            FilmFrameImages.clear(holder.bottom.findViewById(R.id.framePhoto));
        }
        private void bind(View cell,int number) {
            ImageView image=cell.findViewById(R.id.framePhoto);
            deletion.clear(cell.findViewById(R.id.frameDelete));
            FilmFrameImages.clear(image);
            if(number>roll.plannedFrames){cell.setVisibility(View.INVISIBLE);cell.setOnClickListener(null);return;}
            cell.setVisibility(View.VISIBLE);
            FilmFrameRecord frame=roll.frame(number);
            boolean available=export==null || !export.busy();
            cell.setEnabled(available);
            cell.setOnLongClickListener(null); cell.setLongClickable(false);
            androidx.core.view.ViewCompat.removeAccessibilityAction(cell,
                    androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK.getId());
            View camera=cell.findViewById(R.id.frameCamera);
            camera.setVisibility(frame==null?View.VISIBLE:View.GONE);
            camera.setAlpha(number==nextFrame()?1f:0.55f);
            ((TextView)cell.findViewById(R.id.frameNumber)).setText(String.format(Locale.getDefault(),"%02d",number));
            cell.setContentDescription(getString(frame==null?R.string.note_capture_frame:R.string.note_open_frame,number));
            cell.setOnClickListener(v->{ if (export==null || !export.busy()) open(number); });
            if(frame!=null){
                if (available) deletion.attach(cell,cell.findViewById(R.id.frameDelete),rollId,frame);
                FilmFrameImages.load(image,new FilmRollStore(FilmRollFramesActivity.this),frame,720);
            }
        }
    }
    private static final class PairHolder extends RecyclerView.ViewHolder {
        final View top,bottom;
        PairHolder(View view){super(view);top=view.findViewById(R.id.frameTop);bottom=view.findViewById(R.id.frameBottom);}
    }
}
