# Cross-Border Timestamp Binding Closure

Phase 72 adds two independent regression controls:

1. `scripts/verify_cross_border_temporal_binding.py` scans cross-border Java source and fails when an `Instant` is passed to `PreparedStatement.setObject` without `Types.TIMESTAMP_WITH_TIMEZONE`.
2. `CrossBorderTemporalBindingRegressionTest` runs the same repository-level policy during Maven tests.

The supplied archive contains no offending call, so no business source file was modified. If Phase 71 introduces or still contains the historical call, both controls fail and identify the exact file and line. The required source fix is:

```java
statement.setObject(index, instant, Types.TIMESTAMP_WITH_TIMEZONE);
```
