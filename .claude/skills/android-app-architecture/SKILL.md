---
name: android-app-architecture
description: Modern, standard architecture for the Pleos Connect app in Kotlin — Compose UI, ViewModel + StateFlow (unidirectional data flow), Repository wrapping the Pleos SDKs as Flow/suspend, Hilt, coroutines, testing, Gradle setup. Use when creating screens, features, repositories, or project structure under app/, or when deciding how app code should be organized.
paths:
  - "app/**"
---

Scope: the contest app — a single-module Kotlin app demoed on the Pleos emulator (final PT 2026-12-03) and submitted as GitHub source + release APK. Follow Google's official app architecture guide (UI layer → data layer; no domain layer) with current Jetpack defaults, and add nothing the demo doesn't need.

## Stack (decided)

| Concern | Choice |
|---|---|
| Language | Kotlin (JVM target 17), no Java sources |
| UI | Jetpack Compose + Material 3 components themed with Pleos tokens (Pleos UI design guide) |
| Screen state | `ViewModel` + immutable `UiState` data class in `StateFlow`; collect with `collectAsStateWithLifecycle()` |
| Async | Coroutines + Flow; inject `CoroutineDispatcher`s |
| DI | Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`), KSP |
| Navigation | Single Activity + Navigation Compose with type-safe `@Serializable` routes |
| Pleos SDKs | Behind repository interfaces; callbacks → `callbackFlow` / `suspendCancellableCoroutine` |
| Local data | DataStore, only if the feature needs to persist settings (no Room unless the idea truly needs a database) |
| Network | Only if the idea needs our server: Retrofit + OkHttp + kotlinx.serialization, HTTPS only |
| Build | Gradle Kotlin DSL + version catalog `gradle/libs.versions.toml`, Compose BOM |
| Tests | JUnit4 + kotlinx-coroutines-test + Turbine + fakes; Compose UI tests for key screens |

## Layers and package layout

```text
app/src/main/kotlin/<base>/
  MainActivity.kt                 single @AndroidEntryPoint activity, hosts NavHost
  PleosApp.kt                     @HiltAndroidApp
  core/
    designsystem/                 theme, tokens, shared composables
    di/                           Hilt modules (dispatchers, SDK instances)
    car/                          CarUxRestrictions → Flow, window/driving state
  data/
    vehicle/  VehicleRepository.kt, PleosVehicleRepository.kt, model/
    voice/    SpeechRepository.kt (TTS/STT)
    location/ LocationRepository.kt
    remote/   API client, DTOs
  feature/
    <feature>/  <Feature>Screen.kt, <Feature>ViewModel.kt, <Feature>UiState.kt, navigation.kt
```

Dependency direction: `feature` → `data` (interfaces) → SDK/DB/network. `feature` never imports `ai.pleos.playground.*`. Create only the folders the app uses.

## Rules of thumb
1. **Unidirectional data flow**: UI sends events to the ViewModel (`onAction(...)`), the ViewModel updates `UiState`, the UI renders state. No business logic in composables.
2. **Single source of truth**: each piece of data has one owner (a repository); screens observe it.
3. **SDK isolation**: only `data/*/Pleos*Repository` touches Pleos classes. Map SDK types to app models; handle `null` values and SDK exceptions there and expose sealed results.
4. **Lifecycle**: one `Vehicle` instance per process (Hilt `@Singleton`), `initialize()` once; listeners are registered inside `callbackFlow` and removed in `awaitClose`. Collect with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)` so subscriptions stop when no screen is visible.
5. **Driving safety in the state**: expose `isDrivingRestricted` from `core/car` and let screens render a reduced UI from state, not ad-hoc checks.
6. **Fakes first**: every repository interface has a `Fake*` used in tests and in a demo build flavor, so the round-2 demo works even without vehicle signals.

## References (read when needed)
- Layers with full Kotlin examples and Java/Spring analogies → [references/architecture.md](references/architecture.md)
- Wrapping Pleos SDK callbacks (capability, get, register, set, errors) → [references/pleos-sdk-kotlin.md](references/pleos-sdk-kotlin.md)
- Compose UI, theming, driving restrictions, CCP focus → [references/compose-ui.md](references/compose-ui.md)
- Gradle setup, version catalog, testing → [references/build-and-test.md](references/build-and-test.md)

Mandatory conventions and security rules live in `.claude/rules/domain/android-*.md` and load automatically for `app/**`.
