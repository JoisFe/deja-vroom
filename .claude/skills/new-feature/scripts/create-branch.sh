#!/usr/bin/env bash
# Create a convention branch: feature/<ticket>-<slug>. Deterministic, so the model never re-derives the rule.
# Usage: create-branch.sh <TICKET|none> "<short english summary>" [type: feature|fix|chore]
set -euo pipefail

ticket="${1:?ticket required (use 'none' if no ticket)}"
summary="${2:?summary required}"
type="${3:-feature}"

case "$type" in feature|fix|chore) ;; *) echo "type must be feature|fix|chore" >&2; exit 2 ;; esac

slug=$(printf '%s' "$summary" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+|-+$//g' | cut -c1-40 | sed -E 's/-+$//')
[[ -z "$slug" ]] && { echo "summary must contain ASCII letters/digits (write it in English)" >&2; exit 2; }

if [[ "$ticket" == "none" ]]; then branch="$type/$slug"; else branch="$type/$ticket-$slug"; fi

if git show-ref --verify --quiet "refs/heads/$branch"; then
  git switch "$branch"
else
  git switch -c "$branch"
fi
echo "$branch"
