# Runbook — Capacity Forecast and Autoscaling

Collect representative request rate, CPU, memory, P95, error rate, replicas, DB pool, Kafka lag, and backlog. Forecasts must identify the source window/model and include safety headroom. Do not raise HPA maximums without confirming database, broker, downstream, and licensing capacity.

Changes require Operations and Performance approval, a rollback reference, and post-change load/soak evidence. A forecast above active HPA maximum is a capacity risk, not an automatic permission to scale.
