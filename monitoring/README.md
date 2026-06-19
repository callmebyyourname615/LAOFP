# Switching Monitoring

Apply order:

```bash
kubectl apply -f monitoring/prometheus/servicemonitor.yaml
kubectl apply -f monitoring/prometheus/prometheus-rules.yaml
```

Provision Grafana with the files under `monitoring/grafana/provisioning` and mount dashboard JSON files at `/var/lib/grafana/dashboards/switching`.

`alertmanager-config.example.yaml` is intentionally not production-ready until the `alertmanager-receivers` Secret and the platform AlertmanagerConfig selector are confirmed. Do not commit webhook, SMTP, pager, or chat credentials.

Before production, execute each rule with a controlled UAT fault, capture alert firing/resolution evidence, verify the runbook URL used by the operations UI, and tune thresholds from load/soak-test data.

## Backup, WAL and PITR monitoring

Apply `prometheus/backup-rules.yaml` and import `grafana/dashboards/switching-backup-pitr.json`. Backup jobs and the WAL archiver push low-cardinality metrics to the Prometheus Pushgateway configured by `PUSHGATEWAY_URL`.

Required alerts cover stale/failed base backups, stale/failed WAL archive, missing off-site copies, overdue restore drills, and missed restore RTO. See `docs/runbooks/RB-12_BACKUP_PITR_AND_RESTORE.md`.
