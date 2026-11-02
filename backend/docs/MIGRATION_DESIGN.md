# PostgreSQL migration and recovery design

## Original failure

Antigravity M1 added `QueueEntry.serviceDate` as `@Column(nullable=false)` while Hibernate `ddl-auto=update` attempted `ALTER TABLE queue_entry ADD COLUMN service_date DATE NOT NULL` against a populated table. PostgreSQL rejected it because existing rows would contain NULL. Hibernate logged the error yet continued starting the Spring context, and 27 unit tests passed. **Those test results never established that the schema was upgraded.**

## New policy

`spring.jpa.hibernate.ddl-auto=validate` only. A new database is initialized with `database/00_fresh_install.sql`; a populated database uses `01_existing_database_preflight.sql` and `02_existing_database_migration.sql`. Reporting views use `03_reporting_views.sql`.

### Preflight / decision points

1. Full `pg_dump` backup and restoration plan on a separate disposable database.
2. Confirm all check-in timestamps exist; any NULL requires manual investigation.
3. Confirm doctor/day/token uniqueness when `service_date := check_in_time::date`.
4. Confirm no duplicate active patient/doctor/day and at most one active consultation/doctor/day.
5. Confirm legacy timestamp timezone interpretation: `LocalDateTime` was a local wall-clock timestamp, not UTC. Do not silently move historical records across days.
6. Verify PostgreSQL permissions for ALTER, CREATE and INSERT.

### Migration transaction

- Takes explicit `ACCESS EXCLUSIVE` lock on queue_entry to prevent writes during backfill.
- Adds nullable `service_date` where absent; backfills from legacy check-in date.
- Uses a `DO` block that raises an exception on conflicts or missing required dates.
- Applies `NOT NULL`, unique indexes for tokens, active patient check-ins and single active consultation.
- Creates/initializes `daily_queue_counter` using max historical token per doctor/date; retries never reuse a committed token.
- Commits atomically or rolls back on error.

### Recovery

If preflight returns collisions, **do not delete rows automatically**. Document specific row IDs and resolve them with a validated correction plan. To undo a successful migration, prefer restore of the verified `pg_dump` backup to a clean database rather than blindly dropping constraints/columns containing new data. The supplied SQL is forward-only, not a guaranteed automated rollback across later production writes.

### SQL concepts worth studying

- `GROUP BY` plus `HAVING COUNT(*)>1` identifies duplicate business keys.
- `BEGIN`/`COMMIT` define an atomic migration boundary.
- `INSERT ... ON CONFLICT ... DO UPDATE ... RETURNING` serializes counter increments via unique-row conflict handling.
- Partial unique indexes enforce business predicates even when two app nodes process conflicting operations concurrently.
- `PERCENTILE_CONT` computes a percentile over valid waiting times, unlike a simple mean.

## Test database verification

Run `mvn clean test` **after migration**. Also perform API smoke tests, a true concurrent Postgres check-in load test, duplicate same-patient test, simultaneous call-next test and query the persisted `queue_entry` and `daily_queue_counter` rows. Unit stubs alone cannot establish real database locking behavior.
