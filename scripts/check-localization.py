#!/usr/bin/env python3
"""
A missing or malformed translation is a red build, not a warning.

Three things are checked, and each one is a crash or a visibly wrong screen
rather than a matter of polish:

1. **Every string exists in every locale.** A missing one silently falls back to
   `values/`, so a Japanese user sees English and nothing says why.
2. **The placeholders match.** `String.format` throws on a missing argument and
   silently mis-orders on a swapped one — `%1$d / %2$d` translated as
   `%2$d / %1$d` shows the goal as the progress.
3. **No locale is missing entirely.**
4. **No string leans on leading or trailing whitespace.** Android strips it
   from an unquoted resource, so `"Good evening, "` becomes `"Good evening,"`
   and the name runs straight into the comma. A format string is the fix, so
   this fails rather than warns.

iOS has the same gate for the same reason: 漏譯 became a red CI signal there
after it shipped once.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RES = Path("app/src/main/res")
DEFAULT = "values"
LOCALES = ["values-zh-rTW", "values-ja", "values-zh-rCN"]
PLACEHOLDER = re.compile(r"%(\d+)\$[sd]|%[sd]")


def read(folder: str) -> dict[str, str]:
    path = RES / folder / "strings.xml"
    if not path.exists():
        return {}
    return {
        e.get("name"): "".join(e.itertext())
        for e in ET.parse(path).getroot().findall("string")
    }


def placeholders(text: str) -> set[str]:
    """The set of argument slots, so order may change but the arguments may not."""
    return {m.group(0) for m in PLACEHOLDER.finditer(text or "")}


def main() -> int:
    base = read(DEFAULT)
    for name, value in sorted(base.items()):
        if value != value.strip():
            print(f"FAIL: {DEFAULT}/{name} has leading or trailing whitespace")
            return 1
    if not base:
        print(f"FAIL: {DEFAULT}/strings.xml is missing or empty")
        return 1

    problems: list[str] = []
    for locale in LOCALES:
        rows = read(locale)
        if not rows:
            problems.append(f"{locale}: the whole locale is missing")
            continue
        missing = sorted(set(base) - set(rows))
        if missing:
            problems.append(
                f"{locale}: {len(missing)} untranslated — "
                + ", ".join(missing[:8])
                + (" …" if len(missing) > 8 else "")
            )
        extra = sorted(set(rows) - set(base))
        if extra:
            problems.append(f"{locale}: {len(extra)} strings nothing uses — " + ", ".join(extra[:8]))
        for name, value in sorted(rows.items()):
            if value != value.strip():
                problems.append(
                    f"{locale}/{name}: leading or trailing whitespace — Android strips it; "
                    "use a format string instead of concatenating"
                )
        for name in sorted(set(base) & set(rows)):
            want, got = placeholders(base[name]), placeholders(rows[name])
            if want != got:
                problems.append(
                    f"{locale}/{name}: placeholders differ — "
                    f"{DEFAULT} has {sorted(want) or 'none'}, this has {sorted(got) or 'none'}"
                )

    if problems:
        print(f"FAIL: {len(problems)} localisation problem(s)\n")
        for p in problems:
            print("  " + p)
        return 1

    print(f"localisation OK: {len(base)} strings × {len(LOCALES) + 1} locales")
    return 0


if __name__ == "__main__":
    sys.exit(main())
