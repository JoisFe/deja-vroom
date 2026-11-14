# 구현 계획: 실경로·사진·Pleos 연동 게이트

**Branch**: `feature/firstlook-real-road-briefing`<br>
**작성일**: 2026-11-11<br>
**Spec**: [spec.md](spec.md)<br>
**상태**: 공개 문서·artifact 확인 완료, UI 연결에 필요한 외부 권리/경로 입력 게이트 대기

## 요약

현재 앱에는 실제 목적지 좌표/검색 provider, Pleos 선택 경로 geometry, 경로 후보 source, 승인된 도로 사진 renderer가 없다. 공개 Pleos Maven 저장소에서 SDK artifact가 해석되는 것과 프로젝트 이용 승인·runtime 성공은 다른 증거다. 그러므로 앱의 주행 시작 버튼을 현재 Demo route에 연결하거나, 문서로 검증되지 않은 route polyline/POI 기능을 추정해 구현하지 않는다. 먼저 provider-neutral 계약과 보수적 매칭을 유지하고, 구체 API/data 권한과 Pleos 프로젝트 입력이 확보되면 독립 adapter를 구현한다.

## 기술 맥락

| 항목 | 결정 |
|---|---|
| 언어/플랫폼 | Kotlin/JVM 17, 단일 Android Automotive 앱, Compose |
| 기존 의존성 | 현재 앱에서 Pleos SDK를 사용하지 않음. 실연동 코드는 공식 SDK 저장소와 project access 확인 후 추가 |
| 저장소 | 현재 persistence 없음. route/location을 영구 저장하지 않음 |
| 검증 | 저장소 Gradle wrapper 및 `verify` script, unit tests, 설치 가능한 AVD |
| 외부 API | HMG/Pleos 우선. 사진 provider는 계약 및 차량 배포권 확인 후 단일 provider부터 |
| 안전 제한 | SDK 호출은 repository adapter 안에서만; listener 해제; 요청/완료 분리; Mock 기본 흐름 보존 |
| scope | SDK 문서·artifact·access·compile·runtime을 별도 증거로 기록 |

## 설계

1. `RouteSnapshot`의 source/route ID/geometry status를 route adapter가 소유한다. 탐색 이벤트 세대 번호를 두어 reroute 후 늦은 callback을 거부한다.
2. NaviHelper adapter는 목적지 모델의 유효 좌표/POI metadata를 받은 경우만 route 요청할 수 있다. 현재 문자열 검색만 있는 UI를 위해 demo 좌표를 실좌표로 가장하지 않는다.
3. 공개 문서의 trip summary/route point는 polyline으로 취급하지 않는다. 공식 API reference/artifact에 전체 선택 geometry가 확인되기 전 `RouteGeometryStatus.UNAVAILABLE`을 유지한다.
4. `RoadViewProvider`는 metadata 조회와 실제 콘텐츠 렌더링/overlay capability를 분리한다. 차량 화면 권리 확인 전 live adapter를 기본 주입하지 않는다.
5. Pleos TTS/FusedLocation/Vehicle은 각 SDK의 project status 및 support check를 통과한 경우만 별도 adapter로 연결한다. 접근 미확인 시 No-op/Fake를 기본으로 유지한다.
6. 주행 제한 실연동은 Pleos 정책에서 허용한 공식 API를 검토하고, generic Android AVD의 시뮬레이션 결과를 실제 주행 상태로 표기하지 않는다.

## 데이터 흐름

```text
DestinationSelection (provider-backed coordinates + provider identity)
  -> Pleos NaviHelper request
  -> async route events and route ID
  -> full route geometry only if officially exposed
  -> same-source candidates
  -> RouteGeometryMatcher
  -> approved RoadViewProvider metadata/content
  -> safe briefing UI
```

어느 단계에서든 필수 근거가 없으면 그 단계의 실데이터 표시를 비활성화하고 Demo 안내를 유지한다.

## 검증 시나리오

- Mock route, no credentials: build, unit suite, app flow on Android AVD.
- Maven probe: public Pleos AARs resolve from the vendor repository; this proves artifact transport only.
- Pleos access status: must be checked in the owner’s My Project console; cannot be inferred from public Maven resolution.
- NaviHelper route: test initialization, route started/completed/error/cancel, late event after reroute, and source ID/geometry status on Pleos Connect Emulator.
- Roadview: do not issue requests until written vehicle/display/contest rights and key restrictions are known; then test no coverage, stale, wrong direction/road level, attribution, and overlay.
- Pleos restrictions/TTS/location: test each separately in Pleos Connect Emulator and record unavailable signals as NOT RUN/BLOCKED.

## 외부 게이트

| 차단 항목 | 현재 증거 | 필요한 외부 조건 | 영향 |
|---|---|---|---|
| HMG 사진·경로 geometry | 공개 Pleos 가이드에서 앱용 road-view API를 확인하지 못함; 공개 검색은 부재를 증명하지 않음 | 프로젝트/파트너 데이터 담당자의 제공 범위, API, 이용권 답변 | 동일 경로 사진 브리핑을 구현할 수 없음 |
| 선택 경로 geometry | NaviHelper 문서는 경로 요청·탐색 정보 이벤트를 설명, 전체 polyline 반환 여부는 확인되지 않음 | 공식 API 계약 또는 Pleos 기술 확인 | 실경로 구간 매칭 비활성 |
| Kakao Android RoadView | 공식 guide가 별도 협의를 요구 | 차량 화면·공모전 영상·배포·overlay·attribution/캐시 범위 승인 | 실제 Roadview renderer 비활성 |
| TTS/FusedLocation access | 문서화/공개 AAR과 프로젝트별 console 상태가 상충하거나 다를 수 있음 | Playground project 상태 확인, 필요한 client credentials 및 permission approval | No-op/Fake 유지 |
| Pleos runtime | 일반 AAOS AVD만 확인됨 | Pleos Connect Emulator/Virtual Console access | Pleos evaluation NOT RUN |

## Constitution check

- Demo 경로/이미지/주행 상태를 실데이터처럼 표기하지 않는 원칙을 유지한다.
- 신규 SDK 호출/권한·Hilt 구조 변경은 실연동 수용조건이 충족된 이후 필요한 범위만 추가한다.
- 빌드 및 unit 검증 후 evaluator 증거를 일반 Android/Pleos runtime으로 나눠 기록한다.
