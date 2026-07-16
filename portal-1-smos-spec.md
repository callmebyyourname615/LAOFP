# Portal 1: Internal Operations / SMOS Portal

สำหรับ `SYSTEM_ADMIN`, `OPS_ADMIN`, `SETTLEMENT_OFFICER`, `RISK_OFFICER`, `AUDITOR`, `DISPUTE_OFFICER` และ `READ_ONLY`

## 1. Executive Dashboard

- ภาพรวมระบบ
- จำนวนธุรกรรมวันนี้
- Success / Failed / Pending transactions
- Transaction volume และ value
- System health
- Connector health
- Settlement status
- Risk alerts
- Open disputes
- Outbox failures
- SLA / SLO / error budget
- DR status
- Hypercare status

Endpoints:

- `/api/dashboard/overview`
- `/api/dashboard/transactions/summary`
- `/api/operations/dashboard-summary`
- `/api/operations/health`

## 2. Transaction Operations

- ค้นหาธุรกรรมด้วย transaction reference, participant, วันที่ และสถานะ
- Transaction detail
- Transaction timeline
- Payment flow
- Transfer trace
- Inquiry ที่เกี่ยวข้อง
- Risk score
- Retry / failure history
- Export transaction data

Endpoints:

- `/api/operations/transactions`
- `/api/operations/transfers`
- `/api/operations/payment-flows/{transactionRef}`
- `/api/operations/transactions/{transactionRef}/trace`
- `/api/transfers`
- `/api/inquiries`
- `/v1/risk/scores/{txnId}`

## 3. Payment Monitoring

- QR payments
- Static QR และ Dynamic QR
- QR decode
- Request-to-Pay
- Bill payment
- VPA lookup
- Cross-border payment
- Payment status
- Failed / Pending payment
- Refund
- Reversal
- Payment retry

Payment execution ที่มีผลกระทบทางการเงินต้องมี permission และ maker-checker approval

## 4. Participant Management

- Participant / bank / PSP list
- Participant detail และสถานะ
- Activate / suspend participant
- Participant capability
- Transaction volume
- Settlement position
- Certificates และ credentials
- Participant health
- Participant onboarding
- Generate routing สำหรับ bank
- Participant certification
- Participant-specific reports

Endpoints:

- `/api/participants`
- `/api/operations/bank-onboarding`
- `/api/operations/bank-onboarding/generate-routes`
- `/api/operations/bank-status`
- `/v1/participants/{pspId}/certificates/register`
- `/v1/participants/{pspId}/credentials/rotate`
- `/v1/operations/participant-certifications`

## 5. Routing & Connector Management

### Routing Rules

- ดู, สร้าง, แก้ไข, เปิด/ปิด routing rule
- กำหนด priority และ fallback route
- Routing change history
- Routing decision tester

### Connectors

- Connector list และ configuration
- Endpoint และ timeout
- Retry policy
- Circuit breaker status
- Connector health
- Test connector
- Enable / disable connector
- Credential status
- Connector logs

Endpoints:

- `/api/routing-rules`
- `/api/connector-configs`
- `/api/operations/connectors/health`
- `/api/operations/connectors/{connectorName}/test`

## 6. Settlement & Liquidity

- Settlement dashboard
- Settlement calendar, cycle และ batch
- Settlement instruction
- Pending settlement
- Settlement positions
- Liquidity balance และ top-up
- Pool history
- RTGS instruction และ callback status
- Settlement approval / rejection
- Settlement report
- Net position
- Settlement reconciliation

Endpoints:

- `/api/dashboard/settlement`
- `/v1/settlement/balance`
- `/v1/settlement/liquidity/topup`
- `/v1/settlement/pool-history`
- `/v1/settlement/positions`
- `/v1/settlement/rtgs-callback`
- `/v1/operator/crossborder/reconciliation`

คำสั่งที่มีผลกระทบทางการเงินต้องใช้ maker-checker

## 7. Reconciliation

- Daily reconciliation
- Continuous reconciliation
- Participant reconciliation
- Settlement reconciliation
- Cross-border reconciliation
- Promotion funder reconciliation
- Unmatched transactions
- Missing transactions
- Amount mismatch
- Duplicate transactions
- Suspense items
- Reconciliation adjustment
- Export reconciliation report
- Reconciliation audit trail

## 8. Dispute & Refund Management

- Dispute dashboard และรายการ dispute
- Dispute detail และ timeline
- Dispute actions
- Submit / approve / reject resolution
- Upload / download evidence
- Refund retry
- Post-settlement dispute
- Evidence report
- CSV export
- Dispute SLA tracking

Endpoints:

- `/api/operations/disputes`
- `/api/operations/disputes/{disputeId}`
- `/api/operations/disputes/{disputeId}/timeline`
- `/api/operations/disputes/{disputeId}/attachments`
- `/api/operations/disputes/{disputeId}/submit-resolution`
- `/api/operations/disputes/{disputeId}/approve-resolution`
- `/api/operations/disputes/{disputeId}/refund/retry`

## 9. Risk & Fraud Monitoring

สำหรับ `RISK_OFFICER`

- Risk dashboard
- High-risk transactions
- Fraud score
- Velocity alerts
- Risk rule result
- Blocked transactions
- Review transaction
- Risk investigation
- Risk decision history
- Risk threshold configuration
- Export risk report

Endpoints:

- `/api/dashboard/risk`
- `/v1/risk/scores/{txnId}`
- Risk evaluation endpoints
- Fraud scoring services

Risk Officer ห้ามแก้ settlement หรือ participant โดยไม่มี permission ที่เหมาะสม

## 10. AML & Compliance

- Sanctions screening
- Sanctions list import
- Screening result
- Name normalization และ alias matching
- Suspicious transaction
- STR generation
- Compliance case
- Legal hold
- Retention policy
- Compliance report
- Evidence package
- Regulatory submission status

Endpoints:

- `/v1/compliance/*`
- Compliance controller
- Legal hold controller
- AML services

## 11. ISO 20022 & Message Operations

- ISO message search และ detail
- PACS.008, PACS.002 และ ACMT.023
- Message validation
- Security policy
- Encrypt / decrypt message
- Message correlation และ status
- Failed message
- Message replay ตาม permission
- Raw XML viewer
- Message audit trail

Endpoints:

- `/api/iso-messages`
- `/api/iso-messages/{messageKey}`
- `/api/iso-messages/{messageKey}/validate`
- `/api/iso-messages/{id}/encrypt`
- `/api/iso-messages/{id}/decrypt`
- `/api/iso20022/*`

ต้อง mask sensitive data และห้ามแสดง secret หรือ private key บนหน้าจอ

## 12. Outbox & Event Recovery

- Outbox event list
- Failed events
- Stuck events
- Dead-letter events
- Event detail
- Mark reviewed
- Retry one event
- Retry all failed events
- Recover stuck events
- Retry history
- Error message
- Event correlation ID

Endpoints:

- `/api/operations/outbox-failures`
- `/api/operations/outbox-failures/retry-all`
- `/api/operations/outbox-stuck`
- `/api/operations/outbox-stuck/recover-all`
- `/api/outbox-events`
- Outbox dead-letter endpoints

ทุก retry/recovery ต้องมี confirmation, reason และ audit log

## 13. Reports & Data Export

- Report catalog
- Generate / download report
- Schedule report
- Suspend schedule
- Report delivery history และ status
- CSV / JSON / PDF export
- Signed report link
- Report retention
- Failed delivery

Endpoints:

- `/v1/reports/download/{id}`
- `/v1/operator/report-delivery-schedules`

## 14. Promotions & Tariffs

- Create / activate / suspend / extend promotion
- Promotion eligibility
- Budget usage
- Promotion report
- Funder ledger
- Tariff configuration
- Fee calculation
- Fee reconciliation
- Expiry monitoring

Endpoints:

- `/v1/promotions`
- `/v1/promotions/{id}/activate`
- `/v1/promotions/{id}/suspend`
- `/v1/promotions/{id}/report`
- Promotion reconciliation endpoints
- Tariff operations endpoints

## 15. Operations Readiness & Resilience

- Readiness dashboard
- Control checklist
- Approval checklist
- Evidence ledger
- Go-live gate
- Hypercare start / event / scorecard / complete
- BAU activation
- Backup status
- Restore status
- DR status
- Chaos test result
- Capacity status
- SLO status
- Incident link

Endpoints:

- `/api/operations/readiness/*`
- `/api/operations/continuous-assurance/*`
- `/api/dashboard/dr`
- `/api/dashboard/infrastructure`
- `/api/operations/bau/status`

## 16. Admin & Security

สำหรับ `SYSTEM_ADMIN`

- Login / logout
- MFA
- Session management
- Revoke session / revoke all sessions
- User management
- Role assignment
- User activation / deactivation
- API key management
- API key disable / rotation
- Certificate rotation
- Privileged access / break-glass
- Maker-checker requests
- Approval queue
- Security events
- Audit log

Endpoints:

- `/api/auth/*`
- `/api/admin/users`
- `/api/admin/api-keys`
- `/api/admin/requests`
- `/api/audit-logs`

## 17. Audit & Governance

สำหรับ `AUDITOR`

- Search audit logs
- Filter by actor, action, entity และ date
- View before/after data
- View approval chain
- View access history
- View security events
- Export audit evidence
- Legal hold records
- Immutable evidence package

## Global UI and Implementation Requirements

ทุก module ต้องมีหน้าที่เหมาะสม ได้แก่ overview, list, detail, create/edit, action/approval, timeline และ audit history ตามความเหมาะสม

ทุก list page ต้องมี search, filters, date range, status filter, sorting, pagination, row actions, detail navigation, export, loading, empty, error และ retry states

ทุก detail page ต้องมี summary, status, metadata, timeline, related records, technical details, audit history และ permission-aware actions

ทุก mutation ต้องมี confirmation, impact description, reason ตามความเหมาะสม, loading, success, error, toast และ audit log

ต้องใช้ role-based access control และ maker-checker สำหรับงานด้านการเงิน, security, configuration และ recovery

ต้องสร้าง reusable components สำหรับ AppShell, Sidebar, Topbar, PageHeader, KPI card, StatusBadge, HealthIndicator, DataTable, FilterBar, DetailDrawer, Timeline, ChartCard, ProgressBar, Gauge, EmptyState, ErrorState, LoadingSkeleton, ConfirmationDialog, ApprovalDialog, RetryDialog, AuditReasonDialog, FileUpload, XMLViewer, JSONViewer, Pagination, Toast, Export menu, RoleGuard และ PermissionGuard

ข้อมูล mock ต้องอยู่ใน service layer แยกจาก UI และเตรียม service สำหรับเชื่อม REST API จริงในอนาคต
