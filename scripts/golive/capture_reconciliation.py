#!/usr/bin/env python3
"""Capture deterministic, redacted production reconciliation aggregates in one DB snapshot."""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import pathlib
import re
import subprocess

try:
    import yaml
except ImportError as exc:
    raise SystemExit("PyYAML is required") from exc

NAME_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_-]{0,79}$")
ALLOWED_MODES = {"aggregate-nondecreasing", "balanced", "monotonic", "no-increase", "zero"}


def jdbc_to_uri(value: str) -> str:
    if value.startswith("jdbc:postgresql://"):
        return "postgresql://" + value[len("jdbc:postgresql://"):]
    if value.startswith("postgresql://") or value.startswith("postgres://"):
        return value
    raise ValueError("database URL must be PostgreSQL JDBC or URI")


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def sql_literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--queries", default="config/phase55-reconciliation-queries.yaml")
    ap.add_argument("--output", required=True)
    ap.add_argument("--database-url-env", default="DB_URL")
    ap.add_argument("--database-user-env", default="DB_USERNAME")
    ap.add_argument("--database-password-env", default="DB_PASSWORD")
    ap.add_argument("--label", required=True)
    ap.add_argument("--release-reference", required=True)
    ap.add_argument("--git-commit", required=True)
    ap.add_argument("--statement-timeout-seconds", type=int, default=120)
    ap.add_argument("--lock-timeout-seconds", type=int, default=5)
    args = ap.parse_args()

    if not (1 <= args.statement_timeout_seconds <= 600):
        raise SystemExit("statement timeout must be between 1 and 600 seconds")
    if not (1 <= args.lock_timeout_seconds <= 30):
        raise SystemExit("lock timeout must be between 1 and 30 seconds")

    url = jdbc_to_uri(os.environ.get(args.database_url_env, ""))
    user = os.environ.get(args.database_user_env, "")
    password = os.environ.get(args.database_password_env, "")
    if not url or not user or not password:
        raise SystemExit("database connection environment is incomplete")

    query_path = pathlib.Path(args.queries)
    query_text = query_path.read_text(encoding="utf-8")
    spec = yaml.safe_load(query_text)
    if spec.get("schemaVersion") != 1 or not isinstance(spec.get("queries"), dict) or not spec["queries"]:
        raise SystemExit("invalid reconciliation query specification")

    sql_lines = [
        "\\pset tuples_only on",
        "\\pset format unaligned",
        "\\set ON_ERROR_STOP on",
        "BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;",
        f"SET LOCAL statement_timeout = '{args.statement_timeout_seconds}s';",
        f"SET LOCAL lock_timeout = '{args.lock_timeout_seconds}s';",
    ]
    ordered: list[tuple[str, str]] = []
    for name, query in spec["queries"].items():
        if not NAME_RE.fullmatch(str(name)):
            raise SystemExit(f"invalid reconciliation query name: {name}")
        mode = str(query.get("mode", ""))
        if mode not in ALLOWED_MODES:
            raise SystemExit(f"unsupported reconciliation mode for {name}: {mode}")
        raw_sql = str(query.get("sql", "")).strip().rstrip(";")
        if not raw_sql or "\\" in raw_sql:
            raise SystemExit(f"invalid reconciliation SQL for {name}")
        # One JSON line per named query, all under the same MVCC snapshot.
        sql_lines.append(
            "SELECT json_build_object(" 
            f"'name', {sql_literal(str(name))}, "
            "'rows', COALESCE(json_agg(row_to_json(q)), '[]'::json)"
            f")::text FROM ({raw_sql}) q;"
        )
        ordered.append((str(name), mode))
    sql_lines.append("COMMIT;")

    env = os.environ.copy()
    env.update({"PGUSER": user, "PGPASSWORD": password, "PGAPPNAME": "switching-phase55-reconciliation"})
    process = subprocess.run(
        ["psql", url, "-X", "-q"],
        input="\n".join(sql_lines) + "\n",
        text=True,
        capture_output=True,
        env=env,
        timeout=args.statement_timeout_seconds * max(1, len(ordered)) + 30,
    )
    if process.returncode != 0:
        # Never copy stderr because clients may include connection details.
        raise SystemExit("reconciliation snapshot transaction failed")

    parsed: dict[str, list[dict]] = {}
    for line in process.stdout.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError as exc:
            raise SystemExit("unexpected non-JSON reconciliation output") from exc
        name = row.get("name")
        if name in parsed:
            raise SystemExit(f"duplicate reconciliation output: {name}")
        rows = row.get("rows")
        if not isinstance(rows, list):
            raise SystemExit(f"invalid reconciliation rows: {name}")
        parsed[str(name)] = rows

    expected = {name for name, _ in ordered}
    if set(parsed) != expected:
        raise SystemExit("reconciliation output inventory mismatch")
    results = {
        name: {"mode": mode, "rows": parsed[name], "rowCount": len(parsed[name])}
        for name, mode in ordered
    }
    document = {
        "schemaVersion": 1,
        "label": args.label,
        "capturedAt": dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z"),
        "releaseReference": args.release_reference,
        "gitCommit": args.git_commit,
        "isolationLevel": "REPEATABLE READ",
        "readOnly": True,
        "queryDefinitionSha256": sha256_text(query_text),
        "results": results,
    }
    out = pathlib.Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(out), "queries": len(results), "snapshot": "repeatable-read"}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
