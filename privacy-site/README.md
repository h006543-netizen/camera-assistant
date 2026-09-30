# ShutterNote 개인정보처리방침

공개 주소: https://h006543-netizen.github.io/shutternote-privacy/
문의 이메일: h006543@gmail.com

2026-10-01 한국어·영어·프랑스어·일본어 공개 페이지의 HTTP 200 응답 및 앱 방침 본문과의 일치를 확인했다. 앱은 언어에 맞는 공개 페이지를 외부 브라우저로 연다. 브라우저 미설치 시 안내 경로는 소스로 확인했으며 실제 기기 실행 확인은 남아 있다.

## 갱신 방법

1. `content.json`에서 네 언어 원문을 수정한다.
2. `python privacy-site/generate.py`로 웹페이지와 앱 내부 방침을 함께 생성한다.
3. `dist/` 내용을 GitHub의 `shutternote-privacy` 저장소 루트에 업로드한다.
4. GitHub Pages는 main 브랜치의 / (root)에서 배포한다. `.github` 자동화 폴더는 필요하지 않다.
5. 네 언어 공개 페이지와 문의 이메일을 확인한다.

`policy-config.json`의 `published`는 실제 공개 게시 여부다. 현재 true이며 공개 URL은 앱의 `res/values/privacy_config.xml`에도 생성된다. 앱 내부 방침은 동일한 원문으로 유지한다.

## 검증 범위

공개 URL·문의처 확정과 네 언어 본문 확인 완료. 앱의 언어별 외부 브라우저 실행 및 최초 ARCore 안내는 실제 기기에서 별도로 검증한다. 방침 공개를 앱 전체 출시 완료로 간주하지 않는다.
