# Compose UI for Pleos Connect

## Composable rules

- Split `XxxRoute` (stateful: ViewModel, navigation) from `XxxScreen` (stateless: `state`, `onAction`, `modifier`).
- Every public composable takes `modifier: Modifier = Modifier` as its first optional parameter and applies it to the root.
- Hoist state: a composable either owns trivial UI state (`remember`) or receives it; never both for the same value.
- No I/O, no repository calls, no `LaunchedEffect` that fetches data in leaf composables; data comes from the ViewModel.
- Provide `@Preview` for each screen state that matters (loading, data, error, restricted) using fake `UiState`.
- Strings from `stringResource(R.string.…)` (Korean resources), sizes from the theme/dimens, never hardcoded.

## Theme with Pleos tokens

Build one `PleosTheme` in `core/designsystem` that maps the colors, typography, and shapes from the Pleos UI design guide onto `MaterialTheme`. Screens use `MaterialTheme.colorScheme` / `typography` only, so a token change is one edit. Support light and dark.

## Layout for automotive windows

- Pleos runs apps in portrait (1 window) or landscape split windows. Use `currentWindowAdaptiveInfo()` / window size classes and design for compact width first.
- The window can resize at runtime; state lives in the ViewModel so nothing is lost.
- Respect insets (`WindowInsets.safeDrawing`) and don't let the IME push windows without text fields.

## Driving restrictions

```kotlin
@Composable
fun DriveScreen(state: DriveUiState, onAction: (DriveAction) -> Unit, modifier: Modifier = Modifier) {
    if (state.restricted) {
        DrivingSafeContent(state, onAction, modifier)   // large targets, no keyboard, no video
    } else {
        FullContent(state, onAction, modifier)
    }
}
```

- `restricted` comes from `CarUxRestrictions` via the ViewModel (no direct manager calls in UI).
- Check individual flags (`UX_RESTRICTIONS_NO_KEYBOARD`, `NO_VIDEO`, `NO_SETUP`, `LIMIT_STRING_LENGTH`) when only part of a screen is affected.

## Rotary controller (CCP) and focus

- All actions must work by touch. If the app declares CCP support, make every interactive element focusable in a logical order (`Modifier.focusable()`, `focusRequester`, `onKeyEvent`) and test with the D-pad in the emulator.
- Top-level back: `moveTaskToBack(true)` instead of `finish()` (`BackHandler` at the start destination calls the activity).

## Accessibility and safety

- Touch targets ≥ 48 dp (larger if the Pleos guide says so), contrast ≥ 4.5:1, content descriptions on icons.
- Avoid auto-playing animation and video while driving.
