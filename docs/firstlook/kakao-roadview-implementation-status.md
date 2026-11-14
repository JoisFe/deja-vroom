# 복잡 도로 분석 → Kakao Roadview 전체 흐름

확인일: 2026-11-14  
기준 계획: [알고리즘 우선 네비게이션 흐름 계획](../../.omx/plans/firstlook-algorithm-first-navigation-flow.md)  
관련 교차로 fixture: [한남대로 authored OSM record](fixtures/hannam-daero/README.md)

## 현재 기본 흐름

목적지 화면의 제출 버튼은 `Nominatim → OSRM → RouteAnalyzer` 분석을 시작한다. 분석이 성공하면 최대 5개의 점수순 후보를 `SUMMARY` 카드 화면에 표시하고, 화면 상단에서 3개 또는 5개 보기를 고른다. 사용자가 선택한 후보만 `ROADVIEW_TEST` 화면으로 연다.

1. 출발지와 목적지는 모두 입력할 수 있다. 초기 출발지 텍스트는 `수원시 영통구`이며, 사용자가 다른 장소명/주소로 바꾸면 두 값을 각각 Nominatim으로 지오코딩한 뒤 OSRM에 전달한다. 자동완성은 호출하지 않고 같은 검색어는 메모리 캐시를 사용한다. 현재 위치 자동 감지는 아직 연결하지 않았다.
2. OSRM driving route API에서 전체 GeoJSON geometry, distance/duration, maneuver steps 및 intersections를 요청한다.
3. 교차로의 `bearings`/`entry`에 따른 접근 가능한 분기 수와 fork/merge/ramp/roundabout maneuver를 topology feature로 만든다. 출처·분기 수·신뢰도 근거를 유지한다.
4. 검증 route geometry와 route ID에 묶인 후보를 `RouteAnalyzer`/geometry matcher에 넣어 필터링·점수화한다. 후보가 없거나 요청이 실패하면 사용자가 읽을 수 있는 오류를 표시한다.
5. 후보 카드에서 사용자가 확인할 교차로를 고른다. debug Android WebView는 선택된 그 후보 하나만 Kakao `getNearestPanoId()`로 50m 이내 coverage를 조회한다. coverage가 있으면 해당 위치/heading의 Roadview를 열고, 없으면 이유를 표시한다. Roadview 하단의 이전/다음 버튼으로 목록 화면에 돌아가지 않고 주변 후보를 연속 확인할 수 있다. 앱이 임의로 첫 coverage 후보로 대체하지 않는다.
6. 후보 요약 화면에서 3개/5개 보기를 선택한다. 각 후보 카드에는 후보 좌표 반경 80m의 Mapillary 사진 중 거리와 진행 방향이 가까운 contributor 사진을 정적 이미지로 표시하고, 출처 링크를 보존한다.
7. 선택된 Roadview에서는 Kakao의 `_ar_N_*` 도로 위 화살표와 `_at_N_*` 방위표기를 짝지어 후보 진행 heading의 8방위만 남기고 `scale: .65`를 적용한다. 오른쪽 상단 패널은 이 화살표를 반복하지 않고 선택 구간명·분석 점수·근거를 보여준다. OSRM 응답에 `lanes`가 있으면 `valid=true` 차로를 패널의 차로 그림에서 강조한다. lanes가 없으면 차로별 안내 데이터가 없음을 명시한다. 실제 lane-level 안내로 오인하지 않도록 한다.

이 흐름은 `OsmRouteRepository` 기본 구현이 사용되는 개발 prototype이다. 기존 `BRIEFING`/`COMPLETE`/Mock `NAVIGATION` 화면은 코드에 남아 있지만 새 경로 검색은 후보 요약 화면을 기본으로 연다. 고정 OSM `JunctionRecord` fixture는 별도 개발 테스트 버튼으로 실행할 수 있으며 기본 경로 후보 분석과 별개다.

## 구현과 경계

| 영역 | 현재 동작 | 남은 경계 |
|---|---|---|
| Geocoding | Nominatim, 입력한 출발지와 목적지를 각각 제출 시 조회, 한국 범위 | public 저빈도 개발용 endpoint; production SLA 아님; 자동완성과 현재 위치 연결은 없음 |
| Route | OSRM `steps=true`, full GeoJSON, duration/distance | 사용자가 입력한 장소명으로 계산; 차량 현재 위치/차량 네비 경로 아님 |
| 복잡 후보 | OSRM step intersection 진입 branch 수 + maneuver 유형, analyzer 점수 순위 | OSM 그래프 완전성에 좌우; lane 수·실제 난이도·위험도 아님 |
| Roadview | 사용자가 고른 후보 하나의 Kakao panorama coverage 조회; 화면 안에서 이전/다음 후보 전환 | debug 전용 WebView; 차량 표시/배포 권리 별도 |
| 후보 카드 사진 | Mapillary Graph API의 contributor 사진을 정적 이미지로 표시 | Mapillary 토큰·사진 coverage 필요; Mapillary 이미지별 출처 링크를 유지하고, 불가 시 카드 안에 사유 표시 |
| 차로 안내 | OSRM `intersection.lanes[]`의 `indications`/`valid`를 후보 모델까지 전달하고 valid 차로를 오른쪽 상단 구간 정보 패널에서 강조 | 응답에 lane 정보가 없으면 차로 번호를 추정하지 않고 안내 |
| 진행 화살표 | 도로 위 Kakao `_ar_N_*`와 `_at_N_*` DOM 쌍 중 후보 heading 방향만 표시; scale 0.65. 오른쪽 상단에는 구간명·분석 점수·근거를 표시 | Kakao 비공개 DOM 구조/방위표기 의존; 공개 설정 아님 |
| 차량 신호 | 없음; restriction source와 navigation gateway는 Fake/Mock | Pleos project 승인·NaviHelper/Vehicle/UX 신호 미연동 |

OSRM/OSM 분기 수는 도로망 그래프에서 선택 가능한 진입 방향 수를 뜻한다. 차로 수, 회전 안전성, 사고 가능성을 뜻하지 않는다. 별도 OSRM `lanes` 응답의 `valid`만 차로 패널에서 표시하고, 없으면 번호나 차로를 추론하지 않는다.

## 화살표 크기 조정 확인

Kakao 공개 Roadview API의 `Roadview`/`RoadviewClient`/`setViewpoint`/`CustomOverlay` 옵션에는 기본 ground navigation arrow 크기를 조절하는 공개 설정이 없다. 사용자 `MarkerImage`는 별도 마커일 뿐 기본 이동 화살표 크기를 바꾸지 않는다. [Kakao Maps Web API 문서](https://apis.map.kakao.com/web/documentation/)

개발 WebView DOM 검사에서는 native-looking 화살표가 `_ar_N_*` ID를, 대응 방향 표기가 `_at_N_*` ID를 가진 것으로 확인했다. 후보 heading을 8방위로 변환해 일치하는 쌍만 표시하고 다른 방향 쌍은 숨긴다. 표시 화살표에 CSS `scale: .65`를 적용한다. MutationObserver가 동적으로 생성되는 노드를 따라간다. 이는 공식 지원이 아닌 비공개 Kakao DOM 조정이며 SDK 구현 변경 시 방향 매칭/스타일이 깨질 수 있다.

## 후보 카드 실제 도로 사진

Kakao Web API 문서에는 정적 Roadview 이미지 API가 확인되지 않는다. 카드 썸네일은 Mapillary Graph API 이미지 검색을 우선 사용한다. 로컬 서버는 반경 80m bbox와 이미지 ID/썸네일 URL/좌표/촬영 방향/촬영자 필드를 요청하고, 거리와 heading 차이를 함께 평가해 1장을 고른다. Mapillary 토큰·coverage가 있으면 사진을 큰 정적 이미지로 표시하고 Mapillary 및 기여자 출처 링크를 사진 위에 둔다. Mapillary contributor imagery에는 출처 표기와 CC BY-SA 조건이 적용된다([출처/라이선스](https://help.mapillary.com/hc/en-us/articles/115001770409-CC-BY-SA-license-for-open-data)).

로컬 개발 서버는 ignored `local.properties`의 `mapillaryAccessToken`을 사용해 검색을 프록시하고 응답에 `Cache-Control: no-store`를 지정한다. 토큰 미설정/API 실패/coverage 없음이면 카드 사진 영역 전체에 짧은 사유를 표시한다. Kakao Roadview SDK는 파노라마 화면 전용으로 두고 정적 썸네일 대체재로 사용하지 않는다. Google Street View Static API는 정적 이미지 API로 기술적으로 가능하지만 Google 약관이 Street View와 비Google 지도 콘텐츠를 같은 화면에 표시하지 못하게 제한하므로 Kakao Roadview와 함께 쓰는 후보 카드에는 채택하지 않았다([약관](https://cloud.google.com/maps-platform/terms)).

OSRM `intersection.lanes`를 `RouteLane(indications, validForRoute)` 형태로 `BriefingSegment`까지 전달한다. [OSRM API 문서](https://project-osrm.org/docs/v5.23.0/api/)에 따라 경로 maneuver에 유효한 차로(`valid=true`)를 오른쪽 상단 구간 정보 패널의 차로 그림에서 강조한다. 각 차로에 복수 회전 표기가 있으면 모두 그린다. OSRM이 차로 데이터를 생략하면 실제 차로를 추정하지 않고 “차로별 안내 정보 없음”을 표시한다. 2026-11-14 테스트 경로의 OSRM 응답은 모든 후보 intersection에서 `lanes`를 생략해 lane별 권고는 아직 검증되지 않았다. 이 표시는 실차 내비 차로 유도 정확도나 운전 안전성을 확인한 것이 아니다.

Kakao Mobility Directions SDK는 `KNGuide_Route.lane` 및 `KNLane_LaneInfo`의 `turnType`/`highlightType`으로 권장 차로 정보를 표현하는 API를 문서화한다([레퍼런스](https://developers.kakaomobility.com/reference/android-ref-kotlin/class-KNLane_LaneInfo.html)). 현재 FirstLook 경로 데이터는 공개 OSRM이며 Kakao Mobility Directions SDK가 연결되어 있지 않다. SDK 접근·라이선스 및 Pleos 앱 런타임에서의 사용 가능 여부는 별도로 확인해야 한다.

## 파란 도로 강조선 조사 결과

Kakao Web API는 Roadview 위에 `CustomOverlay`를 올리고 좌표와 altitude를 지정하는 기능을 공식 제공한다. [CustomOverlay API](https://apis.map.kakao.com/web/documentation/)와 [Roadview custom overlay 예제](https://apis.map.kakao.com/web/sample/roadviewCustomOverlay/)를 확인했다. 다만 공식 문서에는 주행 경로를 차도 평면에 연속 투영하는 route-line API나 OSRM geometry와 파노라마 사진을 자동 정합하는 API가 없다.

이를 확인하려고 선택 후보 앞 120m OSRM geometry를 6m 간격으로 샘플링해 `altitude(0)` CustomOverlay 파란 segment로 표시했다. Emulator에서는 21개 overlay가 생성됐지만 실제 화면에서 점들이 차도 표면과 맞지 않고 차량 위/도로 위에 떠 보였다([실험 캡처] `/tmp/firstlook-blue-line.png`). 따라서 이 방식은 기본 route 안내로 부정확하다고 판단해 시연 구현을 제거했다. 현재 기본 흐름에는 파란 노면선이 없고, 선택 후보의 이름·점수·출처 근거를 설명 레이블로 표시한다. 후보 위치 `!` overlay 코드는 남아 있지만, 도로 표면에 정합되는 시각 표식으로는 확인되지 않았다.

샘플 이미지처럼 도로 노면에 붙는 선을 구현하려면 panorama 촬영 위치·heading과 지도 경로의 photo-to-route 정합을 먼저 검증하고, 도로 표면의 실제 위치/소실점에 맞춘 투영이 가능해야 한다. 해당 입력은 공개 Kakao Web SDK만으로 확인할 수 없었다. 사람이 검수한 panorama별 선 geometry 또는 별도 영상/차량 SDK 지원 없이 차로 안내처럼 보이는 선을 표시하지 않는다.

## 확인한 결과와 방법

- `강남역` 입력을 에뮬레이터에서 제출해 Nominatim geocode → OSRM route/candidate ranking → 후보 목록 표시 → 선택 후보 Kakao panorama coverage → Roadview 진입까지 확인했다.
- 선택 화면에서 경부고속도로 후보, 복잡도/분기 근거 및 Kakao panorama를 표시했다. OSRM public demo는 초기 403 이후 User-Agent 추가로 emulator 요청이 성공했다.
- 2026-11-14 에뮬레이터에서 후보 화면의 3개/5개 전환을 확인했다. 5개 모드에서 1/5~5/5 카드가 표시됐다: `/tmp/firstlook-five-confirmed.png`.
- Mapillary 토큰이 없는 현재 개발 환경에서 API가 `unconfigured`를 반환하는 것을 확인했다. 토큰 누락 문구는 카드 헤더를 덮지 않고 썸네일 영역 안에 중앙 정렬된다. 이번 실행에서는 실제 Mapillary 사진을 검증하지 못했다.
- 2026-11-14 선택 Roadview 화면에서 65% 진행 화살표, 경로 진행 방향 화살표와 차로 위치 데이터 부재 안내, 이전/다음 후보 버튼을 확인했다: `/tmp/roadview-lane-after.png`. 다음 버튼 입력으로 후보 1/5에서 2/5로 이동하고 Roadview 파노라마가 바뀌는 것을 확인했다: `/tmp/roadview-next-candidate.png`. 실제 OSRM 경로 응답에는 `lanes` 배열이 없어 특정 차로를 선택·강조하지 않는다.
- 파란 노면선 prototype의 화면 정합 문제를 `/tmp/firstlook-blue-line.png`에서 확인하고 기본 구현에서 제외했다.
- `./gradlew :app:assembleDebug`는 변경 코드 반영 후 성공했다. 이번 정리에서는 테스트를 실행하지 않았다.
- 별도 authored OSM fixture의 교차로 matcher/overlay 검증은 [한남대로 fixture](fixtures/hannam-daero/README.md)에 기록한다. 이는 실제 route repository나 실차 경로의 증거가 아니다.

## 운영 전 필수 교체/확인

- OSRM/Nominatim public endpoint는 시연용이다. 배포는 허가된 라우팅·geocoding provider와 쿼터/정책을 정한다.
- 수동 입력 출발지를 차량의 승인된 현재 위치 또는 NaviHelper route input으로 교체하려면 Pleos 권한과 API 지원 범위를 먼저 확인하고 route geometry 및 maneuver 제공 여부를 확인한다.
- Kakao Roadview WebView의 차량용 표시, 키 도메인/패키지 정책 및 배포 권리를 확인한다. CSS selector는 비공개 DOM이라 공식 커스터마이징으로 간주하지 않는다.
- Pleos My Project 승인, 차량 운전 상태/UX 제한, 화면 표시 정책을 확인하고 실제 runtime source에 연결한다.
- 네트워크 실패, 후보 없음, panorama coverage 없음에 대한 회복 동작을 실제 target 환경에서 확인한다.

## 재현

1. ignored `local.properties`에 `kakaoJsKey`와 `sdk.dir`가 설정됐는지 확인한다. 키 값은 문서·Git·로그에 복사하지 않는다.
2. `scripts/roadview/serve.sh`로 localhost:8080 WebView 페이지를 제공한다.
3. Pleos Connect emulator에서 FirstLook을 열고 출발지(예: `서울역`)와 목적지(예: `강남역`)를 입력한 뒤 제출한다. 분석이 끝나면 후보 요약 화면에 입력한 출발지와 목적지가 표시된다.
4. 요약 화면에서 3개/5개를 고르고 각 Mapillary 도로 사진을 확인한다. 카드 사진은 토큰과 coverage가 있을 때만 표시된다. 원하는 카드를 눌러 Roadview를 열고, heading 방향 화살표와 OSRM lane 정보가 있으면 차로 패널을 확인한다. route 없는 고정 OSM overlay는 상단 `Kakao Roadview 개발 테스트` 버튼에서 별도로 확인한다.
