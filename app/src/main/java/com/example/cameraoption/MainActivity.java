package com.example.cameraoption;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

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
        findViewById(R.id.btnLanguage).setOnClickListener(view -> showLanguageDialog());
        findViewById(R.id.btnPrivacyPolicy).setOnClickListener(view -> PrivacyUi.openPolicy(this));
        findViewById(R.id.btnArPrivacy).setOnClickListener(view -> PrivacyUi.showArNotice(this, null));

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
        if (exposureSettingsStore != null && tvIsoValue != null) {
            restoreExposureSettings();
        }
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
