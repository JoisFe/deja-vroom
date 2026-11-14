# 작업 목록: FirstLook 실연동 조사와 검증

**Spec**: [spec.md](spec.md)<br>
**Plan**: [plan.md](plan.md)<br>
**기준일**: 2026-11-11

## Phase 1 - 공개 근거와 로컬 상태

- [x] T001 공식 Pleos NaviHelper/TTS/Playground access 상태를 비교하고 날짜와 불일치를 `research.md`에 기록한다.
- [x] T002 공개 Pleos Maven 저장소에서 현재 SDK 버전 artifact와 전이 AAR resolve를 임시 Gradle probe로 확인한다.
- [x] T003 전체 경로 geometry 제공 여부와 도로 사진 API 공개 증거를 조사한다. 못 찾은 것은 부재로 단정하지 않는다.
- [x] T004 HMG에 확인할 데이터·권리 질문과 답변 기록 양식을 만들고 발송하지 않는다 (`hmg-data-inquiry-draft.md`).
- [x] T005 실제 요청이 가능한 조건과 중단 게이트를 `plan.md`에 분리한다.

## Phase 2 - 구현 전 안전 경계

- [ ] T006 Pleos My Project에서 대상 프로젝트의 NaviHelper/TTS/FusedLocation status와 테스트 access를 확인하고 비밀 값 없이 상태만 남긴다.
- [x] T007 기존 route source ID/geometry, callback generation, photo matching 안전 검사를 유지한다 (`RouteGeometryMatcher.kt`, `RouteAnalyzer.kt`).
- [ ] T008 좌표·POI/provider 출처가 있는 실제 목적지 선택을 제공하거나 공식 POI source를 승인받는다 (`app/src/main/**`).
- [ ] T009 Pleos route geometry/polyline 및 stable route ID contract를 공식 문서/API sample/artifact runtime에서 확인한다 (`docs/contest/**`, `specs/003-firstlook-live-integration/research.md`).
- [ ] T010 HMG road photo/route data의 coverage와 차량·공모전 표시/배포권을 확인하고 답변을 기록한다 (`specs/003-firstlook-live-integration/hmg-data-inquiry-draft.md`).

## Phase 3 - Provider와 Pleos 구현 (외부 게이트 이후)

- [ ] T011 승인된 route source를 `RouteRepository` adapter로 구현하고 route lifecycle/error/reroute generation tests를 추가한다 (`app/src/main/**`, `app/src/test/**`).
- [ ] T012 같은 route geometry와 후보 data를 연결할 수 있을 때만 candidate source와 analyzer integration을 구현한다 (`app/src/main/**`).
- [ ] T013 승인된 roadview provider와 renderer를 구현해 capture date, attribution, missing/stale/error를 표시한다 (`app/src/main/**`).
- [ ] T014 Pleos TTS/location/restriction adapter를 project access, permission, API support gate별로 추가한다 (`app/src/main/**`).
- [ ] T015 실제 destination→same route→briefing→completion→Pleos navigation flow 및 fallback을 Compose/AVD에서 확인한다.

## Phase 4 - 검증 및 출품 문서

- [x] T016 `:app:assembleDebug :app:testDebugUnitTest`와 `git diff --check`를 실행한다. 현재 Android SDK 경로를 환경변수로 지정하고 `--rerun-tasks`로 재빌드/단위 테스트를 강제 실행했다. → PASS, exit 0.
- [x] T017 일반 Android AVD 및 Pleos Connect runtime 평가를 나누어 기록한다. 일반 AVD 전 흐름 PASS 보고서를 확인했고, AVD 목록에는 `FirstLook_AAOS_API35`만 있어 Pleos Connect runtime은 BLOCKED/NOT RUN으로 기록했다.
- [x] T018 README feature/API matrix를 실제 integration/runtime status에 맞춰 동기화한다. 공개 Maven artifact resolve와 앱 미연동/프로젝트 접근 미확인 상태를 분리해 표기했다.

## 현재 진행 판정

- Phase 1은 완료. 공개 Maven artifact resolve는 PASS했으나 app dependency/integration은 하지 않았다.
- Phase 2 중 existing safe matcher 유지 확인만 완료. SDK project access, 목적지 좌표/POI source, route geometry, 사진 권리 등은 외부 근거 대기다.
- 앱 코드에서 현재 화면에 연결할 수 있는 유효한 route/photo 데이터가 없으므로 Phase 3의 안전한 범위는 아직 시작할 수 없다. Demo fallback은 유지한다.
- 이번 실행 검증은 `ANDROID_HOME=/Users/gwanii/Library/Android/sdk ANDROID_SDK_ROOT=/Users/gwanii/Library/Android/sdk ./.claude/skills/verify/scripts/gradle-quiet.sh --rerun-tasks :app:assembleDebug :app:testDebugUnitTest` → PASS, exit 0; `git diff --check` → PASS, exit 0; Markdown lint → PASS로 기록했다.
- 최종 실행 계정/권한 게이트 확인을 위해 기존 browser session inventory를 조회했으나 browser host unavailable이어서 My Project를 확인할 수 없었다. SDK 권한/자격 증명을 조회·변경하지 않았다.
