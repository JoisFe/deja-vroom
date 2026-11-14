# 조사 기록: Pleos SDK와 실경로 데이터

**확인일**: 2026-11-11<br>
**근거 범위**: 공식 Pleos 문서, 공개 vendor Maven repository metadata/AAR resolve, 로컬 프로젝트 상태. 외부 계정·승인·차량 runtime은 확인하지 않았다.

## Pleos SDK

| 기능 | 공개 문서 | artifact 접근 | 프로젝트 접근/runtime | 판정 |
|---|---|---|---|---|
| NaviHelper | v3.0.5 목적지/경유지/경로 요청, Navigation 정보/화면, listener event 문서 확인 | `ai.pleos.playground:NaviHelper:3.0.5` 및 transitive AAR을 임시 Gradle probe로 resolve PASS | 이 repository에 의존성/import 없음; My Project 및 Pleos runtime access 미확인 | 공식 기능 문서화·artifact resolve. 현 앱 미연동 |
| TextToSpeech | v3.3.1 `ON_DEVICE`/`SERVER`/`HYBRID` 문서 확인 | `ai.pleos.playground:TextToSpeech:3.3.1` 및 transitive AAR resolve PASS | create-project 안내에서 TTS `Not Available Yet`; project status/credentials 미확인 | 공식 문서·artifact 존재, 실제 project 사용 가능 여부 충돌/미확인 |
| FusedLocation | 현재 문서는 `getLastKnownLocation`, updates, `isSupported` 등 설명 | 저장소 확인에서 `FusedLocation:1.1.2`는 404, `1.1.3` POM은 200; 정식 dependency graph/compile 미수행 | create-project 안내는 FusedLocation `Not Available Yet`; project/runtime 미확인 | 문서/배포 상태가 다를 수 있음. 앱에 추가하지 않음 |
| Vehicle | 현재 Vehicle State 기능은 문서화, 차종별 capability 확인 필요 | 현재 repo 조사 버전 `2.3.2` POM 200, `2.3.1` 404 | project approval/vehicle runtime 미확인 | artifact resolve만으로 연결하지 않음 |

공식 [NaviHelper v3.0.5 가이드](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)는 목적지 요청과 결과 callback이 비동기라고 설명한다. 공식 [Playground SDK access 안내](https://document.pleos.ai/en/docs/pleos-only/create-my-project)는 Vehicle State/NaviHelper는 Ready to Use라고 하면서 FusedLocation/TTS/STT/Vehicle Control은 Not Available Yet으로 표기한다. 최신 [TextToSpeech v3.3.1 문서](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro)와 [API 호환성 문서](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability)는 TTS 기능 API를 제공/호환으로 설명한다. 문서 간 상태가 일치하지 않으므로 My Project project-specific status가 확인되기 전 실제 연결은 비활성으로 둔다.

### Route geometry 한계

NaviHelper 공개 자료는 목적지 설정, route search events, `CurrentLocationInfo`, TBT, Trip summary/route point 등 내비 정보 유형을 나열한다. 조사한 문서에는 앱이 선택한 전체 route polyline을 반환한다고 명시된 계약을 찾지 못했다. route summary의 `routePoints`/POI point를 연속 도로 geometry로 변환하거나 외부 route와 같은 경로라고 판단하지 않는다. 실제 adapter가 이 모델을 받아도 `geometryStatus=UNAVAILABLE`로 남길 수 있다.

## 도로 사진 공급자

- HMG/Pleos 공개 문서 검색은 개발자용 road-view panorama API를 확인하지 못했다. 내부/파트너 data 존재 여부는 미확인이다. 문의 초안은 [hmg-data-inquiry-draft.md](hmg-data-inquiry-draft.md)에만 작성했으며 보내지 않았다.
- [Kakao Android RoadView 가이드](https://apis.map.kakao.com/android_v2/docs/api-guide/roadview/)는 RoadView 사용에 별도 협의가 필요하다고 밝힌다. Web API의 좌표/panorama 제어 문서는 차량 내 권리 승인이 아니다.
- [Naver Panorama API](https://navermaps.github.io/maps.js.ncp/docs/naver.maps.Panorama.html)는 web panorama/POV/photodate metadata를 문서화하지만 차량 내 사용 및 배포권을 확정하지 않는다.
- 공공 포털의 제한 지역 360 이미지 데이터는 수원-강남 경로를 대체하지 않는다. 도로교통공단 사고다발 이력은 과거 집계 자료일 뿐 현재 위험 판단 근거로 사용하지 않는다.

## 임시 Maven probe 결과

`./gradlew -p /tmp/firstlook-pleos-sdk-probe.<random> resolvePleosSdkProbe --no-daemon`은 NaviHelper 3.0.5와 TextToSpeech 3.3.1 및 전이 AAR을 모두 resolve해 `BUILD SUCCESSFUL`로 종료했다. 이 프로젝트의 Gradle settings/dependency에는 변경이 없었다. 증거는 공개 artifact 접근뿐이며 프로젝트 승인·API 호출·컴파일 통합·런타임은 증명하지 않는다.

## 결정

1. NaviHelper SDK를 앱에 붙이는 것은 가능성은 높지만, 현재 목적지 입력에는 좌표와 POI provider ID가 없어 route request를 의미 있게 구성할 수 없다.
2. SDK가 전체 선택 경로 geometry를 제공한다고 검증하기 전 route-photo matching은 비활성 상태로 유지한다.
3. HMG 사진 접근은 내부/파트너 문의 후 결정한다. 응답 전 provider 추가 호출/사용권 수락을 하지 않는다.
4. TTS artifact 접근 성공은 My Project의 현재 API access status를 대신하지 않는다.
5. 현재 UI 흐름과 Fake/Demo 구현을 계속 보존한다.
