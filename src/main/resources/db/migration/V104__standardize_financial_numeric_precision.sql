-- Phase 62E: standardise monetary amounts to NUMERIC(24,4).
--
-- FX rates, percentages, scores and operational metrics deliberately retain their
-- domain-specific precision.  The helper is idempotent and only touches columns
-- that exist in the target installation, allowing upgrades from older supported
-- baselines while keeping a single migration artifact.

CREATE OR REPLACE FUNCTION phase62_alter_money_column(p_table TEXT, p_column TEXT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    qualified_table TEXT := format('%I.%I', current_schema(), p_table);
    generated_flag TEXT;
BEGIN
    IF to_regclass(qualified_table) IS NULL THEN
        RETURN;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = current_schema()
           AND table_name = p_table
           AND column_name = p_column
    ) THEN
        RETURN;
    END IF;

    SELECT c.is_generated INTO generated_flag
      FROM information_schema.columns c
     WHERE c.table_schema = current_schema()
       AND c.table_name = p_table
       AND c.column_name = p_column;
    IF generated_flag = 'ALWAYS' THEN
        RAISE NOTICE 'Skipping generated money column %.%', p_table, p_column;
        RETURN;
    END IF;

    EXECUTE format(
        'ALTER TABLE %I ALTER COLUMN %I TYPE NUMERIC(24,4) USING %I::NUMERIC(24,4)',
        p_table, p_column, p_column);
END;
$$;

-- Generated balance/net columns must be recreated at the wider scale before
-- their source columns are altered. Neither column participates in a key.
DO $$
BEGIN
    IF to_regclass(format('%I.%I', current_schema(), 'settlement_positions')) IS NOT NULL THEN
        ALTER TABLE settlement_positions DROP COLUMN IF EXISTS net_position;
    END IF;
    IF to_regclass(format('%I.%I', current_schema(), 'psp_pools')) IS NOT NULL THEN
        ALTER TABLE psp_pools DROP COLUMN IF EXISTS available_balance;
    END IF;
END;
$$;

SELECT phase62_alter_money_column('participants', 'max_daily_limit');
SELECT phase62_alter_money_column('participants', 'max_single_txn');
SELECT phase62_alter_money_column('participant_limits', 'limit_value');
SELECT phase62_alter_money_column('payment_flows', 'amount');
SELECT phase62_alter_money_column('transactions', 'amount');
SELECT phase62_alter_money_column('transaction_lookup', 'amount');
SELECT phase62_alter_money_column('hourly_transaction_summary', 'total_amount');
SELECT phase62_alter_money_column('hourly_transaction_summary', 'settled_amount');
SELECT phase62_alter_money_column('daily_transaction_summary', 'total_amount');
SELECT phase62_alter_money_column('daily_transaction_summary', 'settled_amount');
SELECT phase62_alter_money_column('daily_transaction_summary', 'net_amount');
SELECT phase62_alter_money_column('settlement_positions', 'debit_amount');
SELECT phase62_alter_money_column('settlement_positions', 'credit_amount');
SELECT phase62_alter_money_column('settlement_items', 'amount');
SELECT phase62_alter_money_column('reconciliation_items', 'amount');
SELECT phase62_alter_money_column('settlement_instructions', 'net_amount');
SELECT phase62_alter_money_column('settlement_evidence_ledger', 'amount');
SELECT phase62_alter_money_column('reconciliation_control_run', 'expected_amount');
SELECT phase62_alter_money_column('reconciliation_control_run', 'actual_amount');
SELECT phase62_alter_money_column('fraud_velocity_rule', 'max_amount');
SELECT phase62_alter_money_column('fraud_scores', 'amount');
SELECT phase62_alter_money_column('velocity_checks', 'current_value');
SELECT phase62_alter_money_column('velocity_checks', 'limit_value');
SELECT phase62_alter_money_column('psp_pools', 'balance');
SELECT phase62_alter_money_column('psp_pools', 'held_amount');
SELECT phase62_alter_money_column('psp_pools', 'minimum_balance');
SELECT phase62_alter_money_column('pool_transactions', 'amount');
SELECT phase62_alter_money_column('pool_transactions', 'balance_before');
SELECT phase62_alter_money_column('pool_transactions', 'held_before');
SELECT phase62_alter_money_column('pool_transactions', 'balance_after');
SELECT phase62_alter_money_column('pool_transactions', 'held_after');
SELECT phase62_alter_money_column('fx_quotes', 'source_amount');
SELECT phase62_alter_money_column('fx_quotes', 'dest_amount');
SELECT phase62_alter_money_column('fx_quotes', 'fee');
SELECT phase62_alter_money_column('fx_corridors', 'min_amount');
SELECT phase62_alter_money_column('fx_corridors', 'max_amount');
SELECT phase62_alter_money_column('fx_corridors', 'fee_fixed');
SELECT phase62_alter_money_column('bill_tokens', 'bill_amount');
SELECT phase62_alter_money_column('bill_payments', 'amount');
SELECT phase62_alter_money_column('disputes', 'amount');
SELECT phase62_alter_money_column('qr_codes', 'amount');
SELECT phase62_alter_money_column('synthetic_probe_definition', 'maximum_amount');

DO $$
BEGIN
    IF to_regclass(format('%I.%I', current_schema(), 'settlement_positions')) IS NOT NULL THEN
        ALTER TABLE settlement_positions ADD COLUMN net_position NUMERIC(24,4)
            GENERATED ALWAYS AS (credit_amount - debit_amount) STORED;
    END IF;
    IF to_regclass(format('%I.%I', current_schema(), 'psp_pools')) IS NOT NULL THEN
        ALTER TABLE psp_pools ADD COLUMN available_balance NUMERIC(24,4)
            GENERATED ALWAYS AS (balance - held_amount) STORED;
    END IF;
END;
$$;

DROP FUNCTION phase62_alter_money_column(TEXT, TEXT);

CREATE TABLE IF NOT EXISTS financial_precision_policy (
    domain_name VARCHAR(64) PRIMARY KEY,
    precision_digits INTEGER NOT NULL CHECK (precision_digits BETWEEN 1 AND 1000),
    scale_digits INTEGER NOT NULL CHECK (scale_digits BETWEEN 0 AND precision_digits),
    rounding_mode VARCHAR(32) NOT NULL,
    notes VARCHAR(512) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO financial_precision_policy(domain_name, precision_digits, scale_digits, rounding_mode, notes)
VALUES
    ('MONEY', 24, 4, 'HALF_EVEN', 'Payment, settlement, liquidity, fee and ledger amounts'),
    ('FX_RATE', 24, 10, 'HALF_EVEN', 'Foreign-exchange rates; not converted by this migration'),
    ('PERCENTAGE', 12, 6, 'HALF_EVEN', 'Percentages and basis-point rates retain domain-specific scales')
ON CONFLICT (domain_name) DO UPDATE SET
    precision_digits = EXCLUDED.precision_digits,
    scale_digits = EXCLUDED.scale_digits,
    rounding_mode = EXCLUDED.rounding_mode,
    notes = EXCLUDED.notes,
    updated_at = now();
