CREATE TABLE IF NOT EXISTS governed_data_asset (
    id UUID PRIMARY KEY,
    asset_code VARCHAR(160) NOT NULL UNIQUE,
    asset_type VARCHAR(24) NOT NULL CHECK (asset_type IN ('TABLE','TOPIC','API','FILE','REPORT','DASHBOARD','OBJECT_PREFIX')),
    physical_reference VARCHAR(1000) NOT NULL,
    owner_team VARCHAR(120) NOT NULL,
    classification VARCHAR(24) NOT NULL CHECK (classification IN ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED')),
    retention_policy_code VARCHAR(80),
    contains_pii BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','DEPRECATED','RETIRED')),
    evidence_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS data_lineage_edge (
    id UUID PRIMARY KEY,
    source_asset_id UUID NOT NULL REFERENCES governed_data_asset(id) ON DELETE RESTRICT,
    target_asset_id UUID NOT NULL REFERENCES governed_data_asset(id) ON DELETE RESTRICT,
    transformation_code VARCHAR(160) NOT NULL,
    transformation_version VARCHAR(80) NOT NULL,
    processing_purpose VARCHAR(500) NOT NULL,
    field_mapping_hash CHAR(64),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','DEPRECATED')),
    approved_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(source_asset_id,target_asset_id,transformation_code,transformation_version),
    CHECK (source_asset_id <> target_asset_id)
);
CREATE TABLE IF NOT EXISTS control_evidence_catalog (
    id UUID PRIMARY KEY,
    control_code VARCHAR(80) NOT NULL,
    evidence_period_start TIMESTAMPTZ NOT NULL,
    evidence_period_end TIMESTAMPTZ NOT NULL,
    artifact_reference VARCHAR(1500) NOT NULL,
    artifact_sha256 CHAR(64) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    producer VARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('REGISTERED','VERIFIED','REJECTED','SUPERSEDED')),
    sealed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (evidence_period_end >= evidence_period_start),
    UNIQUE(control_code,evidence_period_start,evidence_period_end,artifact_sha256)
);
CREATE TABLE IF NOT EXISTS control_evidence_verification (
    id UUID PRIMARY KEY,
    evidence_id UUID NOT NULL REFERENCES control_evidence_catalog(id),
    verifier VARCHAR(120) NOT NULL,
    decision VARCHAR(16) NOT NULL CHECK (decision IN ('VERIFIED','REJECTED')),
    observed_sha256 CHAR(64) NOT NULL,
    comment VARCHAR(1000),
    verified_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE OR REPLACE FUNCTION prevent_sealed_evidence_mutation() RETURNS trigger AS $$
BEGIN
    IF OLD.sealed_at IS NOT NULL AND (NEW.artifact_reference<>OLD.artifact_reference OR NEW.artifact_sha256<>OLD.artifact_sha256 OR NEW.size_bytes<>OLD.size_bytes) THEN
        RAISE EXCEPTION 'sealed control evidence is immutable';
    END IF;
    RETURN NEW;
END; $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_control_evidence_immutable ON control_evidence_catalog;
CREATE TRIGGER trg_control_evidence_immutable BEFORE UPDATE ON control_evidence_catalog
FOR EACH ROW EXECUTE FUNCTION prevent_sealed_evidence_mutation();
CREATE OR REPLACE FUNCTION prevent_data_lineage_cycle() RETURNS trigger AS $$
DECLARE cycle_found BOOLEAN;
BEGIN
    WITH RECURSIVE descendants(asset_id) AS (
        SELECT target_asset_id FROM data_lineage_edge
         WHERE source_asset_id=NEW.target_asset_id AND status='ACTIVE' AND id<>NEW.id
        UNION
        SELECT e.target_asset_id FROM data_lineage_edge e
          JOIN descendants d ON e.source_asset_id=d.asset_id
         WHERE e.status='ACTIVE' AND e.id<>NEW.id
    )
    SELECT EXISTS(SELECT 1 FROM descendants WHERE asset_id=NEW.source_asset_id) INTO cycle_found;
    IF NEW.source_asset_id=NEW.target_asset_id OR cycle_found THEN
        RAISE EXCEPTION 'data lineage edge creates a cycle';
    END IF;
    RETURN NEW;
END; $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_data_lineage_no_cycle ON data_lineage_edge;
CREATE TRIGGER trg_data_lineage_no_cycle BEFORE INSERT OR UPDATE OF source_asset_id,target_asset_id,status ON data_lineage_edge
FOR EACH ROW WHEN (NEW.status='ACTIVE') EXECUTE FUNCTION prevent_data_lineage_cycle();
