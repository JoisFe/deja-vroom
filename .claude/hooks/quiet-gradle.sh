#!/usr/bin/env bash
# PreToolUse(Bash): rewrite plain `./gradlew <build/test tasks>` to the quiet wrapper so only failures enter context.
# The wrapper keeps the full log on disk and preserves Gradle's exit code.
input=$(cat)
cmd=$(printf '%s' "$input" | jq -r '.tool_input.command // empty')

# Only simple invocations: no pipes, redirects, chaining, or substitutions.
pattern='^\./gradlew( [A-Za-z0-9:._=/*-]+)+$'
if [[ "$cmd" =~ $pattern ]] && [[ "$cmd" =~ (test|build|check|assemble|compile) ]]; then
  new="\"\$CLAUDE_PROJECT_DIR\"/.claude/skills/verify/scripts/gradle-quiet.sh ${cmd#./gradlew }"
  printf '%s' "$input" | jq --arg c "$new" \
    '{hookSpecificOutput: {hookEventName: "PreToolUse", updatedInput: (.tool_input + {command: $c})}}'
else
  echo '{}'
fi
