#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "prod_env_validator", ROOT / "scripts/validate_production_environment.py")
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(MODULE)


class ProductionEnvironmentValidatorTest(unittest.TestCase):
    def setUp(self):
        self.contract = MODULE.load_yaml(ROOT / "config/production-environment-contract.yaml")
        self.patterns = [__import__("re").compile(p) for p in self.contract["placeholderPatterns"]]

    def test_rejects_local_database_and_missing_verify_full(self):
        errors = MODULE.validate_rule(
            "DB_URL", "jdbc:postgresql://localhost:5432/switching", self.contract["variables"]["DB_URL"],
            False, self.patterns)
        self.assertTrue(any("verify-full" in error for error in errors))
        self.assertTrue(any("local/mock" in error for error in errors))

    def test_accepts_template_placeholder_only_in_template_mode(self):
        rule = self.contract["variables"]["DB_PASSWORD"]
        self.assertEqual([], MODULE.validate_rule(
            "DB_PASSWORD", "__INJECT_AT_RUNTIME__", rule, True, self.patterns))
        self.assertTrue(MODULE.validate_rule(
            "DB_PASSWORD", "__INJECT_AT_RUNTIME__", rule, False, self.patterns))

    def test_env_parser_rejects_duplicate_keys(self):
        with tempfile.TemporaryDirectory() as directory:
            env_file = Path(directory) / "duplicate.env"
            env_file.write_text("DB_URL=a\nDB_URL=b\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                MODULE.parse_env_file(env_file)

    def test_kubernetes_delivery_matches_contract(self):
        self.assertEqual([], MODULE.verify_k8s(self.contract, ROOT))


if __name__ == "__main__":
    unittest.main()
