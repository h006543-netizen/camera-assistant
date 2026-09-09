package com.example.cameraoption;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
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
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
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
    }
    private static final String GALLERY_DIRECTORY = "CameraOption";

    private final ExecutorService processingExecutor =
            Executors.newSingleThreadExecutor();
    private final AtomicInteger processingGeneration = new AtomicInteger();

    private ImageView resultImageView;
    private ProgressBar progressView;
    private TextView statusView;
    private TextView saveButton;
    private Spinner filmSpinner;

    private Bitmap originalBitmap;
    private Bitmap displayedBitmap;
    private String imagePath;
    private String capturedIso = ExposureSettingsStore.DEFAULT_ISO;
    private String capturedAperture = ExposureSettingsStore.DEFAULT_APERTURE;
    private String capturedShutter = ExposureSettingsStore.DEFAULT_SHUTTER;
    private String capturedFieldOfView = ExposureSettingsStore.FIELD_OF_VIEW_DEFAULT;
    private double exposureMultiplier = 1.0;
    private FilmProcessor.Preset selectedPreset = FilmProcessor.Preset.NONE;
    private boolean isSaving;
    private boolean saveCompleted;
    private boolean previewProcessing;
    private boolean screenStopped;
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
                                    "갤러리에 저장하려면 저장소 권한이 필요합니다.",
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
        filmSpinner = findViewById(R.id.spinnerFilmPreset);

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

        setupFilmSelector();
        if (savedInstanceState != null) {
            selectedPreset = FilmProcessor.Preset.fromDisplayName(savedInstanceState.getString("preset"));
            filmSpinner.setSelection(selectedPreset.ordinal());
        }
        loadCapturedImage();
    }

    private void setupFilmSelector() {
        List<String> names = Arrays.asList(
                FilmProcessor.Preset.NONE.getDisplayName(),
                FilmProcessor.Preset.GOLD_200.getDisplayName(),
                FilmProcessor.Preset.PORTRA_400.getDisplayName(),
                FilmProcessor.Preset.CINESTILL_800T.getDisplayName(),
                FilmProcessor.Preset.VISION3_250D.getDisplayName()
        );

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                names
        ) {
            @NonNull
            @Override
            public View getView(
                    int position,
                    @Nullable View convertView,
                    @NonNull ViewGroup parent
            ) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(0xFFFFFFFF);
                view.setTextSize(16f);
                view.setPadding(0, 0, 0, 0);
                return view;
            }

            @Override
            public View getDropDownView(
                    int position,
                    @Nullable View convertView,
                    @NonNull ViewGroup parent
            ) {
                TextView view = (TextView) super.getDropDownView(
                        position,
                        convertView,
                        parent
                );
                view.setTextColor(0xFF111111);
                view.setTextSize(16f);
                view.setPadding(24, 20, 24, 20);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filmSpinner.setAdapter(adapter);
        filmSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(
                    AdapterView<?> parent,
                    View view,
                    int position,
                    long id
            ) {
                selectedPreset =
                        FilmProcessor.Preset.fromDisplayName(names.get(position));
                saveCompleted = isCaptureSaved();
                saveButton.setText("저장");
                saveButton.setEnabled(originalBitmap != null);

                if (originalBitmap != null) {
                    applyFilmPreset(selectedPreset);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 선택 없음 항목이 항상 존재하므로 별도 처리가 필요 없다.
            }
        });
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
            showError("촬영한 사진 경로가 없습니다.");
            return;
        }

        File imageFile = new File(imagePath);
        if (!imageFile.isFile() || imageFile.length() <= 0L) {
            showError("촬영한 사진을 불러올 수 없습니다.");
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

                runOnUiThread(() -> {
                    if (isFinishing() || generation != processingGeneration.get()) {
                        exposureAdjusted.recycle();
                        return;
                    }
                    originalBitmap = exposureAdjusted;
                    displayBitmap(originalBitmap);
                    applyFilmPreset(selectedPreset);
                });
                working = null; // Ownership transferred to the main-thread callback.
            } catch (java.util.concurrent.CancellationException ignored) {
                // Backgrounding or destruction invalidated this preview.
            } catch (Exception | OutOfMemoryError error) {
                runOnUiThread(() -> {
                    if (!isDestroyed() && generation == processingGeneration.get())
                        showError("촬영한 사진을 불러오지 못했습니다.");
                });
            } finally {
                recycleIfNeeded(working);
            }
        });
    }

    private void applyFilmPreset(FilmProcessor.Preset preset) {
        int generation = processingGeneration.incrementAndGet();

        if (preset == FilmProcessor.Preset.NONE) {
            displayBitmap(originalBitmap);
            setProcessing(false);
            return;
        }

        setProcessing(true);
        Bitmap source = originalBitmap;
        processingExecutor.execute(() -> {
            try {
            Bitmap processed = FilmProcessor.applyPreset(source, preset,
                    () -> generation != processingGeneration.get());

            runOnUiThread(() -> {
                if (isFinishing() || generation != processingGeneration.get()) {
                    processed.recycle();
                    return;
                }
                displayBitmap(processed);
                setProcessing(false);
            });
            } catch (java.util.concurrent.CancellationException ignored) {
                // A newer selection or a stopped screen owns the next preview.
            } catch (RuntimeException | OutOfMemoryError error) {
                runOnUiThread(() -> {
                    if (!isDestroyed() && generation == processingGeneration.get())
                        showError("사진 처리에 실패했습니다. 다시 선택해 주세요.");
                });
            }
        });
    }

    private void displayBitmap(Bitmap bitmap) {
        Bitmap previous = displayedBitmap;
        displayedBitmap = bitmap;
        resultImageView.setImageBitmap(bitmap);

        if (previous != null
                && previous != originalBitmap
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
        int sampleSize = ImageMemoryBudget.sampleSize(bounds.outWidth, bounds.outHeight, available);
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
        filmSpinner.setEnabled(!processing && !isSaving);
        saveButton.setEnabled(!processing && !isSaving && originalBitmap != null && !saveCompleted);
        if (processing) {
            statusView.setVisibility(View.GONE);
        }
    }

    private void showError(String message) {
        setProcessing(false);
        statusView.setText(message);
        statusView.setVisibility(View.VISIBLE);
    }

    private void requestSave() {
        if (isSaving || originalBitmap == null || imagePath == null) {
            return;
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
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
            showError("저장할 촬영 원본이 없습니다.");
            return;
        }

        isSaving = true;
        filmSpinner.setEnabled(false);
        saveButton.setEnabled(false);
        saveButton.setText("저장 중");

        FilmProcessor.Preset presetToSave = selectedPreset;
        double multiplierToSave = exposureMultiplier;
        SaveJob job = new SaveJob();
        if (SAVE_JOBS.putIfAbsent(imagePath, job) != null) {
            observeSave();
            return;
        }
        SAVE_EXECUTOR.execute(() -> {
            Bitmap decoded = null;
            Bitmap rotated = null;
            Bitmap finalBitmap = null;

            try {
                decoded = decodeScaledBitmap(sourceFile, Integer.MAX_VALUE);
                rotated = rotateFromExif(decoded, sourceFile);
                if (rotated != decoded) {
                    decoded.recycle();
                    decoded = null;
                }

                finalBitmap = FilmProcessor.applyExposureAndPreset(
                        rotated,
                        multiplierToSave,
                        presetToSave
                );
                if (finalBitmap != rotated) {
                    rotated.recycle();
                }
                rotated = null;

                Uri savedUri = writeToGallery(finalBitmap, presetToSave);
                if (savedUri == null) {
                    throw new IOException("갤러리 URI를 만들지 못했습니다.");
                }

                getSharedPreferences("capture_saves", MODE_PRIVATE).edit()
                        .putBoolean(imagePath, true).commit();
                job.succeeded = true;
            } catch (Exception | OutOfMemoryError error) {
                job.succeeded = false;
            } finally {
                recycleIfNeeded(decoded);
                recycleIfNeeded(rotated);
                recycleIfNeeded(finalBitmap);
                job.complete = true;
            }
        });
        observeSave();
    }

    private boolean isCaptureSaved() {
        return imagePath != null && getSharedPreferences("capture_saves", MODE_PRIVATE)
                .getBoolean(imagePath, false);
    }

    private void observeSave() {
        if (screenStopped || isDestroyed() || imagePath == null) return;
        SaveJob job = SAVE_JOBS.get(imagePath);
        if (job != null && !job.complete) {
            isSaving = true;
            filmSpinner.setEnabled(false);
            saveButton.setEnabled(false);
            saveButton.setText("저장 중");
            uiHandler.postDelayed(this::observeSave, 250);
            return;
        }
        isSaving = false;
        saveCompleted = isCaptureSaved() || (job != null && job.succeeded);
        if (job != null) {
            SAVE_JOBS.remove(imagePath, job);
            Toast.makeText(this, saveCompleted ? "CameraOption 앨범에 저장했습니다."
                    : "갤러리에 저장하지 못했습니다. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show();
        }
        saveButton.setText(saveCompleted ? "저장됨" : "저장");
        saveButton.setEnabled(!previewProcessing && originalBitmap != null && !saveCompleted);
        filmSpinner.setEnabled(!previewProcessing);
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle state) {
        state.putString("preset", selectedPreset.getDisplayName());
        super.onSaveInstanceState(state);
    }

    @Override protected void onStart() {
        super.onStart();
        boolean resumePreview = screenStopped;
        screenStopped = false;
        if (resumePreview) {
            if (originalBitmap == null) loadCapturedImage();
            else applyFilmPreset(selectedPreset);
        }
        observeSave();
    }

    @Override protected void onStop() {
        screenStopped = true;
        processingGeneration.incrementAndGet();
        uiHandler.removeCallbacksAndMessages(null);
        super.onStop();
    }

    private Uri writeToGallery(
            Bitmap bitmap,
            FilmProcessor.Preset preset
    ) throws IOException {
        String fileName = GalleryFileNameBuilder.build(
                capturedIso,
                capturedAperture,
                capturedShutter,
                capturedFieldOfView,
                preset.getDisplayName(),
                new Date()
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return writeUsingMediaStore(bitmap, fileName);
        }

        return writeLegacyGalleryFile(bitmap, fileName);
    }

    private Uri writeUsingMediaStore(
            Bitmap bitmap,
            String fileName
    ) throws IOException {
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/" + GALLERY_DIRECTORY
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
        } catch (IOException | RuntimeException error) {
            resolver.delete(uri, null, null);
            throw error;
        }
    }

    @SuppressWarnings("deprecation")
    private Uri writeLegacyGalleryFile(
            Bitmap bitmap,
            String fileName
    ) throws IOException {
        File pictures = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
        );
        File outputDirectory = new File(pictures, GALLERY_DIRECTORY);
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw new IOException("갤러리 폴더를 만들 수 없습니다.");
        }

        File outputFile = new File(outputDirectory, fileName);
        int suffix = 1;
        while (!outputFile.createNewFile()) {
            outputFile = new File(outputDirectory,
                    fileName.substring(0, fileName.length() - 4) + "_" + suffix++ + ".jpg");
        }
        try (OutputStream stream = new BufferedOutputStream(
                new FileOutputStream(outputFile),
                64 * 1024
        )) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                throw new IOException("JPEG 저장에 실패했습니다.");
            }
        } catch (IOException error) {
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
                    "갤러리에 저장하고 있습니다.",
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

        super.onDestroy();
    }
}
