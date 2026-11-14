---

description: "기능 구현 작업 목록 템플릿 (Déjà Vroom)"
---

# 작업 목록: [기능 이름]

**입력**: `specs/[###-feature-name]/`의 `spec.md`(필수), `plan.md`(필수), `research.md`·`data-model.md`(있으면)

**넘겨주기**: 이 파일이 완성되면 Spec Kit 단계는 끝난다. 구현은 `/speckit-implement`가 아니라 **`/new-feature`** 가 이 파일을 읽어 하네스(`pleos-app-developer` → `/verify` → `app-evaluator`)로 진행한다.

## 형식: `- [ ] T### [P?] [US?] 설명 (파일 경로)`

- **[P]**: 병렬 가능 (다른 파일, 의존성 없음)
- **[US1]** 등: 해당 사용자 스토리
- 설명에 정확한 파일 경로를 넣는다 (`app/src/main/kotlin/...`)
- 각 작업 끝에 완료 확인 방법을 적는다: `→ 확인: /verify :app:testPleosDebugUnitTest` 또는 `→ 확인: app-evaluator SC-001`

<!--
  아래 작업은 예시다. /speckit-tasks 는 spec.md의 사용자 스토리(P1, P2...)와 plan.md를 바탕으로 실제 작업으로 바꿔야 한다.
  - 스토리별로 묶어 각 스토리를 혼자 구현·시연할 수 있게 한다.
  - 한국어로 쓴다. 코드·경로만 영어.
  - 이미 있는 공통 기반(테마, Hilt 설정 등)은 다시 만들지 않는다.
-->

## 1단계: 준비 (앱 뼈대가 없을 때만)

- [ ] T001 `app/` 모듈 생성: Kotlin + Compose + Hilt, `pleos`/`demo` 플레이버 (`app/build.gradle.kts`, `gradle/libs.versions.toml`) → 확인: `/verify :app:assemblePleosDebug`
- [ ] T002 [P] `PleosTheme` 기본값 (`core/designsystem/Theme.kt`)
- [ ] T003 [P] 디스패처·SDK Hilt 모듈 (`core/di/`)

---

## 2단계: 공통 기반 (모든 스토리가 의존)

**⚠️ 이 단계가 끝나야 스토리 작업을 시작한다**

- [ ] T004 Repository 인터페이스 + `Fake*` (`data/[영역]/`) → 확인: 단위 테스트
- [ ] T005 `Pleos*Repository`: capability 확인 + 콜백 → `Flow` (`data/[영역]/`) → 확인: 에뮬레이터 로그
- [ ] T006 [P] 매니페스트 권한 (`app/src/main/AndroidManifest.xml`)
- [ ] T007 [P] 주행 제한 상태 `Flow` (`core/car/`) — 스펙에 주행 중 동작이 있으면

---

## 3단계: 사용자 스토리 1 - [제목] (P1) 🎯 시연 MVP

**목표**: [이 스토리가 주는 가치]

**단독 시연**: [Virtual Console 조작 → 기대 화면]

- [ ] T008 [P] [US1] ViewModel 테스트 (`app/src/test/.../feature/[기능]/`) → 확인: `/verify :app:testPleosDebugUnitTest`
- [ ] T009 [US1] `[Feature]UiState` + `[Feature]ViewModel` (`feature/[기능]/`)
- [ ] T010 [US1] `[Feature]Screen`(상태 없음) + `[Feature]Route` + 미리보기 (`feature/[기능]/`)
- [ ] T011 [US1] 주행 중 축소 UI (`feature/[기능]/`)
- [ ] T012 [US1] 평가: SC-001, SC-002 → 확인: `app-evaluator`

**체크포인트**: 스토리 1만으로 시연 가능

---

## 4단계: 사용자 스토리 2 - [제목] (P2)

- [ ] T013 [P] [US2] [ ]
- [ ] T014 [US2] 평가 → 확인: `app-evaluator`

---

## 마지막 단계: 마무리

- [ ] T0XX `/speckit-converge`로 스펙 대비 누락 확인
- [ ] T0XX release APK 빌드·에뮬레이터 실행 확인 (`apk/팀명_앱이름_release.apk`)
- [ ] T0XX 시연 영상 녹화 (`adb shell screenrecord`)

## 의존성과 순서

- 1단계 → 2단계 → 스토리(P1 → P2 → ...) → 마무리
- 같은 단계의 `[P]` 작업은 병렬 가능
- 스토리 1 완료 시점이 최소 시연 가능 시점이다. 11.15 제출용 영상은 여기서 찍는다.
