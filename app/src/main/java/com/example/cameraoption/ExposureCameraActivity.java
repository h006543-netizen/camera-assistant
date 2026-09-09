package com.example.cameraoption;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.util.Range;
import android.util.SizeF;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.Camera2CameraControl;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.CaptureRequestOptions;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.Executor;

@OptIn(markerClass = ExperimentalCamera2Interop.class)
public class ExposureCameraActivity extends AppCompatActivity {

    private static final String[] ISO_VALUES = ExposureSettingsStore.isoValues();
    private static final String[] APERTURE_VALUES = ExposureSettingsStore.apertureValues();
    private static final String[] SHUTTER_VALUES = ExposureSettingsStore.shutterValues();
    private static final String[] FIELD_OF_VIEW_VALUES =
            ExposureSettingsStore.fieldOfViewValues();

    private PreviewView previewView;
    private TextView statusView;
    private TextView isoValueView;
    private TextView apertureValueView;
    private TextView shutterValueView;
    private TextView fieldOfViewValueView;

    private String selectedIso;
    private String selectedAperture;
    private String selectedShutter;
    private String selectedFieldOfView;
    private ExposureSettingsStore exposureSettingsStore;

    private ImageCapture imageCapture;
    private Camera camera;
    private ZoomState zoomState;
    private Camera2CameraControl camera2Control;
    private Range<Integer> sensorIsoRange;
    private Range<Long> sensorExposureRange;
    private long sensorMaximumFrameDurationNanos;
    private double phoneAperture = 1.8;
    private double baseEquivalentFocalLengthMm =
            FieldOfViewCalculator.DEFAULT_PHONE_EQUIVALENT_MM;
    private boolean manualSensorSupported;
    private boolean initialFieldOfViewApplied;
    private ListenableFuture<Void> fieldOfViewApplyFuture;
    private boolean captureInProgress;
    private double lastResidualBrightnessMultiplier = 1.0;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            startCamera();
                        } else {
                            showPersistentStatus("카메라 권한이 필요합니다.");
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CameraSessionCache.clearExpired(this);
        setContentView(R.layout.activity_exposure_camera);
        exposureSettingsStore = new ExposureSettingsStore(this);

        SystemBarInsets.apply(
                this,
                findViewById(R.id.exposureTopControls),
                findViewById(R.id.exposureBottomControls)
        );

        readInitialExposureValues();
        bindViews();
        bindControls();
        updateDisplayedValues();

        if (hasCameraPermission()) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void readInitialExposureValues() {
        ExposureSettingsStore.Values storedValues = exposureSettingsStore.load();
        String intentIso = getIntent().getStringExtra("iso");
        String intentAperture = getIntent().getStringExtra("aperture");
        String intentShutter = getIntent().getStringExtra("shutter");

        selectedIso = ExposureSettingsStore.isValidIso(intentIso)
                ? intentIso
                : storedValues.getIso();
        selectedAperture = ExposureSettingsStore.isValidAperture(intentAperture)
                ? intentAperture
                : storedValues.getAperture();
        selectedShutter = ExposureSettingsStore.isValidShutter(intentShutter)
                ? intentShutter
                : storedValues.getShutter();
        selectedFieldOfView = storedValues.getFieldOfView();

        saveExposureSettings();
    }

    private void bindViews() {
        previewView = findViewById(R.id.exposurePreviewView);
        // TextureView participates in the view layer's color filter on API 24+.
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        statusView = findViewById(R.id.tvExposureStatus);
        isoValueView = findViewById(R.id.tvExposureIsoValue);
        apertureValueView = findViewById(R.id.tvExposureApertureValue);
        shutterValueView = findViewById(R.id.tvExposureShutterValue);
        fieldOfViewValueView = findViewById(R.id.tvExposureFieldOfViewValue);
    }

    private void bindControls() {
        findViewById(R.id.btnExposureBack).setOnClickListener(view -> finish());

        LinearLayout isoButton = findViewById(R.id.btnExposureIso);
        LinearLayout apertureButton = findViewById(R.id.btnExposureAperture);
        LinearLayout shutterButton = findViewById(R.id.btnExposureShutter);
        LinearLayout fieldOfViewButton = findViewById(R.id.btnExposureFieldOfView);

        isoButton.setOnClickListener(view -> showValueDialog(
                "ISO 선택",
                ISO_VALUES,
                selectedIso,
                value -> selectedIso = value
        ));
        apertureButton.setOnClickListener(view -> showValueDialog(
                "조리개 선택",
                APERTURE_VALUES,
                selectedAperture,
                value -> selectedAperture = value
        ));
        shutterButton.setOnClickListener(view -> showValueDialog(
                "셔터속도 선택",
                SHUTTER_VALUES,
                selectedShutter,
                value -> selectedShutter = value
        ));
        fieldOfViewButton.setOnClickListener(view -> showValueDialog(
                "35mm 환산 화각 선택",
                FIELD_OF_VIEW_VALUES,
                selectedFieldOfView,
                value -> selectedFieldOfView = value
        ));

        findViewById(R.id.btnExposureCapture).setOnClickListener(view -> capturePhoto());
    }

    private void showValueDialog(
            String title,
            String[] values,
            String selectedValue,
            ValueSelectionListener listener
    ) {
        int selectedIndex = Math.max(0, Arrays.asList(values).indexOf(selectedValue));

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(values, selectedIndex, (dialog, which) -> {
                    listener.onSelected(values[which]);
                    saveExposureSettings();
                    updateDisplayedValues();
                    applyExposure();
                    applyFieldOfView();
                    dialog.dismiss();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void updateDisplayedValues() {
        isoValueView.setText(selectedIso);
        apertureValueView.setText(selectedAperture);
        shutterValueView.setText(selectedShutter);
        fieldOfViewValueView.setText(selectedFieldOfView);
    }

    private void saveExposureSettings() {
        exposureSettingsStore.save(
                selectedIso,
                selectedAperture,
                selectedShutter,
                selectedFieldOfView
        );
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> providerFuture =
                ProcessCameraProvider.getInstance(this);
        Executor mainExecutor = ContextCompat.getMainExecutor(this);

        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = providerFuture.get();

                previewView.post(() -> bindCameraUseCases(provider));
            } catch (Exception error) {
                showPersistentStatus("카메라를 시작할 수 없습니다.");
            }
        }, mainExecutor);
    }

    private void bindCameraUseCases(ProcessCameraProvider provider) {
        try {
            ViewPort viewPort = previewView.getViewPort();
            if (viewPort == null) {
                showPersistentStatus("촬영 화각을 준비할 수 없습니다.");
                return;
            }

            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build();

            UseCaseGroup useCaseGroup = new UseCaseGroup.Builder()
                    .setViewPort(viewPort)
                    .addUseCase(preview)
                    .addUseCase(imageCapture)
                    .build();

            provider.unbindAll();
            camera = provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    useCaseGroup
            );

            configureManualSensor(camera);
            configureFieldOfView(camera);
        } catch (Exception error) {
            showPersistentStatus("카메라를 시작할 수 없습니다.");
        }
    }

    private void configureManualSensor(Camera camera) {
        Camera2CameraInfo cameraInfo = Camera2CameraInfo.from(camera.getCameraInfo());
        camera2Control = Camera2CameraControl.from(camera.getCameraControl());

        int[] capabilities = cameraInfo.getCameraCharacteristic(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
        );
        sensorIsoRange = cameraInfo.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
        );
        sensorExposureRange = cameraInfo.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
        );
        Long maximumFrameDuration = cameraInfo.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION
        );
        float[] apertures = cameraInfo.getCameraCharacteristic(
                CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES
        );

        manualSensorSupported = containsCapability(
                capabilities,
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
        ) && sensorIsoRange != null && sensorExposureRange != null;

        if (apertures != null && apertures.length > 0 && apertures[0] > 0f) {
            phoneAperture = apertures[0];
        }

        sensorMaximumFrameDurationNanos =
                maximumFrameDuration != null
                        ? maximumFrameDuration
                        : sensorExposureRange != null
                        ? sensorExposureRange.getUpper()
                        : 1_000_000_000L;

        if (manualSensorSupported) {
            hideStatus();
            applyExposure();
        } else {
            showPersistentStatus(
                    "이 카메라는 완전 수동 노출을 지원하지 않아 밝기 변화만 미리 보여줍니다."
            );
            applyFallbackPreview();
        }
    }

    private void configureFieldOfView(Camera camera) {
        Camera2CameraInfo cameraInfo = Camera2CameraInfo.from(camera.getCameraInfo());
        SizeF sensorPhysicalSize = cameraInfo.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE
        );
        float[] availableFocalLengths = cameraInfo.getCameraCharacteristic(
                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
        );

        if (sensorPhysicalSize != null) {
            baseEquivalentFocalLengthMm =
                    FieldOfViewCalculator.chooseBaseEquivalentFocalLength(
                            availableFocalLengths,
                            sensorPhysicalSize.getWidth(),
                            sensorPhysicalSize.getHeight()
                    );
        } else {
            baseEquivalentFocalLengthMm =
                    FieldOfViewCalculator.DEFAULT_PHONE_EQUIVALENT_MM;
        }

        initialFieldOfViewApplied = false;
        camera.getCameraInfo().getZoomState().observe(this, currentZoomState -> {
            zoomState = currentZoomState;
            if (!initialFieldOfViewApplied && currentZoomState != null) {
                initialFieldOfViewApplied = true;
                applyFieldOfView();
            }
        });
    }

    private void applyFieldOfView() {
        if (camera == null || zoomState == null) {
            return;
        }

        float requestedZoomRatio = FieldOfViewCalculator.calculateZoomRatio(
                selectedFieldOfView,
                baseEquivalentFocalLengthMm
        );
        float appliedZoomRatio = FieldOfViewCalculator.clampZoomRatio(
                requestedZoomRatio,
                zoomState.getMinZoomRatio(),
                zoomState.getMaxZoomRatio()
        );

        fieldOfViewApplyFuture = camera.getCameraControl().setZoomRatio(appliedZoomRatio);

        if (Math.abs(appliedZoomRatio - requestedZoomRatio) > 0.01f) {
            Toast.makeText(
                    this,
                    "기기 줌 범위에 맞춰 가장 가까운 화각으로 표시합니다.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private boolean containsCapability(int[] capabilities, int expected) {
        if (capabilities == null) {
            return false;
        }
        for (int capability : capabilities) {
            if (capability == expected) {
                return true;
            }
        }
        return false;
    }

    private void applyExposure() {
        if (!manualSensorSupported
                || camera2Control == null
                || sensorIsoRange == null
                || sensorExposureRange == null) {
            applyFallbackPreview();
            return;
        }

        try {
            ExposureCalculator.Result result = ExposureCalculator.calculate(
                    Integer.parseInt(selectedIso),
                    ExposureCalculator.parseShutterSeconds(selectedShutter),
                    ExposureCalculator.parseAperture(selectedAperture),
                    phoneAperture,
                    sensorIsoRange.getLower(),
                    sensorIsoRange.getUpper(),
                    sensorExposureRange.getLower(),
                    sensorExposureRange.getUpper(),
                    sensorMaximumFrameDurationNanos
            );

            long frameDuration = result.getSensorFrameDurationNanos();

            CaptureRequestOptions options = new CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(
                            CaptureRequest.CONTROL_MODE,
                            CameraMetadata.CONTROL_MODE_AUTO
                    )
                    .setCaptureRequestOption(
                            CaptureRequest.CONTROL_AE_MODE,
                            CameraMetadata.CONTROL_AE_MODE_OFF
                    )
                    .setCaptureRequestOption(
                            CaptureRequest.CONTROL_AF_MODE,
                            CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    )
                    .setCaptureRequestOption(
                            CaptureRequest.CONTROL_AWB_MODE,
                            CameraMetadata.CONTROL_AWB_MODE_AUTO
                    )
                    .setCaptureRequestOption(
                            CaptureRequest.SENSOR_SENSITIVITY,
                            result.getSensorIso()
                    )
                    .setCaptureRequestOption(
                            CaptureRequest.SENSOR_EXPOSURE_TIME,
                            result.getSensorExposureNanos()
                    )
                    .setCaptureRequestOption(
                            CaptureRequest.SENSOR_FRAME_DURATION,
                            frameDuration
                    )
                    .build();

            camera2Control.setCaptureRequestOptions(options);
            applyResidualBrightness(result.getResidualBrightnessMultiplier());

            if (result.isHardwareRangeLimited()) {
                showTransientStatus("기기 노출 범위에 맞춰 가장 가까운 밝기로 표시합니다.");
            } else {
                hideStatus();
            }
        } catch (RuntimeException error) {
            showPersistentStatus("선택한 노출값을 적용할 수 없습니다.");
        }
    }

    private void applyFallbackPreview() {
        double relativeEv = ExposureCalculator.calculateRelativeEv(
                Integer.parseInt(selectedIso),
                ExposureCalculator.parseShutterSeconds(selectedShutter),
                ExposureCalculator.parseAperture(selectedAperture),
                200,
                1.0 / 125.0,
                2.8
        );
        applyResidualBrightness(Math.pow(2.0, relativeEv));
    }

    private void applyResidualBrightness(double multiplier) {
        lastResidualBrightnessMultiplier = multiplier;

        if (multiplier == 1.0) {
            previewView.setLayerType(View.LAYER_TYPE_NONE, null);
            return;
        }
        float scale = (float) ExposureBrightness.encodedScale(multiplier);
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(new float[]{
                scale, 0, 0, 0, 0,
                0, scale, 0, 0, 0,
                0, 0, scale, 0, 0,
                0, 0, 0, 1, 0
        }));
        previewView.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
        previewView.setLayerPaint(paint);
    }

    private void capturePhoto() {
        if (captureInProgress) {
            return;
        }

        if (imageCapture == null) {
            Toast.makeText(this, "카메라를 준비하고 있습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (fieldOfViewApplyFuture == null) {
            Toast.makeText(this, "화각을 준비하고 있습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        captureInProgress = true;
        waitForFieldOfViewAndCapture(fieldOfViewApplyFuture);
    }

    private void waitForFieldOfViewAndCapture(ListenableFuture<Void> applyFuture) {
        if (!applyFuture.isDone()) {
            showTransientStatus("선택한 화각을 적용하고 있습니다.");
            applyFuture.addListener(
                    () -> waitForFieldOfViewAndCapture(applyFuture),
                    ContextCompat.getMainExecutor(this)
            );
            return;
        }

        if (applyFuture != fieldOfViewApplyFuture) {
            waitForFieldOfViewAndCapture(fieldOfViewApplyFuture);
            return;
        }

        try {
            applyFuture.get();
        } catch (Exception error) {
            captureInProgress = false;
            Toast.makeText(
                    this,
                    "선택한 화각을 적용하지 못했습니다. 다시 시도해 주세요.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        capturePhotoNow();
    }

    private void capturePhotoNow() {

        File outputFile;
        try {
            outputFile = CameraSessionCache.createCaptureFile(this);
        } catch (IllegalStateException error) {
            captureInProgress = false;
            Toast.makeText(
                    this,
                    "촬영 임시 파일을 만들 수 없습니다.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(outputFile).build();

        imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(
                            @NonNull ImageCapture.OutputFileResults outputFileResults
                    ) {
                        captureInProgress = false;
                        if (!outputFile.isFile() || outputFile.length() <= 0L) {
                            Toast.makeText(
                                    ExposureCameraActivity.this,
                                    "촬영 파일을 생성하지 못했습니다.",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }

                        Intent intent = new Intent(
                                ExposureCameraActivity.this,
                                FilmResultActivity.class
                        );
                        intent.putExtra(
                                FilmResultActivity.EXTRA_IMAGE_PATH,
                                outputFile.getAbsolutePath()
                        );
                        intent.putExtra(
                                FilmResultActivity.EXTRA_EXPOSURE_MULTIPLIER,
                                lastResidualBrightnessMultiplier
                        );
                        intent.putExtra(FilmResultActivity.EXTRA_ISO, selectedIso);
                        intent.putExtra(
                                FilmResultActivity.EXTRA_APERTURE,
                                selectedAperture
                        );
                        intent.putExtra(
                                FilmResultActivity.EXTRA_SHUTTER,
                                selectedShutter
                        );
                        intent.putExtra(
                                FilmResultActivity.EXTRA_FIELD_OF_VIEW,
                                selectedFieldOfView
                        );
                        startActivity(intent);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        captureInProgress = false;
                        Toast.makeText(
                                ExposureCameraActivity.this,
                                "사진을 촬영하지 못했습니다.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    private void showPersistentStatus(String message) {
        statusView.removeCallbacks(hideStatusRunnable);
        statusView.setText(message);
        statusView.setVisibility(View.VISIBLE);
    }

    private void showTransientStatus(String message) {
        statusView.removeCallbacks(hideStatusRunnable);
        statusView.setText(message);
        statusView.setVisibility(View.VISIBLE);
        statusView.postDelayed(hideStatusRunnable, 2500L);
    }

    private final Runnable hideStatusRunnable = this::hideStatus;

    private void hideStatus() {
        statusView.setVisibility(View.GONE);
    }

    private interface ValueSelectionListener {
        void onSelected(String value);
    }
}
