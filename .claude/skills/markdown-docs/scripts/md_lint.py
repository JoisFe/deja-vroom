#!/usr/bin/env python3
"""Readability + hygiene lint for Markdown docs. Usage: md_lint.py FILE.md [FILE.md ...]

Prints `LEVEL file:line  rule  message`; exits 1 if any ERROR.
Rules follow markdownlint defaults where they overlap (MD001, MD009, MD012, MD024, MD025, MD034, MD040, MD045).
"""
import re
import sys

MERMAID_TYPES = (
    "flowchart", "graph", "sequenceDiagram", "stateDiagram", "stateDiagram-v2", "classDiagram",
    "erDiagram", "gantt", "timeline", "pie", "xychart-beta", "quadrantChart", "journey", "mindmap",
    "gitGraph", "sankey-beta", "block-beta", "requirementDiagram", "C4Context",
)


def lint(path):
    out = []
    add = lambda lvl, n, rule, msg: out.append((lvl, n, rule, msg))
    lines = open(path, encoding="utf-8").read().split("\n")

    in_code, fence, code_lang, code_start, code_body = False, "", "", 0, []
    h1, prev_level, headings = 0, 0, {}
    para_len, para_start, blank_run = 0, 0, 0
    table_cols, list_depth_warned = None, False
    skip_until = 0
    if lines and lines[0].strip() == "---":
        end = next((i for i, l in enumerate(lines[1:], 2) if l.strip() == "---"), 0)
        skip_until, h1 = end, (1 if end else 0)  # frontmatter docs (skills, agents) have no H1 by design

    for n, raw in enumerate(lines, 1):
        if n <= skip_until:
            continue
        line = raw.rstrip("\n")
        m = re.match(r"^(\s*)(`{3,}|~{3,})(.*)$", line)
        if m or in_code:
            blank_run = 0
        if m:
            if not in_code:
                in_code, fence, code_lang, code_start, code_body = True, m.group(2), m.group(3).strip(), n, []
                if not code_lang:
                    add("WARN", n, "MD040", "code fence without a language")
            elif m.group(2).startswith(fence[0]) and len(m.group(2)) >= len(fence):
                if code_lang.split()[0:1] == ["mermaid"]:
                    check_mermaid(code_body, code_start, add)
                in_code = False
            continue
        if in_code:
            code_body.append(line)
            continue

        if line != line.rstrip() and not line.endswith("  "):
            add("WARN", n, "MD009", "trailing whitespace")
        if "\t" in line:
            add("WARN", n, "MD010", "hard tab")

        if not line.strip():
            blank_run += 1
            if blank_run == 2:
                add("WARN", n, "MD012", "multiple blank lines")
            if para_len > 4:
                add("WARN", para_start, "long-paragraph", f"paragraph of {para_len} lines; split or use a list/table")
            para_len, table_cols = 0, None
            continue
        blank_run = 0

        h = re.match(r"^(#{1,6})\s+(.*)$", line)
        if h:
            level, text = len(h.group(1)), h.group(2).strip()
            if level == 1:
                h1 += 1
                if h1 > 1 and not skip_until:
                    add("ERROR", n, "MD025", "more than one H1")
            if prev_level and level > prev_level + 1:
                add("ERROR", n, "MD001", f"heading jumps H{prev_level} → H{level}")
            prev_level = level
            key = text.lower()
            if key in headings:
                add("WARN", n, "MD024", f"duplicate heading (also line {headings[key]})")
            headings.setdefault(key, n)
            if len(text) > 60:
                add("WARN", n, "heading-length", "heading over 60 chars; make it a short phrase")
            if text.endswith((".", "다.", "요.")):
                add("WARN", n, "heading-sentence", "heading reads like a sentence")
            para_len = 0
            continue

        if line.lstrip().startswith("|"):
            cells = re.split(r"(?<!\\)\|", line.strip().strip("|"))
            cols = len(cells)
            if table_cols is None:
                table_cols = cols
                if cols > 7:
                    add("WARN", n, "table-width", f"{cols} columns; split or transpose")
            elif cols != table_cols and not re.match(r"^\|?\s*:?-", line.strip().strip("|")):
                add("ERROR", n, "table-columns", f"row has {cols} cells, header has {table_cols}")
            for cell in cells:
                if len(cell.strip()) > 140:
                    add("WARN", n, "table-cell", "cell over 140 chars; move detail below the table")
                    break
            continue

        lm = re.match(r"^(\s*)([-*+]|\d+[.)])\s", line)
        if lm:
            depth = len(lm.group(1).replace("\t", "    ")) // 2
            if depth >= 3 and not list_depth_warned:
                add("WARN", n, "list-depth", "list nested more than 2 levels")
                list_depth_warned = True
            continue

        if not line.startswith(">"):
            if para_len == 0:
                para_start = n
            para_len += 1
        if re.search(r"(?<![(<\[`])https?://[^\s)>`]+", re.sub(r"`[^`]*`", "", line)) and not re.search(r"\]\(https?://", line):
            add("WARN", n, "MD034", "bare URL; use [text](url) or <url>")
        if len(re.findall(r"\*\*[^*]+\*\*", line)) > 3:
            add("WARN", n, "bold-overuse", "more than 3 bold spans on one line")
        if re.search(r"!\[\]\(", line):
            add("WARN", n, "MD045", "image without alt text")
        if re.search(r"\[(여기|here|click here|링크)\]\(", line, re.I):
            add("WARN", n, "link-text", "non-descriptive link text")

    if in_code:
        add("ERROR", code_start, "unclosed-fence", "code fence never closed")
    if h1 == 0:
        add("WARN", 1, "MD041", "no H1 title")
    if lines and lines[-1] != "":
        add("WARN", len(lines), "MD047", "file should end with a newline")
    return out


def check_mermaid(body, start, add):
    first = next((l.strip() for l in body if l.strip() and not l.strip().startswith("%%")), "")
    kind = first.split()[0] if first else ""
    if kind not in MERMAID_TYPES:
        add("ERROR", start, "mermaid-type", f"unknown or missing diagram type: '{kind}'")
        return
    nodes = set()
    for i, l in enumerate(body, start + 1):
        if kind in ("flowchart", "graph"):
            for label in re.findall(r"\w+\[([^\]\"]*)\]", l):
                if re.search(r"[()\[\]{}:;]", label):
                    add("ERROR", i, "mermaid-label", f'quote label with special chars: ["{label}"]')
            nodes.update(re.findall(r"\b([A-Za-z]\w*)\s*[\[\(\{]", l))
    if len(nodes) > 15:
        add("WARN", start, "mermaid-size", f"{len(nodes)} nodes; split the diagram (aim ≤ 12)")


def main(paths):
    errors = 0
    for p in paths:
        for lvl, n, rule, msg in lint(p):
            print(f"{lvl:5} {p}:{n}  {rule}  {msg}")
            errors += lvl == "ERROR"
    if not errors:
        print("OK: no errors")
    return 1 if errors else 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    sys.exit(main(sys.argv[1:]))
