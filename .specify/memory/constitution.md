# Déjà Vroom 헌법

현대자동차그룹 차량용 앱 공모전 출품작(Pleos Connect 차량 앱)의 모든 스펙·계획·작업이 따라야 하는 원칙이다. 세부 규칙의 원문은 `.claude/rules/`와 `.claude/skills/android-app-architecture/`에 있고, 이 문서는 계획 단계에서 점검할 기준만 요약한다.

## 핵심 원칙

### I. 공모전 범위 우선
- 2026-12-03 2차 PT의 **에뮬레이터 시연**과 **release APK 제출**에 필요한 것만 만든다. 시연에 보이지 않는 기능, 범용 인프라, "나중에 쓸" 추상화는 만들지 않는다.
- 모든 기능은 심사 기준(차량특화성, 창의성, 기술 활용성, 사업성) 중 하나 이상에 기여해야 한다. 스펙에 어떤 기준에 기여하는지 적는다.

### II. 차량 특화와 주행 안전 (협상 불가)
- 주행 중/정차 중 동작을 스펙에서 따로 정의한다. 주행 중에는 `CarUxRestrictions`에 따라 키보드·영상·설정 화면을 제한한다.
- 차량 기능은 Pleos SDK와 AAOS 공식 API로만 쓴다. 모든 SDK 호출 전에 지원 여부(capability)를 확인하고, 지원하지 않으면 UI를 비활성화한다.
- 차량 제어(set) API는 아직 "준비 중"일 수 있으므로 핵심 시연 흐름은 읽기 위주로 설계하고, 제어는 선택 기능으로 둔다.

### III. 표준 아키텍처
- Kotlin + Jetpack Compose, ViewModel + 불변 `UiState`(StateFlow), Repository. 단일 모듈이고, 객체는 Hilt 없이 직접 만들어 생성자로 넘긴다.
- 도메인 로직은 순수 Kotlin `domain` 계층에 둔다. Pleos SDK는 `data/**/Pleos*` Repository 안에서만 호출하고 콜백은 `suspend`/`Flow`로 감싼다.
- 모든 Repository 인터페이스에는 `Fake*` 구현이 있어 차량 신호 없이도 시연하고 테스트할 수 있어야 한다.

### IV. 검증된 완료 (협상 불가)
- "완료"는 `/verify`(빌드·테스트) 통과 **그리고** 평가 에이전트(`app-evaluator`)가 에뮬레이터에서 스펙의 성공 기준을 확인한 상태다. 코드를 읽고 완료라고 선언하지 않는다.
- ViewModel과 Repository 매핑에는 단위 테스트가 있다.

### V. 보안과 블라인드 심사
- APK와 저장소에 비밀 값(Pleos Client Secret, 서명 키, 토큰)을 넣지 않는다.
- 개인정보(VIN, 위치, 음성)는 기능에 꼭 필요한 만큼만 쓰고 로그에 남기지 않는다.
- 코드, 문서, 앱 화면, 시연 영상에 팀원 실명·연락처를 넣지 않는다(블라인드 심사).

## 산출물 규칙

- 스펙·계획·작업 문서(`specs/**`)는 **한국어**로 쓴다. 코드, 식별자, API 이름, 명령어만 영어로 둔다.
- 스펙은 "무엇을, 왜"만 다루고 구현 방법은 계획(`plan.md`)에서 다룬다.
- 구현 중 스펙과 다른 결정을 하면 `spec.md`/`plan.md`를 같은 변경에서 함께 고친다.

## 개발 흐름

| 단계 | 담당 | 산출물 |
|---|---|---|
| 스펙 | `/speckit-specify` → `/speckit-clarify` | `specs/NNN-기능/spec.md` |
| 계획 | `/speckit-plan` (+ `/speckit-checklist`) | `plan.md` 등 |
| 작업 분해 | `/speckit-tasks` → `/speckit-analyze` | `tasks.md` |
| 구현·검증 | `/new-feature` (하네스: `pleos-app-developer`, `/verify`, `app-evaluator`) | 코드, 검증 기록 |
| 마무리 점검 | `/speckit-converge` | 남은 작업이 있으면 `tasks.md`에 추가 |

`/speckit-implement`는 쓰지 않는다. 구현은 하네스 흐름으로 한다.

## 거버넌스

- 이 헌법은 스펙·계획 단계의 점검 기준이다. 상세 규칙과 충돌하면 `.claude/rules/`가 우선하고, 이 문서를 고친다.
- 개정은 팀 합의 후 버전과 날짜를 올린다.

**Version**: 1.1.0 | **Ratified**: 2026-10-24 | **Last Amended**: 2026-11-15
