#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location(
    "production_launch_manifest", ROOT / "scripts/golive/validate_production_launch_manifest.py")
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(MODULE)


def valid_manifest() -> dict:
    return {
        "schemaVersion": 1,
        "environment": "production",
        "release": {
            "reference": "switching-1.0.0-rc.1",
            "gitCommit": "a" * 40,
            "applicationImageDigest": "sha256:" + "b" * 64,
            "rollbackImageDigest": "sha256:" + "c" * 64,
        },
        "scope": {
            "participants": ["SUNDAYBANK"],
            "channels": ["TRANSFER"],
            "currencies": ["LAK"],
            "settlementModel": "DNS_T_PLUS_1",
            "transactionLimits": {"currency": "LAK", "minimumAmount": 1, "maximumAmount": 50000000},
        },
        "serviceObjectives": {
            "maximumRpoSeconds": 300,
            "maximumRtoSeconds": 1800,
            "availabilityPercent": 99.9,
            "minimumPaymentSuccessPercent": 99.9,
            "reconciliationSlaMinutes": 30,
        },
        "owners": {
            "businessOwner": "business-owner",
            "engineeringLead": "engineering-lead",
            "qaLead": "qa-lead",
            "secOpsLead": "secops-lead",
            "sreLead": "sre-lead",
            "changeManager": "change-manager",
        },
        "change": {
            "ticketReference": "CHG-20260722-001",
            "plannedDeploymentUtc": "2026-08-01T02:00:00Z",
            "rollbackOwner": "sre-lead",
            "communicationChannel": "production-cutover-channel",
            "riskRegisterReference": "RISK-20260722-001",
        },
        "environmentSeparation": {
            "uatSeparateFromProduction": True,
            "stagingSeparateFromProduction": True,
            "productionDataRestrictedToProduction": True,
            "productionSecretsExternallyManaged": True,
        },
    }


class ProductionLaunchManifestTest(unittest.TestCase):
    def test_accepts_valid_manifest(self):
        self.assertEqual([], MODULE.validate(valid_manifest()))

    def test_rejects_unassigned_owner_and_unsafe_objectives(self):
        manifest = valid_manifest()
        manifest["owners"]["sreLead"] = ""
        manifest["serviceObjectives"]["maximumRpoSeconds"] = 301
        errors = MODULE.validate(manifest)
        self.assertTrue(any("owners.sreLead" in error for error in errors))
        self.assertTrue(any("maximumRpoSeconds" in error for error in errors))

    def test_rejects_shared_application_and_rollback_image(self):
        manifest = valid_manifest()
        manifest["release"]["rollbackImageDigest"] = manifest["release"]["applicationImageDigest"]
        self.assertTrue(any("rollback image digest" in error for error in MODULE.validate(manifest)))


if __name__ == "__main__":
    unittest.main()
