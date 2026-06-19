-- ============================================================
-- V14 · Pre-create future partitions (CURRENT_DATE + 1 to + 90)
--
-- V4 / V5 / V8 already created -7 to +90 using a loop.
-- This migration is idempotent (IF NOT EXISTS) — safe to re-run.
-- It exists as the canonical reference for the daily
-- PartitionMaintenanceService job that keeps the forward window full.
-- ============================================================

DO $$
DECLARE
    target_date DATE;
    next_date   DATE;
    tbl_name    TEXT;
    pname       TEXT;
    core_tables TEXT[] := ARRAY[
        'payment_flows',
        'inquiries',
        'transactions',
        'transaction_status_history',
        'transaction_events'
    ];
    iso_tables  TEXT[] := ARRAY[
        'iso_messages',
        'iso_message_payloads',
        'iso_validation_errors'
    ];
    stl_tables  TEXT[] := ARRAY[
        'settlement_items',
        'reconciliation_items'
    ];
BEGIN
    FOR d IN 0..90 LOOP
        target_date := CURRENT_DATE + d;
        next_date   := target_date + 1;

        -- Core flow tables
        FOREACH tbl_name IN ARRAY core_tables LOOP
            pname := tbl_name || '_' || TO_CHAR(target_date, 'YYYYMMDD');
            EXECUTE FORMAT(
                'CREATE TABLE IF NOT EXISTS %I PARTITION OF %I
                 FOR VALUES FROM (%L) TO (%L)',
                pname, tbl_name, target_date::TEXT, next_date::TEXT);
        END LOOP;

        -- ISO tables
        FOREACH tbl_name IN ARRAY iso_tables LOOP
            pname := tbl_name || '_' || TO_CHAR(target_date, 'YYYYMMDD');
            EXECUTE FORMAT(
                'CREATE TABLE IF NOT EXISTS %I PARTITION OF %I
                 FOR VALUES FROM (%L) TO (%L)',
                pname, tbl_name, target_date::TEXT, next_date::TEXT);
        END LOOP;

        -- Settlement / Reconciliation tables
        FOREACH tbl_name IN ARRAY stl_tables LOOP
            pname := tbl_name || '_' || TO_CHAR(target_date, 'YYYYMMDD');
            EXECUTE FORMAT(
                'CREATE TABLE IF NOT EXISTS %I PARTITION OF %I
                 FOR VALUES FROM (%L) TO (%L)',
                pname, tbl_name, target_date::TEXT, next_date::TEXT);
        END LOOP;

    END LOOP;
END;
$$;
