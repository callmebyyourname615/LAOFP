#!/usr/bin/env python3
"""Static acceptance checks for Phase 8 backup/PITR implementation."""
from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]


def fail(message: str) -> None:
    raise AssertionError(message)


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        fail(f"required file missing: {relative}")
    return path.read_text(encoding="utf-8")


def require(relative: str, *tokens: str) -> str:
    content = read(relative)
    for token in tokens:
        if token not in content:
            fail(f"{relative} missing token: {token}")
    return content


def parse_yaml(relative: str) -> None:
    try:
        docs = list(yaml.safe_load_all(read(relative)))
    except yaml.YAMLError as exc:
        fail(f"invalid YAML in {relative}: {exc}")
    if not any(doc for doc in docs):
        fail(f"no YAML documents in {relative}")


def main() -> int:
    dockerfile = require(
        "backup/Dockerfile",
        "FROM postgres:16-bookworm",
        "age",
        "awscli",
        "USER postgres",
        "ENTRYPOINT",
    )
    if "USER root" in dockerfile:
        fail("backup image must end as non-root postgres user")

    full = require(
        "backup/bin/full-backup.sh",
        "pg_basebackup",
        "pg_verifybackup",
        "age --encrypt",
        "sha256sum",
        "s3_upload_required_targets",
        "latest.json",
    )
    if "s3_upload_required_targets \"$base_dir\"" in full:
        fail("unencrypted base directory must never be uploaded")

    require(
        "backup/bin/wal-receiver.sh",
        "pg_receivewal",
        "pg_create_physical_replication_slot",
        "--synchronous",
    )
    wal = require(
        "backup/bin/wal-uploader.sh",
        "age --encrypt",
        "sha256sum",
        "s3_upload_required_targets",
        "rm -f \"$path\"",
    )
    if wal.index("s3_upload_required_targets") > wal.index('rm -f "$path"'):
        fail("WAL source appears to be removed before required uploads")

    require(
        "backup/bin/restore-basebackup.sh",
        "s3_download_with_fallback",
        "sha256sum",
        "age --decrypt",
        "pg_verifybackup",
        "recovery.signal",
        "recovery_target_time",
    )
    require(
        "backup/bin/restore-wal.sh",
        "s3_download_with_fallback",
        "WAL checksum mismatch",
        "age --decrypt",
    )
    require(
        "docs/runbooks/templates/BACKUP_RESTORE_DRILL_SIGNOFF.md",
        "RPO",
        "RTO",
        "pg_verifybackup",
        "production go-live gate remains closed",
    )
    require(
        "backup/test/integration-smoke.sh",
        "postgres:16",
        "minio/minio:RELEASE.2025-04-22T22-12-26Z",
        "full-backup.sh",
        "restore-drill.sh",
    )
    require(
        "backup/bin/restore-drill.sh",
        "restore-basebackup.sh",
        "restore-verification.sql",
        "rtoSeconds",
        "verification\": \"PASS",
        "s3_upload_required_targets",
    )

    s3 = require(
        "backup/bin/s3.sh",
        "SECONDARY_S3_ENABLED",
        "SECONDARY_S3_BUCKET",
        "--endpoint-url",
        "--ca-bundle",
    )
    if "--no-verify-ssl" in s3:
        fail("S3 TLS verification may not be disabled")

    lifecycle = json.loads(read("backup/config/s3-lifecycle.json"))
    prefixes = {rule.get("Filter", {}).get("Prefix") for rule in lifecycle["Rules"]}
    if not {"switching/base/", "switching/wal/"}.issubset(prefixes):
        fail("retention policy must cover both base and WAL objects")

    external = require(
        "k8s/external-secrets/backup-secrets.yaml",
        "switching-backup-secrets",
        "switching-restore-secrets",
        "age-identity.txt",
        "switching/prod/restore",
    )
    if "BACKUP_AGE_IDENTITY" in external.split("switching-backup-secrets", 1)[1].split("---", 1)[0]:
        fail("backup Secret must not receive the private age identity")

    restore_cron = require(
        "k8s/backup/restore-drill-cronjob.yaml",
        "suspend: true",
        "ephemeral:",
        "switching-restore-secrets",
        "readOnlyRootFilesystem: true",
    )
    if "hostPath:" in restore_cron:
        fail("restore drill must not use hostPath")

    for relative in (
        "k8s/external-secrets/backup-secrets.yaml",
        "k8s/backup/serviceaccount.yaml",
        "k8s/backup/configmap.yaml",
        "k8s/backup/full-backup-cronjob.yaml",
        "k8s/backup/wal-archiver.yaml",
        "k8s/backup/restore-drill-cronjob.yaml",
        "k8s/backup/maintenance-cronjobs.yaml",
        "k8s/backup/networkpolicy.yaml",
        "k8s/backup/kustomization.yaml",
        "monitoring/prometheus/backup-rules.yaml",
        ".github/workflows/backup-image.yml",
        ".github/workflows/backup-deploy.yml",
        "docker-compose.yml",
    ):
        parse_yaml(relative)

    json.loads(read("monitoring/grafana/dashboards/switching-backup-pitr.json"))

    rules = require(
        "monitoring/prometheus/backup-rules.yaml",
        "SwitchingBaseBackupStale",
        "SwitchingWalArchiveStale",
        "SwitchingBackupCrossRegionCopyFailed",
        "SwitchingRestoreDrillOverdue",
        "SwitchingRestoreDrillFailedOrMissedRto",
    )
    if rules.count("alert:") < 7:
        fail("expected at least seven backup/PITR alert rules")

    compose = require(
        "docker-compose.yml",
        "backup-full:",
        "backup-wal:",
        "switching-backups",
        "max_slot_wal_keep_size=20GB",
    )
    if "BACKUP_AGE_IDENTITY" in compose:
        fail("normal UAT backup services must not receive the restore private key")

    renderer = require(
        "scripts/render_k8s_image.sh",
        "switching-(api|backup)",
        "REPLACE_WITH_BACKUP_IMAGE_DIGEST",
        "sha256:[a-f0-9]{64}",
    )
    fake_digest = "sha256:" + "a" * 64
    out = ROOT / "build" / "phase8-static" / "backup.yaml"
    out.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            str(ROOT / "scripts/render_k8s_image.sh"),
            str(ROOT / "k8s/backup/full-backup-cronjob.yaml"),
            str(out),
            "ghcr.io/example/switching-backup",
            fake_digest,
        ],
        check=True,
    )
    rendered = out.read_text()
    if fake_digest not in rendered or "REPLACE_WITH_BACKUP_IMAGE_DIGEST" in rendered:
        fail("backup image renderer did not produce a digest-pinned manifest")

    for script in sorted((ROOT / "backup/bin").glob("*.sh")) + sorted((ROOT / "backup/test").glob("*.sh")) + sorted((ROOT / "scripts").glob("*.sh")):
        subprocess.run(["bash", "-n", str(script)], check=True)

    private_key_pattern = re.compile(r"AGE-SECRET-KEY-[A-Z0-9]+")
    for path in [ROOT / "backup", ROOT / "k8s", ROOT / "docs"]:
        for file in path.rglob("*"):
            if file.is_file() and private_key_pattern.search(file.read_text(encoding="utf-8", errors="ignore")):
                fail(f"private age identity committed in {file.relative_to(ROOT)}")

    print("Phase 08 static acceptance checks: PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, subprocess.CalledProcessError, json.JSONDecodeError) as exc:
        print(f"Phase 08 static acceptance checks: FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
