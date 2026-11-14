#!/usr/bin/env bash
# PreToolUse(Bash): block commits/pushes on main/master and force-push; tell Claude the team convention instead.
# Only command segments that start with `git` count, so text inside heredocs/strings doesn't trigger it.
input=$(cat)
cmd=$(printf '%s' "$input" | jq -r '.tool_input.command // empty')

# One segment per line: split on newlines, ;, &&, ||, |
segments=$(printf '%s\n' "$cmd" | sed -E 's/(\&\&|\|\||;|\|)/\n/g' | sed -E 's/^[[:space:]]+//')
git_re='^git([[:space:]]+-C[[:space:]]+[^[:space:]]+)?[[:space:]]+'

if printf '%s\n' "$segments" | grep -qE "${git_re}push([[:space:]].*)?[[:space:]](--force|-f)([[:space:]]|$)"; then
  echo "Blocked: force push is not allowed by team policy. Use --force-with-lease on your own feature branch only after asking the user." >&2
  exit 2
fi

if printf '%s\n' "$segments" | grep -qE "${git_re}(commit|push)([[:space:]]|$)"; then
  branch=$(git -C "${CLAUDE_PROJECT_DIR:-.}" branch --show-current 2>/dev/null)
  if [[ "$branch" == "main" || "$branch" == "master" ]]; then
    echo "Blocked: you are on '$branch'. Team convention: work on a feature branch. Create one with .claude/skills/new-feature/scripts/create-branch.sh <TICKET|none> \"<english summary>\" and commit there." >&2
    exit 2
  fi
fi
exit 0
