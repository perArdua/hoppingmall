#!/usr/bin/env python3
import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]

ENUM_HEADER = re.compile(r"enum\s+class\s+(\w+)\s*\(")
ENTRY_NAME = re.compile(r"([A-Z][A-Z0-9_]*)\s*\(")
NAMED_CODE = re.compile(r"\bcode\s*=\s*\"((?:[^\"\\]|\\.)*)\"")
STRING = re.compile(r"\"((?:[^\"\\]|\\.)*)\"")


def skip_string(text, i):
    i += 1
    while i < len(text):
        if text[i] == "\\":
            i += 2
            continue
        if text[i] == '"':
            return i + 1
        i += 1
    return i


def matching(text, i, open_ch, close_ch):
    depth = 0
    while i < len(text):
        ch = text[i]
        if ch == '"':
            i = skip_string(text, i)
            continue
        if ch == open_ch:
            depth += 1
        elif ch == close_ch:
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def entries(body):
    i = 0
    depth = 0
    while i < len(body):
        ch = body[i]
        if ch == '"':
            i = skip_string(body, i)
            continue
        if ch == ";" and depth == 0:
            return
        if ch in "({":
            depth += 1
        elif ch in ")}":
            depth -= 1
        if depth == 0:
            m = ENTRY_NAME.match(body, i)
            if m and (i == 0 or not (body[i - 1].isalnum() or body[i - 1] == "_")):
                close = matching(body, m.end() - 1, "(", ")")
                args = body[m.end():close]
                named = NAMED_CODE.search(args)
                literal = named or STRING.search(args)
                if literal:
                    yield m.group(1), literal.group(1), body.count("\n", 0, i)
                i = close + 1
                continue
        i += 1


def error_code_enums(path):
    text = path.read_text(encoding="utf-8")
    for m in ENUM_HEADER.finditer(text):
        params_close = matching(text, m.end() - 1, "(", ")")
        brace = text.find("{", params_close)
        if brace < 0:
            continue
        supertypes = text[params_close + 1:brace]
        if not re.search(r":.*\bErrorCode\b", supertypes, re.S):
            continue
        body_close = matching(text, brace, "{", "}")
        body = text[brace + 1:body_close]
        base_line = text.count("\n", 0, brace + 1) + 1
        for name, code, offset in entries(body):
            yield m.group(1), name, code, base_line + offset


def main():
    owners = defaultdict(list)
    enum_count = 0
    for path in sorted(ROOT.glob("*/src/main/**/*.kt")):
        if "/build/" in path.as_posix():
            continue
        seen_enums = set()
        for enum_name, name, code, line in error_code_enums(path):
            seen_enums.add(enum_name)
            owners[code].append(f"{path.relative_to(ROOT).as_posix()}:{line} {enum_name}.{name}")
        enum_count += len(seen_enums)

    duplicates = {code: where for code, where in owners.items() if len(where) > 1}
    print(f"ErrorCode enum {enum_count}개, 코드 {sum(len(v) for v in owners.values())}개 검사")
    if not duplicates:
        print("겹치는 오류 코드 없음")
        return 0
    for code in sorted(duplicates):
        print(f"::error::오류 코드 {code}가 {len(duplicates[code])}곳에서 쓰인다")
        for where in duplicates[code]:
            print(f"  {where}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
