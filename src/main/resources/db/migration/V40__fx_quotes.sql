-- V40: Cross-border Payment — FX quotes (P17)
CREATE TABLE fx_quotes (
    quote_id        BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    corridor_id     BIGINT        NOT NULL REFERENCES fx_corridors(corridor_id),
    source_currency VARCHAR(3)    NOT NULL,
    dest_currency   VARCHAR(3)    NOT NULL,
    source_amount   DECIMAL(20,4) NOT NULL,
    dest_amount     DECIMAL(20,4) NOT NULL,
    rate            DECIMAL(20,8) NOT NULL,
    fee             DECIMAL(20,4) NOT NULL,
    issued_at       TIMESTAMP(3)  NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP(3)  NOT NULL,
    used            BOOLEAN       NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_fx_quote_amount CHECK (source_amount > 0)
);

CREATE INDEX idx_fx_quotes_corridor ON fx_quotes(corridor_id);
CREATE INDEX idx_fx_quotes_expires  ON fx_quotes(expires_at) WHERE NOT used;
