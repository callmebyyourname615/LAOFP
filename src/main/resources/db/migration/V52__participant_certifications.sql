CREATE TABLE participant_certifications (
    id BIGSERIAL PRIMARY KEY,
    certification_ref VARCHAR(64) NOT NULL UNIQUE,
    bank_code VARCHAR(32) NOT NULL,
    suite_version VARCHAR(64) NOT NULL,
    git_commit CHAR(40) NOT NULL,
    image_digest VARCHAR(71) NOT NULL,
    evidence_sha256 CHAR(64) NOT NULL UNIQUE,
    result VARCHAR(16) NOT NULL,
    executed_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    recorded_by VARCHAR(160) NOT NULL,
    recorded_at TIMESTAMP NOT NULL,
    details_json TEXT,
    CONSTRAINT fk_participant_certification_bank FOREIGN KEY (bank_code) REFERENCES participants(bank_code),
    CONSTRAINT ck_participant_certification_result CHECK (result IN ('PASS','FAIL')),
    CONSTRAINT ck_participant_certification_commit CHECK (git_commit ~ '^[a-f0-9]{40}$'),
    CONSTRAINT ck_participant_certification_digest CHECK (image_digest ~ '^sha256:[a-f0-9]{64}$'),
    CONSTRAINT ck_participant_certification_dates CHECK (expires_at > executed_at)
);
CREATE INDEX idx_participant_certification_current ON participant_certifications(bank_code, result, expires_at DESC);
