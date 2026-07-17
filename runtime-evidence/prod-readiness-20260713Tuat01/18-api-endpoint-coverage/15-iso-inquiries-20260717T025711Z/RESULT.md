# 18.15 ISO Inquiry Read Model

Status: PASS

Evidence:
- ISO inquiry detail returned the eligibility result and full ISO-oriented inquiry fields.
- Operations list returned paginated results and masked creditor-account data.
- Operations detail returned the selected inquiry with eligibility, expiry, and API navigation fields.

Conclusion:
Both API and operations read models expose ISO inquiry data correctly, with account masking applied to the operations view.
