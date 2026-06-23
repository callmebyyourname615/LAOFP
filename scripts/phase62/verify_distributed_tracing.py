#!/usr/bin/env python3
from pathlib import Path
import sys
fail=[]
checks={
 'pom.xml':['micrometer-tracing-bridge-otel','opentelemetry-exporter-otlp'],
 'src/main/resources/db/migration/V106__distributed_trace_correlation.sql':['outbox_messages','transaction_events','audit_logs','trace_id'],
 'src/main/java/com/example/switching/observability/tracing/TraceContextSupport.java':['currentTraceId','[0-9a-f]{32}'],
 'src/main/java/com/example/switching/outbox/service/OutboxTransactionService.java':['setTraceId','TraceContextSupport'],
 'src/main/java/com/example/switching/outbox/queue/OutboxQueueMessage.java':['String traceId'],
 'src/main/java/com/example/switching/transfer/service/TransactionEventPublisher.java':['trace_id','currentTraceId'],
 'src/main/java/com/example/switching/audit/service/AuditLogService.java':['setTraceId','getTraceId'],
 'src/main/resources/application-prod.yml':['OTEL_EXPORTER_OTLP_TRACES_ENDPOINT'],
 'src/main/resources/application.yml':['observation-enabled','KAFKA_OBSERVATION_ENABLED'],
 'src/main/java/com/example/switching/common/dto/ApiErrorResponse.java':['String traceId','getTraceId','setTraceId'],
 'src/main/java/com/example/switching/common/exception/GlobalExceptionHandler.java':['TraceContextSupport','setTraceId']}
for p,marks in checks.items():
 path=Path(p)
 if not path.is_file(): fail.append(f'missing {p}'); continue
 t=path.read_text()
 for m in marks:
  if m not in t: fail.append(f'{p} missing {m!r}')
if fail:
 print('\n'.join('FAIL: '+x for x in fail)); sys.exit(1)
print('PASS: OTLP tracing and durable outbox/audit correlation')
