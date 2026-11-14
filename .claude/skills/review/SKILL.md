---
name: review
description: Review the current branch's diff (or a given ref range) for correctness, security, and Pleos/Kotlin app and Java backend conventions, returning severity-ranked findings.
argument-hint: "[git range, default main...HEAD]"
context: fork
agent: code-reviewer
allowed-tools: Bash(git diff *)
---

Review this diff. Range: `$ARGUMENTS` (if empty, use `main...HEAD`).

Changed files vs main:
!`git diff --stat main...HEAD || true`

Read the full diff with `git diff <range>` and the surrounding code as needed. Return findings in your standard format, highest severity first.
