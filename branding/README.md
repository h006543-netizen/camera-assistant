# ShutterNote

## 현재 아이콘 — 전체 채움 적용

- 별도 배경색과 외곽 여백을 제거하고 카메라 표면이 아이콘 전체를 채우도록 변경했다. 아래 투명 전경 및 25% 여백 설명은 이전 버전 기록이다.
- 현재 원본은 `shutternote-camera-fullbleed.png`이며 `generate_icons.py`로 일반·원형·배포 아이콘을 생성한다. 적응형 이미지의 중앙 72/108 영역에 원본을 배치하고 가장자리 픽셀을 오버스캔 영역까지 연장한다.
- 일반/원형 이미지 육안 확인 및 불투명 alpha 검증 완료. 실제 기기별 런처 마스크 표시는 미검증이다.
- 명세 완료 기준 대조: 아이콘 외 기능은 변경하지 않았다. 아래 기능별 검증 근거와 미완료 실기기 검증 항목은 유지한다.

## 현재 아이콘 — 2026-09-30 카메라 디자인 적용

- 사용자가 선택한 상단 실버·중앙 블랙·하단 실버의 카메라 시안을 적용했다. 중앙 렌즈, 왼쪽 플래시, 오른쪽 상단 도트를 유지한다.
- 현재 원본은 `shutternote-camera-transparent.png`다. 아래의 렌즈·청록색·주황색 설명은 이전 디자인 기록이다.
- `generate_icons.py`로 투명 전경, 5개 밀도의 일반/원형 아이콘, 512px 배포 이미지를 생성한다. 배경은 `#242424`, 적응형 여백은 각 변 25%다. 기존 리소스 이름 `shutternote_lens`는 참조 호환성을 위해 유지한다.
- 단색 테마 벡터도 카메라 몸체·렌즈·플래시·도트로 변경했다. 메인 화면은 명세대로 제목만 표시한다.
- 투명 alpha와 배포 이미지 육안 확인을 완료했다. 실제 기기의 런처 마스크와 테마 아이콘 표시는 미검증이다.
- 이번 변경의 `:app:assembleDebug`, `:app:lintDebug`가 통과했다. 기능 로직은 수정하지 않아 단위 테스트를 재실행하지 않았다.
- 제품 완료 기준 대조: 이번 변경은 아이콘과 브랜드 명세에 한정된다. 아래 표의 기능별 근거와 실기기 미검증 사항은 유지하며 제품 전체 완료를 주장하지 않는다.

2026-09-29 적용. 모든 언어에서 앱 이름은 ShutterNote. 메인 화면의 앱 이미지는 제거하고 제목만 표시한다.

## 이미지

- `shutternote-icon-source.png`: 사용자가 선택한 내장 image_gen 생성 원본.
- `shutternote-play-icon.png`: 512×512 PNG 배포용 이미지.
- 앱의 `drawable-nodpi/shutternote_lens.png`: 적응형 아이콘용 이미지.
- `mipmap-*`: Android 7 이상 일반/원형 런처용 48, 72, 96, 144, 192px PNG.
- 적응형 아이콘은 각 변 25% 안쪽 여백으로 렌즈와 주황색 기준점을 보호한다. 단색 테마에는 별도의 렌즈 벡터를 사용한다.

원본은 보존하고, 내장 image_gen으로 사각 배경을 제거한 `shutternote-lens-transparent.png`를 새 전경으로 사용한다. 모든 배포 아이콘은 이 투명 전경과 단색 #242424 배경으로 구성한다. applicationId와 namespace는 com.shutternote로 변경했다. 기존 설치본과는 별도 앱으로 인식된다. 테마 식별자와 CameraOption 갤러리 경로는 유지한다. 개인정보 안내에 남은 CameraOption 앨범 표기는 실제 저장 경로를 가리킨다. 개인정보 웹페이지는 로컬 산출물만 갱신했으며 게시하지 않았다.

## 생성 프롬프트

Built-in image_gen, opaque background:

> Use case: logo-brand. Asset type: Android camera companion app icon concept, a single square full-bleed image, 1024x1024. Primary request: original camera lens app icon for Korean app ShutterNote (셔터노트), a simple manual exposure preview and subject distance tool accompanying real film cameras. Create one beautifully resolved icon, not a presentation board. Centered straight-on camera lens, strong simple circular silhouette occupying about 64 percent of square width with generous safe margins. Matte charcoal full-bleed background, two clean concentric brushed silver and graphite lens rings, dark optical glass with restrained deep teal reflection, subtle six-blade aperture visible within glass. One small warm amber-orange alignment mark at top of outer ring makes recognizable signature. Refined tactile analog camera industrial design, semi-realistic dimensional materials but reduced detail for excellent small-size legibility, crisp edges, gentle studio highlights, restrained contrast and elegant visual hierarchy. No text, letters, numbers, brand logos, notebook, camera body, tiny tick marks, rainbow, neon glow, excessive lens flare, watermark, mockup phone, surrounding frame or pre-rounded square corners. Do not reproduce any existing camera app icon. This is an exploratory visual proposal, not a production icon package.

## 검증

- `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` 성공.
- 기존 단위 테스트 38개, 실패/오류 0개.
- 한국어/기본 및 세 외국어의 app_name/main_title 확인. Manifest는 app_name과 일반/원형 런처 리소스를 참조한다.
- 일반/원형 PNG 및 원본 육안 확인. 실제 Android 런처의 마스크·테마 아이콘·언어 전환 표시는 아직 실기기 미검증.

제품 명세 완료 기준과 이번 변경의 대조(제품 전체 출시 완료를 의미하지 않음):

| 명세 항목 | 근거 / 남은 검증 |
|---|---|
| 네 언어 개인정보 및 ARCore 안내 | 새 브랜드로 로컬 안내 생성. 운영자·공개 URL·실기기 흐름은 출시 체크리스트의 미완료 사항 유지 |
| 네 언어 전환 및 설정 유지 | 다섯 strings 리소스의 이름 일치. 재실행·실기기 전환은 미검증 |
| 노출값 선택 및 두 카메라 진입 | 메인 레이아웃 제목을 통일하고 장식 이미지 제거, 빌드 성공. 실기기 진입 미검증 |
| 노출값 재실행 복원 | ExposureSettingsStoreTest 통과. 실기기 복원 미검증 |
| 화각 선택·동일 줌·복원 | FieldOfViewCalculatorTest 통과. 실기기 흐름 미검증 |
| 3:2 프레임 및 실제 화각 | FIELD_OF_VIEW_VALIDATION.md의 실기기 검증 필요 |
| 결과 화면 복귀 후 화각 유지 | 이번 변경 대상 아님. 실기기 미검증 |
| 노출값 변경 시 밝기 | ExposureCalculatorTest/ExposureBrightnessTest 통과. 실기기 프리뷰 미검증 |
| 효과 반영 사진 전달 | 빌드 성공. 실기기 촬영 미검증 |
| 필름 선택·보정 제외 | 이번 변경에서 기능 추가 없음. 실기기 결과 미검증 |
| 갤러리 파일명 | GalleryFileNameBuilderTest 통과. 기존 앨범 경로 유지 |
| 거리 표시 및 실패 안내 | DistanceEstimatorTest 통과. 실기기·권한 흐름 미검증 |
| 거리 표본 제한·중앙 표본·연속 프레임 | DistanceEstimatorTest 통과. 센서 통합 실기기 검증 필요 |
| 거리 실측·지연·오차 | DISTANCE_VALIDATION.md의 실측 작업 필요 |
| ARCore 비필수 | Manifest의 ARCore optional 유지. 미지원 기기 미검증 |
| 제외 기능 미추가 | 이번 변경은 브랜딩 리소스·안내·문서에 한정 |
| API 24 이상·빌드·자동화 테스트 | 빌드 및 38개 테스트 통과. API별 실제 실행 미검증 |

## 배경 경계 수정

사용자의 실기기 화면에서 사각 경계가 확인되어 투명 전경으로 교체했다. 이미지 편집 도구: built-in image_gen, transparent_background=true. 편집 프롬프트: 원형 렌즈 외부의 사각 배경 및 그림자를 투명 alpha로 제거하고 은색 링, 청록색 유리, 조리개와 주황색 기준점을 유지한다. 이름과 메인 화면은 변경하지 않았다.

## 2026-09-30 패키지 및 ISO 변경

- applicationId, namespace, Java 소스/테스트 패키지와 커스텀 레이아웃 참조를 `com.shutternote`로 통일했다.
- 명세의 노출값 선택·복원 항목: 메인/노출계가 사용하는 공통 ISO 목록에 80을 추가하고 정규화 테스트 및 기기 저장/복원 테스트에 반영했다.
- 기존 설치본과는 별도 앱이며 설정 자동 이전은 제공하지 않는다. 기존 갤러리 사진은 유지된다.
- 위 완료 기준 대조표의 나머지 실기기 미검증 사항은 그대로 남아 있다. 기기 테스트 APK 빌드는 실제 테스트 실행을 뜻하지 않는다.
