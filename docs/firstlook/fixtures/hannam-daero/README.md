# 한남대로 교차로 개발 fixture

2026-11-14에 OpenStreetMap API에서 가져온 실제 도로 geometry 세 개를 사람이 선택해 만든 개발 전용 fixture다. API 원본은 `ways.json`, 선택한 분기와 검수 설정은 `config.json`, 알고리즘 출력은 `app/src/debug/assets/junctions/hannam-daero-demo-record.json`이다.

| 용도 | OSM way | OSM 분류·이름 | 방향 근거 |
|---|---:|---|---|
| 접근로 | 1346179854 | primary · 한남대로 | `oneway=yes` |
| 권장 진행로 | 218655248 | primary · 한남대로 | `oneway=yes` |
| 다른 갈래 | 267929583 | residential · 한남대로27길 | OSM에 oneway tag 없음 |

분기점은 `[37.5372981, 127.004718]`이다. builder가 접근로에서 150m 전 시점과 방위, 분기점 10m 전 caution, 각 경로의 20m/40m/60m marker를 계산한다. 우회시간 자료는 없어 cost를 만들지 않는다. lane guidance도 없다.

Kakao Roadview는 계산된 접근점에서 매번 nearest panorama를 조회한다. 2026-11-14 에뮬레이터 화면에서 4.3m 높이 제한 표지와 도로 방향을 OSM geometry와 대조했고, 분기 경로를 실제 도로 사진 위에 표시했다. 도로 사진에서 보이는 도로명 표기와 OSM 이름은 다른 구간 표기일 수 있어, 이 fixture는 경로 주행용 정답 데이터가 아니라 개발 시연 자료다.

표시 결과는 debug source set에 한정한다. 그 화면의 route geometry는 fixture의 접근로→교차로→권장로를 이어 만든 검증용 `RouteSnapshot`이다. 이는 Pleos 내비게이션의 live route가 아니다. Kakao 기본 이동 화살표는 유지하며, 초록/빨강 표식은 개발용 branch annotation이다. 실차 Gear/UX/traffic adapter와 Kakao 차량 표시 권리는 검증되지 않았다.

## 출처 및 라이선스

© OpenStreetMap contributors. OSM data is available under the [Open Database License (ODbL) 1.0](https://www.openstreetmap.org/copyright). 이 폴더의 `ways.json`과 파생 경로 fixture는 OSM attribution을 보존한다. Kakao 이미지의 attribution은 Roadview 화면에도 남긴다.
