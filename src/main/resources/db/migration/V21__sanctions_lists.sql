-- ============================================================
-- V21 · AML/CFT — Sanctions Lists (P19)
--
--   sanctions_lists — master list of sanctioned entities
--                     (BOL, OFAC, UN sources)
-- ============================================================

CREATE TABLE sanctions_lists (
    list_id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    list_type       VARCHAR(10)  NOT NULL,           -- BOL | OFAC | UN
    entity_name     VARCHAR(500) NOT NULL,
    entity_type     VARCHAR(10)  NOT NULL DEFAULT 'PERSON', -- PERSON | ENTITY
    identifiers     JSONB        NULL,                -- {"dob":"...","nationality":"...","id_numbers":[]}
    added_at        TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    source_ref      VARCHAR(100) NULL,               -- source document / version reference
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_sanctions_list_type  CHECK (list_type  IN ('BOL', 'OFAC', 'UN')),
    CONSTRAINT chk_sanctions_entity_type CHECK (entity_type IN ('PERSON', 'ENTITY'))
);

-- Full-text index on entity_name for fuzzy-matching performance
CREATE INDEX idx_sanctions_entity_name ON sanctions_lists USING gin(to_tsvector('simple', entity_name));
CREATE INDEX idx_sanctions_list_type_active ON sanctions_lists(list_type, is_active);
