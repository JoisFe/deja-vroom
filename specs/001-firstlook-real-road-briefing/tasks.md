# 작업 목록: 검증 가능한 초행길 브리핑

**입력**: `specs/001-firstlook-real-road-briefing/`의 `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`

## 1단계: 기준선 및 계약

- [x] T001 기존 Compose 앱·dirty worktree 및 assemble/unit-test 기준선을 기록한다. 기존 변경은 보존한다. → 확인: 기준선 `:app:assembleDebug :app:testDebugUnitTest` PASS
- [x] T002 Pleos/HMG·공공·Kakao·Naver·Google 사진/경로 기능과 차량 사용권 미확인 상태를 기록한다. → 확인: `research.md`
- [x] T003 [P] route/photo attribution·match·content origin 모델을 구현한다 (`app/src/main/kotlin/ai/firstlook/app/model/BriefingModels.kt`). → 확인: `:app:testDebugUnitTest`
- [x] T004 [P] provider 중립 결과 상태와 demo Fake를 구현한다 (`app/src/main/kotlin/ai/firstlook/app/data/RoadViewProvider.kt`). → 확인: provider contract unit test

## 2단계: 스토리 1 - 안전한 Demo 경로 브리핑 (P1)

**목표**: API 키/차량 연결 없이 강남역 Demo 전체 흐름을 시연하되, Mock 정보가 실경로로 오인되지 않는다.

- [x] T005 Demo route와 segment를 `CURATED_DEMO` 출처 및 차로 미검증 상태로 표시한다 (`app/src/main/kotlin/ai/firstlook/app/data/DemoRouteRepository.kt`). → 확인: repository unit test
- [x] T006 [P] 구간 이동 상태와 provider 미승인/출처 상태를 UiState 및 provider contract 단위 테스트로 확인한다 (`app/src/test/kotlin/ai/firstlook/app/feature/FirstLookUiStateTest.kt`, `app/src/test/kotlin/ai/firstlook/app/data/RoadViewProviderTest.kt`). → 확인: `:app:testDebugUnitTest`
- [x] T007 사진 출처·날짜·route match 및 차로 검증 상태를 브리핑 UI에 표시하고 미검증 overlay/문구를 제거한다 (`app/src/main/kotlin/ai/firstlook/app/MainActivity.kt`). → 확인: evaluator SC-002/SC-003
- [x] T008 Demo 화면의 구간 이동→완료 reducer와 다시 보기 선택 상태를 테스트하고, 전체 흐름은 AVD에서 관찰한다 (`app/src/main/kotlin/ai/firstlook/app/feature/FirstLookViewModel.kt`, `app/src/test/kotlin/ai/firstlook/app/feature/FirstLookUiStateTest.kt`). → 확인: unit test + evaluator report
- [x] T009 Demo 및 provider 미승인/경로 geometry 없음/미검증 안내 분기 테스트를 추가/갱신한다 (`app/src/test/kotlin/ai/firstlook/app/data/`). → 확인: `:app:testDebugUnitTest`
- [x] T010 스토리 1 일반 Android AVD 평가: SC-001, SC-002, SC-003. Pleos evaluator는 별도 BLOCKED. → 확인: `.claude/work/eval/001-firstlook-real-road-briefing/report.md`

## 3단계: 스토리 2 - 경로와 사진 근거 구분 (P2)

**목표**: verified route+view 입력에서만 실제 구간/방향을 승인하고, 데이터 미제공은 차단한다.

- [x] T011 analyzer 입력/후보/점수 모델을 추가해 외부/실제 geometry 입력과 curated demo를 분리한다 (`app/src/main/kotlin/ai/firstlook/app/model/BriefingModels.kt`). → 확인: analyzer unit test
- [x] T012 [P] 후보 점수/중복 제거/상위 3개 및 no-geometry 게이트를 순수 함수로 구현한다 (`app/src/main/kotlin/ai/firstlook/app/data/RouteAnalyzer.kt`). → 확인: analyzer unit test
- [x] T013 `EXTERNAL_ROUTE`와 `PLEOS_NAVIGATION` 출처를 같다고 강제하지 않고 미검증 geometry를 거부하는 회귀 테스트를 추가한다 (`app/src/test/kotlin/ai/firstlook/app/data/RouteAnalyzerTest.kt`). → 확인: `:app:testDebugUnitTest`
- [x] T014 Pleos SDK/실제 provider 미연결 상태를 README API 현황 표에 명시한다 (`README.md`). → 확인: 문서와 코드 상태 대조
- [x] T015 스토리 2 일반 Android AVD/단위 평가: 검증 안 된 차로/방향/사진은 표시되지 않음. Pleos evaluator는 BLOCKED. → 확인: evaluator report + unit tests

## 4단계: 마무리

- [x] T016 README 실행법·Demo 모드·키 설정/미연동 상태·제약 표를 보완한다 (`README.md`). → 확인: 문서 점검
- [x] T017 `:app:assembleDebug :app:testDebugUnitTest` 실행 후 `git diff --check` → 확인: exit code 0
- [x] T018 일반 Android AVD에 설치·실행하여 사용자 흐름을 확인하고 Pleos 환경 차이는 별도 기록한다. → 확인: 실행 결과/스크린샷
- [x] T019 `tasks.md`, spec 및 구현 차이를 대조하고 미완료 외부 의존성을 기록한다. → 확인: 아래 `제약 / 자동 fallback`, `README.md`, emulator evaluation report

## 제약 / 자동 fallback

- 차량 표시권·API 키·Pleos SDK 프로젝트 승인이 없으므로 이 기능만을 위해 실제 provider 호출을 시도하지 않는다.
- 전체 경로 geometry API가 확인되지 않아 추정 좌표나 실경로 차로 안내를 만들지 않는다.
- `/verify` 통과 여부와 앱 evaluator 결과를 분리한다. Pleos emulator/실차가 없으면 실제 Pleos 동작은 `NOT RUN`이다.
- 기존 테마/UI 관련 변경 및 훅 설정을 되돌리거나 변경하지 않는다. commit/push 하지 않는다.
- 실 Pleos/Naver/Kakao provider 인증·권리 및 실제 route geometry/source가 없는 상태이므로, 실제 도로 사진 표시와 Pleos 내비 전환은 후속 연동 과제다. 현재 완료된 범위는 Mock, provenance, safe gating, route-candidate scoring contract다.

## Phase 5 - 교차로 레코드 authoring 및 Roadview prototype (Claude artifact 기반)

- [x] T020 수동 검수 way를 교차로 레코드로 변환하는 표준 라이브러리 전용 builder와 입력 검증을 구현한다 (`scripts/roadview/`). → 확인: Python `unittest` 7개 통과
- [x] T021 좌표/방위/누적거리 interpolation, approach/keep/trap 생성, OSRM detour cost 유효성, OSM way provenance 및 ODbL attribution을 테스트한다 (`scripts/roadview/test_make_junction.py`). → 확인: Python `unittest` 7개 통과
- [x] T022 앱 도메인에 교차로 레코드/데이터 출처/수동 검수 상태와 보수적인 route match·RoadView 표시 정책을 추가한다 (`app/src/main/**`). → 확인: `:app:testDebugUnitTest`
- [x] T023 허용된 route와만 레코드를 매칭하고 traffic·gear·UX 신호 누락/오류를 fail-closed 처리하는 eligibility 결과를 구현한다 (`app/src/main/**`). → 확인: route matcher/policy unit tests; 실제 신호 adapter는 미연결
- [x] T024 debug bridge가 OSM record를 전달하고 Kakao Roadview 위에 caution/keep/trap 표식 7개를 표시한다. → 확인: matcher 통과, `junction-overlay-ready:7`, emulator screenshot. debug fixture only; Kakao 기본 이동 화살표는 유지하고 live Pleos gate는 미연동
- [x] T025 `docs/firstlook/` 및 README에 authoring·OSM attribution·실행 결과와 권한/runtime 검증 경계를 기록한다. → 확인: 문서 검토 및 `:app:testDebugUnitTest :app:assembleDebug`; 실차/제출 권리는 NOT RUN
