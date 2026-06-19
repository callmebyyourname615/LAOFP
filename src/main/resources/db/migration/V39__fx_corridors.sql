-- V39: Cross-border Payment — FX corridors (P17)
CREATE TABLE fx_corridors (
    corridor_id      BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_currency  VARCHAR(3)    NOT NULL,
    dest_currency    VARCHAR(3)    NOT NULL,
    target_network   VARCHAR(10)   NOT NULL,
    indicative_rate  DECIMAL(20,8) NOT NULL,
    min_amount       DECIMAL(20,4) NOT NULL DEFAULT 0,
    max_amount       DECIMAL(20,4) NOT NULL DEFAULT 999999999,
    fee_percent      DECIMAL(7,6)  NOT NULL DEFAULT 0.010000,
    fee_fixed        DECIMAL(20,4) NOT NULL DEFAULT 0,
    status           VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP(3)  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_fx_corridor UNIQUE (source_currency, dest_currency, target_network),
    CONSTRAINT chk_fx_network CHECK (target_network IN ('PROMPTPAY','CNAPS','NAPAS','SWIFT')),
    CONSTRAINT chk_fx_status  CHECK (status IN ('ACTIVE','SUSPENDED'))
);

CREATE INDEX idx_fx_corridors_currency ON fx_corridors(source_currency, dest_currency, status);

-- Seed active corridors
INSERT INTO fx_corridors (source_currency, dest_currency, target_network, indicative_rate, min_amount, max_amount, fee_percent, fee_fixed, status) VALUES
  ('LAK', 'THB', 'PROMPTPAY', 0.00180000,   10000,  50000000, 0.010000,   100.0000, 'ACTIVE'),
  ('LAK', 'CNY', 'CNAPS',     0.00013000,   50000, 100000000, 0.015000,    50.0000, 'ACTIVE'),
  ('LAK', 'VND', 'NAPAS',     0.26000000,   10000, 200000000, 0.010000,  5000.0000, 'ACTIVE'),
  ('LAK', 'USD', 'SWIFT',     0.00004800,  100000, 999999999, 0.020000,     1.0000, 'ACTIVE'),
  ('LAK', 'THB', 'SWIFT',     0.00179500, 5000000, 999999999, 0.005000, 10000.0000, 'SUSPENDED');
