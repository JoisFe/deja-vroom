---
name: markdown-docs
description: Write, rewrite, or review Markdown documents so they are short, scannable, and visual — clear structure, plain sentences, tables and Mermaid diagrams where prose gets hard. Use when creating or editing any .md doc (README, guide, spec, checklist, report), or when asked to make a doc easier to read.
argument-hint: "[file or topic]"
---

Target: $ARGUMENTS

Write for a reader who skims first and reads second. Every section should answer one question, and the reader should get the point from headings, the first sentence of each section, and the visuals alone.

## Workflow
1. **Pick the doc type** (Diátaxis): tutorial, how-to, reference, or explanation. Don't mix them in one section. Templates: [references/templates.md](references/templates.md).
2. **Outline first**: H1 title, a 1–3 line summary of what the reader gets, then H2 sections in the order the reader needs them.
3. **Write** with the rules below; details and before/after examples in [references/style-guide.md](references/style-guide.md).
4. **Visualize** anything with steps, branches, states, comparisons, timelines, or numbers. Choose the form with [references/visuals.md](references/visuals.md).
5. **Lint**: `python3 -I .claude/skills/markdown-docs/scripts/md_lint.py <file.md>` and fix every ERROR; fix WARNs unless there is a reason.
6. **Re-read as the reader**: can someone act on this in 30 seconds? Cut what doesn't help them.

## Core rules
- One H1. Headings in order (H2 → H3, no skips). Headings are short noun phrases or tasks, not sentences.
- Lead with the conclusion. Put the answer, decision, or command first; background after.
- One idea per sentence; one topic per paragraph; paragraphs of 3 lines or fewer.
- Lists for parallel items, numbered lists only for ordered steps. Max two levels of nesting.
- Tables for anything compared across the same attributes (≥ 3 items × ≥ 2 attributes). Keep cells short; no paragraphs in cells.
- Bold only the few words a skimmer must not miss (≤ 1–2 per section). No bold walls, no ALL CAPS, no emoji decoration.
- Code, commands, file paths, identifiers in backticks; multi-line code in fenced blocks with a language tag.
- Links with descriptive text, never "here" or bare URLs in prose.
- Same term for the same thing everywhere. Define abbreviations once.
- Facts that can change (versions, dates, prices) carry a date or source.
- Korean docs: 평서문 "~한다/~합니다" 중 하나로 통일, 번역투·불필요한 한자어 피하기, 기술 용어는 원어 유지 (자세한 내용은 style guide).

## Visual first
Prose is the last resort for structure. Use this map before writing a long paragraph:

| Content | Use |
|---|---|
| Ordered steps, a pipeline, a decision with branches | Mermaid `flowchart` |
| Who calls whom, request/response, callbacks | Mermaid `sequenceDiagram` |
| Lifecycle, modes, status changes | Mermaid `stateDiagram-v2` |
| Schedule, milestones, deadlines | Mermaid `gantt` or `timeline` |
| Data model, entity relations | Mermaid `erDiagram` / `classDiagram` |
| Proportions of a whole | Mermaid `pie` (≤ 6 slices) |
| Trend or comparison of numbers | Mermaid `xychart-beta` or a table |
| Options compared on attributes, specs, mappings | Table |
| Priority/effort or risk/impact | Mermaid `quadrantChart` |

Keep diagrams small (≤ ~12 nodes); split larger ones. Every diagram gets one sentence above it saying what to notice.

## When reviewing a doc
Return findings as a short list: location → problem → fix, most important first. Then apply fixes if asked.
