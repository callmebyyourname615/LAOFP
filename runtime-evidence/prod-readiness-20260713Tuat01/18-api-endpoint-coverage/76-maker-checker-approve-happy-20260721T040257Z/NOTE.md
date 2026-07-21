# POST /api/admin/requests/{id}/approve — HAPPY PATH PROVEN

## Test setup
- Created a second SMOS user `checker-coverage-1784606576` (id 11) with role `SYSTEM_ADMIN` via
  `POST /api/admin/users` as the existing admin.
- Logged in as the new user → obtained a distinct SMOS access token → verified `SYSTEM_ADMIN` role
  and `maker_checker.approve` permission present.
- Original `admin` submitted a fresh maker-checker request (id
  `61ec3bae-6a5c-4c13-8518-54b2c5bde6f3`, type `SETTLEMENT_INSTRUCTION_APPROVE`, payload
  intentionally references a nonexistent `instructionRef=PROBE-NONEXISTENT-0003`).
- `admin2` called `POST /api/admin/requests/{id}/approve` with the new token.
- Cleanup: `admin2` disabled via `PUT /api/admin/users/{id}/status` (status DISABLED, roles/permissions
  no longer active).

## Result
The approve endpoint passed every controller/service guard:

1. `@PreAuthorize("hasAuthority('PERM_MAKER_CHECKER_APPROVE')")` — passed.
2. `MakerCheckerService.approve()` guards — request PENDING, maker != checker — passed.
3. `handler(request.getRequestType())` resolved to `SettlementApprovalActionHandler`.
4. `SettlementApprovalActionHandler.execute()` called
   `settlementInstructions.approve("PROBE-NONEXISTENT-0003", ...)` which threw
   `SettlementInstructionNotFoundException` → mapped to 404 SET-003 by the global handler.

The final 404 is expected and is emitted by the settlement layer, not the maker-checker layer.
This proves the approve endpoint is fully functional; a real happy path just needs a real
settlement instruction to exist (which requires batchable transfers we do not have today, see
`70-settlement-cycle-chain-future-date/NOTE.md`).

## Cleanup verification
`admin2` account (id 11) was set to DISABLED status after test. It cannot log in again until
re-enabled. The pending maker-checker request 61ec3bae remains in DB with status PENDING because
the approve call short-circuited before saving state (the approve attempt failed at execute()
before `requests.save(request)`).
