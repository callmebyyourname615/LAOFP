# POST /api/admin/requests + POST /api/admin/requests/{id}/approve

Submit (POST /api/admin/requests) — PASS, 200, PENDING request created.

Approve (POST /api/admin/requests/{id}/approve) — only self-approval negative case tested
(400, correctly rejects maker==checker). Only one distinct ADMIN account (SMOS user "admin") was
available in this session, so the true happy path (a second, different ADMIN/OPS user approving)
was not exercised. Needs a second admin/ops SMOS account to complete.

Left one dangling PENDING request (id e35ec1e5-68b7-4bed-b51d-596ae92bb97c, requestType
SETTLEMENT_INSTRUCTION_APPROVE, payload references a nonexistent instructionRef
PROBE-NONEXISTENT-0002) — harmless; if ever approved by a second admin later it will fail at
execution time since that instruction doesn't exist, no real settlement impact.

POST /api/iso20022/application/*+xml — not a separate route, it's one of the `consumes` media
types on POST /api/iso20022/pacs008 (see IsoPacs008InboundController.java consumes list). Already
exercised by 45-iso-pacs008 (malformed body validation) and 50-iso-pacs008-media-type (vendor
+xml content-type accepted). No separate test needed.
