# Markdown style guide

Distilled from the Google developer documentation style guide, the Microsoft Writing Style Guide, Diátaxis, markdownlint's default rules, and Toss's technical writing guide (technical-writing.dev). Rules here win when they conflict with habit.

## 1. Structure

- **Title + summary.** H1, then 1–3 lines: what this doc is, who it's for, what they'll be able to do.
- **Inverted pyramid.** Conclusion → key details → background. A reader who stops after the first screen should still get the point.
- **Headings are a table of contents.** Reading only the headings should tell the story. Prefer `## 설치` / `## Install the SDK` over `## 이제 SDK를 설치해 보겠습니다`.
- **No orphan headings.** Every heading has at least one line of text before its first sub-heading.
- **Section length.** If a section scrolls past one screen, split it or move detail to a linked doc.
- **One doc, one job.** A how-to doesn't explain theory at length; link to the explanation instead.

## 2. Sentences

| Rule | Before | After |
|---|---|---|
| Conclusion first | 여러 요인을 검토한 결과 A를 쓰기로 했다 | A를 쓴다. 이유는 … |
| Active, direct | 설정이 완료되어야 합니다 | 설정을 완료한다 |
| One idea per sentence | X를 하고 Y를 확인한 뒤 Z가 되면 W를 한다 | X를 한다. Y를 확인한다. Z가 되면 W를 한다 |
| Concrete over vague | 적절히 설정한다 | `timeout`을 30초로 설정한다 |
| Remove filler | 기본적으로, 사실상, 매우, ~하는 것이 가능하다 | (삭제) / ~할 수 있다 |
| Avoid 번역투 | ~에 있어서, ~를 통해, ~되어진다, ~에 대한 | ~에서, ~로, ~된다, ~의/생략 |
| Avoid double negatives | 없지 않다 | 있다 |

- Instructions use the imperative: "Run `./gradlew build`." / "`./gradlew build`를 실행한다."
- Put the condition before the action: "If the build fails, run …" / "빌드가 실패하면 …를 실행한다."
- Numbers: digits with units (`4 GB`, `30초`, `2026-11-15`). Dates in ISO or one consistent local format.
- Korean: 문체는 한 문서 안에서 하나로 (`~한다` 또는 `~합니다`). 기술 용어는 원어 그대로(`capability`, `callback`), 처음 한 번만 우리말 설명을 붙인다. 조사 앞 영문은 띄우지 않는다(`API를`).

## 3. Lists

- Bullets for unordered, parallel items; numbers only when order matters.
- Items are grammatically parallel (all nouns, or all imperative verbs).
- Start each item with the key word; keep items to one or two lines.
- 2–7 items per list. More → group with sub-headings or a table.
- Don't end a list item with a period unless items are full sentences; be consistent.

## 4. Tables

- Use when ≥ 3 items share ≥ 2 attributes (comparisons, specs, mappings, options).
- First column = the thing being compared; header names are short.
- Cells hold words, numbers, short code — not paragraphs. Long notes go below the table.
- Align numbers with units in the header (`RAM (GB)`) instead of repeating units.
- ≤ 6 columns. Wider → split or transpose.
- Use ✓ / – (or 예/아니오) consistently for booleans.

## 5. Emphasis and callouts

- **Bold** = must-not-miss words only. *Italics* rarely. No underline, no ALL CAPS.
- Use GitHub alerts for the few things that really matter, at most ~3 per doc:

```markdown
> [!NOTE]
> Useful context.

> [!WARNING]
> Something that breaks things or loses data.
```

(Types: NOTE, TIP, IMPORTANT, WARNING, CAUTION. They render on GitHub; elsewhere they degrade to a blockquote.)

## 6. Code and commands

- Inline code for identifiers, paths, values, keys: `minSdk`, `app/build.gradle.kts`.
- Fenced blocks always declare a language (`bash`, `java`, `kotlin`, `json`, `yaml`, `text`).
- Commands are copy-pasteable: no `$` prompts, no output mixed in (show output in a separate `text` block).
- Placeholders in angle brackets and explained once: `<PROJECT_ID>`.
- Keep examples minimal and runnable; mark unverified examples as such.

## 7. Links

- Descriptive link text: `[에뮬레이터 설정](02-dev-environment.md#3-에뮬레이터-설치)`, not "여기".
- Relative links inside the repo. External links to the most stable official page.
- Cite sources for facts the reader may need to re-check, next to the fact or at the section end.

## 8. Markdown hygiene (markdownlint-compatible)

- Blank line around headings, lists, code blocks, tables.
- `-` for bullets, `1.` for every numbered item is fine (auto-numbering).
- No trailing spaces, no hard tabs, no multiple blank lines, file ends with one newline.
- No bare URLs in prose; wrap as links or `<https://…>`.
- Unique heading text within a doc (anchors stay stable).
- Images have alt text.

## 9. Final read checklist

- [ ] Title and 1–3 line summary say what the reader gets
- [ ] Headings alone tell the story
- [ ] Each section starts with its point
- [ ] Steps, flows, states, comparisons are diagrams or tables
- [ ] No paragraph longer than 3 lines without a reason
- [ ] Terms consistent; abbreviations defined once
- [ ] Changeable facts carry a date or source
- [ ] Lint passes
