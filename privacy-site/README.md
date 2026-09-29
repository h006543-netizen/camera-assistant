# ShutterNote 개인정보처리방침

네 언어의 정적 페이지와 Android 내부 초안은 같은 `content.json`에서 생성한다.

현재 상태: 운영자 이름과 문의 이메일 대기. 사이트는 등록되었으나 공개 배포되지 않았다. 공개 주소가 실제로 동작한다고 주장하거나 Play Console에 제출하지 않는다.

## 파일

- `content.json`: 한국어·영어·프랑스어·일본어 정책 원문
- `policy-config.json`: 운영자·문의처·문서 기준일·공개 주소·게시 완료 여부
- `generate.py`: `dist/`의 정적 웹페이지와 앱의 `assets/privacy/` 및 `res/values/privacy_config.xml` 생성
- `dist/index.html`: 한국어. 나머지 언어는 `en/`, `fr/`, `ja/`
- `.openai/hosting.json`: 이미 생성된 Sites 프로젝트 ID. 다시 생성하지 않는다.

## 최종 게시 순서

1. 사용자에게 받은 실제 운영자 이름과 문의 이메일을 `policy-config.json`에 입력한다. 임의 정보를 사용하지 않는다.
2. `python privacy-site/generate.py`를 저장소 루트에서 실행한다. 연락처가 완성되면 웹페이지의 초안 표시가 사라진다.
3. Sites의 기존 프로젝트를 사용해 소스를 저장하고 정적 `dist`를 게시한다. 새 프로젝트를 생성하지 않는다.
4. Play 제출용으로 로그인 없이 접근할 수 있는 공개 audience를 적용하고 성공한 배포의 URL을 확인한다.
5. 성공한 배포 URL을 `public_url`에 기록하고 `published`를 `true`로 변경한 다음 생성기를 다시 실행한다. 앱에서 공개 페이지로 연결되는 것은 이 단계부터다.
6. 앱을 빌드하고 네 언어 링크와 첫 ARCore 안내를 검증한다. Play Console에 동일한 공개 URL을 등록한다.

아직 `published=false`이므로 앱은 잘못된 주소를 열지 않고 내부 초안을 표시한다. 실제 게시 전에는 true로 바꾸지 않는다.

## 정책 내용 검토 근거

- [Google Play 사용자 데이터 정책](https://support.google.com/googleplay/android-developer/answer/10144311?hl=ko)
- [ARCore 개인정보 고지](https://developers.google.com/ar/develop/privacy-requirements)
- [ARCore 데이터 보안 양식 안내](https://developers.google.com/ar/develop/play-safety-label)

앱 자체의 사진 업로드와 Google ARCore의 데이터 처리를 구분했다. 앱의 자동 백업이 현재 활성화된 점을 반영했다. 캐시 사진은 24시간 경과 즉시 삭제되는 것이 아니라 다음 메인/노출계 진입에서 정리된다는 소스 동작을 명시했다. 정책 문서는 실제 기능·SDK·백업 설정 변경 시 함께 갱신한다.
