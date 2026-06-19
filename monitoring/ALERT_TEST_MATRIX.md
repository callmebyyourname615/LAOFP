# UAT Alert Firing and Routing Matrix

For every row, trigger the condition in an isolated UAT namespace, capture Pending → Firing → Resolved, verify the configured receiver and open the linked runbook. Never inject a fault into production solely to test an alert.

| Alert | Severity | Hold | Rule file | Runbook | Fired | Routed | Resolved | Evidence |
|---|---|---:|---|---|---|---|---|---|
| `SwitchingActiveParticipantUncertified` | critical | 10m | `monitoring/prometheus/participant-certification-rules.yaml` | `docs/runbooks/RB-20_PARTICIPANT_CERTIFICATION.md` | ☐ | ☐ | ☐ | |
| `SwitchingApiHighErrorRate` | critical | 5m | `monitoring/prometheus/prometheus-rules.yaml` | `docs/runbooks/RB-08_MONITORING_AND_API_SLO.md#switchingapihigherrorrate` | ☐ | ☐ | ☐ | |
| `SwitchingApiHighP95Latency` | warning | 10m | `monitoring/prometheus/prometheus-rules.yaml` | `docs/runbooks/RB-08_MONITORING_AND_API_SLO.md#switchingapihighp95latency` | ☐ | ☐ | ☐ | |
| `SwitchingApiUnavailable` | critical | 2m | `monitoring/prometheus/prometheus-rules.yaml` | `docs/runbooks/RB-08_MONITORING_AND_API_SLO.md#switchingapiunavailable` | ☐ | ☐ | ☐ | |
| `SwitchingBackupCrossRegionCopyFailed` | critical | 15m | `monitoring/prometheus/backup-rules.yaml` | `docs/runbooks/RB-12_BACKUP_PITR_AND_RESTORE.md#switchingbackupcrossregioncopyfailed` | ☐ | ☐ | ☐ | |
| `SwitchingBaseBackupFailed` | critical | 5m | `monitoring/prometheus/backup-rules.yaml` | `docs/runbooks/RB-12_BACKUP_PITR_AND_RESTORE.md#switchingbasebackupfailed` | ☐ | ☐ | ☐ | |
| `SwitchingBaseBackupStale` | critical | 10m | `monitoring/prometheus/backup-rules.yaml` | `docs/runbooks/RB-12_BACKUP_PITR_AND_RESTORE.md#switchingbasebackupstale` | ☐ | ☐ | ☐ | |
| `SwitchingCapacityForecastShortfall` | warning | 30m | `monitoring/prometheus/phase43-52-alert-rules.yaml` | `docs/runbooks/CAPACITY_AUTOSCALING.md` | ☐ | ☐ | ☐ | |
| `SwitchingCertificateExpiringSoon` | warning | 15m | `monitoring/prometheus/phase33-42-alert-rules.yaml` | `docs/runbooks/CERTIFICATE_LIFECYCLE.md` | ☐ | ☐ | ☐ | |
| `SwitchingControlEvidenceUnverified` | warning | 30m | `monitoring/prometheus/phase43-52-alert-rules.yaml` | `docs/runbooks/DATA_LINEAGE_EVIDENCE_CATALOG.md` | ☐ | ☐ | ☐ | |
| `SwitchingCorrectiveActionOverdue` | warning | 30m | `monitoring/prometheus/phase33-42-alert-rules.yaml` | `docs/runbooks/INCIDENT_CAPA.md` | ☐ | ☐ | ☐ | |
| `SwitchingCryptoRotationOverdue` | critical | 15m | `monitoring/prometheus/phase43-52-alert-rules.yaml` | `docs/runbooks/CRYPTOGRAPHIC_ASSET_ROTATION.md` | ☐ | ☐ | ☐ | |
| `SwitchingCutoffRejectionsSpike` | warning | 5m | `monitoring/prometheus/phase43-52-alert-rules.yaml` | `docs/runbooks/SETTLEMENT_CALENDAR_CUTOFF.md` | ☐ | ☐ | ☐ | |
| `SwitchingDatabaseDeadTuplePressure` | warning | 30m | `monitoring/prometheus/database-lifecycle-rules.yaml` | `docs/runbooks/RB-16_DATABASE_LIFECYCLE.md#long-transaction-dead-tuples` | ☐ | ☐ | ☐ | |
| `SwitchingDatabaseInvalidIndex` | critical | 5m | `monitoring/prometheus/database-lifecycle-rules.yaml` | `docs/runbooks/RB-16_DATABASE_LIFECYCLE.md#invalid-index` | ☐ | ☐ | ☐ | |
| `SwitchingDatabaseLongTransaction` | warning | 10m | `monitoring/prometheus/database-lifecycle-rules.yaml` | `docs/runbooks/RB-16_DATABASE_LIFECYCLE.md#long-transaction-dead-tuples` | ☐ | ☐ | ☐ | |
| `SwitchingDatabaseMaintenanceFailed` | critical | 5m | `monitoring/prometheus/database-lifecycle-rules.yaml` | `docs/runbooks/RB-16_DATABASE_LIFECYCLE.md#maintenance-job` | ☐ | ☐ | ☐ | |
| `SwitchingDatabaseMaintenanceStale` | warning | 30m | `monitoring/prometheus/database-lifecycle-rules.yaml` | `docs/runbooks/RB-16_DATABASE_LIFECYCLE.md#maintenance-job` | ☐ | ☐ | ☐ | |
| `SwitchingDatabasePartitionHorizonGap` | critical | 5m | `monitoring/prometheus/database-lifecycle-rules.yaml` | `docs/runbooks/RB-16_DATABASE_LIFECYCLE.md#partition-gap` | ☐ | ☐ | ☐ | |
| `SwitchingDatabasePoolSaturated` | critical | 5m | `monitoring/prometheus/prometheus-rules.yaml` | `docs/runbooks/RB-09_DATABASE_AND_QUEUE_PRESSURE.md#switchingdatabasepoolsaturated` | ☐ | ☐ | ☐ | |
| `SwitchingDecisionRuleApprovalPending` | warning | 24h | `monitoring/prometheus/phase43-52-alert-rules.yaml` | `docs/runbooks/DECISION_RULE_GOVERNANCE.md` | ☐ | ☐ | ☐ | |
| `SwitchingDecommissionPlanOverdue` | critical | 15m | `monitoring/prometheus/phase43-52-alert-rules.yaml` | `docs/runbooks/CONTROLLED_DECOMMISSIONING.md` | ☐ | ☐ | ☐ | |
| `SwitchingErrorBudgetExhausted` | critical | 5m | `monitoring/prometheus/slo-alerts.yaml` | `docs/runbooks/RB-14_SLO_ERROR_BUDGET.md#error-budget-policy` | ☐ | ☐ | ☐ | |
| `SwitchingErrorBudgetNearlyExhausted` | warning | 30m | `monitoring/prometheus/slo-alerts.yaml` | `docs/runbooks/RB-14_SLO_ERROR_BUDGET.md#error-budget-policy` | ☐ | ☐ | ☐ | |
| `SwitchingFinalityReversalPending` | critical | 30m | `monitoring/prometheus/phase43-52-alert-rules.yaml` | `docs/runbooks/PAYMENT_FINALITY_DUPLICATE.md` | ☐ | ☐ | ☐ | |
| `SwitchingGovernedFxRateMissing` | critical | 2m | `monitoring/prometheus/phase33-42-alert-rules.yaml` | `docs/runbooks/FX_RATE_GOVERNANCE.md` | ☐ | ☐ | ☐ | |
| `SwitchingLiquidityHeadroomLow` | critical | 2m | `monitoring/prometheus/phase33-42-alert-rules.yaml` | `docs/runbooks/INTRADAY_LIQUIDITY.md` | ☐ | ☐ | ☐ | |
| `SwitchingManualAdjustmentsPending` | critical | 4h | `monitoring/prometheus/phase43-52-alert-rules.yaml` | `docs/runbooks/MANUAL_FINANCIAL_ADJUSTMENTS.md` | ☐ | ☐ | ☐ | |
| `SwitchingNotificationDeadLetterGrowing` | warning | 10m | `monitoring/prometheus/phase33-42-alert-rules.yaml` | `docs/runbooks/NOTIFICATION_DELIVERY.md` | ☐ | ☐ | ☐ | |
| `SwitchingOperationalMetricsStale` | warning | 3m | `monitoring/prometheus/prometheus-rules.yaml` | `docs/runbooks/RB-08_MONITORING_AND_API_SLO.md#switchingoperationalmetricsstale` | ☐ | ☐ | ☐ | |
| `SwitchingOutboxBacklog` | critical | 5m | `monitoring/prometheus/prometheus-rules.yaml` | `docs/runbooks/RB-09_DATABASE_AND_QUEUE_PRESSURE.md#switchingoutboxbacklog` | ☐ | ☐ | ☐ | |
| `SwitchingParticipantCertificationExpiringSoon` | warning | 1h | `monitoring/prometheus/participant-certification-rules.yaml` | `docs/runbooks/RB-20_PARTICIPANT_CERTIFICATION.md` | ☐ | ☐ | ☐ | |
| `SwitchingRegulatoryReportOverdue` | critical | 15m | `monitoring/prometheus/phase33-42-alert-rules.yaml` | `docs/runbooks/REGULATORY_REPORTING.md` | ☐ | ☐ | ☐ | |
| `SwitchingRestoreDrillFailedOrMissedRto` | critical | 5m | `monitoring/prometheus/backup-rules.yaml` | `docs/runbooks/RB-12_BACKUP_PITR_AND_RESTORE.md#switchingrestoredrillfailedormissedrto` | ☐ | ☐ | ☐ | |
| `SwitchingRestoreDrillOverdue` | warning | 1h | `monitoring/prometheus/backup-rules.yaml` | `docs/runbooks/RB-12_BACKUP_PITR_AND_RESTORE.md#switchingrestoredrilloverdue` | ☐ | ☐ | ☐ | |
| `SwitchingSanctionsDataStaleOrEmpty` | critical | 5m | `monitoring/prometheus/prometheus-rules.yaml` | `docs/runbooks/RB-11_AML_SANCTIONS_AND_STR.md#switchingsanctionsdatastaleorempty` | ☐ | ☐ | ☐ | |
| `SwitchingSettlementFailure` | critical | 2m | `monitoring/prometheus/prometheus-rules.yaml` | `docs/runbooks/RB-10_TRANSACTION_SETTLEMENT_AND_WEBHOOK.md#switchingsettlementfailure` | ☐ | ☐ | ☐ | |
| `SwitchingSloFastBurn` | critical | 2m | `monitoring/prometheus/slo-alerts.yaml` | `docs/runbooks/RB-14_SLO_ERROR_BUDGET.md#fast-burn` | ☐ | ☐ | ☐ | |
| `SwitchingSloSlowBurn` | warning | 15m | `monitoring/prometheus/slo-alerts.yaml` | `docs/runbooks/RB-14_SLO_ERROR_BUDGET.md#slow-burn` | ☐ | ☐ | ☐ | |
| `SwitchingStrSubmissionBacklog` | critical | 15m | `monitoring/prometheus/prometheus-rules.yaml` | `docs/runbooks/RB-11_AML_SANCTIONS_AND_STR.md#switchingstrsubmissionbacklog` | ☐ | ☐ | ☐ | |
| `SwitchingSyntheticProbeFailed` | critical | 1m | `monitoring/prometheus/phase33-42-alert-rules.yaml` | `docs/runbooks/SYNTHETIC_MONITORING.md` | ☐ | ☐ | ☐ | |
| `SwitchingThirdPartyCircuitOpen` | critical | 2m | `monitoring/prometheus/phase43-52-alert-rules.yaml` | `docs/runbooks/THIRD_PARTY_DEPENDENCY_SLA.md` | ☐ | ☐ | ☐ | |
| `SwitchingTransactionLimitDenialsSpike` | warning | 5m | `monitoring/prometheus/phase43-52-alert-rules.yaml` | `docs/runbooks/TRANSACTION_LIMIT_ENTITLEMENT.md` | ☐ | ☐ | ☐ | |
| `SwitchingTransactionRejectSpike` | critical | 5m | `monitoring/prometheus/prometheus-rules.yaml` | `docs/runbooks/RB-10_TRANSACTION_SETTLEMENT_AND_WEBHOOK.md#switchingtransactionrejectspike` | ☐ | ☐ | ☐ | |
| `SwitchingWalArchiveFailed` | critical | 5m | `monitoring/prometheus/backup-rules.yaml` | `docs/runbooks/RB-12_BACKUP_PITR_AND_RESTORE.md#switchingwalarchivefailed` | ☐ | ☐ | ☐ | |
| `SwitchingWalArchiveStale` | critical | 5m | `monitoring/prometheus/backup-rules.yaml` | `docs/runbooks/RB-12_BACKUP_PITR_AND_RESTORE.md#switchingwalarchivestale` | ☐ | ☐ | ☐ | |
| `SwitchingWebhookDeliveryFailure` | warning | 5m | `monitoring/prometheus/prometheus-rules.yaml` | `docs/runbooks/RB-10_TRANSACTION_SETTLEMENT_AND_WEBHOOK.md#switchingwebhookdeliveryfailure` | ☐ | ☐ | ☐ | |

Total alerts: **47**
