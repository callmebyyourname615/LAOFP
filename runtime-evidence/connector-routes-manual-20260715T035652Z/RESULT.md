# Connector & Routes Manual Evidence

Status: PASS

Target:
https://175.11.0.200

Tested flow:
- Listed connector configs successfully.
- Listed routing rules successfully.
- Resolved SUNDAYBANK -> PETERBANK PACS_008 route.
- Verified route uses MOCK_PETERBANK_CONNECTOR.
- Tested connector normal mode successfully.
- Enabled forceReject and verified AC01 response.
- Disabled forceReject and verified connector recovered to TEST_PASSED.

Conclusion:
Connector configuration, routing rules, route resolver, force reject, and connector recovery are working on UAT.
