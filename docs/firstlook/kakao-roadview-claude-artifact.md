# Kakao Roadview Claude 아티팩트

내일 작업을 다시 시작할 수 있도록 사용자가 제공한 Claude 아티팩트 링크와 프로젝트 내 구현 위치를 기록한다.

- 제목: 카카오 로드뷰 WebView 가이드
- 원본 아티팩트: [Claude artifact](https://claude.ai/artifact/ErJtgXvUcZAXuLC7YQusW5)
- 기록일: 2026-11-12
- 열람 상태: 2026-11-12에는 로그인 화면으로 본문을 읽지 못했으나, 2026-11-13 공유 아티팩트 본문 전체를 브라우저에서 확인했다.
- 아티팩트는 OSM way로 approach/keep/trap을 검수해 교차로 레코드를 만들고, OSRM 우회 비용, NaviHelper live event gate, P+UX 기반 WebView 수명주기, Kakao Roadview 오버레이와 fallback을 제안한다. 코드 미실행 및 예시 좌표가 자리 표시자임을 아티팩트 자체가 밝힌다.
- 구현 계획: [Claude Roadview 알고리즘 계획](../../.omx/plans/firstlook-kakao-roadview-algorithm-from-artifact.md). 원본 제안은 Pleos SDK 승인, Kakao 차량 표시 권리 또는 런타임 동작의 증거가 아니므로 공식 문서와 실제 실행 결과로 재검증한다.

## 현재 구현 및 실행 결과 (2026-11-14)

- `scripts/roadview/make_junction.py`가 수동 검수한 OSM way들로 approach 좌표/방위, 교차로 주의점, 진행/대안 분기점을 계산한다. 샘플 입력과 검수 설정은 [Hannam-daero fixture](fixtures/hannam-daero/README.md)에 있다.
- 고정 fixture 시연에 더해 기본 목적지 제출 흐름을 실제 route 분석으로 연결했다. Nominatim 목적지 조회 → OSRM driving route geometry/steps → `RouteAnalyzer` 복잡 후보 점수화 → 후보 카드 전체 표시 → 사용자가 고른 단일 후보 Kakao panorama coverage 확인 → 해당 구간 Roadview 표시 순서다.
- `강남역`을 Pleos emulator에서 제출해 후보 다섯 개의 실제 Kakao Roadview 사진, 선택 후보의 복잡도/분기 근거 및 panorama를 확인했다. 카드/선택 화면 증거는 [구현 상태 기록](kakao-roadview-implementation-status.md)에 있다.
- 선택 Roadview에서는 비공개 `_ar_N_*` 화살표와 `_at_N_*` 방위표기 쌍 중 진행 heading의 8방위 한 쌍만 남기고 CSS `scale: .32`로 축소한다. 기본 panorama는 유지한다. 공개 옵션이 아닌 비공식 DOM 조정이라 Kakao SDK 변경에 취약하다.
- 실제 경로 흐름과 Kakao WebView는 개발용 endpoint와 debug source set에 한정된다. 실차 경로/차량 상태/주행 UX 신호, Pleos 프로젝트 권한, Kakao 차량용 표시·배포 권리는 검증되지 않았다.

## 저장소 내 현재 작업

- 전체 경로 분석 계획과 확인 기록: [알고리즘 우선 네비 흐름](../../.omx/plans/firstlook-algorithm-first-navigation-flow.md), [현재 구현 상태](kakao-roadview-implementation-status.md)
- 실행 방법: 저장소 루트 [README](../../README.md#kakao-roadview-로컬-개발-테스트-debug-전용)
- 개발 서버와 Android debug WebView 통합: `scripts/roadview/`, `web/roadview/`, `app/src/debug/`
- Kakao JS 키와 Android SDK 경로는 ignored `local.properties`에서 로컬로 읽는다. 키 값은 문서나 Git에 복사하지 않는다.
- 2026-11-12: 사용자가 제공한 JavaScript 키를 ignored `local.properties`에 설정했다. `http://localhost:8080/` Referer의 Kakao SDK 요청은 HTTP 200, `http://localhost:8097/`는 domain mismatch를 반환했다. 이후 localhost 개발 서버와 Android `adb reverse`를 통해 emulator 내 실제 Kakao panorama도 확인했다.

## 내일 이어서 할 때

1. 저장소 루트의 ignored `local.properties`에 `kakaoJsKey`와 `sdk.dir`를 설정한다. 키를 Git에 추가하지 않는다.
2. `scripts/roadview/serve.sh`를 실행한다. 스크립트는 에뮬레이터용 `adb reverse`도 설정하고 `web/roadview/`를 제공한다.
3. 기본 경로 알고리즘은 목적지(예: `강남역`)를 입력해 제출한다. 외부 route 분석 뒤 Roadview가 바로 열린다. `Kakao Roadview 개발 테스트` 버튼은 authored OSM fixture의 7개 분기/주의 표식을 별도로 확인한다.
4. 실행 방법과 남은 운영 전 항목은 [구현 상태 보고서](kakao-roadview-implementation-status.md)를 참조한다.
