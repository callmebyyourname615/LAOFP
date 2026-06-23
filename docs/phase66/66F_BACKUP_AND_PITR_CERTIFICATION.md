# 66F — Backup and PITR Certification

Run only against isolated UAT recovery targets. Capture backup checksum, restore duration, row-count reconciliation, RPO and RTO.

## Evidence

Store runtime artifacts under `build/phase66-evidence/<run-id>/66F/`. Redact secrets before signing.

## Required commands

Full mode requires `PHASE66_BACKUP_COMMAND`, `PHASE66_VERIFY_BACKUP_COMMAND`, and `PHASE66_RESTORE_COMMAND`. Point these to the backup container or approved automation; the restore command must target an isolated environment.
