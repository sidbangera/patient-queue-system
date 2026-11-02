# PQueue KPI definitions and API contract

## Cohort first, calculation second

All report filters select visits by **service_date at check-in**, inclusive from/to and optionally doctorId. Completion/no-show counts are the current state of that cohort at report time, not a count of events completed during the calendar interval. This matters when a consultation crosses midnight or an earlier waiting visit is resolved later.

| Metric | Definition | Empty/invalid behavior |
|---|---|---|
| Check-ins | Number of selected queue_entry records | 0 |
| Completed | Selected visits with status COMPLETED | 0 |
| Waiting / in consultation | Current states among selected visits | Historical filters can include stale open visits; not automatically today's live queue |
| Completion rate | 100 * completed / check-ins | null when no visits; display dash |
| No-show rate | 100 * NO_SHOW / check-ins | Not an appointment attendance rate; null when no visits |
| Valid wait sample | Status IN_CONSULTATION or COMPLETED, both timestamps present, start >= check-in | Other visits excluded; count displayed |
| Mean wait | Mean (start-check-in) in minutes among valid wait samples | null when sample empty |
| P90 wait | PostgreSQL PERCENTILE_CONT(0.9) of valid wait minutes | Interpolated percentile, not necessarily an observed value; null empty |
| Mean consultation | COMPLETED visits with end >= start >= check-in | null when no valid durations |
| Daily arrivals | Check-ins grouped by service_date, including zero days | Sum equals summary total for the same snapshot/filter |
| Hourly arrivals | Check-ins grouped by clinic-local check_in_time hour | 24 buckets; missing check-in timestamps excluded |
| Doctor workload | Counts and valid mean wait by doctor | Count is not utilization, productivity, or quality of care |
| Prior period | Immediately preceding equal number of inclusive days | Same doctor filter |
| Check-in change % | 100 * (current-prior) / prior | null if prior=0, not infinity or 100% |
| Completion rate change | Current percentage minus prior percentage | Percentage POINTS, not relative percent change; null if either denominator=0 |

Example: Oct 25-31 (7 inclusive days) compares with Oct 18-24. Two visits, one completed, one waiting means completion=50%; only the started visit contributes a wait. A P90 of 12.4 with one sample says nothing reliable about a population tail.

Data-quality checks count missing check-in times, missing doctor/patient references, service-date versus local timestamp date mismatch, negative waits/durations, completed records with incomplete timestamps, and incompatible status/timestamp combinations. `flaggedVisits` counts each selected visit once; individual flags can overlap and must not be added as distinct visits. Reports do not repair records. Missing service_date records cannot enter a date cohort, but the migrated schema requires NOT NULL.

## Endpoints

Existing login is `POST /api/users/login` with `{username,password}` and returns `{token}`. Retrieve account details separately with `GET /api/users/me` and `Authorization: Bearer <token>`.

All reporting endpoints are authenticated ADMIN GETs:

| Endpoint | Response |
|---|---|
| `/api/analytics/dashboard` | from, to, clinicToday, clinicZone, asOf, summary, comparison, daily, doctors, hourly, quality |
| `/summary` | total, completed, noShows, waiting, inConsultation, waitSampleSize, avgWaitMinutes, p90WaitMinutes, avgConsultationMinutes, noShowRatePct, completionRatePct |
| `/daily` | array {date,checkins,completed,noShows} |
| `/doctors` | array {doctorId,doctorName,department,checkins,completed,noShows,waiting,avgWaitMinutes} |
| `/hourly` | array {hour,checkins}, hours 0..23 |
| `/comparison` | currentFrom/currentTo, previousFrom/previousTo, current/previous summary, checkinsDelta, completedDelta, checkinsChangePct, completionRateDeltaPoints |
| `/quality` | aggregate quality counters |
| `/export.csv` | UTF-8 text CSV, attachment; no patient identifiers or feedback text |

Common optional parameters: `from=YYYY-MM-DD`, `to=YYYY-MM-DD`, `doctorId=positive_integer`. Default end is clinicToday and start is end minus six days. Maximum 366 days. Invalid/reversed dates or nonpositive doctor ID -> 400. Missing/expired authentication -> 401; non-admin -> 403. Unknown positive doctor ID yields an empty filtered cohort, not access to additional records.

CSV columns: `service_date,doctor_id,check_ins,completed,no_shows,avg_wait_minutes,p90_wait_minutes`. Missing averages are empty, not zero. CSV includes doctor IDs but no patient identity. Flutter uses **Copy CSV**; the authenticated endpoint supports downloading. Do not describe the Flutter action as a native file-save dialog.

## SQL reconciliation

Use `database/06_reporting_validation_READ_ONLY.sql`; change its explicit date range and optional doctor ID to the exact dashboard filters. The SQL script is read-only. Reconcile summary counts, valid wait sample, means and P90; check daily sum and quality flags. A concurrent save between separate SQL/API requests can legitimately change counts. Test with a stable synthetic fixture or no writes during reconciliation.

## Excel / Power BI lab

1. Copy CSV from PQueue, paste into a plain text file, save UTF-8 `.csv`; or download using an authenticated REST client without sharing the token.
2. Import via Excel Data > From Text/CSV or Power BI Get data > Text/CSV.
3. Set service_date to Date; IDs/counts to whole number; wait columns to decimal; preserve blanks as missing.
4. Add slicers for date and doctor ID, daily check-in/completion chart and no-show count.
5. Example DAX: `Check-ins = SUM(DoctorDay[check_ins])`; `Completed = SUM(DoctorDay[completed])`; `Completion % = DIVIDE([Completed], [Check-ins])`. Format the last as percentage (do not multiply again by 100).
6. Reconcile filtered totals to the API before interpreting trends.
7. **Do not average daily P90 values to obtain overall P90.** It must be calculated over underlying valid visit waits. Do not take an unweighted mean of doctor/day average waits: this CSV does not include valid-wait sample counts required for weighting. Use the API's overall statistic or extend a controlled aggregate export with the correct denominator.
8. Do not infer staffing performance from two synthetic visits. Explain small samples, case-mix, incomplete visits, clinic hours and missing appointment data. A dashboard demonstrates analysis capability, not proven reductions in patient wait.

No .pbix workbook or completed Power BI project is claimed in this package. This is an executable learning exercise after local API validation.
