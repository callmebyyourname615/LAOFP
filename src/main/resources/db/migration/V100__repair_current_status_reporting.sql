-- Repair the synchronous current-status counters introduced in V86.
--
-- V86 inserted negative deltas when the aggregate row was absent.  PostgreSQL
-- evaluates the INSERT row before ON CONFLICT, so the non-negative CHECK
-- constraint rejected legitimate source-table status transitions.  Decrements
-- must therefore update existing rows only; increments may create a row.

CREATE OR REPLACE FUNCTION reporting.adjust_current_status(
    target_table REGCLASS,
    target_status TEXT,
    delta BIGINT
)
RETURNS VOID AS $$
BEGIN
    IF target_status IS NULL OR delta = 0 THEN
        RETURN;
    END IF;

    IF delta > 0 THEN
        EXECUTE format(
            'INSERT INTO %s(status, total_count, updated_at) VALUES ($1, $2, NOW())
             ON CONFLICT (status) DO UPDATE
             SET total_count = GREATEST(0, %s.total_count + EXCLUDED.total_count),
                 updated_at = NOW()',
            target_table,
            target_table
        )
        USING target_status, delta;
    ELSE
        -- Never insert a negative aggregate row.  Missing/stale rows are repaired
        -- by reporting.rebuild_current_status_reporting().
        EXECUTE format(
            'UPDATE %s
             SET total_count = GREATEST(0, total_count + $2),
                 updated_at = NOW()
             WHERE status = $1',
            target_table
        )
        USING target_status, delta;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION reporting.rebuild_current_status_reporting()
RETURNS VOID AS $$
BEGIN
    -- This function is intended for migration/reconciliation windows.  The share
    -- locks prevent concurrent writes while source-of-truth counts are rebuilt.
    LOCK TABLE transactions, inquiries, outbox_messages IN SHARE MODE;

    TRUNCATE TABLE
        reporting.current_transaction_status,
        reporting.current_inquiry_status,
        reporting.current_outbox_status;

    INSERT INTO reporting.current_transaction_status(status, total_count, updated_at)
    SELECT status, COUNT(*), NOW()
    FROM transactions
    WHERE status IS NOT NULL
    GROUP BY status;

    INSERT INTO reporting.current_inquiry_status(status, total_count, updated_at)
    SELECT status, COUNT(*), NOW()
    FROM inquiries
    WHERE status IS NOT NULL
    GROUP BY status;

    INSERT INTO reporting.current_outbox_status(status, total_count, updated_at)
    SELECT status, COUNT(*), NOW()
    FROM outbox_messages
    WHERE status IS NOT NULL
    GROUP BY status;
END;
$$ LANGUAGE plpgsql;

SELECT reporting.rebuild_current_status_reporting();
