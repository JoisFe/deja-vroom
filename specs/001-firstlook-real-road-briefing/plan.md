# 구현 계획: 검증 가능한 초행길 브리핑

**기능 디렉터리**: `specs/001-firstlook-real-road-briefing/` | **작성일**: 2026-11-11 | **스펙**: [spec.md](spec.md)

## 요약

기존 Compose Demo 흐름을 유지하면서 경로/구간/사진 출처와 검증 상태를 데이터 계약으로 명시하고, 검증 가능한 입력에만 안내를 허용한다. Pleos 공개 문서에서 전체 경로 형상은 확인되지 않았으며, 사진 제공자는 차량 내 표시 권리와 계정 키가 확인되지 않아 실제 연결을 활성화하지 않는다. 따라서 이번에 실행 가능한 결과는 기존 Demo UX를 보존·강화하고, 실제 provider/route SDK 경계 및 실패 상태를 분명히 하는 것이다.

## 기술 맥락

| 항목 | 값 |
|---|---|
| 언어 | Kotlin, JVM target 17 |
| UI | Jetpack Compose + 기존 Pleos 테마 |
| 아키텍처 | ViewModel + 불변 UiState(StateFlow) → Repository; 기존 단일 모듈 유지 |
| DI | 현재 구현과 일관되게 수동 생성. 범위 밖의 Hilt 전환 없음 |
| 테스트 | JUnit4, 기존 Fake/Mock 및 kotlinx-coroutines-test 구성 |
| 실행 환경 | Android 앱, minSdk 28; Pleos 런타임 접근은 별도 상태 |
| 서버/저장 | 이번 범위에서 추가 없음 |
| 성능/안전 | 사전 브리핑 화면은 정차 중; 주행 제한 API가 확인/통합되지 않으면 실제 주행 중 기능 완료를 주장하지 않음 |

## 헌법 점검

| 원칙 | 결과 | 메모 |
|---|---|---|
| I. 공모전 범위 우선 | 통과 | 시연 가능한 주요 흐름과 신뢰 경계를 우선한다. |
| II. 차량 특화와 주행 안전 | 부분 통과 | Mock 흐름은 구현, 실제 CarUxRestrictions/Pleos 전환은 환경/API 검증 대기. |
| III. 표준 아키텍처 | 통과 | Compose, ViewModel, repository 경계와 단일 모듈 유지; 기존 Hilt 미사용을 불필요한 전환으로 확대하지 않는다. |
| IV. 검증된 완료 | 진행 | `/verify` 및 evaluator 실행 전 완료 주장 금지. |
| V. 보안과 블라인드 심사 | 통과 | API 키/차량 식별/개인 위치를 코드나 로그에 추가하지 않는다. |

## 조사 결정

세부 출처와 확인 수준은 [research.md](research.md)에 기록한다.

- Pleos NaviHelper 문서 요약은 목적지 요청, TBT 및 경로 상태를 설명하지만 전체 경로 polyline/지도 사진 API를 문서화하지 않는다.
- Pleos에서 도로 사진/로드뷰 제공을 확인하지 못했다. 공공 포털의 확인된 360도 자료는 제한된 지역·시설 범위로 수원 영통→강남 경로를 대체하지 못한다.
- Kakao Android RoadView 문서는 별도 협의를 요구한다. Kakao JS Roadview 및 Naver Panorama는 기술적으로 WebView 후보이나 차량 내 콘텐츠 표시권·공모전 이용권을 확인하지 못했다. Google도 비용·정책을 통과한 기본 선택지가 아니다.
- 키, 이용권, Pleos 대상 런타임이 없으므로 실제 provider를 호출하지 않는다. Mock와 provider-neutral 계약을 구현하며 사진 매칭 검증 전에는 방향/차로 overlay를 거부한다.

## 화면·상태 계획

| 화면 | 상태 | 주행 중 |
|---|---|---|
| 목적지 | Demo/검색 경로 출처 및 오류 | 제한 필요 |
| 요약 | 구간 수, 경로·콘텐츠 출처/신뢰 | 영상 브리핑 비활성화 |
| 브리핑 | 현재 시점, 날짜, attribution, route match, 차로 검증 | 영상 및 조작 제한 |
| 완료 | 시간/거리/구간 수와 Mock/실제 시작 결과 | 상세 조작 제한 |
| Mock 내비 | Mock 상태 배지 | 실내비로 오인하지 않도록 명시 |

## 구현 위치

```text
app/src/main/kotlin/ai/firstlook/app/
  model/BriefingModels.kt
  data/DemoRouteRepository.kt
  data/RoadViewProvider.kt
  feature/FirstLookViewModel.kt
  MainActivity.kt
app/src/test/kotlin/ai/firstlook/app/
  data/...
  feature/...
specs/001-firstlook-real-road-briefing/
  spec.md plan.md research.md data-model.md quickstart.md tasks.md
  contracts/road-view-provider.md
```

실제 Pleos 클래스 참조를 추가하지 않으며, 추후 실제 SDK 구현은 `data/**/Pleos*` repository 내부에서만 허용한다.

## 시연 흐름

1. 강남역 Demo 목적지를 확인한다.
2. 세 curated 구간의 순서·이미지·Demo 고지를 본다.
3. 구간별 사진 출처 및 검증 여부를 본다. 검증되지 않은 lane/direction을 차단한다.
4. 브리핑 완료에서 Mock 주행 화면으로 이동한다.
5. 다시 보기로 구간 탐색을 반복한다.

## 복잡도 기록

| 항목 | 판단 | 이유 |
|---|---|---|
| Hilt 미도입 | 기존 구조 유지 | 이 기능에만 DI 프레임워크를 추가하는 것보다 작은 생성자 주입이 단순하며, 앱 전반 마이그레이션은 별도 작업이다. |
| 실 provider 미연결 | 명시적 제약 | 차량 표시 권리·키·선택 경로 geometry가 확인되지 않아 구현·실행 시도는 안전하지 않다. |
