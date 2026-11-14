# 기술·데이터 조사 기록

**조사일**: 2026-11-11<br>
**기준**: 공개 공식 문서 및 저장소 내 공모전 자료. 페이지 확인은 사용권 계약, API 접근 승인, 컴파일, 런타임 동작을 입증하지 않는다.

## 결정 요약

| 후보 | 확인된 기능 | 이 MVP에서의 결정 | 남은 검증 |
|---|---|---|---|
| Pleos/HMG | 저장소 SDK 조사표는 NaviHelper의 목적지 요청·비동기 경로 상태·TBT 등을 정리하지만 전체 선택 경로 polyline 및 로드뷰 API는 확인되지 않음 | 실제 경로에 사진 좌표를 붙이지 않음. Pleos 우선 순위는 유지 | 현재 프로젝트의 SDK artifact/권한/지원 차량, 경로 geometry 제공 여부를 Pleos 측에서 확인 |
| 공공데이터 | 성수동 등 특정 지역·시설에 한정한 360도 이미지 dataset 일부 | 영통-강남 경로 사진으로 대체하지 않음 | 해당 경로 coverage, 갱신/촬영일, 이용조건과 위치/방향 메타데이터가 있는 dataset 발견 필요 |
| Kakao Roadview | JS 문서에서 좌표 주변 pano 검색, pano 선택, 시점 제어 API 확인. Android RoadView 가이드는 별도 협의 요구 | 차량 앱 활성 연동 보류 | 차량 내 WebView/공모전 표시 권한, overlay·attribution·key 제약 및 Kakao 별도 협의 필요 |
| Naver Panorama | JS API에서 좌표 기반 Panorama, POV(pan/tilt/fov), 위치 정보/촬영일 접근 문서 확인 | 차량 앱 활성 연동 보류 | 차량 내 사용 및 공모전 배포 권한, WebView/key 제한, 샘플 route coverage 확인 필요 |
| Google Street View | Metadata 응답에서 위치/날짜/저작권 정보, 별도 이미지 API 문서 확인 | 기본 provider로 채택하지 않음 | 비용, 차량 표시권, attribution 및 storage/cache 정책을 실제 이용계획에 맞춰 승인 필요 |

## 공식 문서

### Pleos

- 프로젝트 인벤토리: [Pleos SDK 기능 한눈에 보기](../../docs/contest/12-pleos-sdk-capabilities.md), [NaviHelper·TTS·FusedLocation SDK 기록](../../docs/contest/04-navi-voice-location-sdk.md), [조사 통합 기록](../../docs/contest/09-research-integration-map.md).
- 확인 내용: 공개 가이드 및 저장소 조사 시점의 artifact 요약에는 목적지 요청, 라우팅 callback, TBT/ETA 요약이 있다. 전체 경로 좌표 또는 road-view metadata를 앱에서 읽는 계약은 해당 자료에서 확인되지 않았다. FusedLocation은 현재 위치 계열이며 사진 공급자가 아니다.
- 적용: 외부 Route API 경로를 Pleos 선택 경로와 같다고 취급하지 않는다. Mock route와 실경로를 식별한다.
- 등급: 저장소에 기록된 문서 조사 사실. 현재 심사 프로젝트 승인·실차 지원 상태는 미확인.

### 현대·공공 도로 사진

- Pleos 공식 문서 검색은 `RoadView`, `road view`, `로드뷰` 공개 기능을 확인하지 못했다. 이는 공개 인덱스에서 찾지 못했다는 의미이며 내부/파트너 제공물이 없다는 단정이 아니다.
- [공공데이터포털 성수동 카페거리 360도 파노라마](https://www.data.go.kr/data/15149104/fileData.do)는 공간 범위가 제한된 JPG dataset이다. 영통-강남 전체 경로를 제공하는 실시간 route lookup 계약은 확인되지 않았다.
- [대전 도시철도 역사 360도 자료](https://www.data.go.kr/data/15076390/fileData.do)는 시설 단위 자료로 운전자 시점 도로 사진의 대안이 아니다.
- [도로교통공단 사고다발지역 API](https://www.data.go.kr/data/15056681/openapi.do)는 과거 사고 이력 후보 점수에만 쓸 수 있으며 현재 위험을 판단하지 않는다. 이번 구현에서 API 키 없이 연동하지 않는다.

### Kakao

- [Kakao Maps Web API 문서](https://apis.map.kakao.com/web/documentation/)에 `Roadview`, `RoadviewClient.getNearestPanoId`, `setPanoId`, `setViewpoint`가 설명되어 있어 기술적으로 좌표 주변 panorama 선택/방향 제어가 가능하다.
- [Kakao Android RoadView 안내](https://apis.map.kakao.com/android_v2/docs/api-guide/roadview/)는 RoadView 사용을 위해 별도 협의가 필요하다고 안내한다.
- JS/Web API가 있다는 사실만으로 차량용 Android 앱·WebView 내 재생, 공모전 배포, 오버레이 또는 콘텐츠 사용권을 인정하지 않는다. 별도 협의 및 차량 이용조건 확인 전에는 네트워크 로딩을 활성화하지 않는다.
- 상업/쿼터 조건은 [Kakao Developers quota](https://developers.kakao.com/docs/ko/getting-started/quota)에서 계정/앱 등록 후 다시 확인해야 한다.

### Naver

- [Naver Panorama API](https://navermaps.github.io/maps.js.ncp/docs/naver.maps.Panorama.html)에는 좌표 position 기반 생성, `pan`/`tilt`/`fov`, panorama 위치 좌표 및 `photodate` 정보가 있다.
- [Panorama 튜토리얼](https://navermaps.github.io/maps.js.ncp/docs/tutorial-Panorama.html)과 [인증/시작 가이드](https://navermaps.github.io/maps.js.ncp/docs/tutorial-2-Getting-Started.html)는 웹 API 구성과 client ID 등록을 설명한다.
- 차량 표시/임베딩 라이선스, 차량용 기기 배포 조건, API key restriction, 호출 과금 및 overlay 권한은 이 문서만으로 확정되지 않는다. 확인 전 앱에서 호출하지 않는다.

### Google

- [Street View Metadata](https://developers.google.com/maps/documentation/streetview/metadata?hl=en)는 panorama 위치, pano ID, copyright, 날짜(일부 응답에서 생략/부분값)를 설명한다.
- [Street View 정책](https://developers.google.com/maps/documentation/streetview/policies)은 이미지 저장·스크래핑·캐시 제한과 attribution 등 이용 조건을 둔다.
- 이미지 비용 및 차량 사용 조건을 프로젝트 수준에서 검토하지 않았으므로 이번 provider로 선택하지 않는다.

## 통합 판단

1. 현대 우선 조사에서 이 저장소의 공개 SDK 자료로는 도로 이미지 및 전체 경로 geometry API를 검증하지 못했다.
2. 확인한 공공 360 자료는 demo route coverage가 아니다.
3. Kakao는 Native RoadView 별도 협의 요구가 분명하고, WebView 권리는 추가 확인 필요하다. Naver Web panorama는 기술적으로 유망하나 차량 내 사용권은 미확인이다.
4. 키·계약·선택된 실경로 형상이 제공되지 않았으므로 Kakao/Naver/Google 네트워크를 연결하지 않는다. 사용자에게 수동으로 약관 동의나 비밀정보를 요청하지 않고 Mock 구현을 계속한다.
5. 실사진 UI는 추후 권리와 route match가 함께 통과한 뒤에만 켠다. 지금의 Demo illustration은 실제 도로가 아니라고 표시한다.

## 상태 구분

| 증거 단계 | 이번 작업 상태 |
|---|---|
| 공개 문서 확인 | 일부 SDK 기능/제약 표로 조사 |
| API 접근/계정 키 | 미제공, 미실행 |
| 공급자 이용권·차량 사용 승인 | 미확인 |
| 앱 코드 연결 | Mock/provider-neutral 경계만 |
| 빌드·단위 테스트 | 구현 후 `/verify` 결과 기록 |
| Pleos emulator·실차 런타임 | 이번 환경에서 미검증 |
