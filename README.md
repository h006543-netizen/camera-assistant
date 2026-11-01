ShutterNote

필름카메라 촬영을 보조하기 위한 Android 앱입니다.

현재 버전은 **1.2** (`versionCode 2`)입니다.

## 1.0 이후 핵심 업데이트

- 필름 노트 추가: 롤·폴더·컷별 촬영 기록 관리
- 촬영 정보 확장: 설정·시각·메모·위치 기록과 지도 확인
- 사진 보기 개선: 확대·회전·컷 이동·실제 필름 스캔 비교
- 필름 6종 보정 추가 및 원본/보정 결과 전환
- 갤러리 저장과 롤별 ZIP 내보내기 지원
- 기록 편집·화면 복원·사진 처리 속도와 안정성 개선
주요 기능

- 노출 프리뷰
  ISO, 조리개, 셔터속도를 설정해 촬영 전 밝기를 확인할 수 있습니다.

- 화각 프리뷰
  35mm, 40mm, 50mm 화각을 스마트폰 카메라 화면에서 미리 확인할 수 있습니다.

- 거리 측정
  화면 중앙 피사체까지의 거리를 측정합니다.

- 촬영 및 저장
  현재 설정한 노출값을 반영한 이미지를 촬영하고 저장할 수 있습니다.

- 설정 유지
  마지막으로 사용한 촬영 설정과 언어 설정을 저장합니다.

지원 언어

- 한국어
- English
- Français
- 日本語

사용 기술

- Android
- Java
- CameraX
- ARCore
- Gradle

실행 방법

1. 저장소를 Clone합니다.
2. Android Studio에서 프로젝트를 엽니다.
3. Gradle Sync 후 실행합니다.

git clone https://github.com/h006543-netizen/camera-assistant.git

프로젝트 문서

- PROJECT_SPEC.md
- DISTANCE_VALIDATION.md
- FIELD_OF_VIEW_VALIDATION.md
- RELEASE_CHECKLIST.md
- RELEASE_NOTES.md
- RELEASE_VALIDATION_1.2.md

검사

Windows: `gradlew.bat testDebugUnitTest assembleDebug lintDebug`

Linux/macOS: `./gradlew testDebugUnitTest assembleDebug lintDebug`

보정 자산과 공개 JSON·합성 회귀 기준 데이터는 저장소에 포함됩니다. 원본 해상도의 개인 사진/이진 실험 자료와 PC Film Studio의 로컬 학습·보고서 작업폴더는 포함하지 않습니다. 해당 자료가 필요한 정밀/성능 검사4개는 공개 클론에서 사유를 표시하고 건너뜁니다. 나머지 검사는 자료 없이 실행할 수 있습니다.

GitHub 릴리스의 APK는 Debug 패키지·서명을 사용하는 테스트용입니다. 실제 Android 기기 및 Play 출시 검증 범위는 릴리스 기록을 참고하세요.
