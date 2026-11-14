---
name: verify
description: Build and test with Gradle and report only failures (full log saved to disk). Use after any code change, before claiming a task is done, or as the check step in a loop.
argument-hint: "[gradle tasks, default: build]"
allowed-tools: Bash(.claude/skills/verify/scripts/gradle-quiet.sh *)
---

Run:

```
.claude/skills/verify/scripts/gradle-quiet.sh $ARGUMENTS
```

- Exit 0 → report `PASS` with the one-line summary.
- Non-zero → report the failing tests/errors shown (they are already filtered). Read `build/claude-logs/last.log` only around a specific failure if the excerpt is not enough.
- For long or repeated runs, delegate to the `test-runner` agent so logs never enter the main context.

The exit code is the verdict. Don't declare success from reading code.
