from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import cm
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak, KeepTogether
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfbase import pdfmetrics
from reportlab.lib.colors import HexColor
from datetime import datetime, timezone
from pathlib import Path

OUT = Path('output/pdf/switching-production-readiness-scenarios.pdf')
OUT.parent.mkdir(parents=True, exist_ok=True)

PAGE_W, PAGE_H = A4
MARGIN = 1.55 * cm

styles = getSampleStyleSheet()
styles.add(ParagraphStyle(
    name='CoverTitle', parent=styles['Title'], fontName='Helvetica-Bold', fontSize=26,
    leading=32, alignment=TA_CENTER, textColor=HexColor('#111827'), spaceAfter=18))
styles.add(ParagraphStyle(
    name='CoverSub', parent=styles['Normal'], fontName='Helvetica', fontSize=11,
    leading=16, alignment=TA_CENTER, textColor=HexColor('#475569'), spaceAfter=6))
styles.add(ParagraphStyle(
    name='SectionTitle', parent=styles['Heading1'], fontName='Helvetica-Bold', fontSize=17,
    leading=22, textColor=HexColor('#111827'), spaceBefore=14, spaceAfter=8))
styles.add(ParagraphStyle(
    name='SubTitle', parent=styles['Heading2'], fontName='Helvetica-Bold', fontSize=12.5,
    leading=16, textColor=HexColor('#1f2937'), spaceBefore=10, spaceAfter=6))
styles.add(ParagraphStyle(
    name='BodyX', parent=styles['BodyText'], fontName='Helvetica', fontSize=9.2,
    leading=12.8, textColor=HexColor('#334155'), spaceAfter=4))
styles.add(ParagraphStyle(
    name='Small', parent=styles['BodyText'], fontName='Helvetica', fontSize=8,
    leading=10.5, textColor=HexColor('#475569')))
styles.add(ParagraphStyle(
    name='Cell', parent=styles['BodyText'], fontName='Helvetica', fontSize=7.6,
    leading=9.3, textColor=HexColor('#1f2937')))
styles.add(ParagraphStyle(
    name='CellBold', parent=styles['BodyText'], fontName='Helvetica-Bold', fontSize=7.6,
    leading=9.3, textColor=HexColor('#111827')))
styles.add(ParagraphStyle(
    name='HeaderCell', parent=styles['BodyText'], fontName='Helvetica-Bold', fontSize=7.6,
    leading=9.3, textColor=colors.white))

sections = [
    {
        'id': '01', 'name': 'Deployment State', 'count': 8,
        'objective': 'Prove that the exact release deployed to UAT/production candidate is identifiable, healthy, reproducible, and reversible.',
        'scenarios': [
            ('01.1', 'Build artifact identity', 'Capture jar checksum, image digest, build timestamp, deployed version.', 'Artifact checksum matches deployed container.'),
            ('01.2', 'Container state', 'Capture docker compose ps, image tag, restart time, health state.', 'All required containers are running or healthy.'),
            ('01.3', 'Health endpoints', 'Call /actuator/health with mTLS.', 'Health returns UP with liveness/readiness.'),
            ('01.4', 'Database migration state', 'Capture Flyway schema history and latest migration.', 'No failed migration; expected latest version present.'),
            ('01.5', 'Configuration profile', 'Capture active Spring profiles and UAT env keys without secrets.', 'UAT/prod-like profile is active; secrets not printed.'),
            ('01.6', 'Dependency state', 'Capture Postgres, MinIO, Redpanda readiness.', 'Dependencies healthy and reachable by app.'),
            ('01.7', 'Rollback readiness', 'Verify backup jar/image exists and deploy script can back up current artifact.', 'Rollback artifact exists and is documented.'),
            ('01.8', 'Clock/timezone sanity', 'Capture server UTC/local time and app timestamps.', 'No large drift; timestamps consistent.'),
        ]
    },
    {
        'id': '02', 'name': 'Auth Security', 'count': 10,
        'objective': 'Prove mTLS, JWT, credentials, roles, and protected admin endpoints are enforced.',
        'scenarios': [
            ('02.1', 'mTLS required', 'Call protected endpoint with no client cert.', 'Rejected at edge with no client certificate.'),
            ('02.2', 'mTLS with valid cert', 'Call health using valid client cert/key/CA.', 'TLS succeeds.'),
            ('02.3', 'No JWT on protected API', 'Call /api/operations/health with mTLS but no JWT.', '401 returned.'),
            ('02.4', 'Invalid JWT', 'Call with Bearer invalid-token.', '401 returned.'),
            ('02.5', 'Valid admin login', 'POST /api/auth/login with admin credentials.', 'Access token returned.'),
            ('02.6', 'Wrong password rejection', 'Login with wrong password.', '401 returned.'),
            ('02.7', 'Admin endpoint authorization', 'Call /api/admin/users with admin JWT.', '200 returned.'),
            ('02.8', 'Maker-checker separation', 'Try creator self-approval on promotion/push policy.', '400 business rejection.'),
            ('02.9', 'Session/token expiry posture', 'Record token expiresIn and refresh behavior if used.', 'Expiry configured; refresh path controlled.'),
            ('02.10', 'Audit security events', 'Verify auth/admin changes appear in audit logs where supported.', 'Audit entries present or gap recorded.'),
        ]
    },
    {
        'id': '03', 'name': 'Payment Happy Path', 'count': 9,
        'objective': 'Prove a normal payment can move from inquiry to transfer creation, route selection, dispatch, and success state.',
        'scenarios': [
            ('03.1', 'Participant readiness', 'Verify source and destination participants ACTIVE.', 'Both ACTIVE.'),
            ('03.2', 'Certificate readiness', 'Verify mTLS certificate inventory for involved banks.', 'Valid non-expired certs.'),
            ('03.3', 'Connector readiness', 'Test destination connector.', 'Reachable/healthy or mock SUCCESS.'),
            ('03.4', 'Route resolve', 'Resolve source->destination PACS_008 route.', 'Expected connector returned.'),
            ('03.5', 'Inquiry create/accept', 'Run inquiry flow for beneficiary/account.', 'Inquiry accepted and traceable.'),
            ('03.6', 'Transfer create', 'Submit transfer referencing valid inquiry.', 'Transfer reference returned.'),
            ('03.7', 'Outbox dispatch', 'Verify transfer dispatch event queued and processed.', 'Outbox SUCCESS.'),
            ('03.8', 'Transfer final state', 'Read transfer by reference.', 'Status SUCCESS/SETTLEMENT_READY/SETTLED as designed.'),
            ('03.9', 'Trace/timeline', 'Fetch transfer trace/events.', 'Contains routing, dispatch, response, and state changes.'),
        ]
    },
    {
        'id': '04', 'name': 'Payment Failure Retry', 'count': 8,
        'objective': 'Prove retry and failure handling work when a connector rejects, times out, or is unavailable.',
        'scenarios': [
            ('04.1', 'Force reject on connector', 'Enable forceReject on mock connector.', 'Connector config updates.'),
            ('04.2', 'Submit failing payment', 'Send payment through force reject route.', 'Transfer/outbox records failure.'),
            ('04.3', 'Retry policy active', 'Verify TRANSFER push policy active.', 'Expected retryCount/timeout active.'),
            ('04.4', 'Automatic retry observed', 'Wait or poll retry/outbox history.', 'Retry attempts increase or retry event recorded.'),
            ('04.5', 'Recover connector', 'Disable forceReject / set SUCCESS mode.', 'Connector healthy.'),
            ('04.6', 'Retry recovery', 'Manual or automatic retry succeeds.', 'Outbox SUCCESS and no failed outbox remains for test item.'),
            ('04.7', 'Dead-letter behavior', 'Verify terminal failures appear in dead-letter if retry exhausted.', 'Dead-letter list accurate or empty after recovery.'),
            ('04.8', 'Operations health', 'Call /api/operations/health after test.', 'HEALTHY or expected DEGRADED explained.'),
        ]
    },
    {
        'id': '05', 'name': 'Refund Reversal', 'count': 9,
        'objective': 'Prove post-settlement dispute, maker-checker approval, and refund/reversal completion are reliable.',
        'scenarios': [
            ('05.1', 'Find settled transfer', 'Select original settled transfer reference.', 'Transfer is SETTLED.'),
            ('05.2', 'Create post-settlement dispute', 'POST dispute for settled transfer.', 'Dispute OPEN.'),
            ('05.3', 'Maker submit resolution', 'Submit REFUND_REQUIRED.', 'Status PENDING_APPROVAL.'),
            ('05.4', 'Checker login', 'Login checker user.', 'Token returned.'),
            ('05.5', 'Checker approval', 'Approve resolution.', 'Status RESOLVED_REFUND.'),
            ('05.6', 'Refund record', 'Fetch dispute detail.', 'Refund id/ref present.'),
            ('05.7', 'Refund completion', 'Verify refund status.', 'COMPLETED.'),
            ('05.8', 'Transfer markers', 'Verify original transfer confirmation/settlement confidence.', 'Marked DISPUTED as designed.'),
            ('05.9', 'Health after refund', 'Call operations health.', 'HEALTHY or no unresolved outbox failures.'),
        ]
    },
    {
        'id': '06', 'name': 'DRS Matrix', 'count': 8,
        'objective': 'Prove DRS status matrix, SLA behavior, evidence, and manual escalation paths work.',
        'scenarios': [
            ('06.1', 'DRS dashboard/list', 'List DRS disputes.', 'Endpoint returns expected schema.'),
            ('06.2', 'Technical error scenario', 'Create/identify technical error dispute.', 'Correct type/status.'),
            ('06.3', 'Destination dispute scenario', 'Create destination/post-settlement dispute.', 'Correct type/status.'),
            ('06.4', 'SLA deadline', 'Verify SLA deadline exists and is reasonable.', 'Deadline populated.'),
            ('06.5', 'Maker-checker DRS', 'Submit and approve DRS resolution.', 'Checker controls enforced.'),
            ('06.6', 'Evidence report', 'Download evidence report/CSV where supported.', 'Evidence is complete.'),
            ('06.7', 'Scheduler behavior', 'Verify overdue non-settled technical errors escalate, not refund.', 'Bad auto-refund count zero.'),
            ('06.8', 'DRS audit trail', 'Fetch timeline/audit.', 'Resolution actions recorded.'),
        ]
    },
    {
        'id': '07', 'name': 'Settlement', 'count': 10,
        'objective': 'Prove settlement cycle, DNS/STGS/RTGS style files, callbacks, positions, and reports are consistent.',
        'scenarios': [
            ('07.1', 'Settlement dashboard', 'Fetch settlement dashboard/list.', 'Endpoint returns cycles/positions.'),
            ('07.2', 'Create/close cycle', 'Use existing or create settlement cycle.', 'Cycle closed successfully.'),
            ('07.3', 'Generate settlement positions', 'Fetch cycle positions.', 'Debit/credit positions balanced.'),
            ('07.4', 'Generate instruction/file', 'Generate/upload payment instruction if supported.', 'Instruction reference/file present.'),
            ('07.5', 'RTGS/STGS callback success', 'Submit or capture settlement callback.', 'Cycle/instruction marked settled.'),
            ('07.6', 'Settlement reports', 'Fetch CAMT005/CAMT006/CAMT054 reports.', 'Reports generated and downloadable.'),
            ('07.7', 'No double settlement', 'Retry callback or duplicate action.', 'Idempotent; no duplicate ledger movement.'),
            ('07.8', 'Failed callback path', 'Run failed settlement callback scenario.', 'Failure recorded and recoverable.'),
            ('07.9', 'Liquidity/positions view', 'Verify participant positions.', 'Amounts match settlement report.'),
            ('07.10', 'Settlement audit', 'Capture audit/timeline for cycle.', 'Maker/checker and settlement events recorded.'),
        ]
    },
    {
        'id': '08', 'name': 'Outbox Recovery', 'count': 8,
        'objective': 'Prove outbox reliability, stuck event detection, retry/replay, and health recovery.',
        'scenarios': [
            ('08.1', 'Operations health before', 'Capture /api/operations/health.', 'Baseline recorded.'),
            ('08.2', 'Outbox failures list', 'Fetch failed outbox events.', 'Failures listed or zero.'),
            ('08.3', 'Dead letters list', 'Fetch /v1/operations/dead-letters.', 'Dead letters listed or zero.'),
            ('08.4', 'Manual retry single', 'Retry one failed/stuck event if present.', 'Event moves to SUCCESS or expected terminal state.'),
            ('08.5', 'Retry all/recover all', 'Run recover-all where safe.', 'Failed count reduced.'),
            ('08.6', 'Replay maker-checker', 'Request/approve/execute replay for dead-letter where applicable.', 'Maker-checker enforced.'),
            ('08.7', 'Outbox health after', 'Capture operations health again.', 'HEALTHY or explained residual failures.'),
            ('08.8', 'No duplicate side effects', 'Verify retried event idempotency.', 'No duplicate transfer/settlement record.'),
        ]
    },
    {
        'id': '09', 'name': 'Config Hardcode Check', 'count': 7,
        'objective': 'Prove production-critical settings are externalized and secrets are not hardcoded in source.',
        'scenarios': [
            ('09.1', 'Java source scan', 'Scan src/main/java for hostnames, passwords, keys, buckets.', 'No prod secrets hardcoded.'),
            ('09.2', 'Resource scan', 'Scan application.yml/resources.', 'Only safe local defaults; prod externalized.'),
            ('09.3', 'Docker compose env', 'Review compose env mapping.', 'Critical settings read from env.'),
            ('09.4', 'UAT override check', 'Capture /opt/switching/docker-compose.override.yml without secrets.', 'Required env vars present.'),
            ('09.5', 'Certificate/key paths', 'Verify CA key/cert path config comes from env.', 'No source hardcoding.'),
            ('09.6', 'Secret exposure check', 'Ensure evidence does not include private keys/password dumps.', 'Secrets redacted.'),
            ('09.7', 'Config change principle', 'Validate change can be made in .env/config without source edit.', 'Documented and tested.'),
        ]
    },
    {
        'id': '10', 'name': 'Observability', 'count': 9,
        'objective': 'Prove operators can detect, diagnose, and audit production issues quickly.',
        'scenarios': [
            ('10.1', 'Health endpoints', 'Capture actuator and operations health.', 'Both usable.'),
            ('10.2', 'Dashboard summary', 'Capture operations/dashboard endpoints.', 'Summary returns current counts.'),
            ('10.3', 'Application logs', 'Capture recent app logs around tests.', 'Errors understood; no unexplained fatal errors.'),
            ('10.4', 'Container logs/state', 'Capture docker ps/log tail.', 'Services stable.'),
            ('10.5', 'Audit logs', 'Fetch audit entries for admin changes.', 'Promotion/policy/connector events visible.'),
            ('10.6', 'Metrics endpoint', 'Capture Prometheus/actuator metrics if exposed.', 'Metrics scrape works.'),
            ('10.7', 'Error correlation', 'Verify requestId from API error appears in logs.', 'RequestId searchable.'),
            ('10.8', 'Scheduler verification', 'Verify key schedulers do not generate bad events.', 'No unexpected scheduler errors.'),
            ('10.9', 'Evidence package integrity', 'Generate SHA256SUMS and summary.', 'Evidence immutable enough for review.'),
        ]
    },
]

total = sum(s['count'] for s in sections)


def p(text, style='BodyX'):
    return Paragraph(text, styles[style])


def bullet(items):
    out = []
    for item in items:
        out.append(p('- ' + item, 'BodyX'))
    return out


def header_footer(canvas, doc):
    canvas.saveState()
    canvas.setFont('Helvetica', 8)
    canvas.setFillColor(HexColor('#64748b'))
    canvas.drawString(MARGIN, 0.9*cm, 'LAOFP Switching - Production Readiness Scenario Checklist')
    canvas.drawRightString(PAGE_W - MARGIN, 0.9*cm, f'Page {doc.page}')
    canvas.restoreState()

story = []
story.append(Spacer(1, 2.0*cm))
story.append(p('Switching Production Readiness Scenario Checklist', 'CoverTitle'))
story.append(p('Detailed pre-production test matrix for UAT-to-Production sign-off', 'CoverSub'))
story.append(p(f'Generated: {datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%SZ")}', 'CoverSub'))
story.append(Spacer(1, 0.8*cm))
summary_data = [
    [p('Scope', 'HeaderCell'), p('Value', 'HeaderCell')],
    [p('Main sections', 'CellBold'), p(str(len(sections)), 'Cell')],
    [p('Minimum sub-scenarios', 'CellBold'), p(str(total), 'Cell')],
    [p('Recommended decision', 'CellBold'), p('Production sign-off only after every mandatory scenario is PASS or has an approved risk exception.', 'Cell')],
    [p('Primary evidence root', 'CellBold'), p('runtime-evidence/prod-readiness-YYYYMMDD-HHMMSS/', 'Cell')],
]
t = Table(summary_data, colWidths=[4.2*cm, 11.5*cm])
t.setStyle(TableStyle([
    ('BACKGROUND', (0,0), (-1,0), HexColor('#1e293b')),
    ('GRID', (0,0), (-1,-1), 0.25, HexColor('#cbd5e1')),
    ('VALIGN', (0,0), (-1,-1), 'TOP'),
    ('BACKGROUND', (0,1), (-1,-1), HexColor('#f8fafc')),
    ('LEFTPADDING', (0,0), (-1,-1), 7),
    ('RIGHTPADDING', (0,0), (-1,-1), 7),
    ('TOPPADDING', (0,0), (-1,-1), 6),
    ('BOTTOMPADDING', (0,0), (-1,-1), 6),
]))
story.append(t)
story.append(Spacer(1, 0.7*cm))
story += bullet([
    'This document defines what should be tested before production go-live.',
    'Each scenario should produce machine-readable evidence, terminal/API output formatted with jq, and a human-readable RESULT.md.',
    'A scenario is PASS only when the expected status, state transition, and audit/evidence files are present.',
])
story.append(PageBreak())

story.append(p('Executive Summary', 'SectionTitle'))
story.append(p(f'The production readiness gate is organized into 10 main sections and {total} minimum sub-scenarios. These scenarios cover deployment identity, authentication, payment flows, retries, refunds, DRS, settlement, outbox recovery, configuration hygiene, and observability.', 'BodyX'))

overview = [[p('Section', 'HeaderCell'), p('Area', 'HeaderCell'), p('Sub-scenarios', 'HeaderCell'), p('Gate intent', 'HeaderCell')]]
for s in sections:
    overview.append([p(s['id'], 'CellBold'), p(s['name'], 'CellBold'), p(str(s['count']), 'Cell'), p(s['objective'], 'Cell')])
t = Table(overview, colWidths=[1.6*cm, 4.0*cm, 2.2*cm, 8.0*cm], repeatRows=1)
t.setStyle(TableStyle([
    ('BACKGROUND', (0,0), (-1,0), HexColor('#334155')),
    ('GRID', (0,0), (-1,-1), 0.25, HexColor('#cbd5e1')),
    ('VALIGN', (0,0), (-1,-1), 'TOP'),
    ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, HexColor('#f8fafc')]),
    ('LEFTPADDING', (0,0), (-1,-1), 5),
    ('RIGHTPADDING', (0,0), (-1,-1), 5),
    ('TOPPADDING', (0,0), (-1,-1), 5),
    ('BOTTOMPADDING', (0,0), (-1,-1), 5),
]))
story.append(t)
story.append(PageBreak())

story.append(p('Evidence Folder Standard', 'SectionTitle'))
story.append(p('Use one immutable folder for every full production readiness run. The recommended structure is:', 'BodyX'))
folder_lines = [
    'runtime-evidence/prod-readiness-YYYYMMDD-HHMMSS/',
    '  00-manifest.json',
    '  01-deployment-state/',
    '  02-auth-security/',
    '  03-payment-happy-path/',
    '  04-payment-failure-retry/',
    '  05-refund-reversal/',
    '  06-drs-matrix/',
    '  07-settlement/',
    '  08-outbox-recovery/',
    '  09-config-hardcode-check/',
    '  10-observability/',
    '  11-score.json',
    '  SUMMARY.md',
    '  SHA256SUMS',
]
code_table = Table([[p('<br/>'.join(folder_lines), 'Cell')]], colWidths=[15.8*cm])
code_table.setStyle(TableStyle([
    ('BACKGROUND', (0,0), (-1,-1), HexColor('#f1f5f9')),
    ('BOX', (0,0), (-1,-1), 0.5, HexColor('#cbd5e1')),
    ('LEFTPADDING', (0,0), (-1,-1), 8),
    ('RIGHTPADDING', (0,0), (-1,-1), 8),
    ('TOPPADDING', (0,0), (-1,-1), 8),
    ('BOTTOMPADDING', (0,0), (-1,-1), 8),
]))
story.append(code_table)
story.append(Spacer(1, 0.3*cm))
story += bullet([
    'Every API response should be stored as .json or .txt with HTTP status when useful.',
    'Every section should contain a RESULT.md with Status, Evidence, Conclusion, and Known Risks.',
    'A final SHA256SUMS file should cover every evidence file except SHA256SUMS itself.',
    'Never store private keys, passwords, raw bearer tokens, or production secrets in evidence.',
])
story.append(PageBreak())

for s in sections:
    story.append(p(f"{s['id']} - {s['name']}", 'SectionTitle'))
    story.append(p(f"Objective: {s['objective']}", 'BodyX'))
    story.append(p(f"Minimum scenarios: {s['count']}", 'BodyX'))
    data = [[p('ID', 'HeaderCell'), p('Scenario', 'HeaderCell'), p('Evidence to collect', 'HeaderCell'), p('Pass criteria', 'HeaderCell')]]
    for sid, name, evidence, criteria in s['scenarios']:
        data.append([p(sid, 'CellBold'), p(name, 'CellBold'), p(evidence, 'Cell'), p(criteria, 'Cell')])
    table = Table(data, colWidths=[1.35*cm, 4.05*cm, 5.3*cm, 5.1*cm], repeatRows=1)
    table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), HexColor('#1e293b')),
        ('GRID', (0,0), (-1,-1), 0.25, HexColor('#cbd5e1')),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, HexColor('#f8fafc')]),
        ('LEFTPADDING', (0,0), (-1,-1), 4),
        ('RIGHTPADDING', (0,0), (-1,-1), 4),
        ('TOPPADDING', (0,0), (-1,-1), 4),
        ('BOTTOMPADDING', (0,0), (-1,-1), 4),
    ]))
    story.append(table)
    story.append(Spacer(1, 0.25*cm))
    story.append(PageBreak())

story.append(p('Production Sign-off Decision Rules', 'SectionTitle'))
story += bullet([
    f'Mandatory PASS target: {total}/{total} scenarios, unless a formal risk exception is approved.',
    'No open P0/P1 security, data integrity, settlement, or retry defects.',
    'No unexplained 500 errors in the final evidence run.',
    'All production secrets and environment-specific values must be externalized.',
    'All core money-moving workflows must have audit trail and replay/retry evidence.',
    'All evidence folders must include SUMMARY.md, RESULT.md files, and SHA256SUMS.',
])
story.append(Spacer(1, 0.3*cm))
story.append(p('Recommended score interpretation:', 'SubTitle'))
score_data = [[p('Score', 'HeaderCell'), p('Decision', 'HeaderCell')],
              [p('95-100', 'CellBold'), p('Ready for production sign-off if no critical risks remain.', 'Cell')],
              [p('85-94', 'CellBold'), p('Ready for continued UAT or limited pilot; production requires exception review.', 'Cell')],
              [p('70-84', 'CellBold'), p('Not production ready; resolve material gaps first.', 'Cell')],
              [p('< 70', 'CellBold'), p('Blocked; core controls are incomplete.', 'Cell')]]
t = Table(score_data, colWidths=[3.0*cm, 12.8*cm])
t.setStyle(TableStyle([
    ('BACKGROUND', (0,0), (-1,0), HexColor('#334155')),
    ('GRID', (0,0), (-1,-1), 0.25, HexColor('#cbd5e1')),
    ('VALIGN', (0,0), (-1,-1), 'TOP'),
    ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, HexColor('#f8fafc')]),
    ('LEFTPADDING', (0,0), (-1,-1), 6),
    ('RIGHTPADDING', (0,0), (-1,-1), 6),
    ('TOPPADDING', (0,0), (-1,-1), 5),
    ('BOTTOMPADDING', (0,0), (-1,-1), 5),
]))
story.append(t)

pdf = SimpleDocTemplate(str(OUT), pagesize=A4, rightMargin=MARGIN, leftMargin=MARGIN, topMargin=1.35*cm, bottomMargin=1.35*cm)
pdf.build(story, onFirstPage=header_footer, onLaterPages=header_footer)
print(OUT)
