# Local acceptance tests and resume evidence

Record each test as NOT RUN / PASS / FAIL with command, timestamp, sanitized screenshot and database/API evidence. Do not prefill PASS from the implementation or this checklist. Use only synthetic records. Complete one application's gates before troubleshooting the other.

## PQueue

| ID | Scenario | Expected result |
|---|---|---|
| PQ-01 | Existing admin login after upgrade | Same account/password works; only old token may require login if signing key changed intentionally |
| PQ-02 | Existing historical patient/visit | Original data preserved; no reseed or migration |
| PQ-03 | Open registration, cancel while field focused; reopen and save a NEW synthetic patient | No disposed-controller/assertion crash; one saved record, not duplicate save |
| PQ-04 | Check in existing synthetic patient to doctor | Unique doctor/day token, WAITING row |
| PQ-05 | Repeat active same patient/doctor/date | Conflict; exactly one active row and no success toast |
| PQ-06 | WAITING -> consultation -> completed | Timestamps ordered, history retains record, active queue no longer lists completed |
| PQ-07 | WAITING -> NO_SHOW | Exits active queue; history shows NO_SHOW; wait sample excludes it |
| PQ-08 | Concurrent conflicting transition | Real DB test/local API concurrency serializes or rejects; never silently overwrites terminal state |
| PQ-09 | Analytics same date/doctor as SQL | Counts, mean and P90 match, empty rates display dash |
| PQ-10 | Date range includes zero days | Zero-activity days remain plotted; counts sum correctly |
| PQ-11 | Filter doctor then change quickly | No stale response replaces latest filtered data |
| PQ-12 | Prior-period count=0 | Absolute difference present, relative percent change dash |
| PQ-13 | CSV | Header, date range and doctor match; no patient columns |
| PQ-14 | NURSE or RECEPTIONIST attempts /api/analytics/dashboard | 403; no data leak. Missing token401 |
| PQ-15 | Expired token while viewing a detail route | Return to login, no stale protected view;403 does not log out |
| PQ-16 | Restart backend with same persisted key | No database setup; login works, valid old token still valid until expiry |
| PQ-17 | Desktop narrow width / mobile browser | Navigation usable, doctor table scrolls, labels/errors readable |

Optional real PostgreSQL integration suite (requires a NEW dedicated *_test database, not patient_queue):

```powershell
# Create an empty dedicated test DB deliberately. Do not run against your working database.
& 'C:\Program Files\PostgreSQL\18\bin\createdb.exe' -U postgres pqueue_integration_test
$env:PQ_RUN_DB_TESTS='true'
$env:PQ_TEST_JDBC_URL='jdbc:postgresql://localhost:5432/pqueue_integration_test'
$env:PQ_TEST_DB_USERNAME='postgres'
$p=Read-Host 'Test DB password' -AsSecureString
$env:PQ_TEST_DB_PASSWORD=[System.Net.NetworkCredential]::new('', $p).Password
cd 'E:\PQueue\patient-queue-backend'
mvn '-Dtest=PostgresIntegrationTest' test
Remove-Item Env:\PQ_RUN_DB_TESTS,Env:\PQ_TEST_JDBC_URL,Env:\PQ_TEST_DB_USERNAME,Env:\PQ_TEST_DB_PASSWORD
Remove-Variable p
```

The suite rejects a database whose current_database() does not end with `_test`. It creates random `pq_it_<uuid>` schemas and drops only those schemas on cleanup. It checks atomic counter allocation across connections, actual filtered SQL, duplicate partial indexes and row-lock serialization. It does not itself prove every HTTP interleaving or production load limit. If the test DB already exists, do not recreate or drop it casually; inspect it first.

## VoxFlow

| ID | Scenario | Expected result |
|---|---|---|
| VF-01 | Register, sign out, sign in | Salted password hash, HttpOnly session cookie, no raw token in JSON/localStorage |
| VF-02 | Persist an empty room, reload/restart | Room remains before any submission |
| VF-03 | Incognito public submit | No respondent login/name/email; creator receives one response on refresh |
| VF-04 | Open public metadata endpoint | No existing feedback array, owner email or session returned |
| VF-05 | Creator B accesses A's room GET/PATCH/CSV | 404 for each; anonymous private read401 |
| VF-06 | Change category/status and filter | Charts and inbox agree; actioned is explicitly a creator label |
| VF-07 | Filter more than 20 responses | Pagination does not change totals; export contains all filtered pages |
| VF-08 | Close then attempt submit | 409 and no append; reopen works; archived public room404 |
| VF-09 | Formula-like text with quotes/newlines, such as =1+1 | React displays text; exported cell is neutralized and CSV remains valid |
| VF-10 | Invalid/empty dates, reversed range, oversize text | Clear400 errors, no write, UI remains usable |
| VF-11 | Unknown legacy timestamps | No invented dates; explicit undated count; dated filters exclude them |
| VF-12 | Missing/wrong Origin or CSRF header on mutation |403; normal frontend request succeeds |
| VF-13 | Logout then reuse previous cookie |401; expiry also checked even before Mongo TTL deletion |
| VF-14 | Simultaneous submit/close and room-size boundary | Conditional update preserves OPEN/quota rule |
| VF-15 | Narrow mobile screen, keyboard navigation, network error | Forms/inbox readable; loading/error/empty states appropriate |

Real Mongo test (separate DB URI ending `_test`):

```powershell
cd 'E:\VoxFlow-v2\voxflow-backend'
$env:MONGO_TEST_URI='mongodb://127.0.0.1:27017/voxflow_test'
$env:VOXFLOW_HTTP_TEST='1'
npm run test:integration
Remove-Item Env:\MONGO_TEST_URI,Env:\VOXFLOW_HTTP_TEST
```

Only generated `vf_it_<uuid>_*` collections are removed by the test; it refuses a database not ending `_test`. Use your own protected connection string if authentication is configured. The HTTP test uses actual Express middleware and in-memory persistence; Mongo test separately checks the real adapter. Live smoke tests combine the running Express/Mongo application.

## Resume claim mapping

| Existing resume claim | Supporting source/demo | What must not be inferred |
|---|---|---|
| PQueue SQL-backed operational KPIs | AnalyticsService + InsightsScreen + SQL reconciliation | No measured real-clinic efficiency gain or completed Power BI file |
| PQueue end-to-end outpatient workflow | QueueEntryService + screens.dart + PQ-03..07 | Not an EHR, medical attachment system or production deployment |
| PQueue business-rule validation | QueueStatus, partial indexes, row lock, tests | Passing mocks is not passing actual PostgreSQL concurrency |
| Feedback collection through room links | PublicRoomPage -> submit -> atomic Mongo store | Anonymous does not mean untraceable or one response per person |
| React room-specific feedback dashboard | DashboardPage and owner-qualified API | No real-time subscriptions; new responses use refresh |
| NEW optional: private creator rooms and insights | Authentication, owner checks, categories/status/daily CSV | Add to resume only after local runtime tests pass; no AI sentiment |

This package does not alter your submitted resume. It supplies implementations and evidence paths for the verified claims and the intended upgrades. Save new screenshots only after testing, then update GitHub README screenshots from the real application rather than illustrative design mockups.
