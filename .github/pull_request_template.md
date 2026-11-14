## 무엇

<!-- 한두 줄. 어떤 스펙/이슈의 어떤 작업인지 -->

- 스펙: `specs/NNN-기능/` · 이슈: #

## 왜

## 검증

- [ ] `./gradlew :app:testDebugUnitTest` 통과
- [ ] 에뮬레이터 확인 (해당 시): 성공 기준 SC-___ PASS
- [ ] 스크린샷 / 화면 녹화 첨부 (UI 변경 시)

## 체크

- [ ] Pleos SDK는 `data/**/Pleos*` 안에서만 호출한다
- [ ] 새 Repository에 `Fake*` 구현이 있다
- [ ] 주행 중 화면 제한(`CarUxRestrictions`)을 지킨다
- [ ] 비밀 값, 실명·연락처가 코드·문서·화면에 없다
- [ ] 스펙과 다른 결정을 했다면 `spec.md`/`plan.md`도 고쳤다
