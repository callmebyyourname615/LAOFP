# Phase 73 Chaos Engineering

This directory contains UAT-only Chaos Mesh experiments for Phase 73. The manifests are templates and must be rendered through `scripts/phase73/render_manifest.py`; applying the source templates directly is unsupported.

Safety controls:

- Production execution is denied.
- `TARGET_ENVIRONMENT=uat` is mandatory.
- `PHASE73_EXECUTE_CHAOS=true` is mandatory.
- A non-expired approval document and matching `CHAOS_APPROVAL_TOKEN` are mandatory.
- Only one experiment may run at a time.
- Every experiment has an EXIT cleanup trap.
- Financial integrity and recovery evidence are required before certification.
