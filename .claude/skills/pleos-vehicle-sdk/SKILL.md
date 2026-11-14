---
name: pleos-vehicle-sdk
description: Pleos Connect Vehicle SDK facts (Kotlin app; Java notes kept for reading official samples) — dependency, permissions per domain, capability→get/set flow, callbacks, errors, lifecycle. Use when writing or reviewing code that calls the Vehicle SDK.
paths:
  - "app/**"
---

Source of truth: <https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/> (verify versions there; docs pages disagreed: getting-started shows 2.3.1, project-setup shows 2.3.2).

## Setup
- Repo (`settings.gradle.kts` → `dependencyResolutionManagement.repositories`): `maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }`
- Dependency: `implementation("ai.pleos.playground:Vehicle:2.3.2")`
- minSdk 28; emulator image API 34 (Pleos Connect system image).

## Call flow
1. `val vehicle = Vehicle(context).apply { initialize() }` (one instance, Hilt `@Singleton`)
2. Domain: `vehicle.getDoor()`, `getHVAC()`, … (one object per domain)
3. `checkXxxCapabilities({ config, areaMap -> … }, { e -> … })` → `config.accessType`: `NONE | READ | WRITE | READ_WRITE`; `areaMap[area]` → `AreaBounds` min/max
4. `getCurrentXxx(area, { value -> … }, { e -> … })` / `setXxx(area, value, onComplete, onFailed)` within bounds
5. Subscribe with `registerXxx(listener)`, or `registerXxx(area, listener)` for area-based domains (Door, Seat, Window, HVAC …); always unregister the same way
6. `vehicle.release()` when the owning scope ends

The SDK is Kotlin: callbacks are function types and listener interfaces. In our Kotlin app wrap them as `suspend`/`Flow` (skill `android-app-architecture` → `references/pleos-sdk-kotlin.md`). Official samples are often Java, where lambdas must `return Unit.INSTANCE`.

## Gotchas (verified against the API reference, 2026-10-24)
- Set methods take `(area, value, onComplete, onFailed)`; Kotlin default args don't exist in Java, so pass all four. The guide's 3-arg Java sample does not match the reference; confirm on first compile.
- Area-based domains take the area in `register*` (`registerDoorPosition(area, listener)`); Gear, Odometer, EvBattery and Fuel listeners take only the listener.
- 2.3.2 has no speed API (`VehicleSpeedListener` removed). Read speed with AAOS `CarPropertyManager` + `PERF_VEHICLE_SPEED` (official FAQ).
- `api.Gear` exists (`getGear()`, `CAR_DRIVE`) but is `@Deprecated` in favor of the AAOS `GEAR_SELECTION` property; `getEvBatteryCapacity`, `getEvBatteryLevel` and `getRangeRemainingDistance` are deprecated the same way (javap on the AAR, 2026-10-31). They still work.
- Seat occupancy reads inverted on the Pleos Connect AVD: SDK 2.3.2 returns `true` only when the raw value is `1` (`SeatImpl` getter and `callback.a` listener), while AAOS and the AVD/Virtual Console send VACANT=1, OCCUPIED=2. Keep the SDK contract (`true` = occupied) on cars and invert only on the AVD (javap + logcat, 2026-11-01).
- Telltale APIs need `PRIVATE_CAR_SAFETY` (signature|privileged): don't plan features on them.
- Vehicle Control (set) shows "Coming Soon" in the Playground console; keep control features optional.
- Per-method signatures and permissions: official docs https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/.

## Errors
`onFailed: (Exception) -> Unit`. The docs say `e is VehicleException` → `e.errorCode`, but in the 2.3.2 AAR the exception class is obfuscated and `ErrorCode` is not on the compile classpath: classify by the `ERROR_*` name in `e.message`/`toString()` and map it to an app-level sealed result in the repository. Missing manifest permission: getters → `onFailed` (`ERROR_SECURITY_PERMISSION`); `register*`/`unregister*` → `SecurityException` thrown at once.

## References (read when needed)
- Permission table per domain → [references/permissions.md](references/permissions.md)
- Kotlin wrapping patterns → skill `android-app-architecture` → `references/pleos-sdk-kotlin.md`
- Java variants from the official docs (for reading samples) → [references/java-patterns.md](references/java-patterns.md)
- Doc map with URLs → [references/doc-map.md](references/doc-map.md)
