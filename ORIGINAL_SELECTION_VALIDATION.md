# 원본 선택 시 보정 해제 유지 검증 (2026-10-23)

이 요청은 기존 ‘원본은 임시 비교이며 선택한 필름을 유지’ 정책을 대체한다. 원본 선택이 실제 적용 상태와 저장·내보내기에 반영된다.

| 완료 기준 | 변경 및 확인 |
|---|---|
| 원본 선택 저장 | 노트 상세의 원본 메뉴가 FilmFrameCorrectionViewModel의 보정 해제 경로를 호출한다. 50D/E100D/250D/800T/500T 각각 선택 후 원본으로 변경해 correctionEnabled=false와 기존 보정 JPEG 삭제 및 원본 바이트 보존을 확인했다. |
| 뒤로가기·재진입·재생성 | 해제 저장 중의 툴바/시스템 뒤로가기는 커밋 완료 후 종료한다. 원본 선택 뒤 시스템 뒤로가기/다시 열기와 화면 재생성에서 원본 메뉴와 해제 상태를 확인했다. 임시 비교 목록을 저장 상태로 복원하던 코드를 제거했다. |
| 즉시 화면 반영·실패 보존 | 준비된 원본 Bitmap을 즉시 표시한다. 커밋 성공 시 기존 원본 Bitmap을 유지하면서 보정 Bitmap을 해제한다. 실패 시 이전 보정 화면을 복원하고 오류를 알린다. 저장소의 해제 커밋 실패 검사에서 기존 설정/원본/보정 사본 보존을 재확인했다. UI 실패 표시는 소스 확인이며 실제 기기 저장 실패 주입 검사는 하지 않았다. |
| 목록·스캔·갤러리·ZIP | 상세에서 원본으로 바꾸고 툴바 뒤로가기로 복귀한 목록과 스캔 비교의 원본 픽셀을 확인했다. 새 갤러리 저장 버튼으로 보정 사진을 저장한 뒤 원본 선택 후 다시 저장하면 노트 원본과 바이트가 같은 사진이 생성됨을 확인했다. ZIP 원본 분기는 기존 FilmRollPhotoArchiveTest의 보정 해제 검사로 확인했다. |
| 촬영 결과 | 노트 촬영의 보정→원본→재생성→저장에서 보정 없음/보정 사본 없음/빈 프로필을 확인했다. 일반 촬영은 저장 후 원본을 선택하면 갤러리 보정 설정과 내부 사본을 해제한다. 재생성 후에도 해제되며 외부 갤러리의 기존 원본 한 장은 유지된다. 저장 전 화면에서는 준비된 메모리 사본으로 재선택할 수 있으나 실제 선택/저장은 원본을 따른다. |
| 기존 동작·보정값 | 사진만 보기 및 다음 필름으로 이동한 뒤 툴바/시스템 뒤로가기 검사 4개 통과. 기존 보정 자산 12개는 직전 NoteGallery APK와 동일하다(`.artifact-build/original-selection-profile-preservation.json`). PC Studio/학습 결과는 수정하지 않았다. |

- `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest --offline` 성공: `.artifact-build/original-selection-build.log`. 마지막 갤러리 연계 강화는 테스트 코드만 변경하고 Android 테스트 APK 재빌드 성공: `original-selection-test-build.log`.
- 단위 226개 실패/오류/건너뜀 0. Lint 오류 0/경고 124.
- 읽기 전용 API 36.1 에뮬레이터의 Android 검사 28개 통과, 100.345초: `.artifact-build/original-selection-device-tests.log`. 최신 필름/촬영 3, 노트 목록·상세/스캔 2, 일반 결과 1, 일반 갤러리 1, 노트 갤러리 5, 기존 저장소 12, 뒤로가기 4개다.
- 자체 UUID 노트/사진과 이번 검사에서 반환된 갤러리 URI만 만들고 정리했다. 실제 사용자 휴대폰은 조작하지 않았다. 실제 휴대폰/API 24~28/프로세스 강제 종료·저장소 부족은 미검증이다.
- 설치 APK: `outputs/ShutterNote_OriginalSelectionFix_20261023_debug.apk`. 빌드 출력/배포 사본 해시 일치, 크기·SHA256은 `.artifact-build/original-selection-delivery.json`에 기록했다.
