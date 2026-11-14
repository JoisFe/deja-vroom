# Visuals: tables, Mermaid diagrams, charts

Use a visual when the reader would otherwise have to hold structure in their head. Mermaid renders natively on GitHub, GitLab, and most Markdown viewers (VS Code with the Mermaid extension, Obsidian, Notion import).

## Choosing the form

```mermaid
flowchart TD
    A[What are you showing?] --> B{Order or branching?}
    B -- Steps / decisions --> F[flowchart]
    B -- Messages between parts --> S[sequenceDiagram]
    B -- No --> C{Changes over time?}
    C -- States of one thing --> ST[stateDiagram-v2]
    C -- Dates / milestones --> G[gantt or timeline]
    C -- No --> D{Numbers?}
    D -- Share of a whole --> P[pie]
    D -- Trend / comparison --> X[xychart-beta or table]
    D -- No --> T[Table]
```

## Rules for every diagram

- One sentence before it: what the reader should notice.
- ≤ ~12 nodes and ≤ 2 crossing levels. Split big diagrams by phase.
- Short labels (≤ 5 words). Details go in text or a table below.
- Quote labels that contain spaces plus special characters, parentheses, or Korean punctuation: `A["설치 (선택)"]`.
- Top-down (`TD`) for processes, left-right (`LR`) for pipelines and timelines.
- No styling or colors unless they encode meaning; diagrams must read in light and dark themes.
- Don't use a diagram for a simple list of 3 things.

## Templates

### Flow / decision

```mermaid
flowchart LR
    A[Check capability] --> B{AccessType}
    B -- READ / READ_WRITE --> C[Get value]
    B -- NONE / WRITE --> D[Disable UI]
    C --> E[Register callback]
```

### Sequence

```mermaid
sequenceDiagram
    participant App
    participant SDK as Vehicle SDK
    App->>SDK: initialize()
    App->>SDK: checkDoorPositionCapabilities()
    SDK-->>App: onComplete(config, areaMap)
    App->>SDK: getCurrentDoorPosition(area)
    SDK-->>App: onComplete(value)
```

### State

```mermaid
stateDiagram-v2
    [*] --> Parked
    Parked --> Driving: gear D
    Driving --> Parked: gear P
    Driving --> Restricted: UX restriction on
    Restricted --> Driving: restriction off
```

### Schedule

```mermaid
gantt
    dateFormat YYYY-MM-DD
    title Contest timeline
    section Round 1
    Application window :a1, 2026-10-21, 2026-11-15
    Results            :milestone, 2026-11-23, 0d
    section Round 2
    Build demo         :2026-11-23, 2026-12-02
    Final PT           :2026-12-03, 2d
```

`timeline` is lighter when there are no durations:

```mermaid
timeline
    2026-10-21 : Applications open
    2026-11-15 : Deadline
    2026-12-03 : Final PT
```

### Data model

```mermaid
erDiagram
    TEAM ||--|{ MEMBER : has
    TEAM ||--|| SUBMISSION : sends
```

### Numbers

```mermaid
pie title Effort split
    "Planning" : 30
    "App" : 50
    "Demo video" : 20
```

```mermaid
xychart-beta
    title "CPU limit by state (%)"
    x-axis ["Nav driving", "Other driving", "Parked"]
    y-axis "CPU %" 0 --> 30
    bar [15, 5, 25]
```

### Priority

```mermaid
quadrantChart
    title Feature priority
    x-axis Low effort --> High effort
    y-axis Low impact --> High impact
    Voice memo: [0.3, 0.8]
    Seat memory: [0.7, 0.4]
```

## Tables vs diagrams

| Situation | Prefer |
|---|---|
| Exact values the reader will look up | Table |
| Relationships, order, branching | Diagram |
| Both | Diagram for the shape, table for the values |

## Compatibility notes

- `xychart-beta`, `quadrantChart`, `timeline` need a recent Mermaid (GitHub supports them). If the target viewer is old, fall back to a table.
- Optional render check when Node.js is available: `npx -y @mermaid-js/mermaid-cli -i doc.md -o /tmp/out.md` (downloads the CLI; ask before running in restricted environments).
- For architecture pictures that Mermaid can't express cleanly, an ASCII tree in a `text` block is acceptable for directory layouts only.
