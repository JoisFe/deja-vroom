# 작업 목록: FirstLook 브리핑 근거·상태 고도화

**입력**: `specs/002-firstlook-mvp-hardening/`의 `spec.md`, `plan.md`

## 1단계: 안전한 경로 매칭 (US1)

- [x] T001 [P] 경로 투영/heading/offset 검증 순수 matcher를 추가한다 (`app/src/main/kotlin/ai/firstlook/app/data/RouteGeometryMatcher.kt`) → 확인: `RouteGeometryMatcherTest`
- [x] T002 [US1] 후보에 source route ID를 추가하고 analyzer 입력 검증과 결정적 순위를 강화한다 (`app/src/main/kotlin/ai/firstlook/app/model/BriefingModels.kt`, `app/src/main/kotlin/ai/firstlook/app/data/RouteAnalyzer.kt`) → 확인: `RouteAnalyzerTest`
- [x] T003 [US1] 실제 도로로 오인되지 않는 미설정 candidate source와 상태를 repository에 둔다 (`app/src/main/kotlin/ai/firstlook/app/data/DemoRouteRepository.kt`) → 확인: `DemoRouteRepositoryTest`

## 2단계: 사진 provider 상태 (US2)

- [x] T004 [US2] Provider 결과와 view index를 ViewModel 상태에 연결하고 선택 변경/재시도 로직을 추가한다 (`app/src/main/kotlin/ai/firstlook/app/feature/FirstLookViewModel.kt`) → 확인: `FirstLookUiStateTest`
- [x] T005 [US2] 브리핑 UI에 provider 상태·촬영일·출처·view paging을 노출하고 미검증 fallback을 유지한다 (`app/src/main/kotlin/ai/firstlook/app/MainActivity.kt`) → 확인: AAOS AVD에서 Demo 브리핑 화면 확인; 실제 provider renderer는 외부 게이트

## 3단계: 음성·주행 제한 (US3)

- [x] T006 [P] 음성 및 driving restriction Fake/상태 계약을 추가한다 (`app/src/main/kotlin/ai/firstlook/app/data/RoadViewProvider.kt`) → 확인: `BriefingSpeechControllerTest` 및 Mock 앱 흐름
- [x] T007 [US3] 구간 변경/토글/화면 이탈 시 음성을 취소하고 제한 상태의 화면 조작을 차단한다 (`app/src/main/kotlin/ai/firstlook/app/feature/FirstLookViewModel.kt`, `MainActivity.kt`) → 확인: reducer unit tests 및 AVD에서 제한 모드 화면 진입

## 마지막 단계

- [x] T008 README 기능 상태·외부 게이트를 동기화한다 (`README.md`) → 확인: 코드/표 대조
- [x] T009 `/verify :app:assembleDebug :app:testDebugUnitTest` 및 `git diff --check` → 확인: exit 0
- [x] T010 일반 AAOS AVD에서 설치·목적지→브리핑→완료→Mock 주행 및 제한 화면을 확인하고 Pleos runtime 검증은 분리해 기록한다 (`specs/002-firstlook-mvp-hardening/`) → 확인: 2026-11-11 실행; Pleos Connect 전용 evaluator/runtime 미실시
