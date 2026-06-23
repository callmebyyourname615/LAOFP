#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def require_file(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        FAILURES.append(f"missing file: {relative}")
        return ""
    return path.read_text(encoding="utf-8")


def require_text(relative: str, *needles: str) -> None:
    content = require_file(relative)
    for needle in needles:
        if needle not in content:
            FAILURES.append(f"{relative}: missing contract text {needle!r}")


migration = "src/main/resources/db/migration/V97__smos_user_access_management.sql"
require_text(
    migration,
    "CREATE TABLE smos_users",
    "CREATE TABLE smos_roles",
    "CREATE TABLE smos_permissions",
    "CREATE TABLE smos_auth_sessions",
    "CREATE TABLE smos_maker_checker_requests",
    "ck_smos_mc_different_actor",
)
for role in (
    "SYSTEM_ADMIN", "OPS_ADMIN", "SETTLEMENT_OFFICER", "DISPUTE_OFFICER",
    "RISK_OFFICER", "AUDITOR", "PARTICIPANT_ADMIN", "READ_ONLY",
):
    require_text(migration, f"('{role}'")

for relative in (
    "src/main/java/com/example/switching/usermgmt/controller/AuthController.java",
    "src/main/java/com/example/switching/usermgmt/controller/UserController.java",
    "src/main/java/com/example/switching/usermgmt/controller/MakerCheckerController.java",
    "src/main/java/com/example/switching/usermgmt/service/AuthenticationService.java",
    "src/main/java/com/example/switching/usermgmt/service/UserManagementService.java",
    "src/main/java/com/example/switching/usermgmt/service/MakerCheckerService.java",
    "src/main/java/com/example/switching/usermgmt/filter/SmosJwtAuthenticationFilter.java",
    "src/test/java/com/example/switching/usermgmt/service/TotpServiceTest.java",
    "src/test/java/com/example/switching/usermgmt/service/SmosTokenServiceTest.java",
    "src/test/java/com/example/switching/usermgmt/service/SmosUserManagementIntegrationTest.java",
):
    require_file(relative)

require_text(
    "src/main/java/com/example/switching/usermgmt/service/AuthenticationService.java",
    "MFA_CHALLENGE", "REFRESH_TOKEN", "maxFailedLogins", "noRollbackFor", "SMOS_LOGIN_SUCCEEDED",
)
require_text(
    "src/main/java/com/example/switching/usermgmt/service/MakerCheckerService.java",
    "Maker and checker must be different users",
)
# The lock annotation lives in the repository, while the service enforces actor separation.
require_text(
    "src/main/java/com/example/switching/usermgmt/repository/MakerCheckerRequestRepository.java",
    "PESSIMISTIC_WRITE", "findWithLockById",
)
require_text(
    "src/main/java/com/example/switching/security/config/SecurityConfig.java",
    '"/api/auth/login"', '"/api/admin/users/**"', 'hasRole("SYSTEM_ADMIN")',
)
require_text(
    "src/main/resources/application.yml",
    "SMOS_ENABLED:false", "SMOS_JWT_SECRET", "SMOS_BOOTSTRAP_ENABLED:false",
)
require_text(
    "config/production-environment-contract.yaml",
    "SMOS_ENABLED", "SMOS_MFA_REQUIRED", "SMOS_JWT_SECRET", "SMOS_BOOTSTRAP_ENABLED",
)
require_text(
    "src/main/java/com/example/switching/config/ProductionStartupValidator.java",
    "switching.smos.enabled must be true", "switching.smos.mfa-required must be true",
    "switching.smos.bootstrap.enabled must be false", "must be different from the PSP OAuth signing secret",
)

require_text(
    "k8s/configmap.yaml",
    'SMOS_ENABLED: "true"', 'SMOS_MFA_REQUIRED: "true"', 'SMOS_BOOTSTRAP_ENABLED: "false"',
)
require_text(
    "k8s/external-secrets/application-secrets.yaml",
    "secretKey: SMOS_JWT_SECRET", "property: SMOS_JWT_SECRET",
)

if FAILURES:
    for failure in FAILURES:
        print(f"FAIL: {failure}")
    print(f"SMOS static contract: FAIL ({len(FAILURES)} issue(s))")
    sys.exit(1)

print("SMOS static contract: PASS")
