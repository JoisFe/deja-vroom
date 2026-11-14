# FirstLook 교차로 레코드 작성

`make_junction.py`는 사람이 확인한 OSM의 approach/keep/trap way를 입력받아 FirstLook 교차로 레코드의 좌표와 방향값을 계산한다. 이 도구는 어느 도로가 권장 진출로인지 추측하지 않으며, 산출 JSON은 개발 데이터다.

## 입력 데이터 받기

분기점 중심 좌표를 확인하고 반경 300m 안의 highway way geometry를 Overpass에서 받는다. 좌표 입력 순서는 위도, 경도다.

```sh
curl -sS https://overpass-api.de/api/interpreter \
  --data-urlencode 'data=[out:json][timeout:25];way(around:300,LATITUDE,LONGITUDE)[highway];out tags geom;' \
  -o /tmp/firstlook-ways.json
```

Overpass 응답을 지도에서 확인한 뒤 분기점에서 시작할 way와 해당 geometry의 `splitIndex`, 바깥 방향(`toward_start` 또는 `toward_end`)을 사람이 정한다. way index를 자동으로 갈래 의미로 분류하지 않는다. 실제로 확인하지 않은 OSM `destination`, `name`, `ref` tag는 lane 안내 근거로 사용하지 않는다.

## 작성 설정

아래 좌표와 OSM ID는 형식 예시일 뿐 실제 교차로 데이터가 아니다. `routeId`는 좌표가 검증된 동일한 `RouteSnapshot.routeId`여야 한다.

```json
{
  "id": "city-intersection-a",
  "routeId": "verified-route-snapshot-id",
  "junction": [37.5, 127.0],
  "backMeters": 150,
  "message": "분기점 표지를 미리 확인하세요.",
  "directSeconds": 300,
  "detourSeconds": 1020,
  "retrievedAt": "2026-11-13T00:00:00Z",
  "ways": {
    "approach": { "wayId": "101", "splitIndex": 0, "outwardDirection": "toward_end" },
    "keep": { "wayId": "102", "splitIndex": 0, "outwardDirection": "toward_end" },
    "trap": { "wayId": "103", "splitIndex": 0, "outwardDirection": "toward_end" }
  },
  "review": {
    "manuallyVerified": false,
    "roadviewCoverageVerified": false,
    "notes": "현장·표지·로드뷰 coverage 확인 전"
  }
}
```

선택한 way geometry는 분기점에서 바깥쪽으로 이어지는 방향이어야 한다. 분기점과 선택 vertex의 거리가 20m보다 멀거나, 선택 방향 geometry가 2점 미만이면 생성이 거부된다.

## 생성 및 검수

```sh
python3 scripts/roadview/make_junction.py \
  --ways /tmp/firstlook-ways.json \
  --config /tmp/junction-config.json \
  --output /tmp/junction-record.json

python3 -m unittest discover -s scripts/roadview -p 'test_*.py' -v
```

도구가 `at`(접근로를 분기점부터 150m 거슬러 올라간 지점), `pan`, 위험 갈래 10m 지점 `caution`, keep/trap의 20/40/60m 점을 계산한다. 직접/우회 `duration`은 로컬 OSRM 응답의 초 값을 각각 입력한다. 결측·음수·우회시간이 더 짧은 경우에는 `cost`를 만들지 않는다.

`useRoadview`는 `manuallyVerified`와 `roadviewCoverageVerified`가 모두 true일 때만 true다. 차로 문구를 쓰려면 `laneGuidanceVerified`와 `verifiedLaneMessage`를 함께 제공해야 한다. 사용 전에는 Roadview 사진에서 실제 도로와 방향, 도로 층, 표시 위치, 안내 문구를 사람이 확인한다. `panoId`는 저장하지 않는다.

## 라이선스와 제품 경계

OSM 데이터는 ODbL로 제공된다. OSM 자료를 사용하는 데이터에는 OpenStreetMap과 contributors 출처를 표시하고 라이선스를 명시해야 한다. 파생 데이터 배포 시에는 ODbL 적용 범위를 확인한다. 개발용 한남대로 fixture는 OSM attribution/ODbL 표시와 route/panorama 개발 검수를 포함해 `fixtures/hannam-daero/`에 보관한다. 제품용 fixture는 별도 현장·route·권리 검수를 거친다. [OpenStreetMap copyright/licence](https://www.openstreetmap.org/copyright).

Kakao Roadview 내부 overlay 사용은 기술 시제품이 가능하다는 뜻일 뿐 차량 화면·공모전 제출물의 표시권 확인은 아니다. 공개 SDK/API 문서, My Project 권한, Pleos runtime, 공급자 표시 정책은 각각 별도의 검증 상태로 기록한다. Kakao 기본 화살표가 실제로 분리·숨김됐다는 확인 전에는 자체 방향표식을 route UI에서 활성화하지 않는다.
