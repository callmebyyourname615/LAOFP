#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import tempfile
from pathlib import Path

INSTANT_DECLARATION = re.compile(r"\bInstant\s+([A-Za-z_$][\w$]*)")
SET_OBJECT = re.compile(r"\bsetObject\s*\(", re.MULTILINE)


def iter_calls(text: str):
    for match in SET_OBJECT.finditer(text):
        depth = 1
        index = match.end()
        quote = None
        escaped = False
        while index < len(text) and depth:
            char = text[index]
            if quote:
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == quote:
                    quote = None
            elif char in {'"', "'"}:
                quote = char
            elif char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
            index += 1
        if depth == 0:
            yield match.start(), text[match.end() : index - 1]


def split_arguments(body: str) -> list[str]:
    arguments: list[str] = []
    start = 0
    depth = 0
    quote = None
    escaped = False
    for index, char in enumerate(body):
        if quote:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
        elif char in {'"', "'"}:
            quote = char
        elif char in "([{":
            depth += 1
        elif char in ")]}" :
            depth -= 1
        elif char == "," and depth == 0:
            arguments.append(body[start:index].strip())
            start = index + 1
    arguments.append(body[start:].strip())
    return arguments


def scan_file(path: Path) -> list[dict[str, object]]:
    text = path.read_text(encoding="utf-8", errors="ignore")
    instant_names = set(INSTANT_DECLARATION.findall(text))
    violations: list[dict[str, object]] = []
    for offset, body in iter_calls(text):
        arguments = split_arguments(body)
        if len(arguments) < 2:
            continue
        value = arguments[1]
        is_instant = "Instant.now(" in value or any(
            re.search(rf"\b{re.escape(name)}\b", value) for name in instant_names
        )
        typed = len(arguments) >= 3 and "Types.TIMESTAMP_WITH_TIMEZONE" in arguments[2]
        if is_instant and not typed:
            violations.append(
                {
                    "path": str(path),
                    "line": text.count("\n", 0, offset) + 1,
                    "call": "setObject(" + body.strip() + ")",
                }
            )
    return violations


def scan(root: Path) -> list[dict[str, object]]:
    candidates: list[Path] = []
    for base in [
        root / "src/main/java/com/example/switching/crossborder",
        root / "src/main/java/com/example/switching/phaseii",
    ]:
        if base.exists():
            candidates.extend(base.rglob("*.java"))
    violations: list[dict[str, object]] = []
    for path in sorted(set(candidates)):
        violations.extend(scan_file(path))
    return violations


def self_test() -> bool:
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        base = root / "src/main/java/com/example/switching/crossborder"
        base.mkdir(parents=True)
        fixture = base / "Fixture.java"
        fixture.write_text(
            "import java.time.Instant; class Fixture { void x(java.sql.PreparedStatement p, Instant instant) throws Exception { p.setObject(1, instant); }}",
            encoding="utf-8",
        )
        if len(scan(root)) != 1:
            return False
        fixture.write_text(
            "import java.time.Instant; import java.sql.Types; class Fixture { void x(java.sql.PreparedStatement p, Instant instant) throws Exception { p.setObject(1, instant, Types.TIMESTAMP_WITH_TIMEZONE); }}",
            encoding="utf-8",
        )
        return len(scan(root)) == 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--json-output")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        passed = self_test()
        print("PASS" if passed else "FAIL")
        return 0 if passed else 1
    violations = scan(Path(args.root).resolve())
    payload = {"schemaVersion": 1, "violations": violations, "passed": not violations}
    if args.json_output:
        output = Path(args.json_output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if violations:
        for violation in violations:
            print(f"{violation['path']}:{violation['line']}: untyped Instant JDBC binding")
        return 1
    print("PASS: no untyped Instant JDBC bindings in cross-border source")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
