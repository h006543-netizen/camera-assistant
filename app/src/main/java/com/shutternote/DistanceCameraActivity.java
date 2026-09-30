package com.shutternote;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.Image;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Config;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.FatalException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class DistanceCameraActivity extends AppCompatActivity implements GLSurfaceView.Renderer {

    private final DistanceEstimator distanceEstimator = new DistanceEstimator();
    private boolean depthSupported;
    private long lastDepthTimestamp;
    private long lastFreshDepthTime;
    private long lastDiagnosticTime;
    private String lastDistanceText = "";
    private boolean lastWasGuidance;

    private static final String TAG = "DistanceCameraActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private static final long DISTANCE_SAMPLE_INTERVAL_NANOS = 150_000_000L;

    private FrameLayout cameraPreviewContainer;
    private TextView btnDistanceBack;
    private TextView tvCurrentDistanceValue;

    private GLSurfaceView glSurfaceView;
    private Session arSession;
    private boolean installRequested = false;
    private volatile boolean arSessionResumed = false;

    private int viewportWidth = 0;
    private int viewportHeight = 0;

    private int cameraTextureId = -1;
    private int program = 0;
    private int positionAttribute = 0;
    private int texCoordAttribute = 0;
    private int textureUniform = 0;

    private FloatBuffer screenVertexBuffer;
    private FloatBuffer cameraTexCoordBuffer;

    private long lastDistanceSampleNanos = 0;

    private final float[] screenVertices = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f,  1.0f,
            1.0f,  1.0f
    };

    private final float[] cameraTexCoords = new float[8];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_distance_camera);

        SystemBarInsets.apply(
                this,
                findViewById(R.id.topDistanceBar),
                findViewById(R.id.distanceHudLayout)
        );

        cameraPreviewContainer = findViewById(R.id.cameraPreviewContainer);
        btnDistanceBack = findViewById(R.id.btnDistanceBack);
        tvCurrentDistanceValue = findViewById(R.id.tvCurrentDistanceValue);

        btnDistanceBack.setOnClickListener(view -> finish());
        showGuidance(R.string.distance_move_sideways);

        glSurfaceView = new GLSurfaceView(this);
        glSurfaceView.setEGLContextClientVersion(2);
        glSurfaceView.setPreserveEGLContextOnPause(true);
        glSurfaceView.setRenderer(this);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        cameraPreviewContainer.addView(glSurfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    @Override
    protected void onResume() {
        super.onResume();

        startDistanceSessionIfReady();
    }

    private void startDistanceSessionIfReady() {
        if (arSessionResumed) {
            return;
        }

        if (!hasCameraPermission()) {
            requestCameraPermission();
            return;
        }

        if (arSession == null) {
            if (!createArSession()) {
                return;
            }
        }

        try {
            arSession.resume();
            arSessionResumed = true;
            glSurfaceView.onResume();

        } catch (CameraNotAvailableException e) {
            Log.e(TAG, "CameraNotAvailableException", e);
            handleArStartFailed(getString(R.string.camera_unavailable));

        } catch (FatalException e) {
            Log.e(TAG, "ARCore FatalException", e);
            handleArStartFailed(getString(R.string.ar_start_error));

        } catch (Exception e) {
            Log.e(TAG, "ARCore start failed", e);
            handleArStartFailed(getString(R.string.distance_start_error));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (glSurfaceView != null) {
            glSurfaceView.onPause();
        }

        if (arSession != null && arSessionResumed) {
            arSession.pause();
            arSessionResumed = false;
        }
        lastDistanceSampleNanos = 0L;
        distanceEstimator.reset();
        lastDepthTimestamp = 0L;
        lastFreshDepthTime = 0L;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (arSession != null) {
            arSession.close();
            arSession = null;
        }
    }

    private boolean createArSession() {
        try {
            ArCoreApk.InstallStatus installStatus =
                    ArCoreApk.getInstance().requestInstall(this, !installRequested);

            if (installStatus == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                installRequested = true;
                showGuidance(R.string.distance_install_ar);
                return false;
            }

            arSession = new Session(this);

            Config config = arSession.getConfig();

            // Read the center depth directly; detected background planes are not a rangefinder.
            config.setPlaneFindingMode(Config.PlaneFindingMode.DISABLED);
            config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
            config.setFocusMode(Config.FocusMode.AUTO);
            config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);

            depthSupported = arSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
            if (depthSupported) {
                config.setDepthMode(Config.DepthMode.AUTOMATIC);
            } else {
                config.setDepthMode(Config.DepthMode.DISABLED);
            }

            arSession.configure(config);
            Log.i(TAG, "Distance depth support=" + depthSupported);
            return true;

        } catch (UnavailableUserDeclinedInstallationException e) {
            Log.e(TAG, "User declined ARCore installation", e);
            showGuidance(R.string.distance_install_ar);
            showToast(getString(R.string.ar_install_message));
            return false;

        } catch (Exception e) {
            Log.e(TAG, "createArSession failed", e);
            if (arSession != null) {
                arSession.close();
                arSession = null;
            }
            showGuidance(R.string.distance_unsupported);
            showToast(getString(R.string.ar_error));
            return false;
        }
    }

    private void handleArStartFailed(String message) {
        showGuidance(R.string.distance_reopen);
        showToast(message);

        if (arSession != null) {
            arSession.close();
            arSession = null;
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.CAMERA},
                REQUEST_CAMERA_PERMISSION
        );
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        cameraTextureId = createExternalTexture();
        program = createCameraProgram();

        positionAttribute = GLES20.glGetAttribLocation(program, "a_Position");
        texCoordAttribute = GLES20.glGetAttribLocation(program, "a_TexCoord");
        textureUniform = GLES20.glGetUniformLocation(program, "u_Texture");

        screenVertexBuffer = createFloatBuffer(screenVertices);
        cameraTexCoordBuffer = createFloatBuffer(new float[]{
                0.0f, 1.0f,
                1.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 0.0f
        });
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        viewportWidth = width;
        viewportHeight = height;

        GLES20.glViewport(0, 0, width, height);

        if (arSession != null) {
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            arSession.setDisplayGeometry(rotation, width, height);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (!arSessionResumed || arSession == null || cameraTextureId == -1
                || viewportWidth == 0 || viewportHeight == 0) {
            return;
        }

        try {
            arSession.setCameraTextureName(cameraTextureId);
            // Also apply geometry when the surface existed before permission/session creation.
            arSession.setDisplayGeometry(getWindowManager().getDefaultDisplay().getRotation(),
                    viewportWidth, viewportHeight);

            Frame frame = arSession.update();

            if (frame.hasDisplayGeometryChanged()) {
                frame.transformCoordinates2d(
                        Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                        screenVertices,
                        Coordinates2d.TEXTURE_NORMALIZED,
                        cameraTexCoords
                );

                cameraTexCoordBuffer.clear();
                cameraTexCoordBuffer.put(cameraTexCoords);
                cameraTexCoordBuffer.position(0);
            }

            drawCameraBackground();

            long frameTimestamp = SystemClock.elapsedRealtimeNanos();
            if (lastDistanceSampleNanos == 0L
                    || frameTimestamp - lastDistanceSampleNanos
                    >= DISTANCE_SAMPLE_INTERVAL_NANOS) {
                lastDistanceSampleNanos = frameTimestamp;
                updateDistanceFromDepth(frame);
            }

        } catch (Exception e) {
            Log.e(TAG, "onDrawFrame failed", e);
            distanceEstimator.reset();
            showGuidance(R.string.distance_reopen);
        }
    }

    private void updateDistanceFromDepth(Frame frame) {
        if (!depthSupported) {
            showGuidance(R.string.distance_unsupported);
            return;
        }
        if (frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
            logMeasurement("tracking=" + frame.getCamera().getTrackingState()
                    + " reason=" + frame.getCamera().getTrackingFailureReason());
            distanceEstimator.reset();
            lastDepthTimestamp = 0L;
            int hint;
            switch (frame.getCamera().getTrackingFailureReason()) {
                case INSUFFICIENT_LIGHT:
                    hint = R.string.distance_more_light;
                    break;
                case EXCESSIVE_MOTION:
                    hint = R.string.distance_slow_down;
                    break;
                case INSUFFICIENT_FEATURES:
                    hint = R.string.distance_more_features;
                    break;
                default:
                    hint = R.string.distance_move_sideways;
            }
            showGuidance(hint);
            return;
        }
        try (Image depth = frame.acquireDepthImage16Bits()) {
            long timestamp = depth.getTimestamp();
            long now = SystemClock.elapsedRealtimeNanos();
            if (timestamp <= 0 || frame.getTimestamp() - timestamp > 300_000_000L
                    || (timestamp == lastDepthTimestamp
                    && now - lastFreshDepthTime > 300_000_000L)) {
                distanceEstimator.reset();
                logMeasurement("stale depth frame=" + frame.getTimestamp()
                        + " depth=" + timestamp);
                showGuidance(R.string.distance_move_sideways);
                return;
            }
            if (timestamp == lastDepthTimestamp) return;
            lastDepthTimestamp = timestamp;
            lastFreshDepthTime = now;

            float[] uv = new float[2];
            frame.transformCoordinates2d(Coordinates2d.VIEW,
                    new float[]{viewportWidth / 2f, viewportHeight / 2f},
                    Coordinates2d.IMAGE_NORMALIZED, uv);
            int x = (int) Math.floor(uv[0] * depth.getWidth());
            int y = (int) Math.floor(uv[1] * depth.getHeight());
            Image.Plane plane = depth.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int[][] offsets = {{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            float[] samples = new float[5];
            for (int i = 0; i < offsets.length; i++) {
                samples[i] = DistanceEstimator.readDepthMeters(buffer,
                        depth.getWidth(), depth.getHeight(), plane.getRowStride(),
                        plane.getPixelStride(), x + offsets[i][0], y + offsets[i][1]);
            }
            float measured = DistanceEstimator.centralMedian(samples);
            // Depth gives optical-axis Z, not ray length. Use CPU image intrinsics.
            CameraIntrinsics intrinsics = frame.getCamera().getImageIntrinsics();
            float[] focal = intrinsics.getFocalLength();
            float[] principal = intrinsics.getPrincipalPoint();
            int[] dimensions = intrinsics.getImageDimensions();
            float nx = (uv[0] * dimensions[0] - principal[0]) / focal[0];
            float ny = (uv[1] * dimensions[1] - principal[1]) / focal[1];
            measured *= (float) Math.sqrt(1f + nx * nx + ny * ny);
            float result = distanceEstimator.update(measured, timestamp);
            logMeasurement("depth=" + timestamp + " ageMs="
                    + (frame.getTimestamp() - timestamp) / 1_000_000L
                    + " size=" + depth.getWidth() + "x" + depth.getHeight()
                    + " uv=" + Arrays.toString(uv) + " samples=" + Arrays.toString(samples)
                    + " measured=" + measured + " result=" + result);
            if (!DistanceEstimator.valid(measured)) {
                showGuidance(R.string.distance_aim_surface);
            } else if (Float.isNaN(result)) {
                showGuidance(R.string.distance_hold_target);
            } else if (result > DistanceEstimator.MAX_METERS) {
                showReadout(getString(R.string.distance_out_of_range), false);
            } else {
                showReadout(getString(R.string.distance_reading, formatDistance(result)), false);
            }
        } catch (NotYetAvailableException e) {
            logMeasurement("depth not available; tracking=" + frame.getCamera().getTrackingState());
            distanceEstimator.reset();
            showGuidance(R.string.distance_move_sideways);
        }
    }

    private String formatDistance(float distanceMeter) {
        if (distanceMeter < 1.0f) {
            int distanceCm = Math.round(distanceMeter * 100.0f);
            return String.format(Locale.KOREA, "%d cm", distanceCm);
        }

        return String.format(Locale.US, "%.1f m", distanceMeter);
    }

    private void showGuidance(int messageResource) {
        showReadout(getString(messageResource), true);
    }

    private void logMeasurement(String message) {
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastDiagnosticTime < 1000) return;
        lastDiagnosticTime = now;
        Log.d(TAG, message);
    }

    private void showReadout(String text, boolean guidance) {
        runOnUiThread(() -> {
            if (text.equals(lastDistanceText) && guidance == lastWasGuidance) return;
            lastDistanceText = text;
            lastWasGuidance = guidance;
            tvCurrentDistanceValue.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimension(guidance ? R.dimen.distance_hint_size
                            : R.dimen.distance_reading_size));
            tvCurrentDistanceValue.setText(text);
        });
    }

    private void drawCameraBackground() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);
        GLES20.glUseProgram(program);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId);
        GLES20.glUniform1i(textureUniform, 0);

        GLES20.glEnableVertexAttribArray(positionAttribute);
        GLES20.glVertexAttribPointer(
                positionAttribute,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                screenVertexBuffer
        );

        GLES20.glEnableVertexAttribArray(texCoordAttribute);
        GLES20.glVertexAttribPointer(
                texCoordAttribute,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                cameraTexCoordBuffer
        );

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(positionAttribute);
        GLES20.glDisableVertexAttribArray(texCoordAttribute);
        GLES20.glDepthMask(true);
    }

    private int createExternalTexture() {
        int[] textures = new int[1];

        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);

        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR
        );

        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR
        );

        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
        );

        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
        );

        return textures[0];
    }

    private int createCameraProgram() {
        String vertexShaderCode =
                "attribute vec4 a_Position;" +
                        "attribute vec2 a_TexCoord;" +
                        "varying vec2 v_TexCoord;" +
                        "void main() {" +
                        "    gl_Position = a_Position;" +
                        "    v_TexCoord = a_TexCoord;" +
                        "}";

        String fragmentShaderCode =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;" +
                        "uniform samplerExternalOES u_Texture;" +
                        "varying vec2 v_TexCoord;" +
                        "void main() {" +
                        "    gl_FragColor = texture2D(u_Texture, v_TexCoord);" +
                        "}";

        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        int createdProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(createdProgram, vertexShader);
        GLES20.glAttachShader(createdProgram, fragmentShader);
        GLES20.glLinkProgram(createdProgram);

        return createdProgram;
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    private FloatBuffer createFloatBuffer(float[] values) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(values.length * 4);
        byteBuffer.order(ByteOrder.nativeOrder());

        FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
        floatBuffer.put(values);
        floatBuffer.position(0);

        return floatBuffer;
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startDistanceSessionIfReady();
            } else {
                showGuidance(R.string.distance_permission);
                showToast(getString(R.string.distance_camera_permission));
            }
        }
    }
}
