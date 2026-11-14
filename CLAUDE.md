# Déjà Vroom — Pleos Connect vehicle app (Kotlin)

Reply to the user in Korean. Code and identifiers in English; commit messages, PR titles and bodies in Korean.

## Stack
- `app/` — Pleos Connect vehicle app (Android Automotive, **Kotlin + Jetpack Compose**, minSdk 28, Pleos SDKs). Architecture: skill `android-app-architecture`.
- Build: Gradle wrapper only (`./gradlew`), never a global `gradle`.

## Commands
- Verify (build + test, failures only): `/verify` or `.claude/skills/verify/scripts/gradle-quiet.sh :app:testDebugUnitTest`
- Install on emulator: `./gradlew :app:installDebug`

## Workflow
- Spec first (GitHub Spec Kit, Korean templates): `/speckit-specify` → `/speckit-clarify` → `/speckit-plan` → `/speckit-tasks` (→ `/speckit-analyze`). Principles: `.specify/memory/constitution.md`.
- Build with the harness, not `/speckit-implement`: `/new-feature specs/<NNN-feature>` → `pleos-app-developer` → `/verify` → `app-evaluator` (emulator check of SC-*) → `/speckit-converge` → `/review` → PR. Guide: `docs/workflow.md`.
- Never commit on `main`; a hook blocks it. Branch: `feature/<ticket>-<slug>` via the new-feature script.
- Verify with exit codes (`/verify`), not by reading code and declaring success.

## Product rules (see `docs/concept.md`)
- Read and speak only; no vehicle control.
- Never repeat what the navigation already says; speak actions, not states; stay silent on known roads.
- Do not record speed or harsh braking. Turn arrows show the computed route's own maneuver; never claim a lane number that is not verified.
- Demo data is fake and labeled as Demo on screen.

## Blind review
- No real names, contacts, or affiliations in code, docs, app screens, commits, or videos.

## Where knowledge lives
- Pleos SDK facts → skills `pleos-vehicle-sdk`, `pleos-location-sdk`; first-time setup → `/pleos-getting-started`
- Conventions → `docs/conventions.md`, `.claude/rules/`
- Harness map → `.claude/README.md`
