# 데이터 모델

## RouteSnapshot

| 필드 | 타입/허용값 | 의미 |
|---|---|---|
| routeId | 문자열 | 동일한 경로 스냅샷의 식별자 |
| source | `PLEOS_NAVIGATION`, `EXTERNAL_ROUTE`, `CURATED_DEMO` | 경로가 나온 시스템 |
| geometry | 선택적 좌표 목록 | 전체 경로 형상. 근거 없는 경우 `null` |
| geometryStatus | `VERIFIED`, `UNAVAILABLE`, `UNKNOWN` | geometry 제공/검증 상태 |
| distance/duration | 수치 | 출처가 표시된 경로 요약 |

## BriefingSegment

| 필드 | 의미 |
|---|---|
| id/type/name/description | 구간 식별, 종류와 화면 설명 |
| routeOffsetMeters | 경로 시작점 기준 거리(제공되지 않으면 null) |
| coordinate/approachCoordinate/heading | 구간 및 접근점 위치/방향. 실데이터 증거 없으면 null |
| selectionSource/score/evidence | 분석 또는 curated 목록의 출처와 점수 근거 |
| laneGuidance | 문자열과 별도 verification/근거. 미검증이면 구체 문구 금지 |
| roadViews | 순서가 있는 0~3개 허용 콘텐츠 |

## RoadView

| 필드 | 의미 |
|---|---|
| content | provider-hosted viewer, 제한적 static image 또는 demo artwork 식별 |
| providerName/attribution | 화면 고지할 원 제공자/저작권 표시 |
| capturedAt | 공급자가 준 촬영일; 모르면 null |
| heading/coordinate | 공급자가 준 시점 방향/좌표; 모르면 null |
| routeMatch | `VERIFIED`, `UNVERIFIED`, `MISMATCH`, `STALE`, `NO_COVERAGE`, `NOT_APPLICABLE` |
| contentOrigin | `LIVE_PROVIDER`, `CURATED`, `DEMO_ILLUSTRATION` |

## 안전 규칙

- `CURATED_DEMO` 데이터는 실제 경로 분석 결과가 아니다.
- `routeMatch != VERIFIED`이거나 제공자가 overlay를 허용하지 않으면 차로 화살표 overlay와 구체 차로/거리/출구 문구를 보이지 않는다.
- 실제 provider 콘텐츠는 provider가 허용한 renderer, 보관/캐시/고지 정책을 준수해야 한다.
- `capturedAt == null`은 알려지지 않은 촬영일로 표현한다.
- 경로 geometry가 없으면 좌표, 거리 offset, heading을 추정해 채우지 않는다.
