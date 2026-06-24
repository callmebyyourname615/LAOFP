\set ON_ERROR_STOP on
DO $$
DECLARE v_bad bigint;
BEGIN
  IF to_regclass('public.transaction_record') IS NOT NULL THEN
    EXECUTE 'SELECT count(*) FROM transaction_record WHERE amount < 0' INTO v_bad;
    IF v_bad > 0 THEN RAISE EXCEPTION 'negative transaction amounts: %', v_bad; END IF;
  END IF;
  IF to_regclass('public.settlement_leg') IS NOT NULL THEN
    EXECUTE 'SELECT count(*) FROM (SELECT transaction_id, leg_type, count(*) c FROM settlement_leg GROUP BY transaction_id, leg_type HAVING count(*) > 1) d' INTO v_bad;
    IF v_bad > 0 THEN RAISE EXCEPTION 'duplicate settlement legs: %', v_bad; END IF;
  END IF;
END $$;
