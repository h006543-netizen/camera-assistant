package com.example.cameraoption;

import android.Manifest;
import android.content.pm.PackageManager;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
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
import com.google.ar.core.Config;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.DepthPoint;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.FatalException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class DistanceCameraActivity extends AppCompatActivity implements GLSurfaceView.Renderer {

    private Float smoothedDistanceMeter = null;

    private static final String TAG = "DistanceCameraActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private static final long DISTANCE_SAMPLE_INTERVAL_NANOS = 150_000_000L;

    private FrameLayout cameraPreviewContainer;
    private TextView btnDistanceBack;
    private TextView tvCurrentDistanceValue;

    private GLSurfaceView glSurfaceView;
    private Session arSession;
    private boolean installRequested = false;
    private boolean arSessionResumed = false;

    private int viewportWidth = 0;
    private int viewportHeight = 0;

    private int cameraTextureId = -1;
    private int program = 0;
    private int positionAttribute = 0;
    private int texCoordAttribute = 0;
    private int textureUniform = 0;

    private FloatBuffer screenVertexBuffer;
    private FloatBuffer cameraTexCoordBuffer;

    private long lastUiUpdateTime = 0;
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

        SystemBarInsets.applyTopAndBottomMargin(
                this,
                findViewById(R.id.topDistanceBar),
                findViewById(R.id.distanceHudLayout)
        );

        cameraPreviewContainer = findViewById(R.id.cameraPreviewContainer);
        btnDistanceBack = findViewById(R.id.btnDistanceBack);
        tvCurrentDistanceValue = findViewById(R.id.tvCurrentDistanceValue);

        btnDistanceBack.setOnClickListener(view -> finish());
        tvCurrentDistanceValue.setText("-- m");

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
            handleArStartFailed("카메라를 사용할 수 없습니다.");

        } catch (FatalException e) {
            Log.e(TAG, "ARCore FatalException", e);
            handleArStartFailed("ARCore 거리 측정을 시작할 수 없습니다.");

        } catch (Exception e) {
            Log.e(TAG, "ARCore start failed", e);
            handleArStartFailed("거리계를 실행할 수 없습니다.");
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
        smoothedDistanceMeter = null;
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
                updateDistanceTextOnUi("설치 필요");
                return false;
            }

            arSession = new Session(this);

            Config config = arSession.getConfig();

            /*
                사진 속 거리계 앱에 가까운 방식입니다.

                DepthImage를 직접 읽지 않고,
                ARCore가 인식한 평면, 특징점, 깊이 지점에 대해
                화면 중앙 hitTest를 수행합니다.

                HORIZONTAL_AND_VERTICAL:
                바닥뿐 아니라 벽도 인식하게 합니다.

                DepthMode:
                지원되면 DepthPoint hitTest도 사용하고,
                미지원이어도 Plane / Point hitTest는 계속 사용할 수 있게 합니다.
            */
            config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
            config.setFocusMode(Config.FocusMode.AUTO);
            config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);

            if (arSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                config.setDepthMode(Config.DepthMode.AUTOMATIC);
            } else {
                config.setDepthMode(Config.DepthMode.DISABLED);
            }

            arSession.configure(config);
            return true;

        } catch (UnavailableUserDeclinedInstallationException e) {
            Log.e(TAG, "User declined ARCore installation", e);
            updateDistanceTextOnUi("설치 필요");
            showToast("Google Play Services for AR 설치가 필요합니다.");
            return false;

        } catch (Exception e) {
            Log.e(TAG, "createArSession failed", e);
            updateDistanceTextOnUi("미지원");
            showToast("ARCore를 시작할 수 없습니다.");
            return false;
        }
    }

    private void handleArStartFailed(String message) {
        updateDistanceTextOnUi("미지원");
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
        if (arSession == null || cameraTextureId == -1) {
            return;
        }

        try {
            arSession.setCameraTextureName(cameraTextureId);

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

            long frameTimestamp = frame.getTimestamp();
            if (lastDistanceSampleNanos == 0L
                    || frameTimestamp - lastDistanceSampleNanos
                    >= DISTANCE_SAMPLE_INTERVAL_NANOS) {
                lastDistanceSampleNanos = frameTimestamp;
                updateDistanceByHitTest(frame);
            }

        } catch (Throwable e) {
            Log.e(TAG, "onDrawFrame failed", e);
            updateDistanceTextOnUi("-- m");
        }
    }

    private void updateDistanceByHitTest(Frame frame) {
        if (viewportWidth == 0 || viewportHeight == 0) {
            return;
        }

        if (frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
            updateDistanceTextOnUi("인식 중");
            return;
        }

        float centerX = viewportWidth / 2.0f;
        float centerY = viewportHeight / 2.0f;

        float offset = getResources().getDisplayMetrics().density * 12.0f;

        // The center plus four nearby points is enough for a stable median while
        // avoiding nine AR hit tests on every sampled frame.
        float[][] samplePoints = {
                {centerX, centerY},
                {centerX - offset, centerY},
                {centerX + offset, centerY},
                {centerX, centerY - offset},
                {centerX, centerY + offset}
        };

        ArrayList<Float> distances = new ArrayList<>();

        for (float[] point : samplePoints) {
            HitResult hit = findBestHit(frame.hitTest(point[0], point[1]));

            if (hit != null) {
                float distance = hit.getDistance();

                if (distance > 0.15f && distance < 10.0f) {
                    distances.add(distance);
                }
            }
        }

        if (distances.isEmpty()) {
            updateDistanceTextOnUi("인식 중");
            return;
        }

        Collections.sort(distances);

        float measuredDistance = distances.get(distances.size() / 2);

        if (smoothedDistanceMeter == null) {
            smoothedDistanceMeter = measuredDistance;
        } else {
            float difference = Math.abs(measuredDistance - smoothedDistanceMeter);

            if (difference > 1.5f) {
                smoothedDistanceMeter = measuredDistance;
            } else {
                smoothedDistanceMeter = (smoothedDistanceMeter * 0.7f) + (measuredDistance * 0.3f);
            }
        }

        updateDistanceTextOnUi(formatDistance(smoothedDistanceMeter));
    }

    private HitResult findBestHit(List<HitResult> hitResults) {
        HitResult planeHit = null;
        HitResult pointHit = null;

        for (HitResult hit : hitResults) {
            Trackable trackable = hit.getTrackable();

            if (trackable instanceof DepthPoint) {
                DepthPoint depthPoint = (DepthPoint) trackable;

                if (depthPoint.getTrackingState() == TrackingState.TRACKING) {
                    return hit;
                }
            }

            if (trackable instanceof Plane) {
                Plane plane = (Plane) trackable;

                if (plane.getTrackingState() == TrackingState.TRACKING &&
                        plane.isPoseInPolygon(hit.getHitPose())) {
                    if (planeHit == null) {
                        planeHit = hit;
                    }
                }
            }

            if (trackable instanceof Point) {
                Point point = (Point) trackable;

                if (point.getTrackingState() == TrackingState.TRACKING) {
                    if (pointHit == null) {
                        pointHit = hit;
                    }
                }
            }
        }

        if (planeHit != null) {
            return planeHit;
        }

        return pointHit;
    }

    private String formatDistance(float distanceMeter) {
        if (distanceMeter < 1.0f) {
            int distanceCm = Math.round(distanceMeter * 100.0f);
            return String.format(Locale.KOREA, "%d cm", distanceCm);
        }

        return String.format(Locale.US, "%.1f m", distanceMeter);
    }

    private void updateDistanceTextOnUi(String text) {
        long now = System.currentTimeMillis();

        if (now - lastUiUpdateTime < 120) {
            return;
        }

        lastUiUpdateTime = now;

        runOnUiThread(() -> tvCurrentDistanceValue.setText(text));
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
                showToast("거리계 사용에는 카메라 권한이 필요합니다.");
            }
        }
    }
}
