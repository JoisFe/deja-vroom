# 구현 계획: [기능 이름]

**기능 디렉터리**: `specs/[###-feature-name]/` | **작성일**: [DATE] | **스펙**: [spec.md 링크]
**입력**: `specs/[###-feature-name]/spec.md`

<!--
  작성 규칙
  - 한국어. 코드·API 이름만 영어.
  - 이 계획은 공모전 접수양식 5번 "개발 계획(SDK/API 사용 계획)"의 재료가 된다.
  - 확인되지 않은 SDK 동작은 "확인 필요"로 표시하고 Pleos 공식 문서를 근거로 적는다.
-->

## 요약

[스펙의 핵심 요구사항 + 기술 접근을 2~3문장으로]

## 기술 맥락

| 항목 | 값 |
|---|---|
| 언어 | Kotlin (JVM target 17) |
| UI | Jetpack Compose + `PleosTheme` |
| 아키텍처 | ViewModel + `UiState`(StateFlow) → Repository → Pleos SDK (`.claude/skills/android-app-architecture`) |
| DI | Hilt |
| 테스트 | JUnit4, kotlinx-coroutines-test, Turbine, Fake Repository |
| 실행 환경 | Pleos Connect Emulator 2.1.0 (API 34), minSdk 28 |
| 서버 | [필요 없음 / 필요하면 이유와 범위. Java 21 Spring Boot] |
| 저장소 | [없음 / DataStore — 필요한 경우만] |
| 성능 제약 | 주행 중 CPU 5% 미만(비내비 앱), RAM 400MB 이하 (Pleos 앱 정책) |

## 헌법 점검

<!-- .specify/memory/constitution.md 의 원칙별로 통과/위반을 적는다. 위반은 아래 "복잡도 기록"에 이유를 남긴다. -->

| 원칙 | 결과 | 메모 |
|---|---|---|
| I. 공모전 범위 우선 | [통과/위반] | |
| II. 차량 특화와 주행 안전 | [ ] | |
| III. 표준 아키텍처 | [ ] | |
| IV. 검증된 완료 | [ ] | |
| V. 보안과 블라인드 심사 | [ ] | |

## SDK·API 사용 계획

| 기능 (FR) | SDK / API | 메서드·리스너 | 권한 | 제공 상태 |
|---|---|---|---|---|
| [FR-001] | [Vehicle.EvBattery] | [`getEvBatteryStateOfCharge`, `registerEvBatteryStateOfCharge`] | [`pleos.car.permission.CAR_ENERGY`] | [사용 가능 / 준비 중 / 확인 필요] |

- 지원 여부 확인(capability) 실패 시 처리: [ ]
- 차속·주행 상태가 필요하면: AAOS `CarPropertyManager`(`PERF_VEHICLE_SPEED`), `CarUxRestrictionsManager`

## 화면과 상태

| 화면 | `UiState` 주요 필드 | 주행 중 표시 |
|---|---|---|
| [ ] | [ ] | [ ] |

## 시연 시나리오 (2차 PT)

| 순서 | 조작 (앱 / Virtual Console) | 보여줄 결과 | 관련 SC |
|---|---|---|---|
| 1 | [ ] | [ ] | SC-001 |

## 프로젝트 구조

### 문서 (이 기능)

```text
specs/[###-feature]/
├── spec.md
├── plan.md          # 이 파일
├── research.md      # 필요 시 (SDK 조사 결과)
├── data-model.md    # 필요 시
└── tasks.md         # /speckit-tasks 결과
```

### 소스 코드

<!-- 실제로 만들 경로만 남긴다. -->

```text
app/src/main/kotlin/<base>/
├── feature/[기능]/         # [Feature]Route, [Feature]Screen, [Feature]ViewModel, [Feature]UiState
├── data/[영역]/            # [Xxx]Repository, Pleos[Xxx]Repository, Fake[Xxx]Repository
└── core/                   # designsystem, di, car (필요한 것만)

app/src/test/kotlin/<base>/
└── feature/[기능]/         # ViewModel 테스트
```

**구조 결정**: [실제 경로와 이유]

## 복잡도 기록

<!-- 헌법 점검에서 위반이 있을 때만 쓴다. -->

| 위반 | 필요한 이유 | 더 단순한 대안을 버린 이유 |
|---|---|---|
