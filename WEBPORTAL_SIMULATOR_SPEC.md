# Switching Web Portal — Simulator Spec

> **Purpose**: Web portal สำหรับจำลองการทำงานของ Financial Switching System (ISO 20022) เพื่อให้ทีม dev/QA/demo ทดสอบ flow ได้ครบโดยไม่ต้องใช้ Postman หรือ curl

---

## 1. Overview & Design Direction

| Property | Value |
|----------|-------|
| **Style** | Dark theme, fintech/banking look — ดูน่าเชื่อถือ เหมือน Bloomberg Terminal |
| **Primary color** | Deep navy `#0F172A` + Accent blue `#3B82F6` |
| **Font** | Inter (UI) + JetBrains Mono (JSON/XML payload) |
| **Layout** | Left sidebar navigation + Main content area |
| **Target users** | Developer, QA Engineer, Bank Integration Team, Ops |

---

## 2. Sitemap — Pages ทั้งหมด

```
/                    → Dashboard (Overview)
/simulator           → Transaction Simulator (Main Feature)
  /simulator/inquiry       → Step 1: Create Inquiry
  /simulator/transfer      → Step 2: Create Transfer
  /simulator/inbound       → Inbound PACS.008 Tester
/transactions        → Transaction Explorer
  /transactions/:ref       → Transfer Trace Detail
/inquiries           → Inquiry Explorer
  /inquiries/:ref          → Inquiry Detail
/iso-messages        → ISO Message Viewer
  /iso-messages/:key       → Message Detail + XML Payload
/participants        → Bank Registry
/routing             → Routing Rules
/operations          → Operations & Admin
  /operations/outbox       → Outbox Monitor
  /operations/health       → System Health
/audit               → Audit Log Explorer
```

---

## 3. Page Specifications

---

### 3.1 Dashboard `/`

**Purpose**: Real-time overview ของระบบทั้งหมด

#### Components

**Metric Cards (Row 1)**

| Card | Value | Icon |
|------|-------|------|
| Total Transfers | count | arrow-right-left |
| Success | count + % | check-circle (green) |
| Failed | count + % | x-circle (red) |
| Pending | count + % | clock (yellow) |

**Metric Cards (Row 2)**

| Card | Value | Icon |
|------|-------|------|
| Total Inquiries | count | search |
| Eligible | count + % | check (blue) |
| Not Eligible | count + % | ban (orange) |
| Outbox Pending | count | inbox |

**Charts Section**
- **Transfer Volume (24h)** — Bar chart แบ่งตาม hour แสดง Success / Failed
- **Transfer Status Distribution** — Donut chart: RECEIVED / SUCCESS / FAILED
- **Bank Activity Table** — Top banks by transfer count (sourceBank, destinationBank, txCount)

**Quick Actions**
- Button: `[+ New Simulation]` → ไปหน้า /simulator
- Button: `[View Stuck Outbox]` → ไปหน้า /operations/outbox

---

### 3.2 Transaction Simulator `/simulator`

**Purpose**: หน้าหลักสำหรับจำลอง flow ทั้งหมด — มี stepper 2 steps

#### Layout
```
┌─────────────────────────────────────────────────┐
│  Transaction Simulator                          │
│  ─────────────────────────────────────────────  │
│  Step 1: Inquiry  →  Step 2: Transfer           │
│                                                 │
│  [Left: Input Form]  [Right: Live Response]     │
└─────────────────────────────────────────────────┘
```

---

#### 3.2.1 Step 1 — Create Inquiry

**Left Panel: Inquiry Form**

```
Source Bank *          [Dropdown — list from /api/participants?status=ACTIVE]
Destination Bank *     [Dropdown — list from /api/participants?status=ACTIVE]
Creditor Account *     [Text input] e.g. "1234567890"
Amount                 [Number input] e.g. "10000.00"
Currency               [Dropdown] THB / USD / EUR
Client Inquiry ID      [Text input, optional] auto-generated if empty
Reference              [Text input, optional]
                                          [POST Inquiry]
```

**Right Panel: Live Response**

```
┌─ Response ──────────────────────────────────────┐
│  Status: 200 OK              [timestamp]        │
│  ─────────────────────────────────────────────  │
│  inquiryRef:  INQ-xxxxx                         │
│  status:      ELIGIBLE ✓                        │
│  accountFound: true                             │
│  bankAvailable: true                            │
│  eligibleForTransfer: true                      │
│  destinationAccountName: "John Doe"             │
│  ─────────────────────────────────────────────  │
│  [Raw JSON ▼]                                   │
│  ─────────────────────────────────────────────  │
│  Status Flow:   RECEIVED → ELIGIBLE             │
└─────────────────────────────────────────────────┘
```

**On ELIGIBLE**: Show badge สีเขียว + auto-fill ค่าไปยัง Step 2 + Enable `[Proceed to Transfer →]` button

**On NOT_ELIGIBLE**: Show badge สีแดง + แสดงเหตุผล (bankAvailable/accountFound) + Disable proceed button

---

#### 3.2.2 Step 2 — Create Transfer

**Left Panel: Transfer Form**

```
← From Inquiry: INQ-xxxxx (ELIGIBLE)           [Change]

Source Bank *          [Pre-filled, locked from inquiry]
Destination Bank *     [Pre-filled, locked from inquiry]
Debtor Account *       [Text input] source account
Creditor Account *     [Pre-filled from inquiry, locked]
Amount *               [Pre-filled from inquiry, editable]
Currency *             [Pre-filled from inquiry, locked]
Idempotency Key        [Auto-generated UUID] [Regenerate ↺]
Client Transfer ID     [Text input, optional]
Reference              [Text input, optional]
                                          [POST Transfer]
```

**Right Panel: Live Response**

```
┌─ Response ──────────────────────────────────────┐
│  Status: 201 Created         [timestamp]        │
│  ─────────────────────────────────────────────  │
│  transferRef:  TRF-xxxxx                        │
│  status:       RECEIVED                         │
│  message:      Accepted and queued              │
│  ─────────────────────────────────────────────  │
│  [Raw JSON ▼]                                   │
│  ─────────────────────────────────────────────  │
│  [View Full Trace →]   [Try Duplicate Request]  │
└─────────────────────────────────────────────────┘
```

**"Try Duplicate Request" button**: ส่ง request เดิมซ้ำทันที แสดงผล idempotency hit

---

#### 3.2.3 Transfer Trace Panel (popup/drawer)

เปิดหลังจาก transfer สร้างแล้ว แสดง real-time status polling ทุก 2 วินาที

```
┌─ Transfer Trace: TRF-xxxxx ─────────────────────┐
│                                                 │
│  Timeline                                       │
│  ──────────────────────────────────────────     │
│  ✓ RECEIVED      10:23:01.234    Transfer created
│  ✓ QUEUED        10:23:01.456    Outbox event created
│  ⏳ PROCESSING   10:23:01.789    Worker dispatching
│  ✓ SUCCESS       10:23:02.100    Bank confirmed         │
│                                                 │
│  Route Info                                     │
│  ──────────────────────────────────────────     │
│  Route Code:    ROUTE_KBANKA_SCBB_PACS008      │
│  Connector:     generic-http-connector-1        │
│                                                 │
│  ISO Message                                    │
│  ──────────────────────────────────────────     │
│  Type: PACS_008  │ Direction: OUTBOUND          │
│  Security: ENCRYPTED  │ Validation: VALID        │
│  [View XML Payload]                             │
│                                                 │
│  Outbox Event                                   │
│  ──────────────────────────────────────────     │
│  Status: SUCCESS   Retry Count: 0               │
└─────────────────────────────────────────────────┘
```

---

#### 3.2.4 Inbound PACS.008 Tester

**Purpose**: จำลอง remote bank ส่ง PACS.008 มาหา switching

**Left Panel: XML Builder**

```
Bank Code (X-Bank-Code header) *   [Text input]

Message Fields:
  messageId *           [Text, auto-generate]
  creationDateTime *    [DateTime, now by default]
  numberOfTransactions  [= "1", locked]
  instructionId *       [Text, auto-generate]
  endToEndId *          [Text, auto-generate]
  amount *              [Number]
  currency *            [Dropdown]
  debtorAgentBic *      [Text = bankCode from header]
  creditorAgentBic *    [Text]
  debtorAccount *       [Text]
  creditorAccount *     [Text]

[Generate XML]  [POST to /api/iso20022/pacs008]
```

**Raw XML Editor** (collapsible): แสดง XML ที่ generate ให้แก้ได้ก่อน submit

**Right Panel: PACS.002 Response**

```
┌─ PACS.002 Response ─────────────────────────────┐
│  Content-Type: application/xml                  │
│  ─────────────────────────────────────────────  │
│  Status: ACCEPTED ✓  / REJECTED ✗               │
│  transferRef: TRF-xxxxx                         │
│  ─────────────────────────────────────────────  │
│  [Raw XML ▼]                                    │
└─────────────────────────────────────────────────┘
```

---

### 3.3 Transaction Explorer `/transactions`

**Purpose**: ค้นหาและ browse transfers ทั้งหมด

#### Filter Bar

```
[Status ▼: ALL/RECEIVED/SUCCESS/FAILED]
[Source Bank ▼]  [Dest Bank ▼]
[Search: transferRef / inquiryRef...]
[From Date]  [To Date]              [Search]
```

#### Transfer Table

| # | Transfer Ref | Status | Source → Dest | Amount | Created | Actions |
|---|-------------|--------|---------------|--------|---------|---------|
| 1 | TRF-xxxxx | `SUCCESS` | KBANK → SCB | 10,000 THB | 10:23:01 | [View] |
| 2 | TRF-yyyyy | `FAILED` | BBL → KBANK | 5,000 THB | 10:22:45 | [View] [Retry] |
| 3 | TRF-zzzzz | `RECEIVED` | SCB → BBL | 25,000 THB | 10:22:30 | [View] |

- Status badge: SUCCESS=green, FAILED=red, RECEIVED=yellow
- Pagination / Load more
- Click row → `/transactions/:ref`

---

### 3.4 Transfer Detail `/transactions/:ref`

**Purpose**: Full trace และ timeline ของ 1 transfer

#### Layout: 2 columns

**Left Column — Info & Timeline**

```
Transfer: TRF-xxxxx                 Status: SUCCESS
────────────────────────────────────────────────────
Source Bank:      KBANK
Dest Bank:        SCB
Debtor Account:   0123456789
Creditor Account: 9876543210
Amount:           10,000.00 THB
Inquiry Ref:      INQ-xxxxx
Route Code:       ROUTE_KBANK_SCB_PACS008
Connector:        generic-http-connector-1
External Ref:     EXT-REF-FROM-BANK
────────────────────────────────────────────────────
Timeline
  ✓  RECEIVED     10:23:01.234
  ✓  SUCCESS      10:23:02.100
────────────────────────────────────────────────────
Status History
  RECEIVED → SUCCESS
```

**Right Column — ISO Messages & Outbox**

```
ISO Messages
──────────────────────────────────────────────────
  PACS_008 / OUTBOUND / ENCRYPTED / VALID   [View]
──────────────────────────────────────────────────
Outbox Event
──────────────────────────────────────────────────
  Status: SUCCESS  RetryCount: 0
  Created: 10:23:01
──────────────────────────────────────────────────
Audit Log
──────────────────────────────────────────────────
  TRANSFER_REQUEST_RECEIVED       10:23:01  API
  TRANSFER_VALIDATED_AGAINST_INQ  10:23:01  API
  TRANSFER_ROUTE_RESOLVED         10:23:01  API
  TRANSFER_CREATED                10:23:01  API
  OUTBOX_DISPATCH_STARTED         10:23:01  WORKER
  OUTBOX_DISPATCH_SUCCESS         10:23:02  WORKER
```

---

### 3.5 Inquiry Explorer `/inquiries`

**Purpose**: Browse และ search inquiry records

#### Filter Bar

```
[Status ▼: ALL/ELIGIBLE/NOT_ELIGIBLE]
[Source Bank ▼]  [Dest Bank ▼]
[Search: inquiryRef / creditorAccount...]  [Search]
```

#### Inquiry Table

| # | Inquiry Ref | Status | Source → Dest | Account | Eligible | Created |
|---|-------------|--------|---------------|---------|----------|---------|
| 1 | INQ-xxxxx | `ELIGIBLE` | KBANK → SCB | 9876... | ✓ | 10:22:58 |
| 2 | INQ-yyyyy | `NOT_ELIGIBLE` | BBL → KTB | 1111... | ✗ | 10:22:40 |

Click row → `/inquiries/:ref`

---

### 3.6 Inquiry Detail `/inquiries/:ref`

```
Inquiry: INQ-xxxxx                  Status: ELIGIBLE
────────────────────────────────────────────────────
Source Bank:        KBANK
Dest Bank:          SCB
Creditor Account:   9876543210
Account Name:       John Doe
Amount:             10,000.00 THB
Bank Available:     ✓ Yes
Account Found:      ✓ Yes
Eligible:           ✓ Yes
────────────────────────────────────────────────────
Status History
  RECEIVED → ELIGIBLE
────────────────────────────────────────────────────
Linked Transfers
  TRF-xxxxx  SUCCESS  10:23:01   [View]
```

---

### 3.7 ISO Message Viewer `/iso-messages`

**Purpose**: Browse ISO 20022 messages ทั้งหมด

#### Filter Bar

```
[Type ▼: ALL/PACS_008/PACS_002/PACS_028/PACS_004]
[Direction ▼: ALL/INBOUND/OUTBOUND]
[Security ▼: ALL/PLAIN/ENCRYPTED]
[Search: messageId / transferRef / inquiryRef...]  [Search]
```

#### Message Table

| # | Message ID | Type | Direction | Security | Validation | Transfer Ref | Created |
|---|-----------|------|-----------|----------|-----------|-------------|---------|
| 1 | MSG-TRF-x | PACS_008 | OUTBOUND | ENCRYPTED | VALID | TRF-xxx | 10:23:01 |

Click row → `/iso-messages/:key`

---

### 3.8 ISO Message Detail `/iso-messages/:key`

```
Message: MSG-TRF-xxxxx
────────────────────────────────────────────────────
Message ID:     MSG-TRF-xxxxx
End-to-End ID:  E2E-TRF-xxxxx
Type:           PACS_008
Direction:      OUTBOUND
Security:       ENCRYPTED        Validation: VALID
Transfer Ref:   TRF-xxxxx
────────────────────────────────────────────────────
XML Payload                              [Copy] [↗ Expand]
────────────────────────────────────────────────────
<?xml version="1.0" encoding="UTF-8"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.09">
  <FIToFICstmrCdtTrf>
    ...
  </FIToFICstmrCdtTrf>
</Document>
────────────────────────────────────────────────────
Encrypted Payload (AES)
[hidden — click to reveal if authorized]
```

---

### 3.9 Bank Registry `/participants`

**Purpose**: ดู/จัดการ banks ที่ลงทะเบียนใน system

#### Filter Bar

```
[Status ▼: ALL/ACTIVE/INACTIVE/MAINTENANCE/DISABLED]
[Type ▼: ALL/BANK/SWITCHING/SERVICE_PROVIDER]
[+ Add Bank]
```

#### Bank Table

| Bank Code | Name | Type | Status | Country | Currency | Registered | Actions |
|-----------|------|------|--------|---------|----------|-----------|---------|
| KBANK | Kasikorn Bank | BANK | `ACTIVE` | TH | THB | 2024-01-01 | [Edit] |
| SCB | Siam Commercial | BANK | `ACTIVE` | TH | THB | 2024-01-01 | [Edit] |
| BBL | Bangkok Bank | BANK | `MAINTENANCE` | TH | THB | 2024-01-01 | [Edit] |

**Add/Edit Bank Modal**

```
Bank Code *     [Text] e.g. "KBANK"
Bank Name *     [Text]
Status *        [Dropdown] ACTIVE / INACTIVE / MAINTENANCE / DISABLED
Type *          [Dropdown] BANK / SWITCHING / SERVICE_PROVIDER
Country         [Text] e.g. "TH"
Currency        [Text] e.g. "THB"
                         [Cancel] [Save]
```

---

### 3.10 Routing Rules `/routing`

**Purpose**: ดู/จัดการ routing rules และทดสอบ route resolution

#### Routing Tester (top section)

```
┌─ Route Resolver ────────────────────────────────┐
│  Source Bank [▼]   Dest Bank [▼]               │
│  Message Type [▼ PACS_008]       [Resolve Route]│
│  ─────────────────────────────────────────────  │
│  Route Code:    ROUTE_KBANK_SCB_PACS008         │
│  Connector:     generic-http-connector-1        │
│  Priority:      1                               │
│  Source Status: ACTIVE  │  Dest Status: ACTIVE  │
└─────────────────────────────────────────────────┘
```

#### Rules Table

| Route Code | Source | Dest | Message Type | Connector | Priority | Enabled | Actions |
|-----------|--------|------|-------------|-----------|----------|---------|---------|
| ROUTE_A_B | KBANK | SCB | PACS_008 | http-conn-1 | 1 | ✓ | [Edit] [Disable] |

**Add Rule Modal**

```
Route Code *      [Text]
Source Bank *     [Dropdown]
Dest Bank *       [Dropdown]
Message Type *    [Dropdown] PACS_008 / PACS_002 / PACS_028 / PACS_004
Connector *       [Dropdown — from connector list]
Priority *        [Number, default 1]
Enabled           [Toggle, default ON]
                              [Cancel] [Save]
```

---

### 3.11 Operations `/operations`

**Purpose**: Admin dashboard สำหรับ monitoring และ recovery

#### Sub-sections (tabs)

---

**Tab: Outbox Monitor**

Cards:
- PENDING: count
- PROCESSING: count
- FAILED: count
- STUCK (>5min in PROCESSING): count

Actions:
- `[Retry All Failed]` → POST /api/operations/outbox-failures/retry-all
- `[Recover Stuck]` → POST /api/operations/outbox-stuck/recover

Outbox Table:

| ID | Transfer Ref | Status | Retry Count | Created | Actions |
|----|-------------|--------|-------------|---------|---------|
| 1 | TRF-xxx | `FAILED` | 3 | 10:00:00 | [Retry] [Mark Reviewed] |
| 2 | TRF-yyy | `PROCESSING` | 1 | 10:23:00 | [View] |

---

**Tab: System Health**

```
┌─ Health ────────────────────────────────────────┐
│  API Server      ● Online                       │
│  Database        ● Connected                    │
│  Outbox Worker   ● Running                      │
└─────────────────────────────────────────────────┘

Connectors:
  generic-http-connector-1   ● Available   [Test]
  generic-http-connector-2   ● Available   [Test]
  mock-connector             ● Available   [Test]

Banks Online:
  KBANK   ACTIVE    SCB   ACTIVE    BBL   MAINTENANCE
```

---

**Tab: Transaction Search**

Advanced search เหมือน `/transactions` แต่มี filter เพิ่ม:
- Date range picker
- Bank code (any direction)
- Offset/Limit pagination
- Export CSV button

---

### 3.12 Audit Log Explorer `/audit`

**Purpose**: ค้นหา audit trail

#### Filter Bar

```
[Event Type ▼]  [Reference Type ▼]  [Actor ▼: ALL/API/WORKER/ADMIN]
[Reference ID: search...]                              [Search]
```

#### Audit Table

| Timestamp | Event Type | Reference Type | Reference ID | Actor | Actions |
|-----------|-----------|---------------|-------------|-------|---------|
| 10:23:01 | TRANSFER_CREATED | TRANSFER | TRF-xxx | API | [Detail] |
| 10:23:02 | OUTBOX_DISPATCH_SUCCESS | TRANSFER | TRF-xxx | WORKER | [Detail] |

**Detail Modal**: แสดง JSON payload ที่เก็บไว้ (เช่น full request context)

---

## 4. Global Components

### 4.1 Left Sidebar Navigation

```
[≡ SWITCHING SIMULATOR]
──────────────────────
📊  Dashboard
🧪  Simulator          ← Highlighted (primary action)
     └ Inquiry
     └ Transfer
     └ Inbound Test
💸  Transactions
🔍  Inquiries
📨  ISO Messages
🏦  Banks
🔀  Routing
⚙️  Operations
     └ Outbox
     └ Health
📋  Audit Logs
──────────────────────
[Environment: LOCAL]
[Backend: localhost:8080]
```

### 4.2 Top Header

```
[Page Title]                    [Refresh ↺]  [Env: LOCAL ▼]  [Settings ⚙]
```

**Environment Selector**: เปลี่ยน base URL ของ API (LOCAL / DEV / UAT / PROD)

### 4.3 Error Toast

```
┌─ Error ─────────────────────────────────────────┐
│  ✗  INQ-002: Inquiry validation failed           │
│  Category: BUSINESS | Layer: TRANSFER | Retryable: No│
│  [Details ▼]                                    │
└─────────────────────────────────────────────────┘
```

แสดง `errorCode`, `category`, `layer`, `retryable` จาก `ApiErrorResponse`

### 4.4 Loading State

- Skeleton screens สำหรับ table/card ที่กำลังโหลด
- Spinner บน button ขณะ submit
- "Polling status..." indicator ขณะ trace transfer

### 4.5 Status Badges

| Status | Color | Style |
|--------|-------|-------|
| SUCCESS / ELIGIBLE / ACTIVE | Green | Filled pill |
| FAILED / NOT_ELIGIBLE / DISABLED | Red | Filled pill |
| RECEIVED / PENDING / PROCESSING | Yellow | Filled pill |
| MAINTENANCE / INACTIVE | Orange | Outline pill |
| ENCRYPTED / VALID | Blue | Outline pill |

---

## 5. API Integration Map

| Page / Action | API Call |
|--------------|----------|
| Dashboard metrics | `GET /api/dashboard/overview` |
| Participant dropdown | `GET /api/participants?status=ACTIVE` |
| Create Inquiry | `POST /api/inquiries` |
| Create Transfer | `POST /api/transfers` |
| Transfer trace (polling) | `GET /api/transfers/{ref}` |
| Inbound PACS.008 | `POST /api/iso20022/pacs008` |
| Transfer list | `GET /api/transfers?status=&limit=` |
| Inquiry list | `GET /api/inquiries` |
| ISO message list | `GET /api/iso-messages?messageType=&direction=` |
| ISO message detail | `GET /api/iso-messages/{key}` |
| Bank list | `GET /api/participants` |
| Add bank | `POST /api/participants` |
| Update bank | `PATCH /api/participants/{bankCode}` |
| Routing list | `GET /api/routing-rules` |
| Resolve route | `GET /api/routing-rules/resolve?sourceBank=&destinationBank=&messageType=` |
| Add routing rule | `POST /api/routing-rules` |
| Clear route cache | `POST /api/routing-rules/cache/clear` |
| Outbox events | `GET /api/outbox-events` |
| Stuck outbox | `GET /api/operations/outbox-stuck` |
| Recover stuck | `POST /api/operations/outbox-stuck/recover` |
| Retry failed | `POST /api/operations/outbox-failures/retry-all` |
| Mark reviewed | `POST /api/operations/outbox/mark-reviewed` |
| Connector health | `GET /api/operations/connectors` |
| Test connector | `POST /api/operations/connectors/{name}/test` |
| Audit logs | `GET /api/audit-logs?eventType=&referenceId=` |
| Transaction search | `GET /api/operations/transactions` |

---

## 6. Simulator — Key UX Flows

### Flow A: Happy Path (Inquiry → Transfer → Success)
```
1. เปิด /simulator
2. กรอก Inquiry Form → POST → ELIGIBLE ✓
3. กด "Proceed to Transfer"
4. ค่าต่าง ๆ auto-fill จาก inquiry
5. กรอก Debtor Account → POST Transfer → RECEIVED
6. Drawer เปิด: แสดง Timeline polling ทุก 2 วิ
7. Timeline: RECEIVED → PROCESSING → SUCCESS
8. แสดง External Reference จาก bank
```

### Flow B: Idempotency Test
```
1. สร้าง Transfer สำเร็จ (TRF-xxx)
2. กด "Try Duplicate Request"
3. Response ซ้ำ: transferRef=TRF-xxx (same), message="Duplicate returned existing"
4. แสดง badge "IDEMPOTENCY HIT"
```

### Flow C: Not Eligible
```
1. เลือก Destination Bank ที่ status=MAINTENANCE
2. POST Inquiry → NOT_ELIGIBLE
3. แสดง reason: bankAvailable=false
4. Proceed to Transfer button disabled
```

### Flow D: Failed Transfer + Retry
```
1. สร้าง Transfer ที่ connector ตอบ error
2. Transfer → FAILED (errorCode: EXT-001)
3. ไป /operations/outbox → เห็น FAILED event
4. กด Retry → event กลับมา PENDING
5. Worker process ใหม่
```

### Flow E: Inbound PACS.008
```
1. ไป /simulator/inbound
2. กรอก field ทั้งหมด
3. กด Generate XML → เห็น XML preview
4. กด POST → รับ PACS.002 response
5. ถ้า validation fail → เห็น PACS.002 rejection + error code
```

---

## 7. Tech Stack Recommendation (Frontend)

| Layer | Choice | Reason |
|-------|--------|--------|
| Framework | React + TypeScript | Standard, lovable-compatible |
| Styling | Tailwind CSS | Utility-first, dark theme ง่าย |
| Components | shadcn/ui | Pre-built cards, tables, modals |
| Charts | Recharts | Bar + Donut charts |
| State | TanStack Query (React Query) | API polling + cache |
| XML handling | Browser DOMParser | Parse/display PACS XML |
| Code display | highlight.js / Prism | Syntax highlight JSON/XML |
| Icons | Lucide React | Consistent icon set |

---

## 8. Priority Order (MVP → Full)

### Phase 1 — MVP
- [ ] Dashboard
- [ ] Simulator: Inquiry + Transfer (Step 1 & 2)
- [ ] Transfer Detail with Timeline
- [ ] Transaction list

### Phase 2 — Core Features
- [ ] ISO Message Viewer
- [ ] Inquiry Explorer
- [ ] Bank Registry (view + edit)
- [ ] Routing Rules (view + resolver)

### Phase 3 — Operations
- [ ] Outbox Monitor + Retry
- [ ] System Health
- [ ] Audit Log Explorer
- [ ] Inbound PACS.008 Tester

### Phase 4 — Advanced
- [ ] Idempotency demo flow
- [ ] Environment switcher
- [ ] Export to CSV
- [ ] Dark/Light mode toggle

---

## 9. Data Seed (ตัวอย่าง Banks ใน System)

```
KBANK  Kasikorn Bank            BANK  ACTIVE  TH  THB
SCB    Siam Commercial Bank     BANK  ACTIVE  TH  THB
BBL    Bangkok Bank             BANK  ACTIVE  TH  THB
KTB    Krungthai Bank           BANK  ACTIVE  TH  THB
BAY    Bank of Ayudhya (Krungsri) BANK MAINTENANCE TH THB
TMB    TMBThanachart Bank       BANK  INACTIVE TH THB
```

---

## 10. Error Code Reference (สำหรับ UI display)

| Code | Message TH | แสดงที่ |
|------|-----------|---------|
| REQ-001 | ข้อมูลไม่ครบหรือไม่ถูกต้อง | Form validation |
| INQ-001 | ไม่พบ Inquiry นี้ | Transfer form |
| INQ-002 | ข้อมูล Transfer ไม่ตรงกับ Inquiry | Transfer form |
| INQ-003 | Inquiry ถูกใช้ไปแล้ว | Transfer form |
| PRT-001 | ไม่พบธนาคาร | Inquiry form |
| PRT-002 | ธนาคารไม่พร้อมให้บริการ | Inquiry form |
| RTE-001 | ไม่พบ Routing Rule | Transfer form |
| NET-001 | ไม่สามารถเชื่อมต่อธนาคารปลายทางได้ | Transfer trace |
| NET-002 | Timeout เชื่อมต่อธนาคาร | Transfer trace |
| EXT-001 | ธนาคารปลายทางปฏิเสธรายการ | Transfer trace |

---

*Spec version: 1.0 — Ready for Lovable design prompt*
