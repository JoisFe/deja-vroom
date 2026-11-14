---
name: new-feature
description: Team workflow for building a feature — create the convention branch, take the Spec Kit tasks.md as the plan, implement with the harness agents, verify, evaluate on the emulator, review. Use after /speckit-tasks has produced specs/<NNN-feature>/tasks.md (or for small fixes without a spec).
argument-hint: "[specs/NNN-feature or ticket-id] [short description]"
disable-model-invocation: true
allowed-tools: Bash(.claude/skills/new-feature/scripts/create-branch.sh *) Bash(git branch *) Bash(git status *) Bash(git diff *) Bash(git log *)
---

Current branch: !`git branch --show-current || true`
Specs present: !`ls -d specs/*/ 2>/dev/null || echo none || true`

Request: $ARGUMENTS

Spec Kit owns *what and why* (spec → plan → tasks). This skill owns *how and done* (implement → verify → evaluate). Never run `/speckit-implement`.

## 1. Pick the input
- Feature work: use `specs/<NNN-feature>/tasks.md`. If it is missing, stop and tell the user to run `/speckit-specify` → `/speckit-clarify` → `/speckit-plan` → `/speckit-tasks` (→ `/speckit-analyze`) first.
- Small fix with no spec: write a short checklist to `.claude/work/<branch-slug>/plan.md` (one line per step, each with its verification command) and get the user's approval before coding.

## 2. Branch
`.claude/skills/new-feature/scripts/create-branch.sh <NNN-feature|ticket|none> "<english summary>" [feature|fix|chore]`

## 3. Implement task by task
- App → `pleos-app-developer`. Give each agent the task line, the spec/plan paths, and the files it may touch. `[P]` tasks can run in parallel subagents.
- After each task passes its check (`/verify ...`), tick it in `tasks.md`.
- If an implementation decision changes behavior or scope, update `spec.md`/`plan.md` in the same change.
- If a turn ends with unchecked tasks and no blocker, continue. Stop after the third automatic retry on the same task and report what blocks it.

## 4. Evaluate (definition of done)
- After a user story's tasks pass `/verify`, delegate to `app-evaluator` with the feature directory.
- FAIL → hand the evaluator's "고칠 곳" back to the implementer as new tasks, then re-verify and re-evaluate. BLOCKED → report to the user.
- When all stories pass, run `/speckit-converge` to catch gaps between the artifacts and the code; new tasks go back to step 3.

## 5. Review and integrate
- `code-reviewer` on `git diff main...HEAD`; fix HIGH findings, re-verify.
- Ask before pushing. Push the branch and open a PR to `main` (`gh pr create`); never merge without review.
