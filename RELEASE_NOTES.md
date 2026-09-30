# ShutterNote 1.0

2026-09-30 갱신한 1.0 등록 준비 버전이다. 앱 버전은 `versionName 1.0`, `versionCode 1`을 유지한다.

- applicationId, namespace 및 Java 소스/테스트 패키지를 `com.shutternote`로 변경했다.
- 메인 화면과 노출계의 공통 ISO 선택지에 80을 추가했다. ISO는 총 12개이며 기본값은 200이다.
- 이전 패키지의 R import를 제거하고 새 패키지의 리소스 생성 및 빌드를 검증했다.
- 이전 `com.example.cameraoption` 설치본과는 별도 앱으로 설치되며 기존 설정은 자동으로 이전하지 않는다.

기존에 GitHub에 등록된 `v1.0` 태그는 이전 커밋을 가리킨다. 이번 준비 작업에서는 기존 태그를 변경하거나 새 커밋을 원격에 업로드하지 않는다.

- 모든 지원 언어에서 ShutterNote 이름 사용, 투명 렌즈 전경과 단색 배경의 앱 아이콘 적용.
- ISO·조리개·셔터속도 수동 노출 프리뷰, 3:2 촬영 영역과 환산 화각 선택.
- 중앙 피사체 거리 측정과 미지원·재측정 안내.
- 노출이 반영된 촬영 결과 확인 및 저장. 1.0에서 필름 선택·보정 제외.
- 한국어·영어·프랑스어·일본어 UI 및 개인정보·ARCore 안내.

검증: `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest`(38개), `lintDebug` 성공. 기기 테스트 APK는 빌드만 검증했으며 실기기 테스트는 실행하지 않았다.

실제 기기에서의 거리 정확도·화각·전체 흐름 검증과 공개 개인정보처리방침 운영자·연락처 확정은 남아 있다. 자세한 항목은 `RELEASE_CHECKLIST.md`, `DISTANCE_VALIDATION.md`, `FIELD_OF_VIEW_VALIDATION.md` 및 `branding/README.md`를 참조한다. 이 태그는 현재 소스 버전의 기록이며 스토어 출시 완료를 의미하지 않는다.
