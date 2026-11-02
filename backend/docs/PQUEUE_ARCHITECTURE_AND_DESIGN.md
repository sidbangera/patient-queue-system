# PQueue 5.0 - architecture and implementation guide

## Product objective

A doctor-first outpatient queue application plus administrative operational reporting. Reception can register/check in a patient; a doctor can start or finish a consultation; staff can retrieve past visits. Administrators can inspect aggregate activity and waits. It is a synthetic-data portfolio application, not a certified clinical-record system.

## Architecture (HLD)

```text
Flutter browser/Android client
  main.dart: app, authentication state, navigation
  api.dart: in-memory bearer token, JSON/CSV requests, timeouts, 401 handling
  screens.dart: patient registration/detail, doctor queue, check-in
  insights_screen.dart: filters, metrics, comparisons, quality, CSV
                         |
                         | REST over HTTP locally / HTTPS when deployed
                         v
Spring Security filter chain -> controller/DTO validation
                         |
                 transactional service layer
                 |                         |
           Spring Data JPA          parameterized JDBC reports
                 |                         |
                 +------ PostgreSQL -------+
                    existing schema and data
```

One backend deployable, not artificial microservices. Flutter never connects directly to PostgreSQL. Maven/Flutter versions and the existing database schema are preserved. Clinical files/voice transcription are deliberately excluded from this release.

## Domain and data model (LLD)

- `User` / `app_user`: staff username, password hash, role. Accounts are not doctor profiles.
- `Doctor`: provider name/department.
- `Patient`: existing directory fields (name, age, contact).
- `QueueEntry`: patient, doctor, service_date, token, status, check-in/start/end timestamps.
- `DailyQueueCounter`: one counter per doctor/service_date; last_token_number.

A visit is a queue entry. A patient can have many visits and may return after a prior visit is terminal. IDs are internal identifiers; token numbers are doctor/day queue numbers, not globally unique patient IDs.

Existing constraints retained:

1. Unique `(doctor_id, service_date, token_number)`.
2. Partial unique `(patient_id, doctor_id, service_date)` for WAITING and IN_CONSULTATION.
3. Partial unique `(doctor_id, service_date)` for IN_CONSULTATION.
4. `service_date NOT NULL`, primary and foreign keys from the current migrated schema.

These are specifically per service date. A stale consultation from a previous date is not a global cross-day consultation lock. Historical nullable references are reported by data-quality checks; this upgrade does not silently rewrite them.

## State machine

```text
WAITING -> IN_CONSULTATION -> COMPLETED
   |
   +----> NO_SHOW
```

COMPLETED and NO_SHOW are terminal. A repeat active check-in is invalid, but a new visit after a terminal visit is allowed. There is no appointment-booking model: NO_SHOW means a checked-in visit was marked no-show by staff.

## A check-in, line of responsibility by line of responsibility

1. Flutter `CheckinScreen` collects existing patient/doctor IDs and disables repeat clicks while saving.
2. `ApiClient` attaches the in-memory bearer token; the server checks the actual user's role.
3. The controller delegates to `QueueEntryService.checkIn` inside `@Transactional`.
4. The service loads patient/doctor, derives clinic-local check-in time and service_date using the injected Clock, and checks for an existing active visit.
5. `PostgresQueueTokenAllocator` atomically UPSERTs the doctor/day counter and returns its new value. The counter participates in the surrounding transaction.
6. The service saves the visit. The partial index is the final race-proof duplicate check, not just the initial SELECT.
7. A failed transactional check-in rolls back the counter change and visit together. Do not promise globally gapless tokens under every administrative operation.
8. Flutter shows the returned token and the queue screen retrieves persisted state.

## Why the state update changed

Previously two requests could read the same WAITING state and then save incompatible transitions. `QueueEntryRepository.findByIdForUpdate` now executes a parameter-bound native `SELECT q.* ... FOR UPDATE` inside the transaction before validating transitions. A second updater waits, reads the committed state, and validates that state rather than overwriting it.

A native single-table lock is used instead of applying FOR UPDATE to an aggregate or an eager outer-join result. Unique indexes still arbitrate competing visits for the doctor's same service date. Timestamp ordering and incomplete legacy visit fields are checked before committing a transition.

## Timezone decision

`AppConfig.clinicClock` derives from `app.clinic-zone` (CLINIC_ZONE, default Asia/Kolkata). Check-in dates, check-in times, transition timestamps, and today's queue use this clock. Reporting returns clinicToday/clinicZone so Flutter presets follow the clinic, not the browser timezone.

Existing timestamps are `timestamp without time zone`: they are interpreted as clinic-local wall-clock values. Changing CLINIC_ZONE after recording data is a migration decision, not a harmless UI preference. UTC instants would be appropriate for a future multi-clinic design, but this patch does not reinterpret old records.

## Reporting design

`AnalyticsController` validates dates (1900-2100, at most 366 inclusive days) and positive doctor IDs. `AnalyticsService` binds every external value through NamedParameterJdbcTemplate. No date/doctor string is interpolated into SQL.

The `/dashboard` service transaction uses REPEATABLE_READ and readOnly=true. All its summary, comparison, daily, hourly, workload and quality queries see one consistent database snapshot. This avoids a new check-in appearing in one card but not another while a request is executing. Individual report endpoints and a later CSV request are separate snapshots, so concurrent writes can change their results.

`generate_series` supplies zero-activity dates and all 24 arrival hours. Doctor filtering is applied inside the relevant LEFT JOIN ON condition so zero-activity rows survive. The recovered earlier patch had a filter-concatenation risk; a newline now separates the bound doctor parameter from following GROUP BY clauses.

The CSV is generated directly from queue_entry rather than relying on the historical reporting view's formula version. That view is retained, not dropped or redefined. Existing Power BI files using it should be reconciled before claiming identical metrics.

## Security boundaries

JWT expiry/signature are verified; the filter reloads the user and compares the stored role with the token role. Registration is ADMIN-only; bootstrap is not used during ordinary operation. CORS is explicit in the launcher; it is not a substitute for authentication.

Roles in existing SecurityConfig:

| Operation | Allowed roles |
|---|---|
| Login | Public |
| Create staff account, doctor changes | ADMIN |
| Patient create/update and check-in | RECEPTIONIST, ADMIN |
| View doctor queue and patient directory | DOCTOR, NURSE, RECEPTIONIST, ADMIN |
| Patient visit history | DOCTOR, NURSE, ADMIN |
| Call/complete/no-show | DOCTOR, ADMIN |
| Analytics/CSV | ADMIN |

**Known limitation:** doctor accounts are not bound to doctor records. Role checks do not enforce per-doctor patient ownership. Do not store real clinical files or market this as production healthcare security. A medical records release would require object-level policies, audit logs, consent, retention and secure attachment storage.

Flutter keeps JWTs only in memory, clears the session after an authenticated 401, and navigates back to login. Refreshing the browser requires login. A 403 shows a permission error rather than discarding a valid session.

## Registration-dialog lifecycle

The registration dialog is a dedicated StatefulWidget that owns its controllers and disposes them when Flutter unmounts the dialog. It does not dispose controllers immediately after the showDialog future resolves while the reverse route animation may still be active. The save/cancel regression test exercises this lifecycle.

## Why these design choices

- Layered monolith: understandable API/service/repository boundaries without distributed-system overhead.
- PostgreSQL for relational integrity and transactional allocation; no new database technology.
- Parameterized SQL for reporting: explicit, explainable aggregation formulas and percentile calculation.
- Hand-built Flutter charts: minimal dependency churn, labels and numerical values alongside bars; not a BI-tool replacement.
- DPAPI local secret storage: stable developer signing key without committing plaintext credentials; Windows-user/machine-bound and not a cloud secret manager.
- Preview-first patch: detects code drift rather than overwriting a newer local file.

## Files to study first

1. `lib/main.dart` and `lib/src/api.dart`: authentication/navigation/request lifecycle.
2. `SecurityConfig.java`, `JwtAuthFilter.java`, `JwtUtil.java`: authorization versus authentication.
3. `QueueEntryService.java`, `QueueStatus.java`, `PostgresQueueTokenAllocator.java`: business rules and transactions.
4. `AnalyticsController.java`, `AnalyticsService.java`, `ReportingMath.java`: inputs, SQL, measures, comparison.
5. `insights_screen.dart`: filters, request race protection, display states.
6. Tests: distinguish stubs, Spring context/MockMvc, and opt-in real-PostgreSQL transactions.
