---
name: pleos-location-sdk
description: Pleos FusedLocation SDK 1.1.3 integration facts, permissions, service support, callbacks, proximity alerts, and evidence boundaries. Use when implementing or reviewing location updates, geofences, or location-based vehicle features.
paths:
  - "app/**"
---

Read the official FusedLocation SDK 1.1.3 docs (https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/) before using this SDK.

## Required boundaries

- Treat SDK documentation, Maven availability, compile success, project access approval, privacy permission, service support, and vehicle runtime as separate evidence states.
- Before implementation, check the current My Project status and target Pleos environment. Until access and runtime behavior are confirmed, limit claims to a compile spike or adapter.
- The SDK provides last-known location, location callbacks, and circular Proximity Alert ENTER/EXIT. It does not provide gallery access, speed, odometer, POI data, persistent trips, or timeline storage.
- Declare only `pleos.car.permission.FUSED_LOCATION`; handle synchronous `SecurityException`, nullable location callbacks, listener errors, and service/privacy denial.
- Check `isSupported()` before initialization; it checks service package presence, not approval or permission. Release listeners/alerts and call `release()` with the owning lifecycle.
- Do not infer callback interval, confidence units, geofence limits/persistence/latency, or Android phone behavior; the reference lists these as unresolved.
- Treat coordinates and timestamps as sensitive location data. Apply user consent, minimum collection, retention/deletion, and parked-only interaction requirements.
