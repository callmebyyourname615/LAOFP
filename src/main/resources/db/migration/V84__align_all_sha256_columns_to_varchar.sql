-- Phase 53B follow-up: align ALL remaining SHA-256 CHAR(64) columns with the
-- JPA String mapping (VARCHAR(64)). V83 covered payload_sha256 in
-- configuration_change_requests + outbox_dead_letters only. JPA Hibernate
-- schema validation fails at startup because every other *_sha256 column
-- created across V45–V82 is still bpchar (Types#CHAR).

SET LOCAL lock_timeout = '15s';
SET LOCAL statement_timeout = '5min';

-- V45 — sanctions_import_runs.content_sha256
ALTER TABLE sanctions_import_runs
    ALTER COLUMN content_sha256 TYPE VARCHAR(64)
    USING rtrim(content_sha256)::VARCHAR(64);

-- V50 — privileged_access_sessions.token_hash
ALTER TABLE privileged_access_sessions
    ALTER COLUMN token_hash TYPE VARCHAR(64)
    USING rtrim(token_hash)::VARCHAR(64);

-- V52 — participant_certifications.evidence_sha256 + git_commit
ALTER TABLE participant_certifications
    ALTER COLUMN evidence_sha256 TYPE VARCHAR(64)
    USING rtrim(evidence_sha256)::VARCHAR(64);
ALTER TABLE participant_certifications
    ALTER COLUMN git_commit TYPE VARCHAR(40)
    USING rtrim(git_commit)::VARCHAR(40);

-- V53 — reconciliation_control_run.evidence_sha256
ALTER TABLE reconciliation_control_run
    ALTER COLUMN evidence_sha256 TYPE VARCHAR(64)
    USING rtrim(evidence_sha256)::VARCHAR(64);

-- V55 — participant_lifecycle_case.evidence_sha256
ALTER TABLE participant_lifecycle_case
    ALTER COLUMN evidence_sha256 TYPE VARCHAR(64)
    USING rtrim(evidence_sha256)::VARCHAR(64);

-- V56 — iso_validation_pack (2 columns)
ALTER TABLE iso_validation_pack
    ALTER COLUMN schema_sha256 TYPE VARCHAR(64)
    USING rtrim(schema_sha256)::VARCHAR(64);
ALTER TABLE iso_validation_pack
    ALTER COLUMN rule_sha256 TYPE VARCHAR(64)
    USING rtrim(rule_sha256)::VARCHAR(64);

-- V57 — settlement_evidence_ledger.source_sha256
ALTER TABLE settlement_evidence_ledger
    ALTER COLUMN source_sha256 TYPE VARCHAR(64)
    USING rtrim(source_sha256)::VARCHAR(64);

-- V58 — ops control room (2 tables)
ALTER TABLE ops_daily_control_room
    ALTER COLUMN close_evidence_sha256 TYPE VARCHAR(64)
    USING rtrim(close_evidence_sha256)::VARCHAR(64);
ALTER TABLE ops_control_room_task
    ALTER COLUMN evidence_sha256 TYPE VARCHAR(64)
    USING rtrim(evidence_sha256)::VARCHAR(64);

-- V61 — privacy_access_case.response_sha256
ALTER TABLE privacy_access_case
    ALTER COLUMN response_sha256 TYPE VARCHAR(64)
    USING rtrim(response_sha256)::VARCHAR(64);

-- V62 — compliance_control_run.evidence_sha256
ALTER TABLE compliance_control_run
    ALTER COLUMN evidence_sha256 TYPE VARCHAR(64)
    USING rtrim(evidence_sha256)::VARCHAR(64);

-- V67 — participant_certificate.fingerprint_sha256
ALTER TABLE participant_certificate
    ALTER COLUMN fingerprint_sha256 TYPE VARCHAR(64)
    USING rtrim(fingerprint_sha256)::VARCHAR(64);

-- V68 — regulatory_report_artifact.sha256
ALTER TABLE regulatory_report_artifact
    ALTER COLUMN sha256 TYPE VARCHAR(64)
    USING rtrim(sha256)::VARCHAR(64);

-- V77 — cryptographic asset / rotation (2 tables)
ALTER TABLE cryptographic_asset_inventory
    ALTER COLUMN fingerprint_sha256 TYPE VARCHAR(64)
    USING rtrim(fingerprint_sha256)::VARCHAR(64);
ALTER TABLE cryptographic_rotation_evidence
    ALTER COLUMN artifact_sha256 TYPE VARCHAR(64)
    USING rtrim(artifact_sha256)::VARCHAR(64);

-- V80 — control evidence catalog / verification
ALTER TABLE control_evidence_catalog
    ALTER COLUMN artifact_sha256 TYPE VARCHAR(64)
    USING rtrim(artifact_sha256)::VARCHAR(64);
ALTER TABLE control_evidence_verification
    ALTER COLUMN observed_sha256 TYPE VARCHAR(64)
    USING rtrim(observed_sha256)::VARCHAR(64);

-- V81 — decision rule version / test execution
ALTER TABLE decision_rule_version
    ALTER COLUMN artifact_sha256 TYPE VARCHAR(64)
    USING rtrim(artifact_sha256)::VARCHAR(64);
ALTER TABLE decision_rule_version
    ALTER COLUMN manifest_sha256 TYPE VARCHAR(64)
    USING rtrim(manifest_sha256)::VARCHAR(64);
ALTER TABLE decision_rule_test_execution
    ALTER COLUMN result_sha256 TYPE VARCHAR(64)
    USING rtrim(result_sha256)::VARCHAR(64);

-- V82 — decommission_data_exit_artifact.artifact_sha256
ALTER TABLE decommission_data_exit_artifact
    ALTER COLUMN artifact_sha256 TYPE VARCHAR(64)
    USING rtrim(artifact_sha256)::VARCHAR(64);

-- Post-conditions: confirm no CHAR-based sha256 columns remain.
DO $v84_postcheck$
DECLARE
    remaining_char_count INTEGER;
BEGIN
    SELECT COUNT(*)
      INTO remaining_char_count
      FROM information_schema.columns
     WHERE table_schema = current_schema()
       AND column_name LIKE '%sha256'
       AND data_type = 'character';

    IF remaining_char_count > 0 THEN
        RAISE EXCEPTION 'V84 post-check failed: % CHAR sha256 columns still remain', remaining_char_count;
    END IF;
END
$v84_postcheck$;
