# 필름 노트 컷 화면 구현 검증

## 이전 사진 10% 노출과 상세 컷 위치 복귀 (2026-10-18)

| 완료 기준 | 구현 및 확인 근거 | 확인 범위 |
|---|---|---|
| 이전 사진 노출 확대 | FilmRollFramesActivity의 왼쪽 패딩을 화면 너비 10%+열 간격 12dp로 계산하고 열 너비를 다시 산정한다. clipToPadding=false 및 시작 정렬을 유지한다. | Android 검사에서 이전 framePhoto의 실제 보이는 전역 영역 너비가 화면 너비 10%인지 2px 오차로 대조해 통과. 첫 열 버튼·스크러버 끝 이동·마지막 열 정렬과 10% 표시도 통과 |
| 다음 필름 첫 컷으로 목록 복귀 | FilmFrameDetailActivity가 새 목록에 롤 ID와 첫 컷, 상세 열기/사진만 보기 플래그를 전달한다. 새 목록이 그 컷으로 초기화되고 상세를 ActivityResultLauncher로 연다. | toolbarBackAfterNextOpensTheNewFilmsList: 마지막 기록 21컷인 B 필름의 7컷을 연 뒤 돌아오면 7/8열이 왼쪽 패딩에 정렬됨 |
| 상세에서 바뀐 컷으로 복귀 | 상세 updateIndex가 현재 컷/롤 ID를 결과로 갱신하고 목록이 같은 롤 결과를 받아 해당 열로 이동한다. 일반 목록 진입도 같은 결과 요청을 사용한다. | changedCutInNextFilmReturnsToThatCut: 다음 필름 B에서 7→21컷 이동 후 21/22열 복귀. changedCutAndRecreationReturnToViewedColumn: 일반 상세 7→21컷, 상세 재생성 후 21/22열 복귀와 목록 재생성 시 위치 유지 |
| 연속 이동과 사진만 보기 | 기존 역순/이력 교체·사진만 보기 전달/첫 시스템 뒤로가기 해제는 유지한다. | repeatedNextAndPhotoOnlySystemBackReturnToLatestFilmsList: A→B→C 후 사진만 보기 해제, 두 번째 뒤로가기에서 C의 열린 13/14열 복귀. 과거 롤 화면 이력 없음 |

- assembleDebug·testDebugUnitTest·lintDebug·assembleDebugAndroidTest 성공. 단위 검사 219개 실패/오류/건너뜀 0, Lint 오류 0/경고 126. 로그: .artifact-build/frame-return-final-build.log.
- 읽기 전용 Medium_Phone_API_36.1 에뮬레이터에서 FilmNextRollBackStackTest 4개 통과(13.731초). UUID 자체 롤/사진만 만들고 정리했다. 로그: .artifact-build/frame-return-final-android-tests.log. 실제 휴대폰 표시·물리 터치 감각·프로세스 강제 종료는 미확인이다.
- 설치용 APK: outputs/ShutterNote_FramePosition_20261018_debug.apk. 아래의 이전 릴리스 기록 중 다음 필름 목록/상세를 한 번에 startActivities로 열던 구현은 현재 목록이 상세를 결과 요청으로 여는 방식으로 대체했다.

## 다음 필름 역순 이동과 현재 필름 목록 복귀 (2026-10-18)

이 절은 아래 과거 검증의 정방향 탐색 및 이전 상세 이력 보존 기준을 대체한다.

| 완료 기준 | 구현 및 확인 근거 | 확인 범위 |
|---|---|---|
| 다음 필름 순서 반전 | FilmNextRollNavigation.find가 저장 목록의 현재 인덱스 직전부터 역순으로 검색한다. 같은 폴더만 대상으로 하고 빈 롤/누락 사진/잘못된 컷을 제외하며 첫 유효 컷을 연다. 목록 순서는 저장하거나 변경하지 않는다. | FilmNextRollNavigationTest 7개 통과: 역순, 폴더, 빈 롤/파일, 첫 컷, 재정렬, 끝/없는 현재 롤, 연속 탐색 |
| 새 필름에서 상단 뒤로가기 | FilmFrameDetailActivity.openNextRoll이 CLEAR_TOP으로 FilmRollFramesActivity를 새 롤 ID로 재생성한 후 상세를 연다. 기존 목록의 ViewModel을 재사용하지 않으며 원래 상세를 종료한다. | FilmNextRollBackStackTest.toolbarBackAfterNextOpensTheNewFilmsList 통과: A→B 후 B 목록 복귀 및 A 화면 이력 제거 |
| 연속 이동·사진만 보기·시스템 뒤로가기 | 새 상세에 photoOnly를 전달하고 기존 첫 뒤로가기 처리를 유지한다. 매 이동마다 목록과 상세 이력을 바꾼다. | FilmNextRollBackStackTest.repeatedNextAndPhotoOnlySystemBackReturnToLatestFilmsList 통과: A→B→C 후 첫 뒤로가기에서 사진만 보기 해제, 두 번째에 C 목록 복귀 및 과거 롤 이력 제거 |
| 마지막 사진 표시·작업 보호 | 기존 55% 표시와 버튼 진입을 유지한다. 진입 시 편집 취소/저장 보호를 먼저 적용하고 최신 저장소에서 다음 대상을 다시 찾는다. | 소스 대조. 실제 휴대폰 제스처 감각·편집 중 진입은 미확인 |

- assembleDebug·testDebugUnitTest·lintDebug·assembleDebugAndroidTest 성공. 전체 단위 검사 219개, 실패/오류/건너뜀 0. Lint 오류 0/경고 126. 로그: .artifact-build/next-roll-reverse-build.log 및 next-roll-reverse-final-build.log.
- 읽기 전용 Medium_Phone_API_36.1 에뮬레이터에서 위 Android 검사 2개 통과(6.049초). 테스트는 UUID로 만든 자체 롤/사진만 사용하고 정리한다. 로그: .artifact-build/next-roll-back-stack-tests.log. 첫 실행의 화면 종료 비동기 대기 부족은 검사에서 최대 5초 종료 대기로 수정했으며 제품 코드 변경 없이 재실행 통과했다.
- 실제 사용자 휴대폰 설치/촬영 및 물리 기기 화면 표시는 미확인이다. 배포물: outputs/ShutterNote_FilmNavigation_20261018_debug.apk.

## 마지막 사진에서 다음 필름 보기 (2026-10-18)

| 완료 기준 | 구현 및 확인 근거 | 확인 범위 |
| --- | --- | --- |
| 마지막 사진을 절반보다 조금 더 이동/되돌리기 | FilmFrameDetailActivity의 끝 항목 너비는 pager 너비×0.55. FilmFramePagerSnapHelper가 끝 항목의 오른쪽 끝을 화면 끝에 정렬하며 일반 사진은 기존 전체 너비/중앙 정렬을 사용한다. 반대로 밀면 마지막 사진으로 복귀한다. | 소스 대조, Android 배치 검사 2개 추가/컴파일. 기기 미실행 |
| 버튼으로 다음 필름 사진 보기 | item_note_next_roll의 최소 48dp 버튼과 `다음 필름 사진 보기`를 기본/한국어·영어·일본어·프랑스어로 제공한다. 스크롤은 필름을 전환하지 않으며 버튼 클릭에서만 최신 대상을 재조회하여 다음 상세의 첫 유효 컷을 연다. | XML/클릭 경로/리소스 대조, 빌드/Lint |
| 목록 순서·폴더·실제 사진/컷 번호 | FilmNextRollNavigation은 현재 롤 이후의 같은 폴더만 검색하고 빈 롤/누락 파일·범위 밖 컷을 제외한다. 정렬되지 않은 기록에서도 최소 유효 컷 번호를 유지하며 끝에서 순환하지 않는다. | 신규 단위 검사 6개 모두 통과 |
| 사진만 보기·확대·편집/원본 보존 | 기존 사진 뷰/제스처 코드를 유지한다. 다음 상세에 사진만 보기 여부를 전달하고 기존 상세를 남겨 뒤로 복귀/기존 편집 초안을 보존한다. 사진·롤 저장 경로는 변경하지 않았다. | 소스 대조, 기존 검사 포함 단위 검사 통과. 실제 핀치·드래그·편집/복귀는 미확인 |
| 화면 재생성·접근성 | 마지막 컷/세로 위치/확대 정보와 함께 showingNextRoll을 저장하고 새 화면 너비로 55% 위치를 복원한다. 기존 다음 사진 접근성 동작이 끝 항목까지 이동하고 버튼에 이전 사진 동작을 제공한다. | 소스 대조. 기기 재생성/키보드/TalkBack/큰 글꼴은 미확인 |

- `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest` 성공. 전체 단위 검사 208개 실패/오류/건너뜀 0, Lint 오류 0/경고 123. Android 검사 2개는 테스트 APK 컴파일만 완료했으며 실행 결과로 취급하지 않는다.
- 실제 기기에 설치하거나 사진/저장 데이터를 조작하지 않았다. 55% 이동의 조작감·버튼 표시/진입·뒤로가기·회전/재생성·사진만 보기·큰 글꼴 확인은 남아 있다.

## 상단 버튼 오른쪽 정렬·여백 축소 (2026-10-16)

- main_film_notes.xml의 noteFolderPath 끝 마진 -20dp로 본문 끝 패딩을 상쇄해 새 필름/선택(루트에서는 새 폴더 포함) 묶음을 20dp 오른쪽으로 이동했다. 가장 오른쪽 버튼과 롤 삼선 손잡이가 모두 목록 오른쪽 끝에 닿는 48dp 영역을 사용해 중심선도 맞춘다. 폴더별 시작 마진 -20dp/루트 0은 그대로이며 추가 너비를 제목 가중치가 흡수하므로 뒤로가기/제목의 왼쪽 시작은 유지한다.
- 제목 행 위의 body.paddingTop과 폴더/롤 목록 상단 간격을 12→8dp로 줄였다. MainActivity.updateRollSelectionUi도 위 패딩 8dp/일반 롤 목록 위 8dp로 맞추고 선택 중 롤 목록 위 0dp는 유지했다. 새 필름 앞 간격도 4→2dp로 축소했다. 뒤로가기/새 폴더/선택의 48dp 터치 영역·아이콘/글씨 크기·파란 새 필름 버튼·목록/데이터는 유지했다.
- XML/상태 갱신 값·48dp 크기·정렬 기하 대조와 변경 파일 diff 검사를 통과했다. assembleDebug·lintDebug 성공, Lint 오류 0/경고 130. 배치 변경으로 테스트 추가/재실행은 없으며 실기기의 루트/폴더·선택 모드/오른쪽 끝 터치/큰 글꼴 정렬은 미확인이다. 로그는 .artifact-build/header-right-edge-spacing-build.log.

## 폴더 뒤로가기·제목 왼쪽 이동 (2026-10-16)

- MainActivity.refreshFilmRolls에서 폴더 경로의 유효성을 확인한 뒤 뒤로가기 표시와 함께 noteFolderPath 시작 마진을 폴더 안 -20dp/루트 0으로 설정한다. 폴더 진입/복귀·폴더 삭제 후 루트 복원/화면 재생성 때 같은 경로로 갱신한다.
- 폴더 행 시작이 본문 시작 패딩 20dp를 상쇄하고 match_parent 너비가 20dp 증가하므로 48dp 뒤로가기와 제목은 20dp 왼쪽으로 이동한다. 제목의 가중치 너비가 증가분을 흡수해 새 필름/선택 버튼의 오른쪽 위치는 그대로다. 루트의 제목/본문/롤 목록·글자 크기/버튼 크기/저장·선택 코드는 유지했다. 부모 filmNotesBody의 기존 clipToPadding=false 안에 행을 실제 배치하여 그려진 아이콘만 이동하는 방식은 사용하지 않았다.
- assembleDebug·lintDebug·변경 파일 diff 검사 성공, Lint 오류 0/경고 130. 배치 변경으로 테스트 추가/재실행은 없으며 실제 기기에서의 왼쪽 끝 48dp 터치/폴더와 루트 전환·선택/긴 제목 표시는 미확인이다. 로그는 .artifact-build/folder-header-left-alignment-build.log.

## 한 줄 제목과 파란 새 필름 버튼 (2026-10-16)

- 첨부의 폴더 제목 두 줄 표시를 수정했다. filmNotesHeading을 명시적 AppCompatTextView로 두고 기존 가중치 너비를 유지하며 한 줄/끝 말줄임·14~18sp 자동 크기·왼쪽 정렬을 적용했다. 저장된 전체 폴더명은 바꾸지 않는다. 오른쪽 버튼들은 고정 폭이고 제목이 남는 너비를 차지한다.
- btnAddFilmRoll은 88×48dp·앞 간격 4dp/좌우 패딩 6dp·한 줄/13sp(최소10sp 자동 축소)로 줄이고 기존 bg_btn_blue 파란 둥근 배경·흰 글자를 적용했다. 한국어 `＋ 새 필름` 등 네 언어 짧은 표시를 새 note_header_strings에 추가하고 전체 접근성 설명은 기존 film_notes_add_roll을 사용한다.
- 선택 버튼은 48×48dp·좌우2dp/12sp(최소10sp 자동 축소)·한 줄로 줄였다. 뒤로가기/폴더 생성은 48dp를 유지하고 생성·폴더 이동·선택/작업 상태 코드는 변경하지 않았다. 너무 긴 이름/큰 글꼴은 줄바꿈 대신 제목 끝 말줄임으로 처리한다.
- XML 한 줄/자동 크기·왼쪽 가중치/오른쪽 고정 폭·파란 배경/48dp 터치·네 언어 리소스를 대조했다. assembleDebug·lintDebug 성공, Lint 오류 0/경고 130. 표시 변경으로 테스트 추가·재실행은 없으며 실제 기기의 첨부 폴더명/좁은 화면/큰 글꼴/네 언어/선택 상태는 미확인이다. 로그는 .artifact-build/compact-note-header-build.log.

## 새 롤 버튼을 제목과 폴더 만들기 사이에 배치 (2026-10-16)

- 사용자 첨부의 별도 파란 버튼 배치를 정정했다. main_film_notes.xml의 btnAddFilmRoll을 noteFolderPath 내부의 filmNotesHeading 직후로 이동해 제목/새 롤/기존 선택/폴더 생성이 같은 가로 행에 놓인다. 기존 버튼은 한 개만 유지하며 제목 아래의 전체 너비 파란 영역·16dp 상단 마진은 제거했다.
- NoteSelectionAction을 사용해 14sp/최소 48dp 높이/본문 색의 작은 버튼으로 표시하고 wrap_content·최대 140dp/앞 간격 8dp를 지정했다. 제목은 기존 가중치 너비/내용 높이, 폴더 버튼은 기존 48dp를 유지한다. 새 롤의 번역/현재 폴더 생성·선택 중 숨김·작업 중 비활성·클릭 코드는 유지했다.
- XML 구문/부모·순서/유일성 확인, assembleDebug·lintDebug 성공. Lint 오류 0/경고 130. 배치/스타일 변경으로 테스트 추가/재실행은 하지 않았으며 실제 기기의 같은 줄 배치·긴 제목/번역/큰 글꼴·선택/폴더 내부는 미확인이다. 로그는 .artifact-build/new-roll-header-button-build.log.

## 새 필름 롤 버튼 상단 이동 (2026-10-16)

- main_film_notes.xml에서 btnAddFilmRoll 블록을 본문 하단에서 noteFolderPath(제목/현재 폴더 경로) 바로 뒤로 옮겼다. 제목 → 새 필름 롤 → 빈 안내 → 폴더 목록 → 롤 목록 → 로딩 오류 순서를 XML로 대조했고 같은 ID가 한 개만 존재함을 확인했다.
- 기존 버튼의 전체 너비/56dp 최소 높이/상단 16dp·패딩 12dp/색상/문구와 MainActivity의 생성 클릭(currentFolder 전달)·선택 모드 숨김·작업 중 비활성화 연결을 유지했다. 새 버튼/중복 클릭 처리나 저장 코드는 추가하지 않았다.
- assembleDebug·lintDebug 성공, Lint 오류 0/경고 130. 표시 위치만 변경해 테스트 추가·재실행은 없으며 실제 기기에서 루트/폴더 안·빈 목록/긴 목록/선택 상태의 표시와 클릭은 미확인이다. 로그는 .artifact-build/new-roll-button-top-build.log.

## 삼선 손잡이 오른쪽 끝 배치 (2026-10-16)

- main_film_notes.xml의 filmNotesRollList 끝 패딩을 20→0dp로 변경해 각 롤 행을 오른쪽으로 20dp 확장했다. item_film_roll.xml의 손잡이 앞 마진은 8→28dp로 늘려 48dp 손잡이 전체를 기존보다 20dp 오른쪽으로 옮기고 터치 영역 끝이 목록 오른쪽 끝에 닿게 했다. 그림의 12dp 내부 패딩은 유지한다.
- 늘어난 행 너비 20dp와 앞 간격 증가 20dp가 상쇄되어 본문/체크박스의 위치와 텍스트 폭은 유지된다. 64dp 최소 높이·상하 8dp 패딩/컷 수 위 0dp·구분선·본문 선택/손잡이 순서 변경 로직은 변경하지 않았다. 목록의 시작 패딩 20dp도 유지했다.
- XML 구문/속성·너비 배분 대조, assembleDebug·lintDebug 성공. Lint 오류 0/경고 130. 배치 수정으로 단위/Android 테스트 추가·재실행은 하지 않았다. 실제 기기의 오른쪽 손잡이 표시/터치·가장자리 드래그·본문과의 오조작 감소는 미확인이다. 로그는 .artifact-build/roll-handle-right-edge-build.log.

## 롤 목록 컷 수 위 여백 축소 (2026-10-16)

- item_film_roll.xml의 filmRollCardDetail(기록 수 / 예정 컷 수) 상단 마진을 6dp→0dp로 축소했다. 필름/카메라는 같은 TextView의 줄바꿈이고 컷 수는 별도 TextView이므로 기존 추가 6dp가 컷 수 위에만 있던 것을 확인했다.
- 행과 세로 정보 영역은 wrap_content로 유지하며 별도의 높이/아래 마진/패딩 보상을 추가하지 않았다. 따라서 날짜 유무에 관계없이 일반 롤 행 전체 높이가 6dp 줄어든다. 기존 64dp 최소 높이 하한과 48dp 손잡이/선택 터치 크기, 위/아래 8dp 패딩·날짜 위 8dp/제목 아래 5dp·글자 크기·내용은 유지한다.
- XML 구문·속성 대조, assembleDebug·lintDebug 성공. Lint 오류 0/경고 130. 로그는 .artifact-build/roll-count-spacing-build.log. 표시용 마진만 변경해 단위/Android 테스트 추가·재실행은 하지 않았다. 실제 기기에서의 간격·총 높이/큰 글꼴 표시는 미확인이다.

## 알림 링크를 삼성 내 파일 다운로드 목록으로 정정 (2026-10-16)

- 사용자 첨부 1번에서 잘못 열린 화면을 확인했다. 기존 알림의 ACTION_OPEN_DOCUMENT는 파일을 고르는 시스템 문서 선택 창이며, EXTRA_INITIAL_URI만으로 요청한 2번 파일 관리 화면을 열 수 없었다. 이전 구현에서 저장 위치 연결 완료를 주장한 부분을 정정한다.
- 알림 본문/다운로드 열기는 ACTION_VIEW_DOWNLOADS를 처리하는 삼성 내 파일(com.sec.android.app.myfiles)을 우선 조회하고 명시적 Activity로 연결한다. 미지원 시 기기 다운로드 처리기, 그것도 없으면 실제 ZIP의 ACTION_VIEW로 연결한다. 사용자 파일은 저장 창에서 선택한 그대로 유지한다. 다운로드 외의 위치에 저장했다면 별도 ZIP 열기 동작으로 실제 파일에 접근한다.
- 목록 탐색에는 파일 URI/MIME 필터·읽기 권한을 전달하지 않는다. 출력별 고유 category를 명시적 대상의 PendingIntent 식별에 사용한다. 별도 ZIP 열기에만 실제 URI·application/zip·READ/ClipData를 전달한다. 패키지 가시성 queries에 삼성 내 파일과 VIEW_DOWNLOADS만 추가한다.
- 실제 삼성 기기에서 ACTION_VIEW_DOWNLOADS 처리 여부와 첨부 2번 화면 진입은 기기 확인 전이다. 정적 코드/빌드는 해당 화면의 재현 확인을 대신하지 않는다.
- 검증: assembleDebug·lintDebug·assembleDebugAndroidTest 성공, ZIP 단위 검사 6개 실패/오류/건너뜀 0. Lint 오류 0/경고 130. 기존 알림 Android 검사를 목록 탐색/ZIP 읽기 전용 전달/파일별 PendingIntent 분리 3개로 갱신해 APK 컴파일을 확인했으며 기기에서 실행하지 않았다. 로그는 .artifact-build/export-downloads-link-build.log. 네 언어 다운로드 열기 문구와 ACTION_OPEN_DOCUMENT/EXTRA_INITIAL_URI의 알림 코드 제거를 소스로 대조했다.
- 공개 동작 근거: [Android DownloadManager.ACTION_VIEW_DOWNLOADS](https://developer.android.com/reference/android/app/DownloadManager#ACTION_VIEW_DOWNLOADS), [패키지 가시성과 처리기 조회](https://developer.android.com/training/package-visibility/use-cases).

## 제목 가운데 배치·상단 여백 축소 (2026-10-15)

- 상단 패딩 20→16dp 축소 후 추가 요청을 반영하여 위/아래를 12/12dp로 재분배. 총 여백 24dp 유지(이전 28dp). main_film_notes의 filmNotesBody.paddingTop과 filmNotesRollList.layout_marginTop, MainActivity의 일반 상태 재설정 값 모두 12dp로 일치. 제목 행의 center_vertical·선택 상태의 8/0dp·롤 내부 여백/구분선·상단바 크기는 유지.
- assembleDebug·lintDebug 성공, Lint 오류 0·경고 130. XML 구문·git diff --check 통과. 표시 변경이므로 테스트 추가/재실행 없음. 실기기에서 제목의 실제 세로 중앙/작은 화면/큰 글꼴은 미확인. 로그 .artifact-build/note-heading-spacing-build.log.

## 전체 너비 구분선·상단 선·농도 조정 (2026-10-15)

| 완료 기준 | 근거 / 결과 |
|---|---|
| 농도/두께 | #000000/1dp를 #777777/0.5dp로 변경. 실수 픽셀 높이와 Paint 안티앨리어싱을 사용하여 밀도가 낮아도 얇게 표시 |
| 상단/연속 너비 | FilmRollListLayout.dispatchDraw가 비어 있지 않은 목록 상단에 선을 그림. 롤 사이 선은 부모 목록 전체 너비로 그리며 마지막 아래는 생략. 목록 좌우 마진 -20dp와 부모/목록 clipToPadding 해제로 화면 좌우 끝까지 표시. 행 사이 선은 기존 자식 변환/alpha를 따라감 |
| 내용/입력 보존 | 목록에 20dp 내부 패딩을 두어 기존 글씨/체크박스/손잡이 위치와 행 너비를 유지. 상하 패딩/높이·제목 아래 8dp·선택 상태 0dp와 정렬 기하/순서 저장·선택 입력/폴더 동작 코드는 변경하지 않음. 선은 높이나 터치 View를 추가하지 않음 |
| 검사/미확인 | assembleDebug·lintDebug 성공, Lint 오류 0·경고 130. XML 구문·git diff --check 통과. 표시 변경으로 단위/Android 검사 추가·재실행 없음. 실제 기기 선 농도/반 픽셀 두께·제목 아래/양끝 연결·드래그/선택 상태 표시는 미확인. 로그 .artifact-build/full-width-roll-separators-build.log |

## 롤 사이 검정 구분선 (2026-10-15)

| 완료 기준 | 근거 / 결과 |
|---|---|
| 색/두께/범위 | FilmRollListLayout.drawChild의 Paint Color.BLACK/#000000. 높이는 기기 밀도에 맞춘 1dp(최소 1px). 목록 부모의 기존 좌우 20dp 패딩에 맞춘 행 너비만큼 표시. 마지막 자식 아래는 생략하며 롤 하나/빈 목록에는 선 없음 |
| 배치/이동 보존 | 행 아래쪽 내부에 직접 그려 별도 View·높이·마진·터치 영역을 추가하지 않음. 기존 위/아래 8dp·최소 64dp·목록 상단 8dp 유지. 자식 행의 좌표 행렬/alpha를 적용하여 순서 변경·정렬 이동을 따라가며 기존 실제 행 마지막 그리기 순서도 유지. 선택/드래그/폴더/저장 코드는 수정하지 않음 |
| 검사 | assembleDebug·lintDebug 성공, Lint 오류 0·경고 130. git diff --check 통과. 표시만 변경하여 단위 테스트/Android 검사를 추가·재실행하지 않음. 로그 .artifact-build/black-roll-separators-build.log |
| 실기기 미확인 | 설치/사용자 데이터 조작 미실행. 실제 검정선 두께/양끝·마지막 생략, 순서 변경 중 행 이동과 선택/폴더 묶음 이동 시 선 표시는 기기 확인 필요 |

## 롤 여백·원본 행 세로 이동 (2026-10-15)

이 절이 아래 순서 변경 손잡이 절의 native drag/복사본·원본 흐림·확대 효과를 대체한다. 폴더 묶음 이동은 기존 동작을 유지한다.

| 완료 기준 | 근거 / 결과 |
|---|---|
| 여백 축소 | item_film_roll의 위·아래 패딩 각각 16→8dp, 최소 행 높이 80→64dp. main_film_notes와 MainActivity 일반 상태의 목록 상단 마진 20→8dp. 선택 상태의 0dp·기존 본문 상단 패딩·글씨/내부 항목 간격·48dp 손잡이/체크박스 유지 |
| 실제 행·세로 고정 | 손잡이가 세로 touchSlop 이후 시작/이동/완료를 FilmRollVerticalReorder에 전달. 순서 변경에서 startDragAndDrop/그림자 복사본을 호출하지 않음. 같은 행 뷰가 원래 부모 안에서 translationY로만 이동. X=0/alpha=1/scale=1 유지. FilmRollListLayout은 이동 중인 기존 자식을 마지막에 그려 다른 행 위에 표시하며 배치 순서·좌우 위치·그림자 사본 추가 없음 |
| 불투명/원본 배경 보존 | 이동 중 부모의 불투명 배경색(현재 #F7F7F7)을 새 ColorDrawable로 사용해 뒤쪽 글씨가 겹쳐 보이지 않게 함. 기존 Drawable을 수정하지 않고 종료/취소 시 같은 배경 복원. 새 뷰/사진 사본 생성 없음 |
| 높이·정렬 애니메이션 | FilmRollVerticalOrder가 원래 행 높이/위치를 기준으로 이동 방향의 앞쪽 경계가 이웃 행 중간을 지날 때 목표 순서를 변경. 날짜/여러 줄로 높이가 다른 행과 키 큰 행도 이동 가능. 첫/마지막 행 범위 제한. 이웃은 세로 160ms 감속 이동, 놓기는 180ms 정렬 후 실제 자식 순서 변경·translation 초기화 |
| 저장/취소/자동 스크롤 | 정렬 완료 뒤에만 기존 FilmRollOrderViewModel.save로 순서를 한 번 전달. 순서가 같으면 저장 안 함. 가장자리에서 ScrollView를 스크롤하며 현재 화면 좌표 기준 위치 재계산. CANCEL/추가 손가락·Back/화면 이탈/재바인딩은 이동/정렬을 취소하고 순서·배경·좌표 복구. 저장 실패는 기존 기록으로 목록 복원. 접근성/메뉴 위·아래 이동도 같은 실제 행 애니메이션 사용 |
| 다중 선택 보존 | FilmRollSelection과 본문 선택/해제/범위 입력·폴더 이동 저장 경로 미수정. FilmRollGroupDrag의 기존 native 그림자/수량 배지·폴더 강조는 폴더 묶음 이동에만 사용. 순서 변경은 별도 컨트롤러로 분리. 손잡이 비활성 터치는 본문으로 넘어가지 않고, 건드리지 않은 손잡이는 기존 범위 선택의 intercept를 해제하지 않음 |
| 자동 검사 | assembleDebug·testDebugUnitTest·lintDebug·assembleDebugAndroidTest 성공. 단위 테스트 202개 실패/오류/건너뜀 0, Lint 오류 0·경고 130. 새 순수 기하 검사 5개로 임계값·역방향·키 큰/작은 행·처음/끝·모든 이동의 실제 가변 높이 배치 일치/원복·입력 배열 비변경 확인. 기존 손잡이 6개를 연속 드래그 콜백으로 갱신하고 가로 이동 무시 검사 1개 추가. 새 실제 행 Android 검사 4개: 동일 뷰/불투명/X·크기 유지, 취소·배경/순서 복구, 놓기 중 화면 이탈/미저장, 정렬 완료 후 한 번 저장. Android 11개는 APK 컴파일만 확인하고 기기 실행하지 않음. 기존 본문 범위 선택 검사 2개 미수정. 변경 레이아웃 XML 구문·git diff --check 통과. 로그 .artifact-build/vertical-roll-reorder-build.log |
| 실제 기기 미확인 | 설치/사용자 데이터 조작 미실행. 제목/롤 간 실제 여백, 손잡이를 잡고 원본 행의 세로 추종·교차/놓기·첫/끝·날짜/긴 제목·가장자리 자동 스크롤·연속 조작/재생성·실패/취소·접근성/큰 글꼴, 기존 다중 선택/범위 선택/폴더 묶음 이동 회귀는 기기 확인 필요 |

## 순서 변경 손잡이 (2026-10-15)

사용자 추가 지시대로 다중 선택은 유지하며 기존 순서 변경 방식만 오른쪽 삼선 손잡이로 교체한다. 이 절이 이전 본문 묶음 드래그를 통한 행 순서 변경 기준을 대체한다.

| 완료 기준 | 근거 / 결과 |
|---|---|
| 오른쪽 삼선 손잡이 | item_film_roll 오른쪽에 FilmRollReorderHandle 48×48dp, 12dp 패딩/24dp ic_roll_reorder 벡터. 노트의 기존 note_muted 색상과 NoteIcon 배경 사용. 제목/필름/정보/기간·체크박스 구성 유지 |
| 새로운 순서 변경 | 손잡이 DOWN에서 터치를 받고 기본 touchSlop을 넘기면 해당 롤 한 개만 native drag 시작. 다중 선택 진입/선택 ID 변경 없음. 실제 손잡이의 누른 지점을 그림자 터치 위치로 사용. 행 이동은 기존 ChangeBounds/180ms/감속, 원본 alpha .28·놓기 크기 복귀와 가장자리 자동 스크롤 유지. 손잡이 드래그는 폴더 이동 분기에 들어가지 않음 |
| 기존 순서 변경 제거 | FilmRollGroupDrag.moveAtPointer의 행 재배치는 reordering 모드에만 허용. DROP의 순서 저장도 손잡이 드래그에만 허용. 선택된 롤 본문 드래그를 행 사이에 놓아 순서를 바꾸던 이전 동작 제거. 폴더가 아닌 곳에 놓은 묶음 드래그는 취소 |
| 다중 선택 보존 | FilmRollSelection 미수정. 본문 일반 탭/길게 누르기, selection.begin/range·체크박스 선택/해제, 선택 UI·폴더 이동 저장/선택 ID 전달 유지. 기존 폴더 묶음 그림자의 중심/수량 배지 유지. 선택 중 새 손잡이만 흐리게 비활성화. 비활성 손잡이 터치는 본문으로 전달하지 않으며 손잡이가 추적하지 않은 터치의 취소는 부모 intercept 상태를 바꾸지 않아 기존 범위 선택을 방해하지 않음 |
| 저장·취소·접근성 | 기존 FilmRollOrderViewModel/FilmRollOrder/FilmRollStore.reorder 사용, 같은 폴더의 표시 ID만 최신 기록 기준으로 저장. 실패 시 기존 저장 데이터로 목록 복원. 저장 전 Back/화면 이탈은 원래 순서/alpha 복원. 단일 롤/저장 중 손잡이 비활성. 손잡이 탭에 위/아래 메뉴, 기존 위/아래 접근성 동작은 본문에서 손잡이로 이동. 4개 언어의 롤 이름 포함 안내/툴팁 제공 |
| 자동 검사 | assembleDebug·testDebugUnitTest·lintDebug·assembleDebugAndroidTest 성공. 기존 단위 테스트 197개 실패/오류/건너뜀 0, Lint 오류 0·경고 131. 순서/최신 기록 보존/실패 복구 및 기존 선택/폴더 규칙 검사는 그대로 통과. 새 Android 손잡이 검사 6개: 임계값 이후 한 번만 시작/본문 선택 없음, 탭, 비활성 터치 소비, 취소/화면 이탈, 여러 손가락 중단, 건드리지 않은 손잡이가 본문 범위 선택 intercept를 해제하지 않음. 기존 FilmRollFolderGestureTest 2개는 수정하지 않음. Android 검사는 APK 컴파일만 성공했고 기기 실행하지 않음. 변경 XML 7개 구문/현지화 키 중복 검사 및 git diff --check 통과. 빌드 로그 .artifact-build/roll-reorder-handle-build.log |
| 실기기 미확인 | 설치/사용자 기록 조작 미실행. 손잡이 드래그의 실제 native drag 그림자/터치 고정/행 이동·자동 스크롤·놓기/취소/순서 저장·재실행·긴 제목/큰 글꼴·접근성 메뉴, 기존 본문 다중 선택/범위 선택/해제·폴더 묶음 이동의 실제 회귀 확인은 기기 확인 필요 |

## 첫 페이지 이동 버튼 (2026-10-15)

| 완료 기준 | 근거 / 결과 |
|---|---|
| 확정한 아이콘/위치 3번 | 가로로 긴 필름·정사각형 세 칸·작은 숫자 1·왼쪽 꺾쇠의 v3 SVG를 ic_note_first_frame.xml의 64×64 벡터 경로로 옮김. activity_film_roll_frames의 제목 오른쪽, 내보내기 왼쪽에 framesFirst 추가. 시안의 하늘색 안내 배경/테두리는 포함하지 않고 기존 NoteIcon 배경/밝은 tint 사용 |
| 터치/안내/작은 화면 | 48×48dp 버튼, padding 8dp로 32dp 아이콘. 한국어/영어/프랑스어/일본어 접근성 설명·TooltipCompat 안내. 제목은 기존 weight/2줄/말줄임 유지. 실제 좁은 화면/큰 글꼴 가독성은 기기 미검증 |
| 최신 페이지 진입 유지 | onCreate의 state==null ? (nextFrame()-1)/2 및 저장된 pair 복원, 촬영 성공 뒤 다음 촬영 페이지 정렬·저장된 컷 메모 진입은 그대로 유지 |
| 첫 페이지로 애니메이션 | 버튼 클릭 → export.busy 보호 → scrubber.cancelTouch → pager.stopScroll → deletion.dismiss → settleTo(0,420). currentOffset에서 첫 열까지의 거리를 계산하고 DecelerateInterpolator로 420ms smoothScrollBy. 단순 점프/새 Activity 호출 없음. 기존 일반 탐색바 정렬은 220ms를 유지하고 onScrolled로 progress/컷 설명을 함께 갱신 |
| 상태/기록 보존 | 현재 pair가 0이면 비활성/alpha .35, 다른 페이지에서는 활성. export.busy 중에도 비활성이고 완료/실패/취소 상태 갱신 시 다시 계산. 단일 페이지도 비활성. 클릭은 JSON/사진/메모·컷 수·다음 촬영 번호를 저장/변경하지 않음. 일반 화면 저장은 현재 pair를 보존하는 기존 규칙 유지 |
| 자동 검사 | assembleDebug·testDebugUnitTest·lintDebug·assembleDebugAndroidTest 성공. 기존 단위 테스트 197개 실패/오류/건너뜀 0, Lint 오류 0·경고 131. 기존 탐색 검사로 36컷/홀수 컷/1~999컷 범위/빈 컷 채우기/완료 롤을 확인. 새로운 버튼 전용 단위 테스트는 추가하지 않음. 변경 XML 7개 구문과 각 언어 안내 키 1회 존재 확인. git diff --check 통과. 빌드 로그 .artifact-build/first-page-button-build.log |
| 실기기 미확인 | 설치/사용자 데이터 조작 미실행. 최신 페이지 → 상단 버튼 → 첫 페이지의 실제 420ms 이동/탐색바 연속 동기화, 스크롤 중 클릭/연타·삭제 표시 해제, 내보내기 상태·1/홀수/완료 롤·작은 화면/긴 제목/큰 글꼴/접근성·화면 재생성은 기기 확인 필요 |

## 현재 화면 사진만 보기 (2026-10-15)

이 절이 아래 별도 전체 화면 Activity로 사진을 열던 사진만 보기 절을 대체한다.

| 완료 기준 | 근거 / 결과 |
|---|---|
| 같은 화면/사진 유지 | item_film_frame_feed의 기존 noteDetailPhoto를 FilmComparisonImageView로 사용. FilmFrameDetailActivity.setPhotoOnly는 새 Activity 실행·이미지 로드 없이 상단/시스템 바·컷 번호·메모/위치/비교·삭제를 숨기고 같은 FilmNotePhotoLayout만 페이저 높이로 확장. 사진 영역과 루트는 #000000. 별도 FilmFramePhotoActivity/레이아웃/테마/Manifest 선언 제거 |
| 클릭/확대 즉시 적용 | 첫 탭의 onSingleTapUp에서 즉시 전환. 두 번째 손가락 POINTER_DOWN에서도 확대 임계값 전에 전환. pinch/이중 탭/접근성 확대는 같은 뷰에서 1~5배 확대·축소/이동을 이어감. 상세 전용 listener가 있는 경우 화면 좌표로 터치를 추적하여 헤더/스크롤/뷰 위치 변화가 손가락 이동이나 다른 탭 위치로 오인되지 않도록 처리. 스캔 비교에는 기존 좌표/동작 유지 |
| 단일/이중 탭 구분 | 진입 탭은 확인 콜백에서 복귀시키지 않음. 사진만 보기에서 다음 단일 탭 확인 또는 뒤로가기는 메모 복원. 이중 탭은 메모를 표시하지 않고 1배/2배 전환. 접근성 클릭으로도 숨김/복귀 가능 |
| 복귀/초안/복원 | 홀더별로 메모 세로 위치를 기억하고 기록 뷰는 제거하지 않고 GONE으로 숨김. 복귀 시 사진 1배/기존 영역·기록/스크롤 복원. 키보드만 숨기고 메모 draft 취소/저장 호출 없음. Bundle에 모드·배율·중심·복귀 스크롤 보관. 위치 자동 갱신 payload는 사진 재로딩 없이 기록만 갱신 |
| 기존 탐색/삭제 | 1배 좌우 드래그는 기존 RecyclerView 페이저에 전달, 확대된 드래그는 사진 이동. 일반 사진의 길게 누르기와 삭제 접근성 동작 유지. 사진만 보기에서는 삭제 진입/버튼을 숨김. 기존 스캔 비교의 Swipe listener 경로는 유지 |
| 자동 검사 | assembleDebug·testDebugUnitTest·lintDebug·assembleDebugAndroidTest 성공. 단위 테스트 197개 실패/오류/건너뜀 0, Lint 오류 0·경고 131. 사진 Android 검사 8개: 기존 핀치/이동/중앙 표시/복원/탭 4개를 현 상세 사진 뷰 fixture로 갱신하고 즉시 숨김/다음 단일 탭·이중 탭 구분, 두 번째 손가락/확대 중 resize/동일 뷰·drawable 유지, 영역 높이 전환, 1배 페이저 전달/확대 이동의 새 4개 추가. 테스트 APK 컴파일만 확인하고 기기 실행하지 않음. 빌드 로그 .artifact-build/inline-photo-visibility-build.log |
| 실기기 미확인 | 설치·사용자 데이터 조작 미실행. 실제 사진 클릭/대각선 핀치 시작과 연속 확대·작은/큰/회전된 사진·다중 포인터/취소·메모 복귀 스크롤/초안/키보드·좌우 페이지 이동·시스템 뒤로가기·재생성/프로세스 종료·접근성/삭제는 기기 확인 필요 |

## 사진 탐색바 누른 위치와 무관한 상대 이동 (2026-10-15)

| 완료 기준 | 근거 / 결과 |
|---|---|
| 끝을 눌러도 현재 화면 유지 | FilmFrameScrubber가 기본 SeekBar의 터치 절대 위치 계산을 대체. DOWN 시 FilmRollFramesActivity가 현재 RecyclerView의 실제 픽셀 offset을 시작 progress로 전달하며 누른 x좌표는 손가락 이동 기준점으로만 사용. 단순 탭/터치 슬롭 이내 움직임은 사진 이동 콜백이나 정렬을 실행하지 않음. 진행 중 사진 스크롤은 현재 위치에서 멈춤 |
| 손가락 이동량만 반영 | FilmFrameScrubGesture가 손가락 delta×전체 범위/기존 트랙 너비로 상대 이동을 계산. 처음 드래그는 슬롭 안 움직임을 포함한 실제 이동량을 적용. 기존 사진 열 사이 연속 픽셀 이동을 유지하고 손을 떼면 기존 220ms 가까운 열 정렬 사용. 이전 첫 progress 건너뛰기 임시 처리는 제거 |
| 경계/역방향/손가락 교체 | 처음/끝으로 제한하되 매 이동의 실제 손가락 위치를 새 기준점으로 삼아 과도하게 끝으로 끈 뒤에도 역방향 이동이 즉시 반영. 활성 포인터 교체는 현재 progress를 유지한 채 새 손가락 좌표로 재기준화. UP의 마지막 이동량 반영, CANCEL의 좌표는 무시. 화면 이탈/상태 저장/뷰 분리 시 추적 해제 |
| 기존 조작/표시 | AppCompatSeekBar 상속으로 키보드/접근성 progress 변경을 유지하고 기존 OnSeekBarChangeListener는 그 절대 위치 입력만 처리. 사진 직접 넘기기와 thumb 동기화·설명·48dp 터치·15:70:15 너비/색상/막대 리소스 유지. 사진/메모/컷 번호/다음 촬영 번호 저장 변경 없음 |
| 자동 검사 | assembleDebug·testDebugUnitTest·lintDebug·assembleDebugAndroidTest 성공. 단위 테스트 197개 실패/오류/건너뜀 0, Lint 오류 0·경고 132. 새 단위 검사 6개로 누른 위치 독립성·탭/미세 흔들림·첫 이동량·양끝/즉시 반전·손가락 교체·1~999컷 범위를 확인. 새 Android 터치 검사 4개는 양끝 탭 무이동·동일 거리 드래그·포인터 교체·취소를 로컬 뷰 fixture로 작성하고 APK 컴파일만 확인, 기기 실행하지 않음. git diff --check 통과 |
| 실제 기기 미확인 | 설치/사용자 데이터 조작 미실행. 9/10번 사진 열에서 바 양끝 터치/좌우 드래그·탭 무이동, 사진 스크롤 도중 바 터치, 양끝 반전·빠른 드래그/손가락 교체·취소, 24/36/직접/홀수 컷·회전/재생성·키보드/접근성·정렬 애니메이션은 기기 확인 필요 |

## 롤별 조리개·셔터속도 기억 분리 (2026-10-15)

이 절이 이전 ISO/화각 구현 절의 조리개·셔터 공통 설정 공유 기준을 대체한다.

| 완료 기준 | 근거 / 결과 |
|---|---|
| 롤마다 독립 보관 | FilmNoteExposureStore의 film_note_exposure 설정에 aperture/롤ID·shutter/롤ID 키로 한 쌍 저장. 기존 FilmNoteIsoStore/FilmNoteFieldOfViewStore는 유지. 롤 이름·필름명·폴더와 무관한 고정 ID 기준이며 다른 롤 값은 갱신하지 않음 |
| 진입/선택/재진입 | 노트 ExposureCameraActivity 진입 시 공통/Intent 값 이후 롤 전용 저장값으로 조리개·셔터를 확정. 기존 showValueDialog 선택 → saveExposureSettings는 노트면 전용 저장소에만 apply하고 반환. 촬영/컷 저장 없이 변경 후 나가도 기억하며 다음 진입/재생성은 그 값을 로드. 일반 촬영 보조의 ExposureSettingsStore.save 경로는 기존대로 유지 |
| 처음 값/잘못된 값 | 이전 버전은 롤별 조리개/셔터 기록이 없으므로 첫 진입에는 독립 기본값 f/2.8·1/125. 공통 설정이나 컷 메모에서 수정한 과거 촬영값을 초기값으로 추정하지 않음. 손상된 저장값은 해당 항목만 기본값으로 복원. 유효하지 않은 선택 쌍·빈 롤 ID는 기존 유효 설정을 덮어쓰지 않음 |
| 프리뷰/결과/과거 기록 | applyExposure의 selectedAperture/selectedShutter 사용과 capturePhotoNow에서 촬영 당시 값을 동결하여 결과 Intent에 전달하는 기존 흐름 유지. 새 기억값만 변경하며 저장된 컷 JSON/노출 기록/사진은 수정하지 않음. 기록용 노출 수정에 전용 설정 저장 호출 없음 |
| 삭제/백업 | FilmRollStore.delete의 기존 롤 commit 성공 분기에서만 해당 ID의 조리개/셔터 기억도 정리. commit 실패 분기에서는 제거 호출하지 않음. 폴더 이동/폴더 삭제는 롤 삭제가 아니므로 기억 유지. 백업/기기 이전은 기존 exposure_settings.xml·언어 허용 목록만 포함하며 film_note_exposure는 제외 |
| 자동 검사 | assembleDebug·testDebugUnitTest·lintDebug·assembleDebugAndroidTest 성공. 전체 단위 테스트 191개 실패/오류/건너뜀 0, Lint 오류 0·경고 132. 신규 전용 설정 테스트 7개로 ISO 800/100/400 세 롤의 서로 다른 조리개/셔터 재로딩, 공통/다른 롤과 무관한 기본값, 항목별 변경 격리, 유효성/빈 ID, 삭제 범위, 일반 설정의 양방향 격리를 확인. 기존 ISO/화각 테스트 2개도 조리개/셔터 비공유 기준으로 갱신. 공통 설정의 이전 saveNoteExposure/saveWithoutIso 메서드와 참조 제거. git diff --check 통과 |
| 실제 기기 미확인 | 설치/사용자 데이터 조작 미실행. ISO 800/100 등 2~3개 롤에서 서로 다른 조리개/셔터 선택 → 저장 없이 나가기 → 재진입/다음 컷/앱 재실행/화면 재생성, 일반 촬영 보조와 왕복, 실제 프리뷰/촬영 결과 값, 폴더 이동/롤 이름 변경 후 기억, 롤 삭제 성공/실패는 기기 확인 필요. 기존 롤은 이번 분리 후 최초 진입 시 f/2.8·1/125에서 각각 설정해야 함 |

## 폴더 생성·롤 다중 선택·묶음 이동 (2026-10-15)

이 절의 두 단계 길게 누르기 동작이 아래 롤 순서 변경 절의 즉시 단일 롤 드래그 진입을 대체한다.

| 완료 기준 | 근거 / 결과 |
|---|---|
| 폴더 생성/탐색 | 메인 노트 제목 옆 폴더+ 버튼 → FilmFolderNameDialog의 1~80자 이름 → FilmFolderViewModel의 백그라운드 저장. 루트에는 폴더 카드/소속 롤 수와 폴더 밖 롤을 표시. 폴더 클릭은 그 폴더의 롤 목록, 뒤로가기/상단 화살표는 루트. 폴더는 단일 단계. 이름 초안은 DialogFragment 재생성에 보존 |
| 폴더 관리/새 롤 | 루트의 폴더 길게 누르기 메뉴로 이름 변경/폴더 삭제 제공. 삭제는 소속 롤을 밖으로 꺼내며 JPEG/컷·메모를 삭제하지 않음. 폴더 안 새 롤 생성은 EXTRA_FOLDER_ID 전달 후 롤 JSON과 폴더 소속을 동일 SharedPreferences.Editor.commit으로 기록. 폴더가 사라졌거나 commit이 실패하면 두 값 모두 기존 상태 유지 |
| 길게 누르고 다중 선택 | FilmRollGroupDrag의 미선택 롤 길게 누르기 → 체크박스 선택과 범위 선택 시작. 손을 떼지 않은 드래그는 최초 앵커~현재 행의 연속 범위를 선택하고 이전 탭 선택은 유지. 범위를 줄이면 이번 제스처에서 추가한 범위만 줄임. 손을 뗀 뒤 행/체크박스 탭으로 개별 추가·해제. 선택/취소·다중 삭제·선택 ID 복원 유지 |
| 묶음 드래그/폴더 드롭 | 이미 선택된 롤을 다시 길게 누르면 선택한 롤 개수 배지를 가진 겹친 카드 그림자로 startDragAndDrop. 선택 롤은 alpha 0.28, 폴더 호버는 배경/배율 강조. 놓은 좌표에서 폴더를 다시 확인하고 선택 ID 전부 이동. 다중 창 좌표는 루트 화면 오프셋 반영. 폴더 안에서는 상단 뒤로가기 경로 영역에 놓아 밖으로 꺼냄 |
| 이동/순서/접근성 | 선택 도구 모음의 이동 창에서 다른 폴더/폴더 밖/새 폴더로 이동 가능. 루트·폴더 안의 같은 목록에 묶음을 놓으면 선택한 롤의 상대 순서를 유지한 채 블록으로 재배치. 행 자리 이동/드롭 배율 180ms, 폴더 강조 120ms, 가장자리 프레임별 최대 12dp 자동 스크롤. 화면 읽기는 선택·이동 창·기존 위/아래 이동을 통해 조작 가능 |
| 저장/최신 기록/백업 | FilmRollFolders는 폴더 ID→이름과 롤 ID→폴더 ID만 저장. 기존 film_roll_records 설정의 별도 folders 키로 보관하여 이전 롤 JSON 변경 불필요. FilmFolderStore와 FilmRollStore는 동일 LOCK 아래 최신 데이터에 작업하며 이전 롤 수정 저장은 소속을 덮어쓰지 않음. 실제 파일을 폴더 경로로 옮기거나 복사하지 않음. 기존 백업 제외와 권한 유지 |
| 취소/실패/재생성 | 미완료 드래그는 화면 이탈/저장 상태 복원/목록 갱신 시 중지하고 행 순서/강조 복원. 범위 선택 취소는 선택 상태만 유지하고 저장하지 않음. 성공 작업은 ViewModel에서 이어지고 저장 중 중복 조작 차단. 폴더 쓰기 실패는 이전 폴더 JSON, 폴더 안 롤 생성 실패는 이전 롤+폴더 JSON을 복원. 이동 실패에서 선택/사진·메모 유지. 현재 폴더/선택 ID와 이름 초안 복원 |
| 자동 검사 | 최종 assembleDebug·testDebugUnitTest·lintDebug·assembleDebugAndroidTest 성공. 전체 단위 테스트 184개 실패/오류/건너뜀 0, Lint 오류 0·경고 132. 새 단위 테스트 10개로 이전 설치 호환·Unicode 이름·다중/반복 이동·다른 폴더 보존·이름/대상 검증·삭제/새 롤 처리·손상 거부·폴더 commit 및 롤+소속 동시 실패 복원·이전 롤 수정 후 소속 유지·선택 범위 확장/축소를 확인. 새 Android 범위 제스처 검사 2개는 사용자 화면/저장 데이터를 사용하지 않는 로컬 뷰 fixture로 작성, APK 컴파일만 확인하고 실행하지 않음. git diff --check 통과 |
| 실제 기기 미확인 | 설치/사용자 데이터 조작 미실행. 폴더+ 이름 입력/생성·루트/내부 탐색, 첫 길게 누르기 후 범위 드래그/탭 선택, 선택 묶음을 다시 길게 누르기·폴더 호버/드롭·밖으로 꺼내기·다른 폴더/새 폴더 이동, 묶음 순서 변경·긴 목록 자동 스크롤·취소/실패·이름 변경/폴더 삭제·폴더 안 새 롤 촬영·재실행/회전·큰 글꼴/다중 창·접근성·실제 애니메이션은 기기 확인 필요 |

## 메모 직접 입력 즉시 시작·키보드 위 입력칸 표시 (2026-10-15)

| 완료 기준 | 근거 / 결과 |
|---|---|
| 한 번 누르고 입력 | FilmRecordEditor의 롤 이름·컷 메모·롤 메모 행 클릭은 초안/인라인 입력칸 생성 후 beginTextEditing → EditText.requestFocus → 배치 후 IME 표시로 연결. 이미 열린 텍스트 행 재클릭도 닫지 않고 현재 커서에서 입력 재개. 이름/컷 메모의 입력 안내도 해당 필드에 맞춤 |
| 직접 입력 전환 | 필름/카메라의 `직접 입력` 카테고리를 선택하면 생성된 입력칸으로 바로 포커스/IME 표시. 기존 직접 입력 값으로 진입해도 같은 동작. 컷 수의 직접 입력 선택/기존 직접 입력 값에서도 숫자 입력칸으로 즉시 진입. 노출/화각·목록 선택과 기간 캘린더의 기존 흐름 유지 |
| 키보드 회피 | FilmFrameDetailActivity에 adjustResize와 상세 root의 IME inset 처리 적용. 별도 내비게이션 spacer가 차지한 높이를 제외한 keyboard-navigation만 bottom padding에 반영. IME 높이 변경 뒤 pre-draw에서 입력칸을 다시 노출하고 닫히면 추가 여백 제거. 공식 참고: https://developer.android.com/develop/ui/views/layout/sw-keyboard |
| 입력칸/커서 스크롤 | 입력 포커스 직후 및 IME 반영 뒤 requestRectangleOnScreen으로 같은 NestedScrollView를 이동. 충분한 높이에서는 입력칸+아래 저장/취소 영역의 여유를 확보하고 화면이 좁으면 현재 커서 줄을 우선 표시. 긴 메모 EditText 내부 scrollX/scrollY를 반영해 잘못된 옛 문장 위치로 이동하지 않도록 처리. IME_FLAG_NO_EXTRACT_UI로 가로 화면의 별도 전체 입력 모드 요청 억제 |
| 재바인딩·초안/커서 | 같은 기록의 위치 갱신 등으로 행을 재생성할 때 현재 입력 포커스·선택 시작/끝을 복원하되 IME show를 새로 호출하지 않음. Bundle에 초안·포커스 여부·커서/선택 범위를 보관하고 재생성 시 복원. 지연 IME 요청은 초안/활성 패널/부착/포커스 일치를 확인해 닫힌 항목이나 재사용된 뷰에서 실행하지 않음. 저장/취소·검증/오류·촬영 ID/필드 단위 저장 코드는 유지 |
| 자동 검사 | 최종 assembleDebug·lintDebug·assembleDebugAndroidTest 성공, Lint 오류 0·경고 132. UI 포커스·화면 여백 변경으로 새 단위 테스트를 추가하거나 기존 단위 테스트를 재실행하지 않음. Android 테스트는 기존 APK 컴파일만 확인. git diff --check 통과 |
| 실제 기기 미확인 | 설치/사용자 데이터 조작 미실행. 롤 이름/컷 메모/롤 메모 한 번 터치의 키보드 표시, 직접 입력 카테고리/컷 수 전환, 긴 메모 커서/선택·입력칸과 저장/취소 노출, 큰 글꼴/가로 화면/키보드 종류·높이 변경, IME 닫기/재열기, 위치 갱신/회전 중 초안·커서와 저장/취소는 기기 확인 필요 |

## 롤 길게 누르기·드래그 순서 변경 (2026-10-15)

| 완료 기준 | 근거 / 결과 |
|---|---|
| 길게 누르고 이동 | MainActivity의 롤 카드에 FilmRollDrag 연결. 일반 목록의 2개 이상 롤에서 길게 누르면 Android의 로컬 startDragAndDrop/DragShadowBuilder로 이동 롤을 손가락 아래에 표시. 원래 위치의 행은 alpha 0.28로 표시. 다른 앱 드래그 권한이나 사진·메모·롤 이름의 ClipData 제공 없음 |
| 애니메이션·자동 스크롤 | 측정된 행 높이/중심으로 삽입 위치를 정해 애니메이션 중 좌표에 의한 반복 이동 방지. ChangeBounds+DecelerateInterpolator 180ms로 주변 행 자리 이동, 드롭 후 1.025→1 배율 복귀 180ms. 기존 ScrollView의 가장자리 최대 64dp에서 프레임별 최대 12dp 자동 스크롤. 실제 스크롤 속도·긴/서로 다른 높이 행의 애니메이션은 기기 확인 필요 |
| 저장·최신 기록 보존 | 드롭한 ID 목록만 FilmRollOrderViewModel에 전달하고 백그라운드에서 FilmRollStore.reorder 호출. 기존 공통 LOCK 아래 최신 롤을 읽고 FilmRollOrder.apply로 객체의 순서만 변경하여 commit. 새로 생성된 롤은 맨 위 보존, 삭제된 롤은 복구하지 않음. 기존 수정/촬영/위치 저장은 같은 순서를 다시 읽어 유지 |
| 취소·실패·재생성 | 화면 밖 드롭/시스템 취소는 원래 View 순서 복원. onPause·onSaveInstanceState·촬영 보조 이동·목록 갱신은 미완료 드래그와 자동 스크롤 취소. 성공 드롭 이후 저장은 ViewModel에서 계속하고 재생성 시 같은 상태 관찰. commit 실패는 이전 JSON을 복원하고 목록을 다시 읽어 안내. 드래그 중 임시 순서는 저장하지 않음 |
| 다중 삭제·접근성 | 기존 길게 누르기 선택 대신 제목 오른쪽 최소 48dp `선택` 버튼으로 빈 선택 모드 진입. 체크/다중 삭제·취소·뒤로가기·선택 ID 복원 유지. 선택/삭제/순서 저장 중 드래그 차단, 저장 중 새 롤 생성/롤 열기/선택도 차단. 각 카드에 화면 읽기 위/아래 이동·선택 동작 제공. 추가 문구는 ko/en/fr/ja에 적용 |
| 자동 검사 | assembleDebug·testDebugUnitTest·lintDebug·assembleDebugAndroidTest 성공. 전체 단위 테스트 174개 실패/오류/건너뜀 0, Lint 오류 0·경고 133. 새 순서 검사 5개로 ID 기준 이동/최신 사진·메모·회전 보존, 동시 생성·삭제 병합, 중복/없는 ID·빈 목록, JSON 재로딩/수정 후 순서, commit 실패 시 원래 JSON 복원을 확인. 첫 검사 컴파일에서 테스트의 JSONException 선언 누락을 수정한 뒤 전체 재검사 통과. Android 테스트는 APK 컴파일만 확인 |
| 실제 기기 미확인 | 설치/사용자 데이터 조작 미실행. 길게 누르기 → 위/아래 이동 → 드롭, 주변 행/드롭 애니메이션, 긴 목록 가장자리 자동 스크롤, 화면 밖 드롭·뒤로가기 취소, 화면 이탈/회전/재실행의 순서, 저장 실패·다중 삭제·큰 글꼴·화면 읽기 조작은 실제 기기 확인 필요 |

## CineStill 제품군 추가·VISION3 800T 제거 (2026-10-15)

| 완료 기준 | 근거 / 결과 |
|---|---|
| 존재하지 않는 항목 제거 | film_roll_catalog.xml에서 user-vision3-800t 제거. FilmUsageStore.top도 해당 카탈로그 ID를 제외한 뒤 상위 5개를 산출하므로 과거 이력이 전체 목록/자주 사용 선택으로 재등장하지 않음. 직접 입력 기능과 과거 저장 롤·이력 원본은 유지 |
| CineStill 제품/순서 | CINESTILL을 KODAK 마지막 행과 FUJIFILM 첫 행 사이에 배치. 50D(50)·BwXX(EI 250)·400D(400)·800T(800) 각각 독립 ID/선택 행. 기존 800T ID 유지. 롤 생성/수정 전체 목록과 사진 메모 인라인 필름 선택 모두 catalog.films 순서를 따름 |
| 공식 자료·감도 | CineStill 공식 https://cinestillfilm.com/blogs/news/cinestill-film-stock-primer 에서 4종 확인. https://cinestillfilm.com/pages/bwxx-dev-times 에서 BwXX의 주광 EI 250/텅스텐 EI 200 확인. 카탈로그에는 주광 EI 250으로 등록. ExposureCameraActivity는 기존 film.speed → FilmNoteIsoStore.load 경로를 사용하며 롤별 마지막 선택 ISO가 우선. 조명에 따른 자동 감도 전환/필름 보정 추가 없음 |
| 이전 기록 보존 | 롤 JSON·사진 저장/이름 변경 코드는 수정하지 않음. 제외는 카탈로그와 자주 사용의 표시 결과에만 적용. 기존 VISION3 800T 롤의 이름·사진·메모는 유지되며 카탈로그 감도를 찾지 못하면 기존 미등록 필름 정책(기억한 ISO 우선, 없으면 기본 200)을 따름 |
| 자동 검사 | assembleDebug·testDebugUnitTest·lintDebug 성공. 전체 단위 테스트 169개 실패/오류/건너뜀 0. Lint 오류 0·경고 133. 기존 카탈로그 검사를 새 브랜드 순서/추가 3종/삭제 ID 기준으로 갱신. 새 이력 회귀 검사 1개로 사용 빈도가 높은 삭제 항목의 재등장 방지·유효 5개 채움·과거 이름/ID와 이력 수치 비변경 확인. git diff --check 통과 |
| 실제 기기 미확인 | 기기 설치/사용자 데이터 조작 미실행. 롤 생성/수정과 메모 필름 선택의 브랜드 순서·추가 필름 선택 저장/초기 ISO·자주 사용에서 제거·기존 롤 표시는 기기 확인 필요 |

## 컷 저장 직후 사진·메모 상세 진입 (2026-10-15)

| 완료 기준 | 근거 / 결과 |
|---|---|
| 저장한 컷의 메모 열기 | FilmResultActivity는 기존 파일/컷 저장 성공 뒤에만 RESULT_OK 반환. ExposureCameraActivity가 해당 촬영의 롤 ID·컷 번호를 성공 결과에 명시적으로 전달하고 종료. FilmRollFramesActivity는 롤을 다시 읽어 기록과 사진 파일이 있는 해당 컷의 기존 FilmFrameDetailActivity를 open으로 실행. 다음 빈 컷 번호를 상세 진입에 사용하지 않음 |
| 뒤로가기·실패·취소 | 상세 아래에 원래 롤 목록이 유지되어 상세의 앱/시스템 뒤로가기는 목록으로 복귀. 목록은 기존 다음 촬영 위치를 유지. 저장 전 결과 취소는 기존 카메라로 복귀하며 RESULT_OK가 아니면 상세를 열지 않음. 저장 실패는 기존 결과 화면에 남아 재시도. 저장소·원본·촬영/위치 처리는 변경 없음 |
| 재생성·중복 진입 방지 | 촬영 Activity의 Intent에서 롤/컷을 돌려주어 목록의 onResume이 pendingNumber를 지워도 저장 컷을 식별. 상세 진입은 성공 결과 콜백에서만 수행하고 pendingNumber는 진입 전에 비움. 기존 결과 저장 작업 복원과 ActivityResult 복원 경로 유지. 소스로 확인했으며 실제 재생성 검사는 기기 미실행 |
| 자동 검사 | assembleDebug·lintDebug·assembleDebugAndroidTest 성공. Lint 오류 0·경고 133. 이번 변경은 화면 이동 연결이므로 새 단위 테스트를 추가하거나 기존 단위 테스트를 재실행하지 않음. Android 테스트는 APK 컴파일만 확인 |
| 실제 기기 미확인 | 설치/사용자 데이터 조작 미실행. 컷 저장 → 방금 저장한 사진의 메모 → 뒤로가기로 롤 목록, 중간 빈 컷/마지막 컷 저장, 저장 실패/재시도, 저장 전 취소/재촬영, 저장 중 화면 재생성과 지연 위치 자동 표시 확인은 실제 기기 검증 필요 |

## 노트 사진만 보기·핀치 확대 (2026-10-15)

| 완료 기준 | 근거 / 결과 |
|---|---|
| 노트 사진 터치 진입 | FilmFrameDetailActivity의 사진 클릭 → 비공개 FilmFramePhotoActivity. 롤 ID·컷 번호·촬영 ID를 전달하고 최신 기록과 파일을 확인. 길게 누르기 삭제 연결 유지. 목록 → 상세의 기존 진입은 유지 |
| 사진만 표시·검정 여백 | activity_film_frame_photo.xml은 검정 #000000 배경의 이미지 뷰 하나. 제목·번호·메모·버튼 없음. 전용 테마도 검정이며 시스템 바는 WindowInsetsControllerCompat로 숨김. 가장자리 스와이프 시 시스템 바의 일시 표시 허용 |
| 비율·저장 회전·파일 보존 | 기존 FilmFrameImages의 메모리 제한·백그라운드 로딩과 저장 rotation을 재사용. 비율을 유지한 fit matrix로 전체 사진을 중앙 표시. 비동기 이미지 설정 뒤 refresh 호출. 원본 파일/기록 저장 호출 없음 |
| 확대·이동 | 기존 FilmComparisonImageView의 ScaleGestureDetector로 대각선 포함 두 손가락 핀치 1~5배, 확대 중 드래그·경계 제한, 두 번 누르기 2배/전체 보기 사용. 사진 전환 콜백을 연결하지 않아 사진만 보기에서 컷이 바뀌지 않음. 화면 읽기 확대/축소 동작과 네 언어 설명 제공 |
| 복귀·재생성 | 확정된 한 번 누르기 또는 시스템 뒤로가기로 Activity 종료. 원래 상세의 컷·세로 위치를 유지하는 기존 records payload 갱신 사용. Bundle에 배율·정규화 중심 보관, 지연 로딩 후 복원. 종료 시 이미지 요청 취소/참조 해제 |
| 자동 검사 | assembleDebug·testDebugUnitTest·lintDebug·assembleDebugAndroidTest 성공. 전체 단위 테스트 168개 실패/오류/건너뜀 0. Lint 오류 0·경고 133이며 경고 없는 상태는 아님. 새 FilmFramePhotoGestureTest 4개는 대각선 확대/축소·드래그·비율/검정·지연 로딩/화면 크기 변경 복원·단일/이중 탭 구분을 검사하도록 작성하고 APK 컴파일만 확인. Android 테스트를 기기에서 실행하지 않았음 |
| 실제 기기 미검증 | 설치/사용자 데이터 조작 미실행. 노트 상세 터치 → 사진만 보기, 상하단 순수 검정·시스템 바 숨김, 실제 대각선 핀치/확대 후 이동/이중 탭, 단일 탭·뒤로가기 후 컷·스크롤/메모 초안 유지, 가로 화면·다중 창·화면 재생성·기존 회전 사진·접근성 조작은 기기 확인 필요 |

## 롤 촬영 사진 ZIP 내보내기 (2026-10-10)

사용자 정정에 따라 갤러리 저장 대신 PC로 옮길 수 있는 ZIP 파일을 추가했다. 현재 필름 노트 촬영 사진은 `FilmRollStore.saveFrame`의 앱 내부 `files/film_notes_photos`에 저장하며 `필름 노트 → 롤 → 컷 사진`에서 본다. 일반 촬영 보조의 갤러리 저장과 구분한다.

| 완료 기준 | 근거 / 결과 |
|---|---|
| 롤 전체 촬영 사진 | 롤 사진 화면 우측 상단 48dp 내보내기 버튼 → 시스템 ZIP 저장 창. 선택한 롤의 저장된 촬영 사진을 `롤 이름/frame_001.jpg`처럼 실제 컷 번호순으로 저장. 미촬영 번호를 당기지 않고 스캔/GPS/메모는 포함하지 않음 |
| PC로 옮기기 | 저장 창에서 Downloads 등 사용자가 선택한 위치에 `.zip` 생성. USB 파일 전송 등으로 PC에 복사하고 압축 해제. MediaStore 등록/새 저장소 권한은 추가하지 않음. 시스템 저장 동작은 [Android 공식 문서](https://developer.android.com/training/data-storage/shared/documents-files)의 ACTION_CREATE_DOCUMENT 계약을 사용 |
| 원본·방향 | 회전 없음은 원본 JPEG 바이트 그대로 복사. 회전한 사진은 임시 복사본의 EXIF 방향만 설정하여 재압축하지 않음. 메타데이터·원본·다른 컷·연결 스캔을 수정하지 않음 |
| 작업·취소·복원 | ViewModel의 단일 작업 스레드, 컷별 진행과 저장 단계/취소 표시. 파일 선택/작업 중 재실행·사진 삭제/수정 진입 차단. 화면 재생성은 동일 작업을 관찰. 화면 종료는 작업 취소 요청. 프로세스 복원에서 미완료 문서 삭제 시도 후 실패 상태로 표시하며 자동 재실행/성공 처리하지 않음 |
| 출력 실패 | 내부 임시 ZIP이 완성된 뒤 목적지로 복사. 원본 없음·빈 파일·파일명 경로 이탈·중복 컷·빈 롤·입출력/취소는 성공 처리하지 않음. 실패/취소 문서 삭제를 시도하고 제공자가 삭제를 거부하면 미완성 ZIP 직접 삭제 안내. 정상 작업/취소/실패 시 작업 폴더를 정리하며 강제 종료로 남은 캐시는 Android 캐시 관리 대상 |
| 자동 검사 | 신규 archive 단위 검사 6개 포함 전체 **168개 / 실패·오류·건너뜀 0**. UTF-8 롤 폴더/실제 번호/정렬/바이트 보존/회전 사본 분리·정리/위험 이름·중복·빈 파일 거부/취소·쓰기 실패에서 원본 보존/Windows 파일명 제한 확인 |
| 빌드·Lint | 최종 `assembleDebug`, R8 `assembleRelease`, `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest` 성공(1분 15초). Lint 오류 **0**, 경고 **125**. 기존 경고와 인라인 취소 버튼의 ButtonOrder 등 경고는 남아 있음 |
| 회전 기기 검사 | 실제 JPEG의 0/90/180/270도 EXIF·원본 바이트 보존·디코딩 픽셀 동일성·표시 크기 확인용 Android 테스트 1개 추가. 테스트 APK 컴파일 성공, 기기 실행은 미실행 |
| 개인정보/배포 | ZIP 내보내기 범위·사용자 선택 클라우드 저장·원본 유지·임시 사본 처리 안내를 4개 언어 로컬 방침/앱 HTML/웹 배포 HTML에 반영. 공개 웹페이지 게시·Play 업로드·기기 설치·사용자 사진 조작은 하지 않음. Debug APK는 `.debug` 패키지이며 기존 출시 앱과 별도 설치. Release APK는 서명 전 빌드 |
| 실제 기기 미확인 | 시스템 파일 저장 창, 화면 회전/프로세스 종료/제공자 실패, EXIF를 지원하는 PC 뷰어에서 ZIP 열기, 실제 USB 전송 및 큰 롤의 저장 시간/공간은 기기 확인이 남음. ADB 서버 실행이 현재 환경에서 실패하여 기기 연결/Android 테스트 실행을 확인하지 못함 |

개발용 APK: `outputs/ShutterNote_roll_photo_zip_20261010_debug.apk`. 설치된 앱에는 동일 패키지/서명의 업데이트 또는 Android Studio 재실행이 필요하며 앱 삭제로 사진을 지우고 설치하도록 안내하지 않는다.

## 검정 투명도 5% 배경 선택 (2026-10-05)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 선택한 배경 색상 | styles_film_notes.xml의 note_gallery_background를 #191715에서 불투명 #0D0D0D로 변경. 흰 바탕에 검정 불투명도 95%를 합성한 비교 이미지 기준이며 뷰 alpha는 변경하지 않음 |
| 목록·상세·사진 여백 적용 | activity_film_roll_frames.xml, activity_film_frame_detail.xml, item_film_frame_feed.xml이 같은 note_gallery_background를 참조하는 것을 소스로 확인 |
| 다른 색상·사진 보존 | 빈 칸 #2B2723, 제목/아이콘/손잡이 #ECECE8, 컷 번호 #A8AAA5, 탐색 트랙 #4A4D49 유지. 시스템 바·사진 처리·탐색/기록 로직 변경 없음 |
| 빌드·Lint | :app:assembleDebug와 :app:lintDebug 성공. Lint XML 기준 오류 0·경고 121. 색상 리소스만 변경해 단위 테스트 추가/재실행하지 않음 |
| 실제 기기 미확인 | 설치/사용자 데이터 조작 미실행. 실제 화면의 배경 색감·빈 칸 구분·목록/상세 사진 여백 표시는 기기 확인이 남음 |

이 절이 앞의 웜 블랙 배경 선택을 대체한다. 이전 검증 절의 웜 블랙 언급은 해당 시점의 기록이다.

## 왼쪽 회전 최종 정정 (2026-10-05)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 왼쪽으로 90도 | FilmFrameRecord.rotate에서 현재 각도 -90도를 normalizedRotation으로 정규화. 0도부터 270→180→90→0 순환 |
| 아이콘·접근성 방향 | 사진 위 곡선 화살표의 화살촉을 왼쪽으로 변경. 한국어/영어/프랑스어/일본어 모두 왼쪽/반시계 방향으로 90도 회전 설명 |
| 기존 데이터 보존 | 저장된 rotation 해석·JPEG·다른 컷/메모/노출값 변경 없음. 새 버튼 조작만 -90도 적용. 기존 ID 검증·실패 처리·작은 아이콘/48dp 터치·고정 사진 영역 유지 |
| 자동 검사 | 회전 순환 테스트 기대값 갱신, 기존 대상 촬영 ID 보호·메타데이터 보존·정규화 포함 전체 119개 실패/오류 0. assembleDebug·lintDebug 성공(Lint 오류 0·기존 경고 104) |
| 실제 기기 미확인 | 설치/사용자 데이터 조작 미실행. 사진의 왼쪽 회전·연속 4회 복귀·목록/재실행 표시·버튼 위치 확인은 남음 |

사용자가 방향 표현을 정정하고 왼쪽 회전을 명시했다. 이 절이 이전 오른쪽 회전 요구사항과 사용자 보고/소스 방향 불일치에 관한 기록을 대체한다.

## 오른쪽 회전 방향·아이콘 명확화 (2026-10-05)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 오른쪽 화살표 | ic_note_rotate.xml의 사진 위 곡선 화살표가 왼쪽에서 오른쪽으로 이어지고 끝의 화살촉은 오른쪽을 향함. 사진 표시·0.8 배율·색상·아이콘만 표시 유지 |
| 오른쪽 회전 연결 | FilmFrameRecord.rotate는 이미 +90도 순환, FilmRollStore.rotateFrame에서 저장 후 상세/목록의 FilmFrameImages가 Matrix.postRotate에 그대로 전달. 계산 부호 변경 없음 |
| 기존 사진 방향 보존 | 기존 저장값·JPEG·컷별 기록 변경 없음. 네 언어 접근성 설명은 이미 시계 방향/오른쪽으로 90도 회전이며 유지 |
| 자동 검사 | FilmFrameRotationTest의 90→180→270→0 순환·대상 촬영 ID 보호·정규화 포함 전체 119개 실패/오류 0. assembleDebug·lintDebug 성공(Lint 오류 0·기존 경고 104) |
| 소스와 기기 보고의 차이 | 사용자는 설치본이 왼쪽으로 회전한다고 보고했지만 현재 소스 계산은 오른쪽 방향. 설치본/버전과 실제 픽셀 회전은 확인하지 않았으므로 방향 수정 완료로 단정하지 않음 |
| 실제 기기 미확인 | 설치/사용자 데이터 조작 미실행. 새 빌드에서 구분 가능한 사진 모서리를 기준으로 오른쪽 회전·목록/재실행 일치·아이콘 의미를 확인해야 함 |

## 삭제 아이콘 위치 미세 조정 (2026-10-05)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 오른쪽·위쪽으로 조금 이동 | 목록 item_film_frame.xml과 상세 item_film_frame_feed.xml의 frameDelete 바깥 여백 4dp → 0dp. 우측 기준 각 방향 4dp 이동 |
| 터치·모양 유지 | 두 버튼의 48×48dp·8dp 내부 패딩 유지. 80% 원형/빨간 마이너스와 삭제 이벤트 코드 변경 없음 |
| 상세 사진 경계 유지 | FilmNotePhotoLayout의 실제 중앙 이미지 경계 계산에 새 여백을 적용. 가로 사진의 상단 여백이 아닌 사진 우측 상단에 표시 |
| 자동 확인 | XML 파싱으로 양쪽 여백·크기·패딩 확인. assembleDebug·lintDebug 성공(Lint 오류 0·기존 경고 104). 레이아웃 여백만 변경해 단위 테스트 재실행하지 않음 |
| 실제 기기 미확인 | 설치/사용자 데이터 조작 미실행. 목록·세로/가로 상세에서 위치와 터치 확인은 남음 |

## 상세 사진 고정 영역·가로 사진 중앙 배치 (2026-10-05)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 세로 기준 영역 확보 | FilmNotePhotoLayout.onMeasure에서 표시 너비×1.5를 EXACTLY로 측정. 이미지 유무·회전 방향이 높이 계산에 참여하지 않음 |
| 원본 비율·중앙 배치 | 상세 XML의 이미지 높이 match_parent, fitCenter 유지, adjustViewBounds/minHeight 제거. 웜 블랙 여백. 3:2 가로 사진은 영역의 4/9, 위아래 여백 각각 5/18(약 28/44/28%) |
| 회전 버튼·제목·메모 위치 유지 | 기존 제목/회전/기록 행이 고정 사진 영역 아래에 배치. 회전 저장 후 같은 영역 높이로 이미지 재로드하며 해당 컷의 세로 스크롤 재설정 제거 |
| 재생성/초기 스크롤 복원 | OneShotPreDrawListener로 영역 배치 후 pendingScroll 적용. 비트맵 로딩 완료나 ImageView 크기 변경에 의존하지 않음 |
| 삭제 아이콘 위치 | 중앙 fitCenter 이미지의 실제 표시 경계와 기존 여백을 계산해 우측 상단(end)에 배치. 가로 사진 위의 빈 여백에 아이콘이 뜨지 않음. 기존 48dp 터치 크기 유지 |
| 기존 데이터·사진 보존 | 회전 메타데이터·이미지 로더·원본 JPEG·목록 화면 변경 없음. 기존 촬영 ID 검증과 회전 순환/실패 처리 유지 |
| 자동 검사 | 상세 XML 연결·match_parent/fitCenter·adjustViewBounds 없음 확인. assembleDebug·testDebugUnitTest·lintDebug 성공. 기존 회전/탐색/기록 검사 포함 119개 실패/오류 0, Lint 오류 0·기존 경고 104 |
| 실제 기기 미확인 | 설치/사용자 사진·롤 조작 미실행. 0/90/180/270도 연속 회전에서 제목/버튼 화면 좌표 유지·위아래 여백·재실행/재생성 스크롤·가로 사진 삭제 아이콘·큰 글꼴/가로 화면 확인은 남음 |

이 절이 사진의 가로/세로 비율에 따라 상세 사진 영역 높이를 바꾸는 이전 표시 기준을 대체한다. 자동 검사는 실제 기기의 레이아웃·터치·연속 회전 동작 확인을 대체하지 않는다.

## 상세 메모 제목·촬영 시각 위치 (2026-10-05)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 사진 아래 첫 행 제목 | item_film_frame_feed.xml의 noteFeedDate를 noteFeedTitle로 교체. 밝은 20sp·내용 높이/줄바꿈, ViewCompat로 접근성 제목 제공 |
| 롤 이름 기본값 | FilmFrameDetailActivity.bindRecords에서 기존 roll.title() 사용. 이름이 비었거나 공백이면 filmDisplayName() 표시, 직접 입력 이름과 기존 제조사 접두어 제거 규칙 유지 |
| 촬영 기간 바로 위 별도 칸 | FilmRecordEditor.bind에서 컷 capturedAt를 기존 언어별 DateFormat과 기기 시간대로 표시. 기존 공통 기록 행을 사용하고 제목 `촬영 시각`·본문 날짜/시간 표시 |
| 읽기 전용 시각 | 촬영 시각 행의 클릭/포커스/터치 배경 제거, 편집 화살표 숨김. 저장 필드·촬영 시각 데이터 변경 없음 |
| 편집/컷 전환 연동 | 기존 저장 성공 reload(false)와 기록 재바인딩에서 제목도 갱신. 시각은 바인딩된 컷의 값 사용. 롤 이름/필름의 기존 편집 행 유지 |
| 회전 아이콘 | 제목 오른쪽의 기존 아이콘만 있는 축소 벡터·48×48dp 터치 영역·회전 연결 유지 |
| 자동 확인 | XML/소스로 상단 날짜 제거·제목 연결·시각/기간 순서·읽기 전용·갱신 경로 확인. assembleDebug·119개 단위 테스트 실패/오류 0·lintDebug 성공(Lint 오류 0·기존 경고 104) |
| 실제 기기 미확인 | 설치/사용자 사진·롤 조작 미실행. 긴 제목/큰 글꼴·네 언어·컷 전환 시 시각·이름 수정/삭제 시 제목·회전과 스크롤은 기기 확인이 남음 |

이 절이 상단 촬영 시각·날짜 옆 회전 버튼 배치에 관한 이전 기준을 대체한다.

기준일: 2026-10-04. 기준 문서: PROJECT_SPEC.md의 컷 기록 XML 구현 절.

| 요구사항 | 현재 근거 | 실제 기기 확인 |
|---|---|---|
| 롤 클릭으로 컷 목록 진입, 정보에서 롤 수정 | MainActivity, FilmRollFramesActivity | 미실행 |
| 1/2, 3/4 순서의 가로 2행, 한 열과 다음 열 일부 | item_frame_pair.xml, PairAdapter와 너비 계산 | 사진 크기·다음 칸 노출 폭 미확인 |
| 하단 현재 컷 번호·18위치 탐색·사진 넘기기 동기화 | SeekBar, 시작 정렬 SnapHelper, FilmFrameNavigationTest | 드래그·빠른 플링·마지막 열 미확인 |
| 24/36/직접 입력과 홀수 컷 | FilmFrameNavigationTest에서 1~999컷 검증 | 레이아웃 미확인 |
| 빈 칸 촬영과 보정 사진·촬영 당시 값 저장 | ExposureCameraActivity, FilmResultActivity, FilmRollStore.saveFrame | 촬영 및 재실행 복원 미확인 |
| 임시 캐시와 별도 영구 사진, 저장 성공 후 이동 | files/film_notes_photos, 파일 sync 후 메타데이터 commit | 프로세스 종료·저장 실패 미확인 |
| 기존 롤·최신 컷 보존 | JSON의 선택적 frames 배열, FilmRollFrameEditTest | 기존 사용자 롤 조작 없음 |
| 별도 상세의 원본 비율 사진·하단 기록·함께 넘기기 | activity_film_frame_detail.xml, item_film_frame_feed.xml, FeedAdapter/PagerSnapHelper. 이전 하단 드래그 패널 설계 대체 | 세로/가로 제스처·작은 화면·큰 글꼴·회전 미확인 |
| 컷 메모 편집·초안 복원 | MemoDialog, FilmFrameMemoViewModel | 입력 중 회전·프로세스 복원 미확인 |
| 위치·지도 | 이번 단계는 위치 없음 표시 | GPS·지도 후속 구현 |
| 4개 언어 | 25개 새 문구를 기본/ko/en/fr/ja에 제공, Lint | 번역 화면 실기기 미확인 |
| 삭제·백업·개인정보 | 롤 삭제 뒤 내부 컷 사진 정리, 기존 백업 허용 목록, 로컬 방침 갱신 | 삭제 파일 및 공개 방침 동기화 미확인 |

검사 결과: assembleDebug, testDebugUnitTest 62개(실패/오류 0), lintDebug 오류 0·경고 92. 기기나 에뮬레이터에 설치하지 않았다. 결과는 소스와 빌드·단위 규칙 검증의 근거이며 실제 촬영, 화면 디자인, 스크롤 감각을 검증한 결과가 아니다.

기존의 사용자 작업 파일을 되돌리거나 저장된 롤 데이터를 조작하지 않았다. 사진 교체·개별 컷 삭제, GPS와 지도 서비스는 후속 단계다.

## 삭제 선택 화면 디자인 수정 (2026-10-04)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 얇은 고정 도구 모음, 취소·선택 개수·삭제 순서 | main_film_notes.xml. 배경을 채운 버튼·테두리·그림자 제거, 48dp 이상 터치 영역과 내용에 맞춘 높이 |
| 선택 모드의 중복 제목·공백 제거 | MainActivity.updateRollSelectionUi가 제목을 숨기고 본문 위 여백 8dp·목록 위 여백 0dp 적용. 종료하면 기존 여백·제목 복원 |
| 선택 상태·비활성 상태 구분 | 원형 체크의 checked selector, 행의 selected 배경, 삭제/취소 글자 enabled selector 연결 |
| 중복 필름명 제거 | 제목과 필름명이 같으면 반복하지 않으며 카메라 이름은 계속 표시. 컷 수·기간 유지 |
| 기존 선택·삭제 규칙 | 저장·삭제 코드는 변경하지 않음. 기존 FilmRollSelectionTest 포함 전체 단위 테스트 62개 실패/오류 0 |
| 빌드·정적 검사 | assembleDebug, testDebugUnitTest, lintDebug 성공. Lint 오류 0, 경고 92 |
| 실제 화면 확인 | 기기/에뮬레이터 설치·조작 미실행. 큰 글꼴·번역·회전 시 레이아웃과 색감은 실기기 미검증 |

## 사진 목록 배경·탐색바 수정 (2026-10-04)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 사진을 강조하는 배경 | activity_film_roll_frames.xml의 중성 차콜 배경, 밝은 제목·아이콘. item_film_frame.xml의 짙은 빈 칸·밝은 번호. 저장 이미지·사진 처리 변경 없음 |
| 숫자·도트·눈금 제거 | framesPosition 뷰 제거. 탐색바의 두 tickMark 속성을 null로 지정하고 눈금 리소스 삭제. 2dp 트랙·24×4dp 막대 손잡이·48dp 터치 영역 |
| 연속 이동·손을 뗀 뒤 정렬 | 열당 1,000단계 탐색값을 픽셀 오프셋으로 연결. 드래그 중 SnapHelper 정렬 억제. 손을 떼면 가까운 열로 220ms 감속 이동. 한 번의 탭·키보드/접근성 변경에도 smoothScrollBy 적용 |
| 사진 넘김·탐색바 동기화 | 실제 열 오프셋으로 손잡이 위치 갱신. 화면 숫자 대신 접근성 설명으로 현재 컷 범위 제공. 복원·최초 진입은 즉시 해당 열 표시 |
| 컷 수·기존 기록 유지 | 컷 수별 최대 탐색 범위와 홀수 마지막 칸 유지. 저장·삭제 코드 변경 없음. 기존 탐색/저장/선택 테스트 포함 62개 실패·오류 0 |
| 자동 검사 | assembleDebug·testDebugUnitTest·lintDebug 성공. Lint 오류 0·경고 93(차콜 루트 배경의 Overdraw 경고 포함) |
| 실기기 확인 | 설치·조작 미실행. 실제 사진 대비·긴 롤의 빠른 드래그·탭·스냅·마지막 열·큰 글꼴·접근성 이동 감각 미검증 |

## 웜 블랙 선택·롤별 ISO 기억 (2026-10-04)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 비교안 2번 색상 | note_gallery_background #191715, empty #2B2723, text #ECECE8, muted #A8AAA5, rule #4A4D49. 이전 차콜 대체 |
| 첫 촬영은 필름 감도 | ExposureCameraActivity가 롤 filmId로 FilmRollCatalog.findFilm의 speed를 조회하고 FilmNoteIsoStore.load에 전달 |
| 변경한 ISO 우선·롤별 기억 | ISO 선택 리스너에서 별도 film_note_iso 설정의 롤 ID 키에 즉시 저장. 재진입·재생성 시 우선 로드, 촬영 성공 여부와 무관 |
| 일반 ISO와 양방향 독립 | 노트 진입은 일반 ISO를 초기값으로 사용하지 않음. 노트의 공통 노출 저장은 saveWithoutIso로 ISO 키를 쓰지 않음 |
| 나머지 노출 설정 | 조리개·셔터·화각은 기존 공유 동작 유지. 저장 사진은 촬영 당시 selectedIso를 기존 컷 기록에 전달 |
| 정보 없는 필름 | 카탈로그 감도 없음/비지원은 독립 기본값 200. 마지막 유효한 노트 ISO가 있으면 우선 |
| 삭제·백업 | 롤 삭제 성공 후 해당 ISO 키만 제거. 기존 두 백업 XML의 허용 목록을 확장하지 않아 film_note_iso 제외 |
| 자동 테스트 | FilmNoteIsoStoreTest 6개: 일반 ISO 변경 전후 초기값, 촬영 없는 변경·저장소 재생성, 일반 ISO 보존, 롤별 독립·필름 수정 우선순위, 무효값, 일부 롤 삭제 |
| 빌드·검사 | assembleDebug·testDebugUnitTest·lintDebug 성공. 전체 68개 실패/오류 0, Lint 오류 0·경고 93 |
| 실기기 검증 | 설치/사용자 데이터 조작 미실행. 실제 카메라 진입·ISO 선택·앱 재실행·화면 회전·웜 블랙 색감은 기기 확인이 남음 |

## 사진·본문을 함께 넘기는 피드 상세 (2026-10-04)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 기존 사진 클릭·별도 화면 | FilmRollFramesActivity의 기존 상세 진입 유지. 선택한 컷으로 상세의 가로 페이지 초기화 |
| 사진 아래 기록 | item_film_frame_feed.xml의 비율 유지 ImageView 아래 날짜·컷 메모·촬영값·위치·롤 정보를 세로로 표시. 웜 블랙 유지 |
| 사진·메모 동시 이동 | 각 가로 페이지는 사진과 해당 컷의 모든 기록을 하나의 NestedScrollView 안에 포함. RecyclerView + PagerSnapHelper로 함께 이동/정렬 |
| 실제 번호 순서·빈 컷 제외 | recordedNumbers로 순서 정렬. 미촬영·사진 파일 없는 컷은 상세 페이지에 넣지 않고 기존 실제 번호와 전체 예정 컷 수 유지 |
| 메모 추가·수정 | 빈 컷 메모는 큰 빈 상태 문구 없이 추가 동작 표시. 기존 DialogFragment·ViewModel 저장 유지, 대화상자의 컷 번호로 저장해 다른 컷에 잘못 적용하지 않음 |
| 롤 수정 후 최신 기록 | 롤 수정 화면 링크와 onResume 재조회. 기록만 payload로 갱신하여 사진 재읽기·세로 위치 초기화를 피함 |
| 복원·접근성 | 현재 컷·세로 스크롤 저장, 사진 로드 뒤 위치 복원. 이전/다음 컷 접근성 동작 제공. 4개 언어에 메모 추가 문구 제공 |
| 기존 드래그 패널 제거 | BottomSheetBehavior와 드래그 핸들·패널 XML/배경·폐기 문구 제거. 상세 뒤로가기는 목록으로 복귀 |
| 자동 검사 | 신규 sparse/중복/범위/단일 컷 순서 테스트 3개 포함 전체 71개 실패·오류 0. assembleDebug·lintDebug 성공, Lint 오류 0·경고 94 |
| 실제 기기 확인 | 설치/사용자 데이터 조작 미실행. 가로 넘김과 세로 스크롤 충돌, 사진 비율·길이에 따른 메모 위치, 회전 복원·빠른 이동·실제 메모 저장은 기기 확인이 남음 |

## 기록 제목·개별 컷 삭제·카메라 이력·E100D (2026-10-04)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 제목과 본문 구분 | item_film_frame_feed.xml에서 촬영 설정·필름·카메라 제목과 값을 분리. 카메라 값이 없으면 해당 그룹 숨김 |
| 목록/상세 사진 길게 누르기 | FilmFrameDeletion이 확인 창과 접근성 길게 누르기 동작 제공. 확인 창의 컷 번호·촬영 ID로 삭제 요청 |
| 해당 컷만 삭제·번호 유지 | FilmRollStore.deleteFrame에서 최신 기록 조회, 촬영 ID 확인, 메타데이터 commit 성공 후 내부 사진 정리. 다른 컷/롤 정보 유지. 빈 슬롯 재촬영 가능 |
| 삭제 후 상세 탐색 | FilmFrameDetailActivity가 최신 사진 목록 갱신, 인접 사진 선택, 마지막 사진 삭제 시 목록 복귀 |
| 마지막 카메라 복원 | CameraUsageStore의 마지막 ID/이름을 새 롤 기본값으로 적용. 직접 입력 기종 지원. 수정 중인 롤·복원된 초안은 덮어쓰지 않음 |
| 자주 사용 최대 3개 | CameraSelectionActivity의 맨 위 카테고리. 사용자 사용 횟수 내림차순, 동률 최근 사용 우선. 성공한 생성/카메라 변경만 집계, 단순 재저장·취소 제외 |
| 이전 롤과 개인정보 | 기존 롤에서 이력 1회 초기화. 기기 내 camera_usage 설정 저장, 백업 허용 목록 추가 없음. 로컬 방침/앱 내 HTML 갱신, 공개 게시 미실행 |
| E100D 별도 항목 | film_roll_catalog.xml에 kodak-ektachrome-e100d, ISO 100. 기존 E100 유지. 카탈로그 테스트로 독립 키·감도 확인 |
| 자동 검사 | 새 카메라 이력 5개·컷 삭제 4개·카탈로그 1개 포함 전체 81개 실패/오류 0. assembleDebug·testDebugUnitTest 성공. 개인정보 HTML 반영 후 최종 assembleDebug·lintDebug 재성공, Lint 오류 0·경고 94 |
| 실제 기기 미확인 | 설치·사용자 사진/롤 조작 미실행. 길게 누르기/취소/확인·화면 회전·실제 파일 삭제·마지막 사진 복귀·앱 재실행 카메라 복원·큰 글꼴 배치는 사용자 기기 확인이 남음 |

이 절의 개별 컷 삭제 구현이 앞선 후속 범위 표기를 대체한다. GPS·지도와 사진 교체는 계속 후속 범위다.

## 사진 위 삭제 아이콘 (2026-10-04)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 확인 팝업 제거 | FilmFrameDeletion의 DialogFragment/AlertDialog 제거. 길게 누르기는 아이콘 표시만 하고 해당 아이콘 클릭에서 삭제 ViewModel 실행 |
| 사진 우측 상단 `−` | 목록·상세 사진 FrameLayout에 32dp 반투명 검정 원형/밝은 마이너스, 48dp 터치 영역. 기본 숨김·빈 컷 표시 없음 |
| 선택 해제·재활용 | 다시 길게 누르기/시스템 뒤로가기/가로 탐색으로 숨김. 한 번에 하나만 표시하며 bind/recycle 때 이전 버튼·대상 연결 정리 |
| 데이터 삭제 규칙 유지 | 촬영 ID 검증·해당 컷만 삭제·번호 유지·실패 시 기록 보존·마지막 상세 복귀 유지. 삭제 중 중복 클릭 차단 |
| 자동 검사 | assembleDebug·testDebugUnitTest·lintDebug 성공. 전체 81개 실패/오류 0. UI 표시·터치 동작은 소스 확인이며 실제 기기 검증은 아님 |
| 남은 실제 기기 확인 | 목록/상세 길게 누르기·아이콘 위치/대비·삭제 탭·뒤로가기·가로 이동·재생성. 기기 설치/사용자 데이터 조작 미실행 |

앞선 삭제 확인 창 항목은 이 아이콘 방식으로 대체한다.

## 사진 삭제 아이콘 크기·색상 (2026-10-05)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 보이는 크기 80% | 공유 ic_note_remove.xml의 중심 (16,16) 기준 group scaleX/scaleY=0.8. 원형과 마이너스를 함께 축소해 원형 지름 32dp → 25.6dp |
| 빨간 마이너스 | strokeColor=#EF5350. 반투명 웜 블랙 원형 배경 유지 |
| 터치 영역·감도 유지 | 목록 item_film_frame.xml과 상세 item_film_frame_feed.xml의 48×48dp 버튼·8dp 패딩·위치 유지. FilmFrameDeletion의 길게 누르기/클릭 연결 변경 없음 |
| 자동 확인 | XML 파싱으로 양쪽 버튼의 크기·패딩·공유 리소스와 벡터 배율·빨간색 확인. assembleDebug·lintDebug 성공. 표시 리소스만 변경했으므로 단위 테스트는 재실행하지 않음 |
| 실제 기기 확인 | 설치/조작 미실행. 목록·상세의 실제 표시 크기·색 대비·길게 누르기·축소된 아이콘 주변 터치는 미확인 |

이 절이 앞선 32dp 원형/밝은 마이너스 표시 기준을 대체한다.

## 사진 기록의 항목별 작은 편집 영역 (2026-10-04)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 추가 기록 표시 | FilmRecordEditor.bind에서 컷 메모/노출 기록·롤 이름/필름/카메라/화각/컷 수/기간/메모·현상 상태/현상소/접수일/완료일/현상 메모 표시. 빈 값도 추가 가능 |
| 개별 항목 클릭 | item_note_record.xml의 제목/값 행 바로 아래에 해당 입력만 펼침. 한 번에 한 항목, 저장/취소/뒤로가기. 사진을 덮는 모달·전체 화면 전환 없음 |
| 필름 롤 수정 버튼 제거 | 상세 피드 XML의 noteEditRoll 및 Activity의 수정 화면 링크 제거. 기존 목록의 롤 정보 화면은 유지 |
| 선택형/입력형 편집 | 필름·카메라 검색/직접 입력, 자주 사용 카메라 우선. 노출 기록·화각·현상 상태는 작은 선택 목록. 날짜 YYYY.MM.DD 입력·빈 값으로 삭제, 메모 최대 1,000자 |
| 저장 범위·동시 변경 보존 | FilmRollStore.editRecord가 최신 롤을 잠금 안에서 조회 후 해당 필드만 갱신. 롤 필드는 모든 컷 공유, 컷 필드는 촬영 ID 확인. 사진 파일/실제 번호·다른 필드·노출계 ISO 기억을 변경하지 않음 |
| 초안·실패 보존 | FilmRecordEditViewModel의 초안/작업 유지, Activity Bundle로 대상 컷·촬영 ID·필드·입력 복원. 실패는 편집 영역 안에 표시하며 초안 유지. 저장 중 중복 실행 차단 |
| 검증 | FilmRecordEditsTest 9개: 필드 범위/최신 메모 보존, 삭제/교체된 촬영 보호, 컷 수 축소 제한, 엄격한 날짜·타임존·기간/현상 날짜 검증, 사진 정체성 보존, 잘못된 payload 거부 |
| 자동 검사 | 전체 90개 테스트 실패/오류 0. assembleDebug·testDebugUnitTest·lintDebug 성공. 최종 APK 패키징 성공, Lint 오류 0·경고 100 |
| 실제 기기 미확인 | 설치/사용자 데이터 조작 없음. 사진/기록 스크롤·키보드·작은 화면/큰 글꼴·검색 선택·초안 회전/프로세스 복원·실제 저장/재실행 확인이 남음 |

기존 컷 메모 팝업과 상세의 일괄 롤 수정 버튼에 관한 항목은 이 방식으로 대체한다. 촬영 시각·위치 없음은 읽기 전용이다.

## 메모 촬영 기간 캘린더 편집 (2026-10-05)

### 캘린더 바로 열기 (2026-10-05 추가 요청)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 입력·수정 첫 터치 | FilmRecordEditor.row의 PERIOD 행 클릭에서 기간 초안을 만들고 표시한 직후 openPeriodPicker를 호출. 빈 값과 기존 기간에 공통 적용. 같은 기간을 편집 중일 때도 현재 초안으로 바로 호출하여 중간 날짜 버튼 터치를 제거 |
| 날짜·저장·복원 유지 | 기존 날짜 범위 캘린더/선택 전달/고유 토큰 확인 리스너/중복 캘린더 방지/취소·지우기/인라인 저장·취소 경로 유지. bind/show에는 새 자동 호출을 넣지 않아 기록 갱신이나 화면 복원으로 캘린더가 새로 열리지 않음. 소스로 확인 |
| 자동 검사 | assembleDebug·lintDebug 성공, Lint 오류 0·경고 121(기존과 동일), git diff --check 통과. 호출 진입만 변경하여 단위 테스트 추가/재실행 없음 |
| 실제 기기 미확인 | 설치/사용자 데이터 조작 미실행. 빈 기간·기존 기간·편집 중인 기간의 첫 터치, 선택값 표시, 캘린더 취소/뒤로가기·화면 재생성, 지우기 및 실제 저장은 기기 확인 필요 |

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 롤 만들기와 같은 캘린더 | FilmRecordEditor의 PERIOD 입력을 MaterialDatePicker.dateRangePicker로 교체. 캘린더 모드로 열며 기존 시작·끝 날짜를 선택 상태로 전달 |
| 하루·기간 표시 | 동일 날짜/기간을 FilmRollRules.period로 표시. 선택한 UTC 날짜를 기존 yyyy.MM.dd 초안 형식으로 변환해 같은 저장 검증 경로 사용 |
| 선택·취소·지우기 | 확인 리스너만 초안 값을 갱신. 캘린더 취소/뒤로가기는 날짜 변경 없음. 날짜 지우기는 두 값을 함께 비우며 기존 인라인 저장/취소로 적용/폐기 |
| 재생성 복원 | ViewModel/Bundle 초안과 MaterialDatePicker Fragment 상태 사용. 고유 토큰이 일치하는 PERIOD 초안에 확인 리스너를 재연결하고 이전 리스너 제거. 다른 편집 초안/저장 중에는 날짜 반영 차단 |
| 데이터 보존 | 저장은 기존 FilmRecordEditViewModel/FilmRollStore.editRecord 유지. 해당 롤 기간만 갱신하고 대상 촬영 ID 검증·다른 컷/메모/사진 보존·실패 시 초안 유지 |
| 터치·번역 | 날짜 선택·지우기 최소 높이 48dp. 한국어/영어/프랑스어/일본어의 롤 생성 문구 재사용. 필요 없는 직접 입력용 시작일/종료일 문구 제거 |
| 자동 검사 | assembleDebug·testDebugUnitTest·lintDebug 성공. 전체 119개 실패/오류 0, Lint 오류 0·기존 경고 104. FilmRollRulesTest의 하루/기간/UTC 표시와 FilmRecordEditsTest의 날짜 검증/지우기/다른 기록 보존 확인 |
| 실제 기기 미확인 | 설치/사용자 데이터 조작 미실행. 날짜 선택/취소/지우기·캘린더 열린 상태 회전/프로세스 복원·실제 저장/재실행·큰 글꼴/번역 배치는 미확인 |

이 절이 촬영 기간의 직접 날짜 입력·모달 없음에 관한 이전 기준을 대체한다. 다른 항목의 인라인 편집과 저장 규칙은 유지한다.

## 탐색바 너비 70% (2026-10-04)

- activity_film_roll_frames.xml의 기존 좌우 24dp 안쪽 너비를 15:70:15 가중치로 나누어 탐색바를 약 70%로 줄이고 가운데 배치했다. 48dp 높이·손잡이·탐색 이벤트 연결은 유지했다.
- assembleDebug 성공. 레이아웃 변경만 수행해 단위 테스트는 다시 실행하지 않았다. 실제 기기의 길이·터치/드래그 감각은 미확인이다.

## 제조사 없는 필름 이름 (2026-10-04)

- 새 필름 지정은 카탈로그 제품명만 사용한다. FilmRollRecord.filmDisplayName이 기존 지정 필름의 제조사 접두어를 표시 시 제거하며 롤 목록/사진 목록 제목·롤 정보·상세 기록·필름 항목 편집에 연결했다. 사용자 지정 롤 이름/직접 입력 필름·카메라 이름은 유지한다. 제조사 그룹과 필름 ID/감도는 변경하지 않았다.
- assembleDebug·testDebugUnitTest 성공(기존 90개 테스트). 화면 표시 연결은 소스로 확인했으며 기기 설치/사용자 저장 데이터 변경은 수행하지 않았다.

## 노트 화각 적용·롤별 기억 (2026-10-04)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 첫 진입은 롤 화각 | ExposureCameraActivity가 최신 롤의 focalLength를 읽고 FilmNoteCaptureSettings가 35/40/50을 해당 mm 선택으로 연결 |
| 변경 값 우선·롤별 기억 | 화각 선택 리스너에서 film_note_fov의 롤 ID 키에 즉시 저장. 다음 컷/재실행/재생성에서 load 우선. 촬영 성공 여부와 무관하며 나중에 롤 화각을 바꿔도 사용자 선택 우선 |
| 일반 설정과 독립 | saveNoteExposure는 일반 ISO/화각 키를 쓰지 않고 조리개/셔터만 공유. 노트 진입은 일반 화각을 초기값으로 쓰지 않음 |
| 프리뷰·사진·기록 연결 | 선택값을 기존 CameraControl 줌 적용에 전달, 촬영은 적용 future 완료를 기다림. 촬영 시 shotField에 고정해 결과/컷 기록으로 전달 |
| 삭제·백업 | 롤 삭제 성공 후 해당 화각 키 제거. 기존 백업 허용 목록 확장 없음. 로컬 방침/앱 내 HTML 갱신, 공개 게시 미실행 |
| 자동 검사 | 화각 매핑/잘못된 입력 3개, 롤별 저장/재생성/일반 설정 보존/무효값/선택 삭제 5개 포함 전체 98개 실패·오류 0. assembleDebug·testDebugUnitTest·lintDebug 성공, Lint 오류 0·경고 100 |
| 실기기 미확인 | 기기 설치/사용자 데이터 조작 미실행. 실제 50mm 프리뷰/저장 사진 범위, 변경 후 재진입/재실행/회전 및 일반 노출계와의 독립성을 기기에서 확인해야 함 |

이 절이 앞선 노트/일반 화각 공유 검증을 대체한다.

## 메모의 카테고리 선택·간격 (2026-10-04)

- 필름·카메라 항목을 열면 높이 240dp의 인라인 선택 영역에서 왼쪽 제조사/자주 사용/직접 입력 카테고리, 오른쪽 검색·개별 항목을 표시한다. 필름 제품명에는 제조사 접두어가 없고 카메라 선택은 기존 사용 이력과 연결된다. 직접 입력은 해당 카테고리를 선택한 경우에 제공한다.
- 컷 수는 24/36/직접 입력을 선택하고 직접 입력일 때만 숫자 필드를 표시한다. 선택 카테고리·검색어·직접 입력 초안은 기존 Bundle에 유지한다. 저장/취소와 기존 필드 단위 검증을 유지한다.
- 상세의 현상·스캔 제목과 5개 행을 제거했다. 저장된 현상 정보와 롤 생성 화면은 변경하지 않았다. 이전 현상 항목 초안 복원은 닫힌 상태로 처리한다.
- 항목 위 간격 18→12dp, 섹션 구분선 위/아래 28/16→20/12dp. 선택 항목의 터치 높이 48dp 유지.
- assembleDebug·testDebugUnitTest·lintDebug 성공, 테스트 98개 실패/오류 0. 실제 기기 설치·사용자 데이터 조작 미실행. 카테고리 선택/내부 스크롤·검색/키보드·큰 글꼴/회전·간격은 기기 확인이 남음.

## 자주 사용 필름·새 롤의 화각/컷 수 복원 (2026-10-04)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 자주 사용 최대 5개 | FilmUsageStore의 사용 횟수/최근 순 정렬. 기존 롤로 1회 초기화, 직접 입력도 지원. 필름 선택의 자주 사용/전체 탭과 상세의 카테고리 목록에 연결 |
| 실제 사용만 집계 | FilmRollStore의 성공한 새 롤 생성/필름 변경에서만 record. 같은 카탈로그 ID의 제조사 표시 변경은 신규 사용으로 세지 않음. 선택/취소/단순 재저장 제외 |
| 화각/컷 수 기본값 | FilmRollDefaultsStore가 성공한 생성 또는 해당 필드 변경을 기억. 새 롤에만 restore, 수정 중인 롤과 복원된 초안은 덮어쓰지 않음. 직접 입력 컷 수와 모드 유지 |
| 촬영 선택과 독립 | 촬영 중 바꾼 화각은 기존 film_note_fov에만 저장하며 새 롤 기본값을 바꾸지 않음. 기본 이력 없으면 35mm/36컷 |
| 재생성·개인정보 | 선택한 필름 탭/초안 Bundle 복원. film_usage·film_roll_defaults는 기존 백업 허용 목록에서 제외. 로컬 방침/앱 내 HTML 갱신, 공개 게시 미실행 |
| 자동 검사 | 신규 빈도/동률/상위 5개/초기화/직접 입력/표시 변경 4개 및 기본값/재실행/필드 독립/검증/초기화 4개 포함 전체 106개 실패/오류 0. assembleDebug·testDebugUnitTest·lintDebug 성공, Lint 오류 0·경고 104 |
| 실제 기기 미확인 | 설치/사용자 데이터 조작 미실행. 자주 사용/전체 전환·선택/체크·화각/직접 컷 수의 새 롤 복원·취소/회전/앱 재실행은 기기 확인이 남음 |

## 800 감도 필름 2개 추가 (2026-10-04)

- film_roll_catalog.xml에 CineStill 800T(800)와 요청 기록명 VISION3 800T(EI 800)를 별도 ID로 추가했다. VISION3 800T는 공식 제품 확인을 의미하지 않으며 기존 VISION3 500T는 유지했다. 사용자에게 씨네 800의 해석과 비공식 기록명 구분을 안내했다.
- 기존 초기 ISO 연결이 카탈로그 speed=800을 읽으며, 사용자 ISO 기억이 있으면 그 값을 우선한다. 전체/카테고리/자주 사용 목록은 동일 카탈로그와 기록 이력을 사용한다.
- assembleDebug·testDebugUnitTest 성공(전체 106개). 카탈로그 테스트에서 추가 ID의 존재·중복 없음·브랜드 그룹/감도 순서를 확인했다. 기기 설치와 실제 선택/촬영은 미실행이다.

## 촬영 화각 기록 통일·저장하지 않은 결과 뒤로가기 (2026-10-04)

- 상세의 화각 행을 롤 기본 focalLength 대신 컷의 fieldOfView로 표시한다. 촬영 설정 요약과 같은 값을 사용하며 촬영 정보 영역으로 옮겼다. SHOT_FOCAL 편집은 해당 컷의 같은 fieldOfView만 갱신하고 롤/다른 컷/사진 파일은 변경하지 않는다.
- ExposureCameraActivity의 결과 전달 플래그와 즉시 종료를 제거하고 노트 결과용 ActivityResultLauncher를 연결했다. 저장하지 않은 결과는 기존 카메라로 복귀, 저장 성공 RESULT_OK일 때만 카메라가 목록에 성공을 전달하고 종료한다. 일반 촬영 결과 흐름은 유지했다.
- 촬영 순간 shotField 고정 → 결과 EXTRA_FIELD_OF_VIEW → 컷 저장 연결을 소스로 확인했다. 신규 동기화/잘못된 값·촬영 ID 보호 테스트 2개 포함 전체 108개 실패/오류 0. assembleDebug·testDebugUnitTest·lintDebug 성공.
- 기기 설치/사용자 데이터 조작 미실행. 실제 50mm 촬영 기록·설정 유지·앱/시스템 뒤로가기·재촬영·회전/프로세스 복원·저장 후 목록 이동은 실제 기기 확인이 남음.

## 노트 사진 회전 (2026-10-04)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 상세에서 회전 | 날짜 옆 noteRotate 버튼, 48dp 터치 영역. 누를 때마다 시계 방향 90도 회전 |
| 컷별 방향 저장 | FilmFrameRecord.rotation JSON 저장, 기존 기록 기본 0도. FilmRollStore가 최신 촬영 ID를 검증한 해당 컷만 갱신 |
| 원본과 다른 기록 보존 | 원본 JPEG 재압축 없이 FilmFrameImages에서 표시용 비트맵 회전. 다른 컷·메모·노출값 유지 |
| 상세/목록 반영 | 두 화면 모두 저장된 회전값으로 이미지 로드. 회전된 비트맵 크기로 비율 반영, 상세 세로 위치 복원 연결 |
| 저장 중 조작/실패 | FilmFrameRotationViewModel로 중복 실행 방지·화면 재생성 유지. 실패 시 방향 유지와 안내 |
| 자동 검사 | 회전 순환·촬영 ID/다른 기록 보호·각도 정규화 3개 포함 전체 111개 실패/오류 0. assembleDebug·testDebugUnitTest·lintDebug 성공, Lint 오류 0·경고 104 |
| 실제 기기 미확인 | 설치/사용자 데이터 조작 미실행. 사진 비율·스크롤 유지·목록 반영·재실행·연속 누르기·화면 재생성은 실제 기기 확인이 남음 |

## 회전 아이콘 1번 후보 적용·축소 (2026-10-05)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 선택한 1번 모양 | ic_note_rotate.xml에 사진 사각형·산/점과 모서리의 짧은 시계 방향 화살표를 1.7 단위의 둥근 선으로 구성. 기존 원형 새로고침 모양 제거 |
| 아이콘만 표시 | 벡터 경로만 사용하며 숫자·90°·버튼 텍스트 추가 없음. 기존 접근성 contentDescription 유지 |
| 현재보다 작은 표시 | 24dp 벡터 중심 (12,12) 기준 scaleX/scaleY=0.8. 19.2dp 표시 영역 안에서 렌더링 |
| 터치·회전 동작 | item_film_frame_feed.xml의 기존 48×48dp 버튼·12dp 패딩·위치 유지. 회전 클릭/저장/이미지 로드 코드 변경 없음 |
| 자동 확인 | XML 파싱으로 4개 경로·0.8 배율·기존 터치 영역·보이는 텍스트 없음 확인. assembleDebug·lintDebug 성공(Lint 오류 0·기존 경고 104). 표시 리소스 변경만으로 단위 테스트 재실행하지 않음 |
| 실제 기기 미확인 | 설치/조작 미실행. 실제 작은 아이콘의 선명도·크기·사진 회전 조작은 기기 확인이 남음 |

## 자주 사용 항목 길게 눌러 삭제 (2026-10-05)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 필름/카메라 선택창 | FilmRollActivity·CameraSelectionActivity의 자주 사용 개별 행에 FrequentItemRemoval 연결. 전체 카탈로그에는 삭제 동작 없음 |
| 상세 카테고리 | FilmRecordEditor의 자주 사용 행 삭제 후 해당 영역만 갱신. 선택값·카테고리·검색·저장/취소 초안 유지 |
| 이력 삭제 범위 | 해당 ID 또는 직접 입력 이름의 빈도/이름/최근 이력만 삭제. 기존 롤과 마지막 기본값 유지, 후순위 항목으로 채움 |
| 지속성과 재사용 | 삭제 후 재실행/기존 롤 재초기화로 되살리지 않음. 실제 재사용 저장 시 횟수 1부터 재등록 |
| 실패/접근성 | commit 실패 시 메모리의 이력을 복원하고 목록 갱신 없이 안내. 팝업 없이 Toast, 접근성 길게 누르기 동작에 삭제 문구 제공 |
| 자동 검사 | 두 이력 저장소 각각 삭제 지속·기본값 보존·재초기화 방지·재사용·직접 입력 구분·저장 실패 복원·후순위 표시 검사. 전체 119개 실패/오류 0, assembleDebug·testDebugUnitTest·lintDebug 성공, Lint 오류 0·경고 104 |
| 실제 기기 미확인 | 설치/사용자 데이터 조작 미실행. 길게 누르기와 클릭 구분·빈 목록·검색 중 삭제·초안 보존·접근성 조작은 기기 확인이 남음 |

## 메모 내 지도·좌표·구글 지도 연결 (2026-10-05)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 메모 안 지도 하나 | FilmNoteLocationView에 현재 컷의 저장된 위치가 있을 때 200dp MapLibre 지도를 표시. 프로그램은 MapLibre Native 13.6.1 OpenGL, 배경 데이터는 OSM HTTPS raster 타일. 다른 컷/재사용/Activity 종료 시 지도 수명 정리. 레코드 갱신·사진 회전은 동일 좌표의 지도를 재생성하지 않음 |
| 핀 생성·편집 | FilmLocationEditorDialog에서 지도 탭·위도/경도 직접 입력·명시적 현재 위치 가져오기. 기본 지도 중심은 서울이지만 초기 위치가 없는 컷에는 핀이나 좌표를 자동 저장하지 않음. 저장/취소·기존 위치만 삭제·초안/저장 작업의 화면 재생성 보존을 연결. 현재 위치는 촬영 당시 위치로 자동 간주하지 않음 |
| 위치 권한·실패 경로 | 버튼에서만 fine/coarse 함께 요청, 대략 위치 허용. 네이티브 Fused/GPS/Network 제공자에서 한 번 가져오고 20초 제한·2분 이상 된 좌표 거부. 수동 변경/중지/취소 시 요청 취소. 백그라운드 권한·촬영 중 수집 없음. 권한 거부/서비스 꺼짐/시간 초과는 수동 선택 가능. 이 흐름은 소스 확인이며 기기 검증 미실행 |
| 컷 데이터 보존 | FilmFrameRecord.location의 선택적 JSON, 예전 기록 기본 없음·잘못된 위치만 무시. FilmRollStore의 잠금 안에서 최신 롤 로드·컷과 촬영 ID 확인·그 위치만 갱신·기존 commit 실패 복원 경로 사용. 롤 수정의 최신 frames 병합 유지. 위치는 기존 롤 데이터와 함께 백업/기기 이전 제외 |
| 구글 지도 링크 | 저장된 지도와 좌표 탭 → HTTPS Google Maps search URL의 api=1, query=위도%2C경도. 원래 좌표의 전체 정밀도·부호를 전달하고 Google API 키 없음. 브라우저/지도 앱이 없으면 안내. 양수/음수 좌표 및 프랑스어 기본 로케일 링크를 단위 테스트로 확인. 앱/브라우저 실행은 실제 기기 미확인 |
| 지도 출처·통신 | 화면 하단 © OpenStreetMap contributors 표기와 출처 링크. 표준 tile.openstreetmap.org URL·앱 식별 User-Agent·32MB HTTP 캐시·서버 캐시 헤더·헤더가 전혀 없을 때 7일 캐시. zoom prefetch=0, 최대 zoom 19. 대량/오프라인 사전 다운로드 없음. 지도 실패/20초 로딩은 재시도, 좌표와 외부 링크는 별도 유지 |
| 개인정보 | 4개 언어 원문·앱 HTML·웹 배포 파일에 기기 내 좌표·정확도 저장, 명시적 위치 권한, OSM 타일 영역/IP/앱 식별 전송 및 Google 링크 좌표 전달 추가. 공개 페이지 미게시·현재 공개 문서 기준일 미확인. generator는 published_content_date가 현재 기준일과 일치하기 전까지 앱의 최신 내부 안내를 사용 |
| 자동 검사 | assembleDebug·testDebugUnitTest·lintDebug·assembleRelease 성공. 전체 127개 실패/오류 0(좌표 기능 8개 추가). Lint 오류 0·경고 121; 경고 전체가 기존 경고인 것은 아님. .artifact-build/verify_note_map.py로 스타일 JSON·번역 일치·백그라운드 권한 없음·위치 하드웨어 선택·Vulkan 제한 없음·지도 4개 ABI 중 64비트 ELF 및 비압축 APK 16KB 정렬 확인 |
| 서버 점검 | 연락처를 포함한 HEAD 요청은 자동 승인 검토에서 거절되어 실행되지 않음. 앱 코드에서도 연락처를 제거. 앱 이름/패키지만 사용하는 공개 z0/x0/y0 타일 HEAD 점검으로 HTTP 200·image/png·Cache-Control을 확인. 사용자 위치/사진/연락처 전송 없음. 서버 응답 확인은 실제 Android 화면 검증을 대체하지 않음 |
| 실제 기기 미확인 | 설치/사용자 데이터 조작 미실행. 지도 표시/핀 탭·현재 위치/대략 권한/거부·인터넷 끊김/재시도·취소/저장 실패·키보드/큰 글꼴·사진 가로 탐색/세로 스크롤·재생성/재실행·Google 앱 설치/미설치 연결은 사용자 기기 확인 필요. 공개 OSM 타일 기반 시험 구현이며 배포용 공급자/이용량 검토 및 공개 개인정보 갱신은 남음 |

## 롤 생성 시 위치 권한·촬영 위치 자동 저장 (2026-10-05)

이 절은 위 지도 구현의 버튼에서만 권한 요청·촬영 중 자동 수집 없음 기준을 사용자 추가 요청에 따라 대체한다.

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 새 롤 생성에서 권한 요청 | FilmRollActivity는 검증/저장 성공 뒤 위치 권한이 없을 때 fine/coarse를 함께 요청. 기존 롤 수정·저장 실패는 요청하지 않음. 응답 뒤 허용 여부와 관계없이 롤 생성 완료. 권한 창 동안 중복 저장 차단·처리 여부/대기 여부/완료 여부를 Bundle에 보존. 실제 권한 창은 기기 미확인 |
| 촬영마다 자동 위치 | 노트 ExposureCameraActivity에만 FilmCaptureLocation을 연결하고 onResume에서 갱신 시작. 플랫폼 Fused(API 31+) 또는 GPS(정밀 권한)/Network 사용. 촬영 화면에서는 위치 권한을 요청하지 않음. 거부 시 즉시 위치 없음으로 진행. 일반 촬영 보조에서는 위치 수집 객체도 생성하지 않음 |
| 촬영 시점 고정·오래된 위치 방지 | 단조 증가 elapsedRealtimeNanos 기준 셔터 15초 이내 위치만 사용. 없으면 JPEG 촬영과 병행해 최대 5초 대기하며 이후 새 위치로 해당 컷을 채우지 않음. 비슷한 시점의 Network 위치가 정확한 GPS 위치를 덮지 않음. 좌표/정확도·촬영 시각을 해당 결과 Intent에 고정하고 저장 버튼을 누른 시점의 위치는 읽지 않음 |
| 거부·GPS 실패에서도 사진 유지 | 권한 없거나 제공자 없으면 즉시 위치 없이 촬영. 시간 초과 시 null 위치로 결과 진행. onPause에서 위치 구독 제거/대기 종료; 이미 받은 사진 결과는 전경 복귀 뒤 열며 화면이 가려진 동안 결과를 자동 실행하지 않음. 이미지 실패 시 해당 위치 요청도 중지하고 새 촬영은 별도로 준비. 종료 뒤 늦은 위치 콜백은 이전 구독/촬영 ID로 차단. 소스로 확인, 프로세스 강제 종료 중 촬영 복구와 실제 기기 상태 전환은 미검증 |
| 저장·메모 지도·기존 기록 | FilmFrameCapture의 optional 좌표/시간을 FilmResultActivity.saveCurrentResult에서 FilmFrameRecord에 함께 반영. 이전 Intent는 위치 없음·기존 파일 시각으로 저장. 기존 FilmNoteLocationView.bind는 좌표가 있는 현재 컷에 지도를 바로 생성하므로 현재 위치 버튼 불필요. 과거 위치 없는 컷은 자동 변경하지 않으며 수동 수정/삭제·구글 지도 링크 유지 |
| 개인정보·수집 범위 | ko/en/fr/ja 소스와 앱 HTML·배포용 HTML에 롤 생성에서 권한 요청, 전경 노트 촬영 자동 기록, 거부/위치 미수신 시 사진만 저장, 화면 이탈 시 중지 반영. 백그라운드 권한 추가 없음. 좌표는 기존 컷 데이터와 함께 백업/기기 이전 제외. 공개 개인정보 사이트는 미게시이며 내부 최신 안내를 사용 |
| 자동 검사 | assembleDebug·assembleRelease·전체 단위 테스트 132개 실패/오류 0·Lint 오류 0/경고 121(이전 지도 구현과 같은 수) 통과. 촬영 위치 신선도/촬영 뒤 다른 위치로 이동/늦은 위치/정확도/대략 위치 및 없음 테스트 5개 추가. Intent parcel 복원·좌표 없는 재사용·기존 시간 fallback의 Android 테스트 2개는 assembleDebugAndroidTest로 APK 컴파일만 성공, 기기 실행하지 않음. OSM 설정·번역·전경 권한/하드웨어·지도 4개 ABI의 ELF/APK 정렬 점검 및 git diff --check 통과 |
| 실제 기기 완료 기준 | 새 롤 허용/거부/대략 권한 → 촬영/저장/메모 지도, 권한을 이미 허용한 상태의 새 롤, 기존 롤 수정 시 요청 없음, 서비스 꺼짐/첫 수신 지연/실내 실패, 촬영 직후 이동과 결과 화면 오래 대기, 앱 가림/복귀, 권한 창에서 화면 재생성/프로세스 재생성, 저장 실패/재시도, 다음 컷·앱 재실행의 위치 일치 여부는 기기 미검증. 자동 검사는 GPS 수신 및 실제 지도 표시를 보장하지 않음 |

## 사진 촬영 우선·완료 후 위치 조회 (2026-10-05)

이 절은 위 자동 위치 구현의 촬영 화면 위치 수집 및 위치 완료를 기다리는 결과 이동을 대체한다.

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 셔터에서 위치 처리 제거 | ExposureCameraActivity에서 FilmCaptureLocation 객체·시작/중지·촬영 전 좌표 요청/대기 제거. 기존 화각 적용 확인과 takePicture 호출 유지. 사진 저장 콜백에서 촬영 시각과 JPEG 완성의 단조 시각만 Intent에 넣고 즉시 noteResultLauncher 실행. GPS 확인/완료 여부를 촬영 호출과 결과 이동 조건으로 사용하지 않음 |
| 결과 먼저 표시·촬영 화면 안내 제거 | NoteShot.locationReady/좌표 제거, 결과 실행은 JPEG Intent 준비와 전경 상태만 확인. 촬영 화면의 note_location_locating 표시 제거. FilmResultActivity는 기존 사진 처리/표시를 그대로 시작하며 noteLocation 완료에 의존하지 않음. 실제 촬영 지연/프레임 시점은 CameraX 및 기기 검증 필요 |
| 촬영이 끝난 뒤 위치 요청 | 새 사진의 photoReadyNanos를 FilmFrameCapture로 전달하고 결과 화면 onStart의 FilmResultLocationViewModel에서 위치 요청. JPEG 완성부터 남은 5초만 사용. 최근 15초 캐시/새 위치를 사용할 수 있지만 시간이 지난 결과/기기 재부팅은 현재 위치를 붙이지 않음. 일반 촬영·기존 Intent는 위치 요청 없음 |
| 저장과 위치 병행 | 저장 버튼은 이미지 디코딩/노출 처리를 즉시 시작. 최종 컷 작성 직전에 저장 작업 스레드만 FilmPostPhotoLocation.awaitForSave로 남은 시간 대기. 사진 촬영과 결과 UI 스레드는 기다리지 않음. 위치 없음/거부/시간 초과/화면 이탈은 null 좌표로 기존 사진 저장 실행. 촬영 시각은 원래 셔터 시각 유지 |
| 재생성·취소·늦은 콜백 | ViewModel에 같은 요청과 좌표를 보관하고 완성된 좌표/위치 없음은 Bundle에도 보존. 화면 재생성에서는 요청을 재시작하지 않음. 일반 화면 이탈/onCleared에서 요청 중지하고 저장 대기 해제. 완료된 FilmPostPhotoLocation은 늦은 콜백으로 위치를 바꾸지 않음. 기존 사진 JSON/촬영 ID 검증 및 메모 지도/수동 편집/외부 지도 연결 유지 |
| 자동 검사 | assembleDebug·assembleRelease·testDebugUnitTest·lintDebug·assembleDebugAndroidTest 성공. 전체 137개 실패/오류 0, Lint 오류 0·경고 121. 새 테스트 5개로 긴 노출 뒤 위치 창·위치 도착과 저장의 연결·취소 후 늦은 위치·오래된 결과/재부팅·복원된 위치/위치 없음 검사. 기존 Android Intent 테스트에 사진 완성 시각 parcel 복원/이전 Intent 기본값 추가, 기기 실행은 하지 않음 |
| 개인정보 | 4개 언어 소스/앱 HTML/웹 배포용 페이지를 촬영 후 결과 화면의 전경 위치 사용·셔터/결과 표시 무대기·화면 이탈 시 중지로 갱신. 공개 웹 게시 미실행, 내부 최신 안내 사용 유지 |
| 실제 기기 미검증 | 셔터 순간과 실제 촬영 프레임, 위치 서비스 꺼짐/느린 첫 GPS에서도 결과 이동 속도, 사진 표시 전/직후 저장, 메모 지도 좌표, 저장 중 앱 가림/복귀, 결과 화면 재생성, 긴 노출, 권한 거부/대략 위치를 실제 기기에서 확인해야 함. 기기 설치나 사용자 데이터 조작은 하지 않음 |

## 지도 우측 아래 출처 표시 축소 (2026-10-05)

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 저장 형태 확인 | FilmFrameLocation.toJson은 위도/경도/선택적 정확도를 저장. FilmNoteLocationView는 해당 좌표로 FilmNoteMap을 생성. 지도 스크린샷 저장은 없으며 지도 타일은 기존 HTTP 캐시/네트워크로 읽음 |
| 표시 축소 | FilmNoteMap의 출처 글씨 11sp → 10sp, 좌우/상하 패딩 6/4dp → 4/2dp. 배경은 작은 TextView에만 칠하고 기존 48dp 전체 배경 제거. 전체 © OpenStreetMap contributors 문구 및 기존 명암 대비 유지 |
| 터치·접근성·큰 글꼴 | 투명 FrameLayout에 최소 너비/높이 48dp 터치 및 콘텐츠 설명/포커스를 적용. 저작권 URL 유지. 글씨는 우측 아래에 항상 표시하고 중복 화면 읽기 방지. 내용이 길어지면 wrap_content로 높이가 늘어남. 메모 지도와 편집 지도에 공통 적용 |
| 출처 기준 확인 | OSMF Attribution Guidelines(https://osmfoundation.org/wiki/Licence/Attribution_Guidelines)의 읽기 쉬운 출처/저작권 링크/지도 주변 배치 기준과 Tile Usage Policy를 확인. 크기 하한 숫자를 임의의 의무 기준으로 주장하지 않고 전체 문구/명암/링크 유지 |
| 자동 검사 | assembleDebug·lintDebug 성공, Lint 오류 0·경고 121(이전과 같은 수). 화면 표시만 변경하여 단위 테스트는 추가하거나 재실행하지 않음 |
| 실제 기기 미확인 | 기기 설치/조작 미실행. 축소 후 가독성·작은 배경의 실제 크기·큰 글꼴/좁은 화면의 줄바꿈·출처와 지도 클릭 구분은 기기 확인 필요 |

## 사진 저장 후 위치 자동 추가·최대 10초 조회 (2026-10-05)

이 절은 위 촬영 우선 구현의 결과 화면 전용 조회·저장 작업의 위치 대기·5초 제한을 대체한다.

| 완료 기준 | 확인 근거 / 결과 |
|---|---|
| 촬영·사진 저장 무대기 | 셔터/CameraX/JPEG 결과 이동은 기존 촬영 우선 흐름 유지. FilmResultActivity.saveCurrentResult의 awaitForSave를 제거하고 현재 좌표만 즉시 조회하여 사진 파일/컷 기록을 먼저 저장. 저장 성공 시 위치 상태를 알리고 결과 화면을 기존처럼 종료하므로 GPS 미완료는 저장 완료 조건이 아님 |
| 화면 이동·다음 촬영 중 지속 | FilmCaptureLocationJobs는 Application 전경에서 촬영 ID별 작업을 보관. 결과 ViewModel 해제 뒤에도 저장 완료/저장 중 작업을 유지하고 사진별 별도 상태/구독으로 연결. Activity 전경 수명 콜백으로 앱 안의 화면 이동을 유지하며 앱 전체가 가려지면 미완료 조회 종료. 화면 재생성은 같은 촬영 ID의 작업을 재사용하고 구성 변경에는 짧은 전경 전환 유예 적용. 실제 Activity 전환은 기기 미실행 |
| 위치가 먼저/사진이 먼저 | FilmPostPhotoLocation의 사진/위치 완료는 동기화된 별도 상태이며 차단 대기 없음. 이미 받은 위치는 최초 사진 저장에 포함. 늦은 위치는 사진 저장 성공 뒤 한 번만 claim하여 별도 작업 스레드에서 기존 컷의 location만 수정. 완료 순서 교차·동시 완료 한 번 연결·저장 중 화면 종료·다음 사진의 역순 위치 수신은 단위 테스트 통과 |
| 10초·수신 즉시 종료 | WAIT_NANOS를 10초로 변경. JPEG 완성의 단조 시각부터 남은 시간만 사용하고 최근 15초 제공자 캐시 기준 유지. 수신/시간 초과/앱 배경 전환/미저장 취소 시 tracker.stop에서 removeUpdates와 타이머 제거. 9초 수신 허용·10초 경계/초과·오래된 결과·재부팅·완료된 상태 복원은 단위 테스트 통과. 실제 GPS 구독 해제 시점은 기기 미확인 |
| 열린 메모 지도 자동 표시 | 좌표 저장 commit 성공 뒤 변경 롤 ID를 LiveData로 전달. FilmFrameDetailActivity는 활성 수명에서 해당 롤을 다시 읽고 records payload로 갱신. 사진 재로딩/스크롤 초기화 없이 FilmNoteLocationView가 새 좌표 지도를 생성. 이미 닫힌 메모는 다음 진입의 기존 저장 데이터로 표시. 소스 확인이며 실제 지도 표시/스크롤 유지 검증은 기기 미실행 |
| 삭제·수동 변경 보호 | 최신 저장 데이터를 LOCK 아래 읽고 롤/컷/촬영 ID·위치 없음·locationRevision 0을 확인한 경우만 자동 좌표 추가. 수동 수정/삭제는 revision을 증가시켜 JSON에 보관하므로 위치를 다시 삭제한 경우도 자동 입력을 차단. 과거 JSON은 revision 0으로 호환. 다른 촬영 ID/삭제된 컷/기존 좌표/수동 변경과 삭제 후 재로드/기존 메모·회전 유지 단위 테스트 통과 |
| 실패·취소의 사진 보존 | 위치 없음/권한 거부/시간 초과는 null 좌표 완료로 사진을 유지. 위치만 저장하는 commit 실패는 사진 파일을 삭제하지 않음. 미저장 결과 취소와 화면 종료 뒤 사진 저장 실패는 GPS 요청 종료. 화면에 남은 사진 저장 실패는 재시도를 허용하며 받은 좌표를 유지. 실패/재시도·취소 후 늦은 콜백·좌표 없음 상태는 단위 테스트, 저장소 실패 처리/권한 경로는 소스 확인 |
| 개인정보·권한 | 4개 언어 소스·앱 HTML·배포용 HTML을 최대 10초 전경 조회/앱 내부 화면 이동 중 지속/사진 저장 후 자동 추가/성공·제한 시간·앱 배경·미저장 취소 시 중지로 갱신. 공개 사이트 게시 미실행. 백그라운드 위치 권한/서비스 추가 없음 |
| 자동 검사 | assembleDebug·assembleRelease·testDebugUnitTest·lintDebug·assembleDebugAndroidTest 성공. 전체 145개 실패/오류 0, Lint 오류 0·경고 121(이전과 같은 수). OSM 설정/번역/전경 권한·선택적 하드웨어/지도 4개 ABI ELF·APK 정렬과 git diff --check 통과. Android 테스트는 APK 컴파일만 확인하고 기기 실행하지 않음 |
| 실제 기기 미검증 | GPS 지연 중 저장 완료/다음 촬영/다른 화면 조작, 열린 메모 자동 지도 표시·스크롤, 10초 제한/빠른 수신 즉시 GPS 종료, 앱 배경 전환·구성 변경, 연속 사진별 좌표 연결, 롤/컷 삭제 및 수동 위치 편집·삭제와 자동 수신의 동시 처리, 권한 거부/대략 위치/위치 서비스 꺼짐, 사진/좌표 저장 실패·재시도는 실제 기기 검증 필요. 기기 설치나 사용자 데이터 조작은 하지 않음 |
## ZIP 내보내기 완료 알림·저장 위치 연결 (2026-10-16)

| 완료 기준 | 대조 결과 |
| --- | --- |
| 불필요한 설명 제거 | FilmRollFramesActivity의 성공/취소 토스트와 네 언어 취소 문구를 제거. 성공 문구는 시스템 알림용 짧은 제목으로 변경. 실패/미완성 정리 안내와 진행 표시는 유지 |
| 성공 ZIP만 알림 | FilmRollExportViewModel에서 목적지 출력 스트림을 닫고 COMPLETE가 확정된 후 한 번 호출. 화면 관찰자에서는 게시하지 않으며 취소/실패/프로세스 복구 경로에 성공 알림 없음 |
| 저장 위치 연결 | Android 8 이상 ACTION_OPEN_DOCUMENT + application/zip + 실제 목적지의 EXTRA_INITIAL_URI로 부모 폴더 표시를 요청. Android 7은 같은 문서의 ACTION_VIEW 선택 창. 저장 경로나 제공자 document ID를 추정하지 않음. 파일명은 실제 DISPLAY_NAME 조회 |
| 파일별 독립성·권한 | 문서 URI를 PendingIntent data와 알림 tag에 사용. FLAG_IMMUTABLE과 READ 전용 ClipData/URI 권한, 지속 READ 권한 시도. 알림은 직접 파일 탐색 Activity를 열고 서비스/브로드캐스트 중계 없음 |
| 알림 권한·차단 | POST_NOTIFICATIONS만 추가. Android 13 이상 첫 내보내기에서 한 번 요청, 응답에 관계없이 저장 창 진행. 알림 차단/권한 또는 제공자 오류가 ZIP 저장 실패로 전파되지 않음. export_notifications 설정은 기존 백업 허용 목록에 포함하지 않음 |
| 표시·언어 | 소리/진동 없는 낮은 중요도 채널, 파일명과 짧은 완료 제목/저장 위치 열기 동작. 한국어 기본/ko·영어·프랑스어·일본어 리소스 연결 |
| 빌드·회귀 | assembleDebug·lintDebug·assembleDebugAndroidTest 성공. FilmRollPhotoArchiveTest 6개 실패/오류/건너뜀 0. Lint 오류 0/경고 130. 로그: .artifact-build/export-notification-build.log |
| 새 Android 검사 | 실제 URI·ZIP MIME 필터/초기 위치·읽기 전용 전달 및 연속 두 내보내기의 PendingIntent 분리 검사 2개 추가. 테스트 APK 컴파일만 성공, 기기 실행하지 않음 |
| 실제 기기 미확인 | Android 13+ 최초 허용/거부·설정에서 알림 차단, 완료 후 탭/알림 여러 개/화면 재생성·앱 재실행, Downloads/SD/클라우드 제공자의 초기 위치 지원, Android 7 ZIP 앱 유무·삭제/이동된 ZIP 처리 확인이 남음. 제공자가 초기 위치를 무시하면 해당 폴더를 보장할 수 없음. 설치·사용자 자료 조작 미실행 |

연결 방식은 [Android DocumentsContract 초기 위치 계약](https://developer.android.com/reference/android/provider/DocumentsContract#EXTRA_INITIAL_URI)과 [알림 권한 문서](https://developer.android.com/develop/ui/views/notifications/notification-permission)를 기준으로 했다.

