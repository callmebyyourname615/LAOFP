# Financial Precision Policy

## Canonical domains

| Domain | PostgreSQL | Java | Rounding |
|---|---|---|---|
| Money | `NUMERIC(24,4)` | `BigDecimal` | `HALF_EVEN` at controlled boundary |
| FX rate | `NUMERIC(24,10)` | `BigDecimal` | `HALF_EVEN` only during quote/application |
| Percentage / basis points | domain-specific | `BigDecimal` | never implicitly converted to money scale |

`double` and `float` are prohibited for monetary, fee, settlement, liquidity and
reconciliation values. Rounding must occur at an explicitly documented boundary;
intermediate calculations retain their required domain scale.

## Migration and rollback

`V104__standardize_financial_numeric_precision.sql` upgrades financial amount
columns. The migration requires a maintenance window because PostgreSQL can take
an access-exclusive lock while rewriting older columns. Before execution, capture
row counts, maximum precision/scale and a reconciliation checksum. Rollback is a
forward migration; do not narrow precision after financial data has been accepted.

## Read/write consistency

Balances, idempotency decisions, maker-checker execution, settlement transitions
and immediate post-write inquiry responses must use the primary database. Reporting
and dashboard transactions may use the replica when marked read-only.
