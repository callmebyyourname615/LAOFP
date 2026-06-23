\set ON_ERROR_STOP on

-- Financial invariants used after clean install and every supported upgrade path.
DO $$
DECLARE
    negative_counters bigint;
    duplicate_refs bigint;
BEGIN
    SELECT count(*) INTO negative_counters
    FROM (
        SELECT total_count FROM reporting.current_transaction_status
        UNION ALL
        SELECT total_count FROM reporting.current_inquiry_status
        UNION ALL
        SELECT total_count FROM reporting.current_outbox_status
    ) counters
    WHERE total_count < 0;
    IF negative_counters <> 0 THEN
        RAISE EXCEPTION 'negative reporting counters: %', negative_counters;
    END IF;

    SELECT count(*) INTO duplicate_refs
    FROM (
        SELECT transaction_ref
        FROM transactions
        GROUP BY transaction_ref
        HAVING count(*) > 1
    ) duplicates;
    IF duplicate_refs <> 0 THEN
        RAISE EXCEPTION 'duplicate transaction references: %', duplicate_refs;
    END IF;
END $$;
