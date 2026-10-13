# QA cleanup / reset

วันที่: 2026-10-13 | สถานะ QA หลัง reset: **NOT_RUN**

ลบ 7,217 files รวม 64,015,946 bytes (~64.0 MB).

ล้างแล้ว:

- runtime-evidence และ historical certification/preflight run bundles
- target: compiled output, packaged build, Surefire/JaCoCo reports
- execute-and-verify logs, phase61 evidence, performance results และ phase76/77 validation outputs
- output PDF, temporary PDF generation/rendered files, Python caches และ OS metadata
- loose root XML/JSON/CSV/API captures, shell transcript, empty scratch files
- unreferenced historical phase implementation/validation reports และ package/change manifests
- peripheral portal specs และ duplicate API HTML presentation; API inventory/spec ที่ใช้กับ switching QA ยังคงอยู่
- endpoint status registry ที่เก็บ PASS เก่า; generator รองรับการไม่มีไฟล์นี้และเริ่ม NOT_TESTED

เก็บไว้: core/extended switching source, test cases/fixtures, migrations, configs, deployment assets, operational tooling, API contracts, runbooks/templates และเอกสารที่ verifiers ใช้เป็น dependency. รวมถึง phase checklists ที่ required โดย QA scripts

ตรวจ hash ของไฟล์ code/tests/configs/tooling ที่เก็บไว้ 2,530 ไฟล์แล้ว ไม่เปลี่ยนจากก่อน cleanup; existing uncommitted implementation changes ยังคงอยู่

เพิ่ม .gitignore สำหรับ generated evidence/run outputs เพื่อไม่ให้ผลรอบใหม่ปะปนกับ source. ไฟล์เก่าที่ tracked อยู่แสดงเป็น deletion ใน working tree; ยังไม่ commit และไม่แก้ Git history

ทุก 61 checklist items เป็น NOT_RUN; evidence/time/reviewer/result fields ว่าง. Reset notes ใน readiness documents ระบุให้ใช้ผล QA ใหม่เท่านั้น

ขอบเขตนี้ไม่ล้าง database หรือ Docker volumes และไม่รันทดสอบใหม่. เริ่ม QA บน isolated disposable DB เพราะ test harness มี DROP DATABASE fallback

รายละเอียดการลบอยู่ใน CLEANUP_REMOVED_FILES.csv; required QA dependency exceptions อยู่ใน CLEANUP_SUMMARY.json. ไม่มีการเก็บสำเนาเนื้อหาผลรันเก่าไว้ในรายงาน cleanup
