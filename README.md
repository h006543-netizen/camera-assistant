# ShutterNote

필름카메라 촬영을 보조하고 롤별 촬영 기록을 관리하는 Android 앱입니다.
현재 버전은 **1.2**입니다.

## 1.0 이후 핵심 업데이트

- **필름 노트와 롤 관리 추가** — 필름·카메라·촬영 기간·컷 수를 기록하고, 폴더 분류와 롤 순서 변경을 지원합니다.
- **컷별 촬영 기록 확장** — 사진과 촬영 설정·시각·메모를 함께 보관하고, 위치 기록과 지도 확인을 지원합니다.
- **사진 보기와 비교 개선** — 전체 화면 보기, 확대·이동·회전, 컷 간 이동, 실제 필름 스캔과 비교를 지원합니다.
- **필름별 보정 추가** — 50D·E100D·250D·800T·500T·PORTRA400 보정과 원본/보정 전환을 제공하며, 원본 사진과 이전 보정 버전을 보존합니다.
- **저장과 내보내기 확장** — 노트 사진의 갤러리 저장, 롤별 ZIP 내보내기, 완료 알림과 저장 위치 열기를 지원합니다.
- **조작과 안정성 개선** — 기록 편집·키보드 표시·화면 복원을 다듬고, 연속 사진 이동과 보정 처리의 메모리·속도를 개선했습니다.

## 기본 촬영 보조

ISO·조리개·셔터속도에 따른 노출 프리뷰, 35mm·40mm·50mm 화각 프리뷰, 중앙 거리 측정, 노출을 반영한 촬영·저장을 제공합니다. 한국어·영어·프랑스어·일본어를 지원합니다.

## 실행과 검사

Android Studio에서 저장소를 열고 Gradle Sync 후 실행합니다. Java·CameraX·ARCore를 사용합니다.

```bash
git clone https://github.com/h006543-netizen/camera-assistant.git
```

Windows:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

Linux/macOS:

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

보정 자산과 공개 회귀 기준 데이터는 저장소에 포함됩니다. 개인 원본 사진과 로컬 학습·보고서 폴더는 포함하지 않으며, 해당 자료가 필요한 검사 4개는 공개 클론에서 사유와 함께 건너뜁니다.

## 문서

- [제품 명세](PROJECT_SPEC.md)
- [버전별 변경 기록](RELEASE_NOTES.md)
- [1.2 검증 기록](RELEASE_VALIDATION_1.2.md)
- [거리 측정 검증](DISTANCE_VALIDATION.md)
- [화각 검증](FIELD_OF_VIEW_VALIDATION.md)
- [출시 체크리스트](RELEASE_CHECKLIST.md)
