# RoadViewProvider 계약

실제 제공자는 차량 내 표시 권한과 경로 geometry가 준비된 후 추가한다. 공급자 원본 URL/SDK 객체는 UI에 노출하지 않고 `RoadViewSet` 또는 실패 상태로 변환한다.

```kotlin
interface RoadViewProvider {
    suspend fun findViews(
        approachPoint: GeoCoordinate,
        headingDegrees: Double?,
        radiusMeters: Int,
    ): RoadViewLookupResult
}
```

`RoadViewLookupResult`는 다음을 표현한다.

- `Available(views)`: 제공자 결과와 attribution, 촬영일, 좌표, heading, route matching evidence 포함
- `NoCoverage`: 근처 사진이 없음
- `NotAuthorized`: 차량 내 표시/계정 허가가 없어 네트워크 호출을 하지 않음
- `Unavailable(reason)`: timeout, offline, quota, provider error; 비밀 키나 응답 원문을 로그에 남기지 않음

`DEMO_ILLUSTRATION`은 별도 Fake provider가 제공하며 실제 좌표·실제 방향·실제 도로 표지를 주장하지 않는다. 공급자 SDK 및 entitlement 호출은 provider 구현 내부에 격리한다. 현재 계약은 설계용이며 공급자 API 직접 연결 상태를 뜻하지 않는다.
