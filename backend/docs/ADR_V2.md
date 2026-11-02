# Architecture decision records — v2

## ADR-007: Explicit schema migration rather than Hibernate updates

**Decision:** use SQL scripts and `ddl-auto=validate`. **Context:** `service_date NOT NULL` failed against existing rows yet startup tests continued. **Trade-off:** manual migration step but predictable, backed-up and auditable changes. **Alternatives:** auto-DDL (unsafe), Flyway/Liquibase (recommended follow-up when deployments grow).

## ADR-008: JDBC for PostgreSQL atomic token allocation

**Decision:** use a focused `JdbcTemplate` allocator with `INSERT ... ON CONFLICT ... RETURNING`, sharing the same transactional datasource as JPA writes. **Context:** unsafe `COUNT()+1` and ambiguous JPA native INSERT RETURNING handling. **Trade-off:** PostgreSQL-specific SQL. **Alternative:** JPA row lock on precreated counter plus retry; more queries and initialization logic. **Verification debt:** live DB concurrency tests.

## ADR-009: No public account self-registration

**Decision:** only ADMIN can create staff accounts; explicitly bootstrap first admin via environment variables. **Context:** hospital user roles are security privileges and should not be publicly self-assigned. **Trade-off:** no open signup, extra local setup step. **Alternatives:** invite flow/enterprise SSO (deferred).

## ADR-010: Flutter separate source application

**Decision:** create `patient-queue-flutter` separate from Spring Boot backend, using REST API only. **Rationale:** cross-device clinician UX and independent build workflows. **Trade-off:** shared contract must be tested. Platform scaffolds generated with user's Flutter SDK.

## ADR-011: Admin analytics from aggregate data

**Decision:** ADMIN-only `/api/analytics` and aggregate PostgreSQL view. **Rationale:** business insights without giving BI clients raw patient identities. **Trade-off:** no doctor-level personal dashboard yet because user-to-doctor mapping is absent. **Alternative:** future doctor-specific analytics after scoping is implemented.

## ADR-012: Defer clinical file and voice features

**Decision:** document precise future requirements and show planned-state UI only. **Rationale:** permissions, health-data sensitivity, consent, malware scanning, secure storage, audit and review cannot be faked. **Trade-off:** less feature coverage in current portfolio demo but fewer unsafe claims. See `FUTURE_PATIENT_FILES_AND_VOICE_DESIGN.md`.
