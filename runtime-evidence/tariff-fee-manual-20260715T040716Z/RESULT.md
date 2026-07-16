# Tariff / Fee Result

Status: PASS

Evidence:
- Created a DRAFT tariff version with one PACS_008 LAK fixed-fee rule.
- Maker self-approval was rejected.
- Separate checker approved the tariff.
- Checker activated the tariff.
- Fee assessment returned grossFee/netFee LAK 1000.0000.

Tariff ID: 9fc3a6b0-e7cc-4a84-aa67-eb1c6d44450c

Additional verification:
- Fee assessment selection now prefers the latest active tariff valid_from when multiple active tariff plans match the same message/currency criteria.
