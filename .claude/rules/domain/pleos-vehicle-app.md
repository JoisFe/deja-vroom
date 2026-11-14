---
paths:
  - "app/**"
---

# Domain: Pleos Connect vehicle app (Kotlin)

- Call order: `Vehicle(context)` → `initialize()` (once, Hilt singleton) → `getXxx()` domain → `checkXxxCapabilities` → branch on `AccessType` → `getCurrentXxx`/`setXxx` within `AreaBounds` → `release()` when the process-level owner ends.
- Wrap SDK callbacks in the data layer: one-shot → `suspendCancellableCoroutine`, listeners → `callbackFlow` with unregister in `awaitClose`. Patterns: skill `android-app-architecture` → `references/pleos-sdk-kotlin.md`.
- Every API needs its `pleos.car.permission.*` in `AndroidManifest.xml`; declare only domains used. Missing permission: getters report it through `onFailed`, while `register*`/`unregister*` throw `SecurityException` at once. In 2.3.2 `VehicleException` is obfuscated, so classify errors by the `ERROR_*` name in the message.
- `NONE`/`WRITE`-only capability → disable the UI path, don't call Get.
- Vehicle 2.3.2 has no speed API: use AAOS `CarPropertyManager` (`PERF_VEHICLE_SPEED`). `Vehicle.getGear()` exists (`CAR_DRIVE`) but is `@Deprecated` in favor of the AAOS `GEAR_SELECTION` property, as is `getEvBatteryCapacity` (→ `INFO_EV_BATTERY_CAPACITY`); they still work, prefer the AAOS property once third-party access is confirmed (javap, 2026-10-31).
- Respect driving restrictions (`CarUxRestrictions`) and audio focus rules.
- SDK facts and per-method details: skill `pleos-vehicle-sdk`, official docs https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/.
