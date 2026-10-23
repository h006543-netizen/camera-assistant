package com.shutternote;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class FilmResultActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_PATH = "film_result_image_path";
    public static final String EXTRA_EXPOSURE_MULTIPLIER = "film_result_exposure_multiplier";
    public static final String EXTRA_ISO = "film_result_iso";
    public static final String EXTRA_APERTURE = "film_result_aperture";
    public static final String EXTRA_SHUTTER = "film_result_shutter";
    public static final String EXTRA_FIELD_OF_VIEW = "film_result_field_of_view";

    private static final int MAX_PREVIEW_EDGE = 1440;
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final java.util.concurrent.ConcurrentHashMap<String, SaveJob> SAVE_JOBS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final class SaveJob {
        volatile boolean complete;
        volatile boolean succeeded;
        volatile boolean partial;
    }
    private static final String GALLERY_DIRECTORY = "Camera";

    private final ExecutorService processingExecutor =
            Executors.newSingleThreadExecutor();
    private final AtomicInteger processingGeneration = new AtomicInteger();

    private ImageView resultImageView;
    private ProgressBar progressView;
    private TextView statusView;
    private TextView saveButton;
    private FilmCorrectionCategory correctionCategory;

    private Bitmap originalBitmap;
    private Bitmap correctedBitmap;
    private boolean correctionSelected;
    private String correctionProfile=FilmColorCorrection.E100D;
    private boolean viewingOriginal;
    private Bitmap displayedBitmap;
    private String imagePath;
    private String capturedIso = ExposureSettingsStore.DEFAULT_ISO;
    private String capturedAperture = ExposureSettingsStore.DEFAULT_APERTURE;
    private String capturedShutter = ExposureSettingsStore.DEFAULT_SHUTTER;
    private String capturedFieldOfView = ExposureSettingsStore.FIELD_OF_VIEW_DEFAULT;
    private double exposureMultiplier = 1.0;
    private boolean isSaving;
    private boolean saveCompleted;
    private boolean previewProcessing;
    private boolean screenStopped;
    private FilmResultLocationViewModel noteLocation;
    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private final ActivityResultLauncher<String> storagePermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            saveCurrentResult();
                        } else {
                            Toast.makeText(
                                    this,
                                    getString(R.string.storage_permission),
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_film_result);

        SystemBarInsets.apply(
                this,
                findViewById(R.id.filmResultTopControls),
                findViewById(R.id.filmResultBottomControls)
        );

        resultImageView = findViewById(R.id.ivFilmResult);
        progressView = findViewById(R.id.progressFilmProcessing);
        statusView = findViewById(R.id.tvFilmResultStatus);
        saveButton = findViewById(R.id.btnFilmResultSave);
        correctionSelected = savedInstanceState != null && savedInstanceState.getBoolean("correctionSelected", false);
        if(savedInstanceState!=null && FilmFrameCapture.isNote(getIntent())) {
            String saved=savedInstanceState.getString("correctionProfile",FilmColorCorrection.E100D);
            if(FilmCorrectionProfiles.supported(saved))correctionProfile=saved;
        }
        viewingOriginal = savedInstanceState != null && savedInstanceState.getBoolean("viewingOriginal", false);
        if (savedInstanceState == null && !FilmFrameCapture.isNote(getIntent()))
            correctionSelected = FilmGalleryRecipeStore.corrected(this, getIntent().getStringExtra(EXTRA_IMAGE_PATH));
        correctionCategory = new FilmCorrectionCategory(findViewById(R.id.noteCorrectionCategory));

        findViewById(R.id.btnFilmResultBack).setOnClickListener(view -> handleBack());
        saveButton.setOnClickListener(view -> requestSave());
        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        handleBack();
                    }
                }
        );

        if (FilmFrameCapture.isNote(getIntent())) {
            noteLocation = new ViewModelProvider(this).get(FilmResultLocationViewModel.class);
            boolean restoredComplete = savedInstanceState != null && savedInstanceState.getBoolean("noteLocationComplete");
            FilmFrameLocation initial = FilmFrameCapture.location(getIntent());
            if (restoredComplete) {
                double latitude = savedInstanceState.getDouble("noteLatitude", Double.NaN);
                double longitude = savedInstanceState.getDouble("noteLongitude", Double.NaN);
                initial = FilmFrameLocation.valid(latitude, longitude)
                        ? new FilmFrameLocation(latitude, longitude, savedInstanceState.getDouble("noteAccuracy", -1)) : null;
            }
            noteLocation.configure(getIntent().getStringExtra(FilmFrameCapture.ROLL),
                    getIntent().getIntExtra(FilmFrameCapture.NUMBER, 0), getIntent().getStringExtra(EXTRA_IMAGE_PATH),
                    FilmFrameCapture.photoReadyNanos(getIntent()), initial, restoredComplete);
        }
        loadCapturedImage();
    }

    private void loadCapturedImage() {
        imagePath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
        exposureMultiplier = getIntent().getDoubleExtra(
                EXTRA_EXPOSURE_MULTIPLIER,
                1.0
        );
        capturedIso = ExposureSettingsStore.normalizeIso(
                getIntent().getStringExtra(EXTRA_ISO)
        );
        capturedAperture = ExposureSettingsStore.normalizeAperture(
                getIntent().getStringExtra(EXTRA_APERTURE)
        );
        capturedShutter = ExposureSettingsStore.normalizeShutter(
                getIntent().getStringExtra(EXTRA_SHUTTER)
        );
        capturedFieldOfView = ExposureSettingsStore.normalizeFieldOfView(
                getIntent().getStringExtra(EXTRA_FIELD_OF_VIEW)
        );

        if (imagePath == null || imagePath.trim().isEmpty()) {
            showError(getString(R.string.photo_path_missing));
            return;
        }

        File imageFile = new File(imagePath);
        if (!imageFile.isFile() || imageFile.length() <= 0L) {
            showError(getString(R.string.photo_unavailable));
            return;
        }

        setProcessing(true);
        int generation = processingGeneration.incrementAndGet();

        processingExecutor.execute(() -> {
            Bitmap working = null;
            try {
                Bitmap decoded = decodeScaledBitmap(
                        imageFile,
                        getPreviewMaximumEdge()
                );
                working = decoded;
                Bitmap rotated = rotateFromExif(decoded, imageFile);
                if (rotated != decoded) {
                    decoded.recycle();
                }
                working = rotated;

                Bitmap exposureAdjusted =
                        FilmProcessor.applyExposure(rotated, exposureMultiplier,
                                () -> generation != processingGeneration.get());
                if (exposureAdjusted != rotated) {
                    rotated.recycle();
                }
                working = exposureAdjusted;

                Bitmap correction = null;
                try { if (correctionSelected) {
                    if (!FilmFrameCapture.isNote(getIntent()) && isCaptureSaved()) {
                        if (!FilmGalleryRecipeStore.select(getApplicationContext(), imagePath, true)) throw new IOException("Correction missing");
                        correction = decodeScaledBitmap(FilmGalleryRecipeStore.correction(getApplicationContext(), imagePath), getPreviewMaximumEdge());
                    } else correction = FilmColorCorrection.apply(getApplicationContext(), exposureAdjusted,correctionProfile,
                            () -> generation != processingGeneration.get());
                } }
                catch (java.util.concurrent.CancellationException cancelled) { throw cancelled; }
                catch (Exception | OutOfMemoryError ignored) { /* Keep the original available. */ }
                Bitmap corrected = correction;

                runOnUiThread(() -> {
                    if (isFinishing() || generation != processingGeneration.get()) {
                        exposureAdjusted.recycle();
                        recycleIfNeeded(corrected);
                        return;
                    }
                    originalBitmap = exposureAdjusted;
                    correctedBitmap = corrected;
                    boolean failedCorrection = correctionSelected && corrected == null;
                    if (failedCorrection) correctionSelected = false;
                    displayBitmap(correctionSelected && !viewingOriginal ? correctedBitmap : originalBitmap);
                    setProcessing(false);
                    if (failedCorrection) showError(getString(R.string.correction_render_error));
                });
                working = null; // Ownership transferred to the main-thread callback.
            } catch (java.util.concurrent.CancellationException ignored) {
                // Backgrounding or destruction invalidated this preview.
            } catch (Exception | OutOfMemoryError error) {
                runOnUiThread(() -> {
                    if (!isDestroyed() && generation == processingGeneration.get())
                        showError(getString(R.string.photo_load_error));
                });
            } finally {
                recycleIfNeeded(working);
            }
        });
    }

    private void displayBitmap(Bitmap bitmap) {
        Bitmap previous = displayedBitmap;
        displayedBitmap = bitmap;
        resultImageView.setImageBitmap(bitmap);

        if (previous != null
                && previous != originalBitmap
                && previous != correctedBitmap
                && previous != bitmap
                && !previous.isRecycled()) {
            previous.recycle();
        }
    }

    private Bitmap decodeScaledBitmap(File file, int maximumEdge) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("이미지 크기를 읽을 수 없습니다.");
        }

        Runtime runtime = Runtime.getRuntime();
        long available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
        int sampleSize = ImageMemoryBudget.sampleSize(bounds.outWidth, bounds.outHeight, available,
                FilmFrameCapture.isNote(getIntent()) && maximumEdge!=Integer.MAX_VALUE?80:16);
        int largestEdge = Math.max(bounds.outWidth, bounds.outHeight);
        while (largestEdge / (sampleSize * 2) >= maximumEdge) {
            sampleSize *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);

        if (bitmap == null) {
            throw new IOException("이미지를 디코딩할 수 없습니다.");
        }

        int decodedLargestEdge = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (decodedLargestEdge > maximumEdge) {
            float scale = maximumEdge / (float) decodedLargestEdge;
            int scaledWidth = Math.max(1, Math.round(bitmap.getWidth() * scale));
            int scaledHeight = Math.max(1, Math.round(bitmap.getHeight() * scale));
            Bitmap scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    scaledWidth,
                    scaledHeight,
                    true
            );
            if (scaled != bitmap) {
                bitmap.recycle();
            }
            bitmap = scaled;
        }
        return bitmap;
    }

    private int getPreviewMaximumEdge() {
        int screenLargestEdge = Math.max(
                getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels
        );
        return Math.max(1, Math.min(MAX_PREVIEW_EDGE, screenLargestEdge));
    }

    private Bitmap rotateFromExif(Bitmap source, File file) throws IOException {
        ExifInterface exif = new ExifInterface(file.getAbsolutePath());
        int orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
        );

        float rotation;
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                rotation = 90f;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                rotation = 180f;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                rotation = 270f;
                break;
            default:
                return source;
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        return Bitmap.createBitmap(
                source,
                0,
                0,
                source.getWidth(),
                source.getHeight(),
                matrix,
                true
        );
    }

    private void setProcessing(boolean processing) {
        previewProcessing = processing;
        progressView.setVisibility(processing ? View.VISIBLE : View.GONE);
        saveButton.setEnabled(!processing && !isSaving && originalBitmap != null && !saveCompleted);
        updateCorrectionControls();
        if (processing) {
            statusView.setVisibility(View.GONE);
        }
    }

    private void selectCorrection(boolean selected) {
        if (previewProcessing || isSaving || originalBitmap == null) return;
        if (saveCompleted && !FilmFrameCapture.isNote(getIntent()) && correctionSelected != selected) { changeSavedCorrection(selected); return; }
        viewingOriginal = !selected;
        correctionSelected = selected;
        if (!selected || correctedBitmap != null) {
            displayBitmap(selected ? correctedBitmap : originalBitmap);
            updateCorrectionControls(); return;
        }
        renderSelectedCorrection();
    }

    private void selectNoteCorrection(String key) {
        if(FilmCorrectionCategory.ORIGINAL.equals(key)) { selectCorrection(false);return; }
        if(previewProcessing || isSaving || originalBitmap==null)return;
        if(correctedBitmap != null && FilmCorrectionProfiles.key(correctionProfile).equals(key)) {selectCorrection(true);return;}
        String profile=FilmCorrectionProfiles.latest(key);
        if(profile.isEmpty())return;
        setProcessing(true);int generation=processingGeneration.incrementAndGet();Bitmap source=originalBitmap;
        processingExecutor.execute(()->{
            Bitmap result=null;
            try {result=FilmColorCorrection.apply(getApplicationContext(),source,profile,()->generation!=processingGeneration.get());}
            catch(Exception | OutOfMemoryError ignored) { }
            Bitmap rendered=result;
            runOnUiThread(()->{
                if(isDestroyed() || isFinishing() || generation!=processingGeneration.get()) {recycleIfNeeded(rendered);return;}
                if(rendered!=null) {
                    Bitmap previous=correctedBitmap;correctedBitmap=rendered;correctionProfile=profile;
                    correctionSelected=true;viewingOriginal=false;displayBitmap(rendered);recycleIfNeeded(previous);
                }
                setProcessing(false);
                if(rendered==null)showError(getString(R.string.correction_render_error));
            });
        });
    }

    private void changeSavedCorrection(boolean selected) {
        boolean previousSelection = correctionSelected;
        correctionSelected = selected;
        if (!selected) displayBitmap(originalBitmap);
        setProcessing(true); int generation = processingGeneration.incrementAndGet();
        processingExecutor.execute(() -> {
            Bitmap preview = null;
            boolean success = FilmGalleryRecipeStore.select(getApplicationContext(), imagePath, selected);
            if (success && selected) try {
                preview = decodeScaledBitmap(FilmGalleryRecipeStore.correction(getApplicationContext(), imagePath), getPreviewMaximumEdge());
            } catch (Exception | OutOfMemoryError ignored) { }
            Bitmap rendered = preview;
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing() || generation != processingGeneration.get()) { recycleIfNeeded(rendered); return; }
                if (success) {
                    Bitmap previous = correctedBitmap;
                    viewingOriginal = !selected;
                    correctionSelected = selected; correctedBitmap = rendered;
                    displayBitmap(selected && !viewingOriginal && rendered != null ? rendered : originalBitmap);
                    if (previous != rendered) recycleIfNeeded(previous);
                } else {
                    correctionSelected = previousSelection;
                    displayBitmap(correctionSelected && !viewingOriginal ? correctedBitmap : originalBitmap);
                }
                setProcessing(false);
                if (!success || selected && rendered == null) showError(getString(R.string.correction_change_error));
            });
        });
    }

    private void renderSelectedCorrection() {
        setProcessing(true);
        int generation = processingGeneration.incrementAndGet();
        Bitmap source = originalBitmap;
        String version=correctionProfile;
        processingExecutor.execute(() -> {
            Bitmap result = null;
            try { result = FilmColorCorrection.apply(getApplicationContext(), source,version,
                    () -> generation != processingGeneration.get()); }
            catch (Exception | OutOfMemoryError ignored) { }
            Bitmap rendered = result;
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing() || generation != processingGeneration.get()) { recycleIfNeeded(rendered); return; }
                correctedBitmap = rendered;
                if (rendered == null) {
                    correctionSelected = false;
                    if (saveCompleted && !FilmFrameCapture.isNote(getIntent())) FilmGalleryRecipeStore.select(this, imagePath, false);
                }
                displayBitmap(rendered == null || viewingOriginal ? originalBitmap : rendered);
                setProcessing(false);
                if (rendered == null) showError(getString(R.string.correction_render_error));
            });
        });
    }

    private void updateCorrectionControls() {
        boolean note=FilmFrameCapture.isNote(getIntent());
        correctionCategory.bind(correctionSelected && !viewingOriginal ? FilmCorrectionProfiles.key(correctionProfile) : FilmCorrectionCategory.ORIGINAL,
                !previewProcessing && !isSaving && originalBitmap != null, previewProcessing || isSaving,
                key -> note || FilmCorrectionCategory.ORIGINAL.equals(key) || FilmCorrectionCategory.E100D.equals(key),
                key -> {if(note)selectNoteCorrection(key);else selectCorrection(FilmCorrectionCategory.E100D.equals(key));});
    }

    private void showError(String message) {
        setProcessing(false);
        statusView.setText(message);
        statusView.setVisibility(View.VISIBLE);
    }

    private void requestSave() {
        if (isSaving || previewProcessing || originalBitmap == null || imagePath == null) {
            return;
        }

        if (!FilmFrameCapture.isNote(getIntent()) && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }

        saveCurrentResult();
    }

    private void saveCurrentResult() {
        if (isSaving || saveCompleted) {
            return;
        }

        File sourceFile = new File(imagePath);
        if (!sourceFile.isFile() || sourceFile.length() <= 0L) {
            showError(getString(R.string.save_source_missing));
            return;
        }

        isSaving = true;
        updateCorrectionControls();
        saveButton.setEnabled(false);
        saveButton.setText(getString(R.string.saving));

        double multiplierToSave = exposureMultiplier;
        boolean applyCorrection = correctionSelected;
        String profileToSave=correctionProfile;
        SaveJob job = new SaveJob();
        if (SAVE_JOBS.putIfAbsent(imagePath, job) != null) {
            observeSave();
            return;
        }
        if (noteLocation != null) noteLocation.saving();
        SAVE_EXECUTOR.execute(() -> {
            Bitmap decoded = null;
            Bitmap rotated = null;
            Bitmap finalBitmap = null;
            Bitmap corrected = null;
            java.util.List<Uri> addedGallery = new java.util.ArrayList<>();

            try {
                decoded = decodeScaledBitmap(sourceFile, Integer.MAX_VALUE);
                rotated = rotateFromExif(decoded, sourceFile);
                if (rotated != decoded) {
                    decoded.recycle();
                    decoded = null;
                }

                finalBitmap = FilmProcessor.applyExposure(rotated, multiplierToSave);
                if (finalBitmap != rotated) {
                    rotated.recycle();
                }
                rotated = null;
                if (applyCorrection) corrected = FilmColorCorrection.apply(getApplicationContext(), finalBitmap,profileToSave, () -> false);

                if (FilmFrameCapture.isNote(getIntent())) {
                    FilmFrameRecord frame = new FilmFrameRecord();
                    frame.number = getIntent().getIntExtra(FilmFrameCapture.NUMBER, 0);
                    frame.captureId = imagePath;
                    frame.capturedAt = FilmFrameCapture.capturedAt(getIntent(), sourceFile.lastModified());
                    // Snapshot only what is available. GPS never delays photo saving.
                    frame.location = noteLocation.position();
                    frame.iso = capturedIso; frame.aperture = capturedAperture;
                    frame.shutter = capturedShutter; frame.fieldOfView = capturedFieldOfView;
                    frame.correctionEnabled = applyCorrection;
                    frame.correctionProfile = applyCorrection ? profileToSave : "";
                    if (!new FilmRollStore(getApplicationContext()).saveFrame(
                            getIntent().getStringExtra(FilmFrameCapture.ROLL), frame, finalBitmap, corrected))
                        throw new IOException("Note cut could not be committed");
                    noteLocation.saved();
                } else {
                    Uri output = writeToGallery(finalBitmap, "");
                    addedGallery.add(output);
                    if (!FilmGalleryRecipeStore.save(getApplicationContext(), imagePath, output, applyCorrection, corrected)) {
                        getSharedPreferences("capture_saves", MODE_PRIVATE).edit().remove(imagePath).commit();
                        throw new IOException("Save receipt failed");
                    }
                }
                job.succeeded = true;
            } catch (Exception | OutOfMemoryError error) {
                job.succeeded = false;
                android.util.Log.e("FilmResult", "Photo save failed", error);
                for (Uri uri : addedGallery) if (!removeGalleryOutput(uri)) job.partial = true;
                if (noteLocation != null) noteLocation.failed();
            } finally {
                recycleIfNeeded(decoded);
                recycleIfNeeded(rotated);
                recycleIfNeeded(finalBitmap);
                recycleIfNeeded(corrected);
                job.complete = true;
            }
        });
        observeSave();
    }

    private boolean isCaptureSaved() {
        if (FilmFrameCapture.isNote(getIntent())) return imagePath != null && new FilmRollStore(this)
                .hasCapture(getIntent().getStringExtra(FilmFrameCapture.ROLL), imagePath);
        return imagePath != null && getSharedPreferences("capture_saves", MODE_PRIVATE)
                .getBoolean(imagePath, false);
    }

    private void observeSave() {
        if (screenStopped || isDestroyed() || imagePath == null) return;
        SaveJob job = SAVE_JOBS.get(imagePath);
        if (job != null && !job.complete) {
            isSaving = true;
            saveButton.setEnabled(false);
            saveButton.setText(getString(R.string.saving));
            uiHandler.postDelayed(this::observeSave, 250);
            return;
        }
        isSaving = false;
        saveCompleted = isCaptureSaved() || (job != null && job.succeeded);
        if (job != null) {
            SAVE_JOBS.remove(imagePath, job);
            Toast.makeText(this, saveCompleted ? getString(R.string.save_success)
                    : getString(job.partial ? R.string.correction_save_partial : FilmFrameCapture.isNote(getIntent())
                    ? R.string.note_store_error : R.string.save_error), Toast.LENGTH_SHORT).show();
        }
        saveButton.setText(saveCompleted ? getString(R.string.saved) : getString(
                FilmFrameCapture.isNote(getIntent()) ? R.string.note_save_frame : R.string.save));
        saveButton.setEnabled(!previewProcessing && originalBitmap != null && !saveCompleted);
        updateCorrectionControls();
        if (saveCompleted && FilmFrameCapture.isNote(getIntent())) { setResult(RESULT_OK); finish(); }
    }

    @Override protected void onStart() {
        super.onStart();
        boolean resumePreview = screenStopped;
        screenStopped = false;
        if (noteLocation != null) noteLocation.start();
        if (resumePreview) {
            if (originalBitmap == null) loadCapturedImage();
            else if (saveCompleted && !FilmFrameCapture.isNote(getIntent()) && correctionSelected && correctedBitmap == null) changeSavedCorrection(true);
            else if (correctionSelected && correctedBitmap == null) renderSelectedCorrection();
            else setProcessing(false);
        }
        observeSave();
    }

    @Override protected void onStop() {
        correctionCategory.dismiss();
        screenStopped = true;
        processingGeneration.incrementAndGet();
        uiHandler.removeCallbacksAndMessages(null);
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("correctionSelected", correctionSelected);
        out.putString("correctionProfile",correctionProfile);
        out.putBoolean("viewingOriginal", viewingOriginal);
        if (noteLocation != null && noteLocation.isComplete()) {
            out.putBoolean("noteLocationComplete", true);
            FilmFrameLocation position = noteLocation.position();
            if (position != null) {
                out.putDouble("noteLatitude", position.latitude); out.putDouble("noteLongitude", position.longitude);
                out.putDouble("noteAccuracy", position.accuracyMeters);
            }
        }
        super.onSaveInstanceState(out);
    }

    private Uri writeToGallery(
            Bitmap bitmap, String kind
    ) throws IOException {
        String fileName = GalleryFileNameBuilder.build(
                capturedIso,
                capturedAperture,
                capturedShutter,
                capturedFieldOfView,
                new Date(new File(imagePath).lastModified())
        );
        fileName = fileName.substring(0, fileName.length()-4) + kind + ".jpg";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return writeUsingMediaStore(bitmap, fileName);
        }

        return writeLegacyGalleryFile(bitmap, fileName);
    }

    private boolean removeGalleryOutput(Uri uri) {
        if (uri == null) return true;
        try {
            if ("file".equals(uri.getScheme())) {
                File file = new File(uri.getPath());
                boolean removed = !file.exists() || file.delete();
                MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, new String[]{"image/jpeg"}, null);
                return removed;
            }
            return getContentResolver().delete(uri, null, null) > 0;
        } catch (Exception error) { return false; }
    }

    private Uri writeUsingMediaStore(
            Bitmap bitmap,
            String fileName
    ) throws IOException {
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        String relativePath = Environment.DIRECTORY_DCIM + "/" + GALLERY_DIRECTORY + "/";
        Set<String> existingNames = new HashSet<>();
        try (Cursor cursor = resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media.DISPLAY_NAME},
                MediaStore.Images.Media.RELATIVE_PATH + " = ?",
                new String[]{relativePath}, null)) {
            if (cursor == null) throw new IOException("저장 파일명을 확인하지 못했습니다.");
            while (cursor.moveToNext()) existingNames.add(cursor.getString(0));
        }
        values.put(MediaStore.Images.Media.DISPLAY_NAME,
                GalleryFileNameBuilder.availableName(fileName, existingNames));
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(
                MediaStore.Images.Media.RELATIVE_PATH,
                relativePath
        );
        values.put(MediaStore.Images.Media.IS_PENDING, 1);

        Uri uri = resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
        );
        if (uri == null) {
            throw new IOException("MediaStore 항목을 만들 수 없습니다.");
        }

        try {
            OutputStream rawStream = resolver.openOutputStream(uri);
            if (rawStream == null) {
                throw new IOException("갤러리 출력 스트림을 열 수 없습니다.");
            }
            try (OutputStream stream = new BufferedOutputStream(rawStream, 64 * 1024)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                    throw new IOException("JPEG 저장에 실패했습니다.");
                }
            }

            ContentValues completed = new ContentValues();
            completed.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, completed, null, null);
            return uri;
        } catch (IOException | RuntimeException | OutOfMemoryError error) {
            resolver.delete(uri, null, null);
            throw error;
        }
    }

    @SuppressWarnings("deprecation")
    private Uri writeLegacyGalleryFile(
            Bitmap bitmap,
            String fileName
    ) throws IOException {
        File dcim = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM
        );
        File outputDirectory = new File(dcim, GALLERY_DIRECTORY);
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw new IOException("갤러리 폴더를 만들 수 없습니다.");
        }

        File outputFile = new File(outputDirectory, fileName);
        int suffix = 1;
        while (!outputFile.createNewFile()) {
            outputFile = new File(outputDirectory,
                    GalleryFileNameBuilder.numberedName(fileName, suffix++));
        }
        try (OutputStream stream = new BufferedOutputStream(
                new FileOutputStream(outputFile),
                64 * 1024
        )) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                throw new IOException("JPEG 저장에 실패했습니다.");
            }
        } catch (IOException | RuntimeException | OutOfMemoryError error) {
            if (outputFile.exists()) {
                outputFile.delete();
            }
            throw error;
        }

        MediaScannerConnection.scanFile(
                this,
                new String[]{outputFile.getAbsolutePath()},
                new String[]{"image/jpeg"},
                null
        );
        return Uri.fromFile(outputFile);
    }

    private void handleBack() {
        if (isSaving) {
            Toast.makeText(
                    this,
                    getString(R.string.save_progress),
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        finish();
    }

    private static void recycleIfNeeded(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    @Override
    protected void onDestroy() {
        processingGeneration.incrementAndGet();
        processingExecutor.shutdownNow();

        uiHandler.removeCallbacksAndMessages(null);
        resultImageView.setImageDrawable(null);
        // An interrupted worker may still read its source. Let GC reclaim these
        // after both the view and worker release their references.
        displayedBitmap = null;
        originalBitmap = null;
        correctedBitmap = null;

        super.onDestroy();
    }
}
