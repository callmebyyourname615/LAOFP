\set ON_ERROR_STOP on
SELECT CASE WHEN COALESCE(abs(sum(net_position)),0)=0 THEN 'PASS' ELSE 'FAIL' END AS settlement_balance
FROM settlement_positions;
SELECT count(*) AS duplicate_postings FROM (
 SELECT transaction_ref,bank_code,direction,settlement_date
 FROM settlement_items GROUP BY 1,2,3,4 HAVING count(*)>1
) d;
SELECT count(*) AS flyway_failures FROM flyway_schema_history WHERE success=false;
