ShutterNote

필름카메라 촬영을 조금 더 편하게 하기 위해 만든 Android 앱입니다.

필름카메라는 촬영 전에 결과를 바로 확인하기 어렵고, 목측식 카메라는 피사체와의 거리도 직접 판단해야 합니다. ShutterNote는 스마트폰 카메라를 보조 도구로 활용해 촬영 전에 노출과 화각을 확인하고, 필요한 경우 피사체까지의 거리도 측정할 수 있도록 만들었습니다.

사용자의 촬영값을 자동으로 정해 주는 앱이라기보다, 필름카메라를 사용할 때 직접 판단하는 데 필요한 정보를 빠르게 확인하는 데 초점을 맞췄습니다.


주요 기능

1. 노출 프리뷰

ISO, 조리개, 셔터속도를 직접 설정하면 해당 값에 따른 밝기 변화를 카메라 화면에서 확인할 수 있습니다.

지원 ISO
50 / 100 / 125 / 160 / 200 / 250 / 400 / 500 / 800 / 1600 / 3200

조리개와 셔터속도도 직접 선택할 수 있으며, 설정한 값은 프리뷰에 바로 반영됩니다. 마지막으로 사용한 설정은 앱을 다시 실행해도 유지됩니다.


2. 35mm 기준 화각 확인

스마트폰 카메라를 이용해 필름카메라에서 사용할 화각을 미리 확인할 수 있습니다.

현재 지원하는 화각은 기본 화각, 35mm, 40mm, 50mm입니다.

스마트폰 카메라의 센서와 초점거리 정보를 바탕으로 35mm 필름 기준 화각을 계산해 화면에 적용합니다. 프리뷰와 촬영 결과는 3:2 비율을 기준으로 합니다.

이 기능은 구도를 미리 확인하기 위한 용도이며, 실제 렌즈의 왜곡이나 심도, 보케 같은 광학적 특성까지 그대로 재현하는 기능은 아닙니다.


3. 중앙 피사체 거리 측정

ARCore Depth를 이용해 화면 중앙에 있는 피사체까지의 거리를 측정할 수 있습니다.

거리 측정 화면에서는 카메라 프리뷰와 중앙 기준점, 측정 거리, 현재 측정 상태를 간단하게 확인할 수 있습니다.

예)
대상까지 2.7 m

약 10m 이내의 피사체 측정을 기준으로 하며, 측정이 어렵거나 지원 범위를 벗어난 경우에는 임의의 값을 표시하지 않고 다시 측정하거나 지원 여부를 확인할 수 있도록 안내합니다.


4. 촬영 결과 확인 및 저장

현재 설정한 ISO, 조리개, 셔터속도의 노출 효과를 반영한 이미지를 촬영한 뒤 저장 전에 확인할 수 있습니다.

저장되는 파일 이름에는 촬영 설정과 시간이 함께 기록됩니다.

예)
ISO400_f2.8_1-125_35mm_20260805_234500.jpg

갤러리에서도 어떤 설정으로 촬영했는지 바로 확인할 수 있도록 구성했습니다.


지원 언어

한국어
English
Français
日本語

앱 안에서 언어를 변경할 수 있으며, 선택한 언어는 다시 실행해도 유지됩니다.


기술 스택

Platform: Android
Language: Java
Java Version: Java 11
Camera: AndroidX CameraX 1.3.4
Distance Measurement: Google ARCore 1.54.0
Minimum SDK: API 24
Target SDK: API 36
Build System: Gradle


프로젝트 구조

camera-assistant/
├─ app/                    Android 애플리케이션
├─ branding/               앱 아이콘 및 브랜딩 자료
├─ gradle/                 Gradle 설정
├─ privacy-site/           개인정보처리방침 페이지
├─ PROJECT_SPEC.md         프로젝트 전체 기능 명세
├─ DISTANCE_VALIDATION.md  거리 측정 검증 문서
├─ FIELD_OF_VIEW_VALIDATION.md
├─ RELEASE_CHECKLIST.md    출시 전 확인 항목
└─ RELEASE_NOTES.md        버전별 변경 사항


실행 방법

저장소를 Clone합니다.

git clone https://github.com/h006543-netizen/camera-assistant.git

Android Studio에서 프로젝트를 연 뒤 Gradle Sync를 진행하면 됩니다.

Debug APK 빌드

./gradlew assembleDebug

단위 테스트

./gradlew testDebugUnitTest

Lint 검사

./gradlew lintDebug


권한 및 기기 요구사항

카메라 프리뷰와 촬영 기능을 사용하려면 카메라 권한이 필요합니다.

거리 측정 기능은 Google Play Services for AR(ARCore)을 사용합니다. ARCore 또는 Depth 기능을 지원하지 않는 기기에서는 거리 측정 기능에 제한이 있을 수 있지만, 노출 프리뷰와 촬영 기능은 별도로 사용할 수 있습니다.


현재 버전

ShutterNote 1.0

현재 구현된 기능

- ISO / 조리개 / 셔터속도 기반 노출 프리뷰
- 3:2 촬영 프레임
- 35mm 기준 화각 프리뷰
- 중앙 피사체 거리 측정
- 노출 효과가 적용된 이미지 확인 및 저장
- 한국어 / 영어 / 프랑스어 / 일본어 지원

현재 포함하지 않은 기능

- 자동 노출 추천
- 추천 셔터속도 / 조리개 / ISO
- 초점 거리 추천
- 심도 계산
- 카메라 및 렌즈별 프로파일
- 필름 종류 선택
- 필름 색감 시뮬레이션


관련 문서

PROJECT_SPEC.md
전체 기능과 동작 기준을 정리한 문서입니다.

DISTANCE_VALIDATION.md
거리 측정 기능의 검증 기준을 정리했습니다.

FIELD_OF_VIEW_VALIDATION.md
화각 계산과 검증 기준을 정리했습니다.

RELEASE_CHECKLIST.md
배포 전에 확인해야 할 항목을 정리했습니다.

RELEASE_NOTES.md
버전별 변경 사항을 기록합니다.

