# Repository Guidelines

## Project Structure & Module Organization

This is a Java 21 Spring Boot switching service. Application code lives under `src/main/java/com/example/switching`, organized by domain packages such as `settlement`, `reconciliation`, `webhook`, `promotion`, and `usermgmt`. Runtime configuration and static assets are in `src/main/resources`; Flyway migrations are in `src/main/resources/db/migration` and must keep the `V<number>__description.sql` pattern. Tests live in `src/test/java`, with test fixtures and profiles in `src/test/resources`. Operational assets are top-level folders such as `k8s`, `scripts`, `sql`, `docs`, `performance`, `security`, and `runtime-evidence`.

## Build, Test, and Development Commands

- `./mvnw clean test` runs the Maven test suite.
- `./mvnw verify` runs tests and generates the JaCoCo report.
- `./mvnw spring-boot:run` starts the application with the active Spring profile.
- `./run.sh local` loads `.env` and starts the app locally, choosing a free port from `8080`.
- `./run.sh docker` starts the Docker Compose stack; `./run.sh stop` stops it.
- `./run.sh test:single CLASS=SomeTest` runs one test class through Maven.

Copy `.env.example` to `.env` before using `run.sh` commands that require local configuration.

## Coding Style & Naming Conventions

Follow existing Spring conventions: controllers, services, repositories, entities, DTOs, and enums stay in the matching domain package. Use 4-space indentation for Java and descriptive class names such as `PushPaymentPolicyController` or `SettlementInstructionRepository`. Keep SQL migration names lowercase and action-oriented after the version prefix. Prefer constructor injection and validation annotations where the existing package already uses them.

## Testing Guidelines

Tests use JUnit 5 with Spring Boot test support and Testcontainers PostgreSQL. Place integration test support in shared base classes such as `AbstractIntegrationTest`; keep fixtures under `src/test/resources`. Name test classes after the unit or flow under test, ending in `Test` or `IntegrationTest`. Run `./mvnw clean test` before submitting Java changes and `./mvnw verify` when changes affect persistence, migrations, or coverage-sensitive paths.

## Commit & Pull Request Guidelines

The current Git history contains placeholder subjects like `a`; do not copy that style. Use short imperative commits, for example `Add settlement retry audit log` or `Fix webhook signature validation`. Pull requests should include the purpose, key changed paths, test results, linked issue or phase document when applicable, and screenshots for UI/static asset changes.

## Security & Configuration Tips

Do not commit real secrets. Use `.env.example`, `backup/.env.example`, and Kubernetes secret templates as references. Changes touching `security`, `k8s`, `config`, Flyway migrations, or evidence generation should document operational impact and any required rotation, migration, or rollback step.
