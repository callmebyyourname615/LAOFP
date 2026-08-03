#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location(
    "runtime_evidence_redactor", ROOT / "scripts/security/redact_runtime_evidence.py")
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(MODULE)

JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.signature123"


class RuntimeEvidenceRedactorTest(unittest.TestCase):
    def test_redacts_token_fields_and_jwt_strings(self):
        value, changes = MODULE.redact_json({"accessToken": JWT, "detail": f"Bearer {JWT}"})
        self.assertEqual("[REDACTED]", value["accessToken"])
        self.assertIn("[REDACTED_JWT]", value["detail"])
        self.assertEqual(2, changes)

    def test_redacts_private_key_header_in_log_text(self):
        header = "-----BEGIN " + "PRIVATE KEY-----"
        value, changes = MODULE.redact_text(f"rejected {header} header")
        self.assertIn("[REDACTED_PRIVATE_KEY_HEADER]", value)
        self.assertEqual(1, changes)

    def test_removes_private_key_file_when_applying(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "client.key"
            private_key = "-----BEGIN " + "PRIVATE KEY-----\nvalue\n-----END " + "PRIVATE KEY-----\n"
            path.write_text(private_key, encoding="utf-8")
            action, changes = MODULE.redact_file(path, apply=True)
            self.assertEqual("removed-private-key", action)
            self.assertEqual(1, changes)
            self.assertFalse(path.exists())

    def test_preserves_valid_json_after_redaction(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "evidence.json"
            path.write_text(json.dumps({"token": JWT}), encoding="utf-8")
            MODULE.redact_file(path, apply=True)
            self.assertEqual("[REDACTED]", json.loads(path.read_text(encoding="utf-8"))["token"])


if __name__ == "__main__":
    unittest.main()
