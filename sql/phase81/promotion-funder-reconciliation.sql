\set ON_ERROR_STOP on
SELECT p.id, p.code, p.status, p.funder_participant_id, p.currency,
       p.budget_cap, p.budget_reserved, p.budget_consumed,
       COALESCE(sum(s.amount) FILTER (WHERE s.status='SETTLED'),0) AS settled_amount,
       p.budget_cap - p.budget_reserved - p.budget_consumed AS available_budget
FROM promotion p
LEFT JOIN promotion_application a ON a.promotion_id=p.id
LEFT JOIN promotion_settlement s ON s.promotion_application_id=a.id
GROUP BY p.id,p.code,p.status,p.funder_participant_id,p.currency,
         p.budget_cap,p.budget_reserved,p.budget_consumed
ORDER BY p.code;
SELECT count(*) AS duplicate_rewards FROM (
 SELECT promotion_id, transaction_reference, count(*)
 FROM promotion_application GROUP BY 1,2 HAVING count(*)>1
) d;
SELECT count(*) AS invalid_budget_rows
FROM promotion
WHERE budget_reserved < 0 OR budget_consumed < 0
   OR budget_reserved + budget_consumed > budget_cap;
