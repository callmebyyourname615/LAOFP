# Lovable Prompt — LaoFPS SMOS Web Portal

> Copy the prompt below into a fresh Lovable project and start building.

---

## 📋 The Prompt (paste this into Lovable)

```
Build a modern dark-themed admin web portal called "LaoFPS SMOS — Scheme Management & Operations Subsystem" for the Lao Fast Payment Switching system, the national real-time payment switch of Laos.

This is a B2B operational dashboard used by central bank operators, settlement officers, dispute officers, risk officers, and participant administrators. It is NOT a consumer app.

================================================================
DESIGN SYSTEM
================================================================

Theme: Dark mode only. Modern, professional, fintech-grade. Inspired by Linear, Vercel, Stripe Dashboard, and Datadog.

Colors:
- Background base    : #0A0B0F (near-black, slight blue tint)
- Surface elevated   : #14161D
- Surface highest    : #1C1F28
- Border subtle      : #252832
- Border strong      : #363A45
- Text primary       : #E8EAED
- Text secondary     : #9CA3AF
- Text muted         : #6B7280
- Accent primary     : #6366F1 (indigo — for primary actions, active nav)
- Accent success     : #10B981 (emerald)
- Accent warning     : #F59E0B (amber)
- Accent danger      : #EF4444 (red)
- Accent info        : #3B82F6 (blue)
- Lao national accent: #C8102E (deep red — use sparingly for branding only)

Typography:
- Primary font: Inter
- Monospace  : JetBrains Mono (for transaction IDs, hashes, JSON viewers)
- Headings   : 600 weight, tight letter-spacing
- Body       : 400 weight, 14px default, 1.5 line-height

Layout:
- Persistent left sidebar 240px wide with collapsible groups
- Top bar 56px: global search, environment switcher (UAT/Prod), notifications bell, user menu
- Main content uses 24px padding, max-width 1600px
- Card-based content blocks with 12px border radius, subtle border, no heavy shadows
- Tables: dense rows (40px height), zebra optional, sticky header

Common UI elements:
- Status pills (rounded, small caps): SUCCESS / PENDING / FAILED / WARNING / NEUTRAL
- Severity badges: CRITICAL / HIGH / MEDIUM / LOW / INFO
- Empty states with icon + title + helper text + primary action
- Skeleton loaders for tables and cards
- Toast notifications top-right
- Confirmation modals for destructive actions
- Side drawer for record details (slides from right, 600px wide)
- Maker-checker approval card pattern: yellow banner "Pending checker approval"

================================================================
AUTHENTICATION
================================================================

Routes:
- /login                — username + password
- /login/mfa            — 6-digit TOTP code
- /logout

After login, route to /dashboard. Show user name + role pill in top-right user menu.

================================================================
USER ROLES (RBAC)
================================================================

Mock 8 roles, restrict sidebar items per role:
1. SYSTEM_ADMIN       — sees everything
2. OPS_ADMIN          — operations + monitoring
3. SETTLEMENT_OFFICER — settlement + reports
4. DISPUTE_OFFICER    — dispute cases only
5. RISK_OFFICER       — risk + AML
6. AUDITOR            — read-only across all
7. PARTICIPANT_ADMIN  — own participant config only
8. READ_ONLY          — dashboards only

================================================================
NAVIGATION — LEFT SIDEBAR (grouped)
================================================================

▸ OVERVIEW
  • Dashboard Home (/dashboard)

▸ GOVERNANCE
  • Participant Management            (/governance/participants)
  • Participant Certification         (/governance/certifications)
  • Service Enrollment                (/governance/service-enrollment)
  • Scheme Rules                      (/governance/scheme-rules)
  • Decision Authority                (/governance/decision-authority)

▸ BUSINESS ADMINISTRATION
  • User Management                   (/admin/users)
  • Roles & Permissions               (/admin/roles)
  • Fee Management                    (/admin/fees)
  • Promotion Management              (/admin/promotions)
  • Currency Management               (/admin/currencies)
  • Biller Management                 (/admin/billers)
  • Bilateral Agreements              (/admin/bilateral-agreements)
  • Cross-Border Partners             (/admin/cross-border-partners)

▸ CONFIGURATION
  • Transaction Parameters            (/config/transaction)
  • Settlement Parameters             (/config/settlement)
  • Dispute Parameters                (/config/dispute)
  • Risk Rules                        (/config/risk-rules)
  • Notification Templates            (/config/notifications)
  • Service Parameters                (/config/service-parameters)

▸ OPERATIONAL
  • Transaction Dashboard             (/ops/transactions)
  • Settlement Dashboard              (/ops/settlement)
  • Risk Dashboard                    (/ops/risk)
  • Participant Dashboard             (/ops/participants)
  • Infrastructure Dashboard          (/ops/infrastructure)
  • Cross-Border Dashboard            (/ops/cross-border)
  • DR Dashboard                      (/ops/disaster-recovery)
  • Incident Management               (/ops/incidents)
  • Exception Monitoring              (/ops/exceptions)
  • Participant Communications        (/ops/communications)

▸ REPORTING
  • Regulatory Reports                (/reporting/regulatory)
  • Settlement Reports                (/reporting/settlement)
  • Dispute Reports                   (/reporting/disputes)
  • AML / Sanctions Reports           (/reporting/aml)
  • Scheduled Report Delivery         (/reporting/delivery)

▸ INFRASTRUCTURE
  • Health Monitoring                 (/infra/health)
  • Capacity Monitoring               (/infra/capacity)
  • One-Click DR Switchover           (/infra/dr-switchover)

▸ COMPLIANCE
  • Audit Trail                       (/compliance/audit)
  • Maker-Checker Queue               (/compliance/approvals)

▸ SETTINGS
  • My Profile                        (/settings/profile)
  • API Keys                          (/settings/api-keys)

================================================================
PAGE SPECIFICATIONS (build these in priority order)
================================================================

╔══════════════════════════════════════════════════════════════╗
║ PAGE 1 — Dashboard Home (/dashboard)                          ║
╚══════════════════════════════════════════════════════════════╝

Top row: 6 KPI cards
  • Today's transactions      : value + sparkline + delta vs yesterday
  • Today's volume (LAK)      : formatted "12.4 B LAK"
  • Active participants       : 18 / 22
  • Settlement SLA            : 99.8% on-time
  • Open incidents            : 3 (red pill if > 0)
  • System health             : "All systems operational" (green dot)

Second row: 2 charts side-by-side
  • Hourly transaction volume — area chart, last 24h
  • Channel breakdown         — donut (A2A / QR / Bill / Cross-Border)

Third row: 2 tables
  • Recent failed transactions (last 10)
  • Pending maker-checker approvals (top 5 with "Review" button)

╔══════════════════════════════════════════════════════════════╗
║ PAGE 2 — Participant Management (/governance/participants)    ║
╚══════════════════════════════════════════════════════════════╝

Header: "Participants" + button "Onboard New Participant"
Filters: status (Active / Suspended / Pending), tier (Tier 1 / 2 / 3), search by name/code
Table columns:
  Code | Name | Tier | Status | Services Enrolled | Last Activity | Actions (View / Edit / Suspend)

Onboarding flow uses a 4-step wizard:
  1. Identity & Contacts
  2. Technical Configuration (endpoints, mTLS cert upload, IP allowlist)
  3. Service Enrollment (checkbox list: A2A, QR, Bill, Tax, Cross-Border, RTP)
  4. Review & Submit (becomes maker-checker request)

Detail page tabs: Overview | Connectivity | Services | Fees | Bilateral | Certificates | Audit Log

╔══════════════════════════════════════════════════════════════╗
║ PAGE 3 — Settlement Dashboard (/ops/settlement)               ║
╚══════════════════════════════════════════════════════════════╝

Top KPIs (4 cards):
  • Pending net positions    : 7
  • Today's settled volume   : 8.2 B LAK
  • SLA breached cycles      : 0
  • Failed settlements (7d)  : 2

Cycle status grid (4 columns for 4 cycles):
  Cycle 1 (08:45) — CLOSED — total: 2.1B LAK — participants: 18/18
  Cycle 2 (11:45) — IN_PROGRESS — countdown timer
  Cycle 3 (15:15) — SCHEDULED
  Cycle 4 (19:45) — SCHEDULED

Pending Net Positions table:
  Participant | Net Position | Direction (DR/CR) | Cycle | Approval Status | Action

Each row with "Approve" button opens maker-checker drawer.

Bottom: Recent settlement events (timeline)

╔══════════════════════════════════════════════════════════════╗
║ PAGE 4 — Risk Dashboard (/ops/risk)                           ║
╚══════════════════════════════════════════════════════════════╝

Top KPIs:
  • Active alerts            : 24 (8 critical, 12 high, 4 medium)
  • Velocity violations 24h  : 12
  • Sanctions hits pending   : 3 (red pill)
  • Avg case age             : 4.2 hours

Charts:
  • Risk score distribution (histogram)
  • Alerts by type over time (stacked area)

Alert queue table:
  Severity | Type | Participant | Description | Created | Status | Assignee | Action

Click row → drawer with: full context, transaction history, recommended action, "Escalate" / "Clear" / "Investigate" buttons.

╔══════════════════════════════════════════════════════════════╗
║ PAGE 5 — Cross-Border Dashboard (/ops/cross-border)           ║
╚══════════════════════════════════════════════════════════════╝

Top: 4 corridor status cards (Bakong KH / NAPAS VN / PromptPay TH / UPI IN)
Each card shows:
  • Status dot (green/amber/red)
  • Adapter latency P95
  • Today's volume
  • Today's failed rail messages
  • FX rate snapshot

Charts:
  • Inbound vs Outbound by corridor (grouped bar)
  • Reconciliation status per corridor (donut: matched/unmatched/pending)

Failed rail messages table:
  Rail | Direction | External Ref | Failure Reason | Created | Retry Count | Action (Retry / DLQ / Investigate)

╔══════════════════════════════════════════════════════════════╗
║ PAGE 6 — DR Dashboard (/ops/disaster-recovery)                ║
╚══════════════════════════════════════════════════════════════╝

Top:
  • Primary region        : ap-southeast-1 (green dot)
  • Standby region        : ap-southeast-2 (green dot)
  • Replication lag       : 0.8s
  • Last DR drill         : 12 days ago — PASSED

Big red button: "INITIATE DR SWITCHOVER" — opens 3-step confirmation modal:
  1. Confirm reason + ticket number
  2. Type "SWITCHOVER" to confirm
  3. Two-person approval (current user + selected approver)

Timeline below shows: Last 5 DR events with timestamps.

╔══════════════════════════════════════════════════════════════╗
║ PAGE 7 — User Management (/admin/users)                       ║
╚══════════════════════════════════════════════════════════════╝

Visible only to SYSTEM_ADMIN.

Table: Username | Full Name | Email | Roles | Status | Last Login | MFA | Actions

Header: "Invite User" button.

Invite flow:
  1. Email + full name
  2. Pick roles (multi-select from 8)
  3. Optional: assign participant (if PARTICIPANT_ADMIN role)
  4. Submit → user receives invite email with password setup link

Detail drawer: tabs Overview | Roles | Sessions | Audit Log | Reset Password / Disable MFA / Lock

╔══════════════════════════════════════════════════════════════╗
║ PAGE 8 — Maker-Checker Queue (/compliance/approvals)          ║
╚══════════════════════════════════════════════════════════════╝

Two tabs:
  - "Awaiting My Approval" (default)
  - "My Submissions"

Each row card:
  • Request type (e.g., "Suspend Participant: ACLEDA")
  • Maker name + timestamp
  • Payload diff viewer (JSON tree, highlighted changes)
  • "Approve" (primary) and "Reject with reason" buttons
  • Reject opens reason textarea modal

╔══════════════════════════════════════════════════════════════╗
║ PAGE 9 — Promotion Management (/admin/promotions)             ║
╚══════════════════════════════════════════════════════════════╝

Table: Code | Name | Type (Waiver/Cashback/Sponsored) | Funder | Budget | Used % | Start | End | Status

"New Promotion" wizard:
  1. Basics (code, name, type, funder)
  2. Eligibility rules — SpEL expression editor with syntax highlighting + variable reference card
  3. Budget & limits
  4. Priority & schedule
  5. Review (creates maker-checker request)

Detail page: Eligibility Rules | Budget Burn-down Chart | Applied Promotions Log

╔══════════════════════════════════════════════════════════════╗
║ PAGE 10 — Fee Management (/admin/fees)                        ║
╚══════════════════════════════════════════════════════════════╝

Tariff scheme table grouped by service.
Each row: Service | Channel | Fee Type (Fixed/Tiered/Percent) | Charging Model (Sender/Receiver/Shared) | Amount | Effective From

Click row → expand to show tiered ranges table.
Edit creates maker-checker request.

╔══════════════════════════════════════════════════════════════╗
║ PAGE 11 — Audit Trail (/compliance/audit)                     ║
╚══════════════════════════════════════════════════════════════╝

Powerful filters: actor, resource, action, date range, IP.
Table with monospace event fields, "Chain verified ✓" badge at top.
Each event row expands inline to show full payload diff (JSON).
Export buttons: CSV / JSON / Signed PDF.

================================================================
MOCK DATA REQUIREMENTS
================================================================

Generate realistic mock data for at least:
- 22 participants (Lao banks like BCEL, LDB, APB, JDB, LVB, ST Bank, ACLEDA Laos, etc.)
- 50 recent transactions (mix of A2A, QR, Bill)
- 4 settlement cycles per day
- 20 risk alerts (varied severity)
- 8 pending maker-checker requests
- 10 users with different roles
- 5 active promotions
- 4 cross-border corridors with status

Use these BRD-mandated currency codes: LAK (primary), USD, THB, KHR, VND, CNY.
Format LAK amounts with "B" / "M" / "K" suffix for large numbers.

================================================================
INTERACTIONS & MICRO-DETAILS
================================================================

- Every table row clickable → opens detail drawer (slide-in right)
- Cmd+K opens global search across participants / transactions / users
- Top bar shows environment pill: "PRODUCTION" (red border) vs "UAT" (amber border) — switchable
- Every destructive action requires typed confirmation
- Empty states must explain how to add data, with primary CTA
- Loading states: skeleton, not spinners
- 404 page: "This route requires SYSTEM_ADMIN" or "Not found" based on auth
- Responsive: collapse sidebar to icons on screens < 1280px, hide it below 1024px (this is a desktop-first ops tool)
- Keyboard shortcuts panel (press "?"): G+D = dashboard, G+P = participants, etc.

================================================================
SAMPLE COPY (tone: professional, terse, no emojis except status dots)
================================================================

Page titles use sentence case.
Button labels are verbs: "Approve", "Suspend participant", "Initiate switchover".
Error messages explain WHAT failed and WHAT to do next.

================================================================
BUILD ORDER
================================================================

Start with the design system + sidebar + Dashboard Home (Page 1).
Then build Participant Management (Page 2), Settlement Dashboard (Page 3), Maker-Checker Queue (Page 8) — these are the 4 most-used screens by operators.

Use React + TypeScript + Tailwind + shadcn/ui + lucide-react icons + Recharts for charts. Keep state local with React hooks; no Redux. Mock data in a /mocks folder.

The result should feel like a 2026 enterprise SaaS — confident, fast, dark, and dense with information without being cluttered.
```

---

## 📌 Tips การใช้กับ Lovable

1. **Paste prompt ทั้งหมดในครั้งเดียว** — Lovable จะสร้าง multi-page app
2. **หลังจาก initial build เสร็จ** ให้ค่อย refine ทีละ page เช่น:
   > "Now polish the Settlement Dashboard with real-time updating cycle progress bars and add a maker-checker drawer when clicking Approve"
3. **เปลี่ยนสี / spacing** ได้ทีหลังโดยพิมพ์:
   > "Tighten table row height to 36px and use #6366F1 for primary buttons"
4. **เพิ่มหน้าใหม่ทีหลัง** โดย reference page list ด้านบน:
   > "Build the Risk Dashboard (Page 4) per the original spec"

## 📎 BRD Trace

| Section ใน prompt | อ้างอิง BRD |
|---|---|
| Sidebar 8 modules | **BRD 7.1.8** — SMOS Responsibilities |
| 7 dashboards | **BRD 8.1** — Web-Based Monitoring |
| 8 roles | **BRD 7.6 / BR-SMOS-014–018** |
| Maker-checker | **BRD 7.6** — sensitive function approval |
| One-click DR | **BRD 11 / BR-SMOS-055** |
| Notification channels | **BRD 8.3** |
| Promotion DSL editor | **BRD 7.2.10.3 / BR-SMOS-032** |
| Bilateral agreement | **BRD 7.2.10.8** |

---

*Generated 2026-06-22 — update prompt if SMOS scope evolves*
