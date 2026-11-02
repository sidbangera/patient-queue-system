# Interview preparation and SQL guide

Use this as a guide to **understand and verify**, not as a script to memorize untested claims.

## Architecture questions

1. **Why Spring Boot?** It supplies typed REST controllers, dependency injection, validation, transactions, Spring Security and JPA, suiting a structured hospital workflow.
2. **Why Flutter?** One source base can support mobile and desktop/browser clients; platform-specific permissions still require testing.
3. **Why modular monolith?** Independent services would add network/deployment overhead without demonstrated scale/team boundaries.
4. **Why not Flutter directly to PostgreSQL?** Credentials, authorization and business rules must stay server-side.
5. **Controller vs service vs repository?** Controller handles HTTP, service enforces workflows and transaction boundaries, repository persists state.
6. **Why use Enum state pattern?** Fixed status set with explicit `canTransitionTo` rules, simpler than multiple GoF state classes.
7. **What's the difference between authentication and authorization?** Authentication identifies a user, authorization grants role/record-specific capabilities; the latter still needs per-doctor ownership modeling.
8. **Why sign JWTs with persistent secret?** Tokens remain verifiable after reboot. Secret rotation revokes tokens; static fallback is unsafe.
9. **Why verify DB role after validating JWT?** A signed old token could contain an obsolete role. The database represents current staff privileges.
10. **Why is CORS not an authentication measure?** Browser origin policy is not a replacement for server-side roles; native Flutter is not subject to browser CORS.
11. **Why PostgreSQL?** ACID transactions, relational constraints, row conflict serialization and strong SQL analytics.
12. **Why a per-day counter?** `COUNT()+1` races and may reuse values. Per doctor/date UPSERT serializes token allocation.
13. **What exactly is a transaction?** Atomic unit of work: counter increment and queue insert succeed or roll back together.
14. **What does the database index add?** A final uniqueness invariant even under concurrency or multiple app instances.
15. **What was wrong with Hibernate auto-DDL?** New NOT NULL column on populated rows failed; logged warning did not fail old tests. A migration must backfill before adding constraint.
16. **How would you improve doctor privacy?** Associate staff account to doctor/department/patient relationship and enforce ownership at query + service boundaries.
17. **What is incomplete?** Clinical files/voice, doctor account binding, production health-data security, runtime validation in this environment.
18. **How do you prove correctness?** Unit tests plus live PostgreSQL concurrency tests, role access tests, migration checks and UI smoke tests.

## SQL exercise 1 — active queues

```sql
SELECT doctor_id, COUNT(*) AS active_patients
FROM queue_entry
WHERE service_date = CURRENT_DATE AND status IN ('WAITING','IN_CONSULTATION')
GROUP BY doctor_id
ORDER BY active_patients DESC;
```

Understand filtering before grouping and why we do not use `COUNT(*)` to allocate tokens.

## SQL exercise 2 — wait-time distributions

```sql
SELECT doctor_id,
 ROUND(AVG(EXTRACT(EPOCH FROM (consultation_start_time-check_in_time))/60.0)::numeric,1) AS avg_wait_min,
 PERCENTILE_CONT(0.9) WITHIN GROUP
  (ORDER BY EXTRACT(EPOCH FROM (consultation_start_time-check_in_time))/60.0) AS p90_wait_min
FROM queue_entry
WHERE consultation_start_time >= check_in_time
GROUP BY doctor_id;
```

Mean and P90 answer different operational questions. Neither predicts future wait time.

## SQL exercise 3 — migration preflight duplicate check

```sql
SELECT doctor_id,check_in_time::date,token_number,COUNT(*)
FROM queue_entry
GROUP BY doctor_id,check_in_time::date,token_number
HAVING COUNT(*)>1;
```

Explain `HAVING`, composite business keys and why migration should stop if it returns rows.

## SQL exercise 4 — patient history join

```sql
SELECT p.name, d.name AS doctor, q.service_date, q.token_number, q.status
FROM queue_entry q
JOIN patient p ON p.id=q.patient_id
JOIN doctor d ON d.id=q.doctor_id
WHERE p.id=1
ORDER BY q.check_in_time DESC;
```

Practice converting query outputs into acceptance criteria and privacy-safe reports.

## Analyst and business scenarios

1. **Manager says wait time is too high:** define who counts as 'waiting', baseline range, department filter, measure P90/mean, investigate case mix and recommend a testable staffing/process change.
2. **No-show rate jumps:** validate denominator, status mapping and missing data before recommending reminders.
3. **Dashboard differs from SQL:** reconcile timezone, data filters, canceled/no-show treatment, NULL timestamps, aggregation grain and refresh time.
4. **Doctor requests X-ray access:** clarify authorized users, storage format, visit linkage, secure download, consent and retention before implementation.
5. **Agent implemented feature but tests pass:** distinguish compilation, mocked unit test, live DB migration, UI interaction and load/concurrency checks.

## Interview-ready STAR story: debugging schema failure

- Situation: queue system was being upgraded with a service_date field.
- Task: preserve existing check-in data while introducing doctor/date uniqueness.
- Action: discovered auto-DDL failed on populated rows; designed backup/preflight/backfill/constraint transaction and set schema mode to validate.
- Result: **pending local execution and confirmation**; do not claim production deployment or proven migration until tested.

## What to revise for November 5

SQL SELECT/WHERE, JOINs, GROUP BY/HAVING, aggregation, window functions, transactions/ACID, constraints/indexes, NULL logic; Excel PivotTables/SUMIFS, Power BI data modeling, and business process mapping. Write queries yourself; the project is a learning dataset.
