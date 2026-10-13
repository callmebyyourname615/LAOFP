# Core Switching — fresh QA cycle

วันที่ reset: 2026-10-13 | Repository: `/Users/macbookpro/Desktop/Switching`

**QA status: NOT_RUN. PROD readiness: UNVERIFIED.** ผลการรันเก่าถูกล้างแล้วตามคำขอ ทุก gate ต้องมี evidence ใหม่ของ exact release candidate ก่อน Go-live

- [Phase checklist](PHASE_CHECKLIST.md): 11 audit phases / 61 checks พร้อมเกณฑ์ผ่าน
- [QA tracker](CHECKLIST.csv): ทุกข้อ NOT_RUN และไม่มี evidence/result เก่า
- [Implementation trace](IMPLEMENTATION_TRACE.md): source → tests → evidence ที่ต้องเก็บใหม่
- [Current QA status](PRELIMINARY_STATUS.md): ขอบเขต reset และสิ่งที่ต้องตรวจ
- [Source baseline](QA_BASELINE.json): HEAD, current source hashes และ migration inventory หลัง cleanup
- [Cleanup report](QA_RESET.md): สิ่งที่ลบ สิ่งที่เก็บ และการตรวจความครบถ้วน
- [Removed file manifest](CLEANUP_REMOVED_FILES.csv): รายชื่อที่ลบและเหตุผล ไม่มีเนื้อหา evidence เก่า

เริ่ม P00 freeze source/config/schema/image แล้ว P01 build/test/migrations บน isolated QA infrastructure. งาน cleanup รอบนี้ไม่มี fresh QA execution หรือ deployment

GO ต้องผ่าน P00–P08, มี signed approvals ใน P09 และเตรียม P10 พร้อมก่อนเปิด traffic. Phase 99 GO_CANDIDATE เพียงอย่างเดียวไม่แทนทุก gate
