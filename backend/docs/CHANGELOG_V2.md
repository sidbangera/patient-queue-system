# v2 change log — supplied source vs generated release candidate

## Baseline from uploaded ZIP

Original source had patient/doctor CRUD, queue state enum, JWT filter, admin-only role assignment checks, an unverified JPA native UPSERT token query, `service_date` NOT NULL with auto Hibernate schema updates, and tests. Flutter did not exist. 27/27 tests passed on the user's Windows machine **before this revision**, despite a failed automatic DDL operation.

## Code changes in this delivery

| Area | Change | Why | Verification status |
|---|---|---|---|
| DB schema | Add fresh-install and populated-database preflight/migration SQL | Safely backfill required service date; prevent partial auto-DDL | Static review only; not executed |
| Hibernate config | Change `ddl-auto=update` to `validate` | Fail on incompatible schema instead of silently continuing | Requires local runtime test |
| Token allocation | Replace JPA insert-returning query path with JDBC `JdbcTemplate` UPSERT allocator | Reliable PostgreSQL generated token return in same transaction | Unit stubs updated; Postgres integration pending |
| Queue rules | Add saveAndFlush and DB partial unique indexes | Catch/check active duplicates and concurrent consultation rules | Real DB concurrency pending |
| Auth | Require `JWT_SECRET` externally, validate current DB role per token | Avoid fallback known secret and stale role acceptance | Local Maven test pending |
| Staff management | Require ADMIN for `/register`; controlled env bootstrap for first ADMIN | Stop public staff account creation and escalation | Role tests adjusted; integration pending |
| Security config | Explicit fallback `UserDetailsService`, stateless JSON unauthorized/forbidden, configurable local CORS | Cleaner security wiring and dev Flutter Web integration | Local browser integration pending |
| Doctor data | Remove unconditional startup demo-doctor seeding | Avoid modifying DB unexpectedly on every run | Source inspection |
| Patient history | Add patient-visit-history API | Support doctor patient detail workflow | Local API test pending |
| Analytics | Add ADMIN-only summary and daily trend SQL/API plus reporting view | Demonstrate business-analysis KPIs | SQL and role test pending |
| Flutter | New responsive application source and API client | Doctor queues, role-based navigation, registration/check-in, history, admin insights | Flutter SDK tests pending |
| Documentation | Current HLD/LLD/API/migration/study/status documents | Enable interviews and controlled local verification | Authored |

## What did not change

- Existing `Patient`, `Doctor` and `QueueStatus` business concepts.
- Existing source files for unused legacy services or patient/doctor CRUD beyond security routing.
- No Docker setup (preserved for user's hands-on learning).
- No cloud deployment, microservices, Redis, Kafka, clinical file/voice APIs.

## Known technical debt before public or real patient use

Doctor-specific access binding, patient confidentiality audit, encryption policies, secure token lifecycle, live DB integration tests, constrained clinical attachments, voice-consent workflow, migrations under a managed migration tool, timezone modeling and load tests. See `RELEASE_STATUS.md`.
