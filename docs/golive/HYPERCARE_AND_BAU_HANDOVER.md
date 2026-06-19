# Hypercare and BAU Handover

Minimum hypercare is 24 hours; seven days is recommended for the initial production release.

Monitoring cadence:

- Every 15 minutes during the first hour
- Hourly for the first 24 hours
- Daily until hypercare exit

Required signals are defined in `config/phase55-hypercare-policy.yaml`. Hypercare cannot close with an open critical incident, unresolved high incident, balance mismatch, duplicate business reference, missing signal, or firing critical alert.

BAU handover requires independent acceptance from business, security, and operations. Known high issues require an owner, target date, and two-person risk acceptance. Open critical known issues block closure.
