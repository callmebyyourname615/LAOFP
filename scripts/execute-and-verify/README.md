# Execute & Verify Toolkit

ชุด script สำหรับเดินสาย **execute → verify → collect evidence** เพื่อพา repo จากสถานะ "code complete" ไปสู่ "production ready"

## โครงสร้าง

```
scripts/execute-and-verify/
├── 00-run-all.sh                 # orchestrator — รันทั้ง 5 step ตามลำดับ
├── 01-verify-schema-v83.sh       # Action #1 — ddl-auto + V83 migration
├── 02-verify-metrics-activation.sh # Action #2 — metrics profile
├── 03-run-static-and-tests.sh    # Action #3 — verifiers + mvn verify
├── 04-credential-rotation-check.sh # Action #4 — credential audit (read-only)
├── 05-runtime-evidence-check.sh  # Action #5 — runtime evidence inventory
└── evidence/                     # log + artifact ของแต่ละรอบรัน
    └── YYYYMMDD_HHMMSS/
        ├── 01-verify-schema-v83.log
        ├── 02-verify-metrics-activation.log
        └── ...
```

## วิธีใช้

### รันทั้งหมดทีเดียว

```bash
./scripts/execute-and-verify/00-run-all.sh
```

- หยุดทันทีที่ step ไหน fail
- log อยู่ที่ `scripts/execute-and-verify/evidence/<timestamp>/`
- step 1,2,3 ต้อง pass; step 4,5 เป็น read-only checklist ไม่ทำให้ pipeline fail

### รันทีละ step

```bash
./scripts/execute-and-verify/01-verify-schema-v83.sh
./scripts/execute-and-verify/02-verify-metrics-activation.sh
./scripts/execute-and-verify/03-run-static-and-tests.sh
./scripts/execute-and-verify/04-credential-rotation-check.sh
./scripts/execute-and-verify/05-runtime-evidence-check.sh
```

## สถานะแต่ละ Step

| Step | สิ่งที่เช็ค | ใช้เวลา | ต้องการ |
|---|---|---|---|
| **01** | ddl-auto=validate, V83 ครบ, integration tests V83 ผ่าน | ~5–30 นาที | Docker (Testcontainers) |
| **02** | OperationalMetrics profile activate ใน prod | <1 นาที | – |
| **03** | static verifiers + `mvn verify` ทั้งหมด | ~10–60 นาที | Docker, JDK 17, Maven |
| **04** | audit `.env.prod.example` ไม่มี placeholder + reminder operator | <1 นาที | – |
| **05** | inventory ว่า B1–B8 ของ evidence ครบไหม | <1 นาที | – |

## Pre-requisite ก่อนรัน

```bash
# Mac: ต้องมี Docker Desktop เปิดอยู่
docker info >/dev/null && echo "Docker OK"

# Java 17+
./mvnw --version

# Python 3 (สำหรับ static verifiers)
python3 --version
```

## Step 4 และ 5 ทำอะไร (และทำไม pass เสมอ)

- **Step 4** เป็น **checklist สำหรับ operator** ไม่ใช่ automated test — เพราะการ rotate secret + purge git history ต้องคนทำเอง (มี side effect ระดับ org)
- **Step 5** เป็น **evidence inventory** — บอกว่ายังต้องไปรัน drill ไหนบ้าง ไม่ได้รันให้

ทั้งสองตัว exit 0 เสมอ — ใช้เป็น "checklist สรุปสถานะ" ตอนจบ pipeline

## ตีความผลลัพธ์

| ผลรัน | แปลว่า |
|---|---|
| Step 01–03 PASS + Step 04 ไม่มี ⚠️ + Step 05 ครบทุก ✅ | ✅ **Production Ready (repo-side)** |
| Step 01–03 PASS แต่ Step 05 ❌ หลายตัว | 🟡 Code OK, runtime evidence ยังไม่ครบ → ไปทำ drill ตามที่ Step 5 บอก |
| Step 01–03 ตัวใดตัวหนึ่ง FAIL | 🔴 มี code/config issue ต้องแก้ก่อน |
| Step 04 มี ⚠️ ใน `.env.prod.example` | 🔴 prod config มี placeholder อย่าปล่อยขึ้น prod |

## Workflow แนะนำ

```
วันที่ 1   : รัน 00-run-all.sh — ดู step 01–03 ผ่าน, list งานจาก step 05
วันที่ 2–5 : รัน drill ตาม step 05 (perf, DR, backup) เก็บ evidence
วันที่ 6   : รัน 00-run-all.sh อีกครั้ง — step 05 ควรเขียวหมด
วันที่ 7   : operator ทำ step 04 actions (rotate + purge) → sign-off
วันที่ 8   : เริ่ม Phase 55A (assemble immutable RC)
```

## หมายเหตุ

- ไม่ได้แตะ `docker-compose.yml`, `init-db-users.sh`, `.env*` ตามนโยบายที่ตกลงไว้ (DB credential เดิม)
- Evidence โฟลเดอร์ถูก `.gitignore` ได้ตามต้องการ (เพิ่มเองภายหลัง)
