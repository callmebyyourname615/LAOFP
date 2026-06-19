# Production Rollback Checklist

1. Release commander declares `ROLLBACK` and records the reason.
2. Set canary weight to zero immediately.
3. If stable Deployment changed, restore the exact previous digest.
4. Wait for stable rollout and readiness.
5. Run synthetic transaction validation.
6. Capture reconciliation and compare with pre-cutover baseline.
7. Verify outbox replay does not create duplicates.
8. Verify Kafka lag and database replication recover.
9. Keep schema at V83 unless the approved database authority explicitly directs a forward fix.
10. Open an incident and preserve all logs/evidence without secrets.

Example application rollback:

```bash
kubectl -n switching annotate ingress switching-api-canary \
  nginx.ingress.kubernetes.io/canary-weight=0 --overwrite
kubectl -n switching set image deployment/switching-api \
  switching-api='<repository>@sha256:<previous-digest>'
kubectl -n switching rollout status deployment/switching-api --timeout=600s
```
