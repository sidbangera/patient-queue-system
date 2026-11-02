# Understand and defend both projects

Use this as a reading path, not a script to memorize. Every answer must match the actual code and your local test evidence. State that the projects use synthetic/demo data and were developed with AI assistance when asked; explain the decisions you reviewed and tests you personally ran.

## Two-minute project introductions

**PQueue:** "I built an outpatient workflow for registration, doctor-specific check-in, consultation transitions and visit history. I also built SQL-backed operational reports so an administrator can inspect arrivals, completion/no-show rates and waiting times. The main design challenge was enforcing queue rules under concurrent requests while keeping the metrics correctly defined. Flutter calls a Spring Boot API backed by PostgreSQL. The application is a demo, and I have not claimed real clinical deployment or measured reductions in waiting time."

**VoxFlow:** "I built a feedback workflow where a creator creates a persistent room, shares a submission link, and privately reviews anonymous responses. Responses have explicit categories and review states; filters and CSV support basic analysis. The important security boundary is that knowing the public link allows submission, not reading other feedback. I separated domain logic from the MongoDB adapter, used server-side sessions for creators, and made room closure and feedback acceptance one atomic MongoDB write."

## PQueue: questions and follow-ups

1. **What business problem does it address?** Keeping an ordered outpatient workflow and making activity/waits visible. It does not predict diagnosis, replace an EHR, or prove staffing improvements. Follow-up: identify receptionist, doctor, nurse and admin needs separately.
2. **Walk through one check-in.** Read `CheckinScreen`, `ApiClient.send`, `QueueEntryController`, `QueueEntryService.checkIn`, allocator and repository. Explain HTTP request, authentication, business validation, transaction, database constraint and returned token in that order.
3. **Why not count rows + 1 for the token?** Two concurrent readers can see the same count. An atomic counter-row UPSERT serializes increments for a doctor/date; a unique index backs the visit token. Explain what a transaction rollback does to the counter and visit.
4. **What happens at midnight?** `Clock` is injected with clinic timezone. New visits use the new service date and another counter row. Old visits retain their assigned date. Browser time does not choose service_date. The existing single-consultation constraint is per doctor/date, not a cross-day global lock.
5. **What counts as a duplicate?** Same patient, doctor, service date with WAITING/IN_CONSULTATION. After COMPLETED/NO_SHOW a new visit can be created; this is not a one-visit-per-day rule.
6. **Why both a service check and database constraint?** The pre-check gives a useful message; only the database constraint closes a race between concurrent requests. Discuss conflict handling without claiming mocks prove PostgreSQL locking.
7. **How do status races work?** `findByIdForUpdate` locks the actual visit row inside the transaction. The next updater validates the latest committed status. WAITING can become IN_CONSULTATION or NO_SHOW; only the former can become COMPLETED.
8. **Why use a native lock query?** It targets a single physical queue row, avoids aggregate FOR UPDATE and eager outer-join locking problems, and is parameterized. ORM use does not remove the need to understand database semantics.
9. **Authentication versus authorization?** JWT signature/expiry establish token validity; stored user/role and endpoint rules determine permissions. Existing role tokens are checked against current database roles. Clinical object-level permissions remain incomplete.
10. **What happens to JWTs on restart?** Same signing secret preserves validity until expiry; generating a new key invalidates old tokens. DPAPI stores the developer key encrypted outside the repository. It is not password storage for users; bcrypt handles user passwords.
11. **Why is CORS not security by itself?** It affects browsers reading cross-origin responses; an HTTP client can still call the API. Role checks remain server-side. Different local web ports need explicit origin configuration.
12. **Explain average vs P90 wait.** Average is total valid wait divided by valid sample count. P90 is a continuous percentile describing the upper part of the observed distribution. A one-sample P90 is not convincing evidence about operational tail performance.
13. **Why might totals not equal completed + no-shows?** Some visits are still WAITING/IN_CONSULTATION. Denominators use the selected check-in cohort; zero denominator is missing/undefined, not 0%.
14. **Can you average P90s across doctors?** No. Percentiles are not additive. Overall P90 needs the underlying wait distribution. An average of averages needs valid sample weights; the exported CSV does not currently include those weights.
15. **What is the prior-period comparison?** A selected N-day inclusive window is compared with the immediately preceding N days using the same doctor filter. Count percent change is undefined from zero. Percentage-point difference is a different measure.
16. **Why one dashboard endpoint?** Its read-only repeatable-read transaction gives all cards one snapshot. Separate HTTP requests could see inconsistent totals during updates. CSV requested later can legitimately differ.
17. **What does the quality panel actually do?** Counts aggregate missing/invalid timestamps/references and status mismatches. It does not fix data or expose patient-level records. Individual flags overlap; flaggedVisits is a distinct per-row OR count.
18. **How did the registration crash happen?** Controllers were disposed before the dialog's reverse animation fully removed its widgets. The dialog now owns/disposes its own controllers. Show `RegisterPatientDialog` and its regression test.
19. **Which tests used real PostgreSQL?** Be precise. Unit stubs cover logic; context checks validate schema; optional PostgresIntegrationTest uses multiple real connections and an isolated schema in a dedicated *_test database. Only claim that last test passed after running it locally.
20. **What would you change for real clinics?** User-to-doctor binding, object-level authorization, audit trails, HTTPS, consent/retention, backup recovery tests, observability and security review. Multi-clinic UTC time modeling is a separate migration, not an env-var tweak.

## VoxFlow: questions and follow-ups

1. **Why does room creation need a database operation?** A link is not a persisted resource. The room must exist with an owner/status before the first response, and survive reload/restart even when empty.
2. **How is feedback anonymous?** Respondents are not asked for identity and no respondent identity field is stored. The service/hosting infrastructure still processes technical metadata, and text can identify its author. Do not promise absolute anonymity or one response per person.
3. **Why use opaque sessions instead of JWTs here?** Server-side session hashes give immediate logout revocation and simple ownership lookup. Explain trade-off: each request needs session storage access, unlike purely stateless verification.
4. **Why hash session tokens?** A database leak should not directly reveal usable raw sessions. Tokens are high-entropy random values; SHA256 is suitable for token lookup, while passwords require a slow salted KDF like scrypt.
5. **How are passwords handled?** Salted async scrypt, derived key comparison with timingSafeEqual; raw password not stored or returned. Account creation validates minimum length, login errors are generic, and rate limits provide basic abuse friction.
6. **Why HttpOnly cookies?** JavaScript cannot read the session value, reducing direct token theft from script access. XSS can still perform actions as the user; cookies also require CSRF controls. This is not an all-purpose security solution.
7. **What CSRF controls are present?** SameSite=Lax plus an exact Origin allowlist and required X-VoxFlow-Request header on mutations. Production cross-site deployment needs reconsidered cookie and hosting design; do not switch to wildcard origins.
8. **Can a creator read another creator's room using its id?** No. Each private access queries id + authenticated ownerId; response is404. Show ownedRoom and Mongo roomByOwner, not only the React Protected component.
9. **Why not a separate feedback collection?** A bounded embedded array lets a standalone MongoDB update atomically check OPEN/capacity and append. The explicit 1,000-response/2,000-character limit is the trade-off. Large-scale usage needs a different data model, not removing the bound.
10. **What if a response arrives as the owner closes the room?** The conditional write either precedes the close and succeeds, or follows it and fails. It cannot rely on a stale read of room status. Explain serialization without guaranteeing which competing request wins.
11. **Do room edits overwrite new responses?** No. Metadata uses $set and submission uses $push; no full feedback array is saved from a stale UI object.
12. **How are categories generated?** Respondent selection with owner correction, not NLP or sentiment inference. AI was intentionally not added merely for a resume keyword.
13. **What does ACTIONED mean?** A creator-applied workflow label. It does not prove a complaint was resolved or a business outcome achieved. Reopening/correcting statuses is allowed.
14. **Do filters affect the charts?** Yes, all dashboard figures use the same filtered room snapshot. Inbox pagination does not restrict analytic totals. Export includes all matching pages.
15. **How do legacy dates work?** Plain string responses had no timestamps. Import uses null, not today's time. Date filters exclude undated records and the unfiltered dashboard explicitly counts them.
16. **How do you avoid CSV formula injection?** Escape quotes/commas/newlines and neutralize formula-leading strings, including whitespace/control prefixes. CSV is owner-only; it still may contain sensitive response text.
17. **Is it real-time?** No. It refreshes through REST. Do not claim WebSockets, push streaming or live subscriptions. Explain when you would add SSE or a polling policy and how privacy would still be enforced.
18. **What is tested here versus locally?** Actual core/router/HTTP adapter tests ran with in-memory persistence, plus pure frontend helper tests. Express/Mongo and browser build/runtime gates need the installed dependencies and database. Use the included integration test names as evidence only after execution.
19. **What is the rate-limit limitation?** Buckets are process-local; they reset on restart and do not coordinate replicas. Trusted reverse proxy settings must be controlled. There is no stored respondent IP in Mongo, but transient request processing is not invisibility.
20. **How would you scale?** Separate response collection with indexed owner/room/time queries, a well-defined close/write consistency protocol, bounded pagination, distributed abuse prevention, audit logging and deployment monitoring. Avoid promising this complexity is already implemented.

## Analyst-style demonstration, not only developer terminology

For each feature explain: user need -> functional requirement -> business rule -> implemented component -> acceptance test -> limitation -> possible decision.

Example: "Administrators need to spot long waits" -> date/doctor filter -> valid wait denominator -> parameterized SQL -> compare known fixture to chart -> small samples and case-mix -> investigate demand/capacity, rather than claim automatic staffing recommendations.

Example: "A creator must follow up on suggestions" -> private review inbox -> owner-only access + review statuses -> route/service/conditional queries -> second-owner403/404 test and status-filter test -> no verification of actual action outcome -> use categories/trends to prioritize a review.

## Practical study exercises

- Trace a PQueue request using file names, not a memorized diagram.
- Write SELECT/GROUP BY/HAVING for visit counts and explain NULL timestamps.
- Calculate a two-period percentage change and percentage-point difference by hand.
- Attempt an unauthorized VoxFlow request and explain why a hidden button is insufficient.
- Explain the database choice in each app and why one uses transactions and the other a single-document atomic write.
- Show a failed test or bug you fixed and distinguish its root cause from its visible symptom.
- Read the release limitations aloud; a confident answer can include "not implemented" and a reasoned next design step.
