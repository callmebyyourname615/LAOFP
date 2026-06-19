# RB-07 Production Config And Kafka

## Purpose

This runbook explains where production configuration values come from and how
to validate them before go-live. The repo contains templates only; production
secrets must come from the bank/operator secrets manager.

## Required Sources

| Area | Source in production |
| --- | --- |
| DB credentials | DBA-created app and Flyway users |
| Kafka bootstrap servers | Managed Kafka/Redpanda cluster endpoints |
| Kafka username/password | Kafka ACL/SASL user created by platform team |
| Kafka truststore/keystore | Cluster CA/client certificate bundle, if private CA or mTLS is used |
| RTGS/FIU/payment gateway URLs | BoL/payment-network onboarding documents |
| Object storage keys | Object storage IAM/service account |
| Message crypto key | KMS-generated 256-bit key, exported as base64 only if required |
| OAuth/signing secrets | Secrets manager generated values |

## Kafka Production Shape

Use at least three brokers and TLS. For SASL/SCRAM:

```properties
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka-1.example:9093,kafka-2.example:9093,kafka-3.example:9093
KAFKA_SECURITY_PROTOCOL=SASL_SSL
KAFKA_SASL_MECHANISM=SCRAM-SHA-512
KAFKA_SASL_JAAS_CONFIG=org.apache.kafka.common.security.scram.ScramLoginModule required username="switching-outbox" password="...";
OUTBOX_QUEUE_ENABLED=true
OUTBOX_QUEUE_TOPIC=switching.outbox.dispatch
OUTBOX_QUEUE_GROUP_ID=switching-outbox-dispatcher
```

The Kafka user should have:

- `WRITE` on `switching.outbox.dispatch`
- `READ` on `switching.outbox.dispatch`
- `CREATE` or pre-created topic access, depending on platform policy
- consumer group access for `switching-outbox-dispatcher`

Recommended topic baseline:

```bash
rpk topic create switching.outbox.dispatch \
  --partitions 6 \
  --replicas 3 \
  --topic-config cleanup.policy=delete \
  --topic-config retention.ms=604800000
```

## Pre-Deploy Check

Create a real `.env.prod` from `.env.prod.example`, replace every placeholder,
then run:

```bash
./scripts/check_prod_config.sh .env.prod
```

For Kubernetes templates:

```bash
./scripts/check_prod_config.sh .env.prod k8s/configmap.yaml k8s/secret.yaml
kubectl apply --dry-run=server -f k8s/
```

Expected result before go-live: no `REPLACE_ME`, no `localhost`, no `mock-*`,
no Kafka `PLAINTEXT`, and PostgreSQL URLs use `sslmode=require` or
`sslmode=verify-full`.
