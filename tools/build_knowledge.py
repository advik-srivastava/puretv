"""Build app/knowledge.json from a checkout of cleanyourtv's community debloat lists.

    python -I tools/build_knowledge.py <path to cleanyourtv/examples>

For every package it counts how many tested lists disable it, how many keep it (commented out),
and the note those lists give it most often. Reads files only.
Source data: https://github.com/Gathin23/cleanyourtv (MIT), see NOTICE.md.
"""
import json
import re
import sys
from collections import Counter
from pathlib import Path

PACKAGE_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+")


def read_list(text):
    disabled, commented = {}, {}
    for line in text.splitlines():
        line = line.strip()
        target = disabled
        if line.startswith("#"):
            line = line[1:].strip()
            target = commented
        body, _, note = line.partition("#")
        words = body.split()
        if not words or not PACKAGE_RE.fullmatch(words[0]):
            continue
        if target is commented and len(words) != 1:
            continue
        target.setdefault(words[0], note.strip())
    for name in disabled:
        commented.pop(name, None)
    return disabled, commented


def main():
    root = Path(sys.argv[1])
    lists = sorted(root.glob("*/debloat*.txt"))
    disabled, commented, notes = Counter(), Counter(), {}
    for path in lists:
        d, c = read_list(path.read_text(encoding="utf-8-sig", errors="replace"))
        for name, note in d.items():
            disabled[name] += 1
            if note:
                notes.setdefault(name, Counter())[note] += 1
        for name, note in c.items():
            commented[name] += 1
            if note:
                notes.setdefault(name, Counter())[note] += 1
    packages = {}
    for name in sorted(set(disabled) | set(commented)):
        note = notes[name].most_common(1)[0][0] if name in notes else ""
        # Notes are shown in the UI as plain text; keep them short and printable.
        note = re.sub(r"[^\x20-\x7e]", "", note)[:140]
        packages[name] = [disabled[name], commented[name], note]
    out = Path(__file__).resolve().parents[1] / "app" / "knowledge.json"
    out.write_text(json.dumps({"lists": len(lists), "packages": packages}, separators=(",", ":")),
                   encoding="utf-8")
    print(f"{len(lists)} lists, {len(packages)} packages -> {out}")


if __name__ == "__main__":
    main()
