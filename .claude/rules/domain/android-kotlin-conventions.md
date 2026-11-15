---
paths:
  - "app/**"
---

# Domain: Kotlin / Compose code conventions

Base: Kotlin coding conventions (kotlinlang.org), Android Kotlin style guide, Compose API guidelines. Architecture decisions: skill `android-app-architecture`.

## Kotlin
- Kotlin only; official code style (4-space indent, 120-col lines). Format with the IDE/ktlint before committing.
- Names: `PascalCase` types, `camelCase` functions/properties, `SCREAMING_SNAKE_CASE` constants, packages lowercase. Files named after their main type.
- Prefer `val` and immutable collections. `data class` for state and models.
- Null safety: no `!!`. Use `?.`, `?:`, early return, or a sealed result. Treat every SDK callback value as nullable.
- Model closed sets with `sealed interface` / `enum class` and exhaustive `when` (no `else` branch on sealed types).
- Scope functions sparingly and only for their purpose (`apply` to configure, `let` for nullable chains); don't nest them.
- Public API of a class is minimal; default to `private`/`internal`.

## Coroutines and Flow
- Launch in `viewModelScope`/`lifecycleScope` only; never `GlobalScope` or `runBlocking` in app code.
- Pass `CoroutineDispatcher`s as constructor parameters (no Hilt, so no `@IoDispatcher` qualifier); don't hardcode `Dispatchers.IO` inside classes that need tests.
- Main-safe APIs: a `suspend fun` must be safe to call from the main thread (switch context inside).
- Expose `StateFlow`/`Flow` read-only; keep `MutableStateFlow` private (`_uiState` + `uiState`).
- Collect in UI with `collectAsStateWithLifecycle()`.
- Don't swallow `CancellationException`; when using `runCatching` around suspend calls, rethrow it.

## Architecture
- Package by layer: `ui/`, `feature/`, `domain/`, `data/`, `model/`, plus `core/designsystem/`. Dependencies point one way: `ui` → `feature` → `data`/`domain` → `model`; `domain` has no Android imports.
- UI → ViewModel → Repository interface. Only `data/**/Pleos*` files import `ai.pleos.playground.*`; only `core/car` imports `android.car.*`.
- ViewModels hold no `Context`, `View`, or `Activity` references.
- Every repository interface has a `Fake*` implementation, used in tests and to show the app without a car. There is no `demo` flavor.

## Compose
- `XxxRoute` (stateful) + `XxxScreen(state, onAction, modifier)` (stateless). `modifier: Modifier = Modifier` is the first optional parameter.
- No business logic, I/O, or repository access in composables. Side effects only via `LaunchedEffect`/`DisposableEffect` with correct keys.
- User-facing text (Korean) goes in pure label functions in `ui/` (`Labels.kt`, `TripLabels.kt`) with unit tests, not in string resources. Colors and dimensions come from the theme (`DejaVroomTheme` in `core/designsystem`), not hardcoded.
- Each screen has `@Preview`s for its main states, including driving-restricted.

## Tests
- Unit tests for every ViewModel and repository mapping; names in backticks describing behavior: `` `shows error when permission denied` ``.
- Use fakes over mocks for our own interfaces; test with `runTest` and assert on `state.value` (Turbine is not used).

## Comments and commits
- KDoc on public types and non-obvious functions; comments explain why, not what. No commented-out code.
- Commit messages, PR titles and bodies in Korean.
