---
name: pleos-getting-started
description: Step-by-step first run of a Pleos Connect app in Kotlin + Compose — Playground project, emulator/AVD, Vehicle SDK import, permission, first API call on the emulator.
disable-model-invocation: true
argument-hint: "[step number to resume from]"
---

Guide the user through these steps, doing the file work yourself and pausing where only the user can act (accounts, Android Studio UI). Resume from step $ARGUMENTS if given. Track progress in `.claude/work/getting-started.md`.

1. **Playground project** (user): sign in at Pleos Playground → Workspace › My Project › create. Note Project ID; Vehicle State SDK needs no application. Ask the user to confirm when done.
2. **Emulator** (user, Android Studio): add the Pleos Connect system-image update site, install the API 34 image, create an Automotive AVD (cold boot, 4 GB RAM). Exact steps: [references/emulator.md](references/emulator.md).
3. **Project** (you): create `app/` as an Android Automotive module in **Kotlin + Compose**, minSdk 28, Gradle Kotlin DSL + version catalog, following skill `android-app-architecture` (start with Hilt, one feature, a Fake repository). Gradle files: [references/gradle-app.md](references/gradle-app.md).
4. **SDK import** (you): add the Pleos Maven repo and `ai.pleos.playground:Vehicle` (check latest version in docs).
5. **Permission** (you): `pleos.car.permission.CAR_DOORS` in the manifest.
6. **First call** (you): Door position read with capability check and `release()` — use the Kotlin pattern in skill `android-app-architecture` → `references/pleos-sdk-kotlin.md`.
7. **Run** (both): `/verify :app:assembleDebug`, then the user boots the AVD; `./gradlew :app:installDebug`. On failure walk: minSdk → permission → sync → emulator booted → `onFailed` message.

