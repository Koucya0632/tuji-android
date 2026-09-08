#!/usr/bin/env python3
"""
Pull en / ja / zh-Hans for Android strings that already exist on iOS.

iOS's catalogue is keyed by its **source text** — `sourceLanguage` is zh-Hant,
so the key *is* the Chinese sentence. Android keys are identifiers, so the join
is on the zh-Hant value, not on the name.

Placeholders differ and have to be rewritten in both directions:

    iOS      %@   %lld            (positional only by order)
    Android  %1$s %1$d            (explicitly numbered)

Emits JSON on stdout: {android_name: {"zh-Hant": …, "en": …, "ja": …, "zh-Hans": …}}
so the generator downstream never has to know about xcstrings.
"""
import json, re, sys, xml.etree.ElementTree as ET

IOS = "/Users/rex/Desktop/tuji/tuji-ios/Tuji/Resources/i18n/Localizable.xcstrings"
ANDROID = "app/src/main/res/values/strings.xml"
LANGS = ("en", "ja", "zh-Hans")

PLACEHOLDER = re.compile(r"%\d\$[sd]|%[@sd]|%lld")


def skeleton(text: str) -> str:
    """The text with every placeholder replaced by one marker, for matching."""
    return PLACEHOLDER.sub("\x00", text or "").strip()


def ios_to_android(text: str, template: str) -> str:
    """
    Rewrite iOS placeholders into Android's numbered form.

    The numbering comes from the **Android** string, because that is the one the
    format call has to match — an iOS `%@ %lld` maps onto whatever `%1$s %2$d`
    the Android original used, in order.
    """
    order = PLACEHOLDER.findall(template or "")
    out, i = [], 0
    for piece in re.split(r"(%@|%lld|%d|%s)", text or ""):
        if piece in ("%@", "%lld", "%d", "%s"):
            out.append(order[i] if i < len(order) else piece)
            i += 1
        else:
            out.append(piece)
    return "".join(out)


def main() -> int:
    ios = json.load(open(IOS))["strings"]
    by_skeleton = {}
    for key, entry in ios.items():
        by_skeleton.setdefault(skeleton(key), (key, entry))

    out = {}
    for element in ET.parse(ANDROID).getroot().findall("string"):
        name = element.get("name")
        zh = element.text or ""
        row = {"zh-Hant": zh}
        found = by_skeleton.get(skeleton(zh))
        if found:
            _, entry = found
            for lang in LANGS:
                unit = ((entry.get("localizations") or {}).get(lang) or {}).get("stringUnit")
                if unit and unit.get("value"):
                    row[lang] = ios_to_android(unit["value"], zh)
        out[name] = row

    json.dump(out, sys.stdout, ensure_ascii=False, indent=1)
    return 0


if __name__ == "__main__":
    sys.exit(main())
