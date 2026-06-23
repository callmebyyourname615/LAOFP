# 66C — Build and Test Closure

Repository mode executes `./mvnw -B clean verify`, then requires fresh Surefire/Failsafe XML reports with zero failures and errors.

## Evidence

Store runtime artifacts under `build/phase66-evidence/<run-id>/66C/`. Redact secrets before signing.
