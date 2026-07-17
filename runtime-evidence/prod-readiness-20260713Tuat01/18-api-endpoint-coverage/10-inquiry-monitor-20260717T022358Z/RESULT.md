# 18.10 Inquiry Monitor and Inquiry-to-Transfer Trace

Status: PASS

Endpoints verified:

- `GET /api/inquiries` returned filtered and paginated inquiry records.
- `GET /api/inquiries/{inquiryRef}` returned the selected inquiry and its status history.
- `GET /api/inquiries/{inquiryRef}/transfers` returned the linked transfer in `READY_FOR_SETTLEMENT` state.

Fix applied:

- The monitor query referenced a nonexistent `inquiries.message` column. It now aliases `error_message AS message`, preserving the API response contract.

Conclusion: Operations can trace an inquiry from its monitor record through its linked payment transfer on UAT.
