# Promotion Manual Evidence

Status: PASS

Target:
https://175.11.0.200

Tested flow:
- Admin login succeeded.
- UAT health returned UP.
- Promotion list returned successfully.
- Admin created a DRAFT promotion.
- Maker self-activation was rejected.
- Checker activated the promotion successfully.
- Admin suspended the promotion successfully.
- Final promotion status is SUSPENDED.

Promotion ID:
22db8003-12fd-4b31-8270-6a34f3cd2e76

Final status:
SUSPENDED

Conclusion:
Promotion create, maker-checker activation, and suspend flow are working on UAT.
