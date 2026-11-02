# Product requirements, user stories and UAT

## Problem statement

A doctor needs a quick operational view of today's queue and previous patient visits; reception needs error-resistant check-in; management needs aggregate queue performance. The project reduces multiple manual handoffs **without** turning into a full electronic medical record system.

## Stakeholders

- **Doctor:** calls next patient, sees own queue, retrieves visit history and completes a consultation.
- **Nurse:** reads current queue and patient data.
- **Receptionist:** registers patients and allocates check-in tokens.
- **Admin:** creates staff accounts and doctor profiles, reviews synthetic aggregate KPI performance.
- **Patient:** subject of records, no patient portal in scope.

## Current process vs future process

| Current/fragmented | Proposed software-supported process |
|---|---|
| Staff manually record arrivals and token numbers | System assigns atomic per-doctor, per-day tokens |
| Doctor calls based on physical list | Live queue with WAITING/IN_CONSULTATION status and transitions |
| Visit history spread across records | Visit history lookup in one client screen |
| Administrators estimate bottlenecks | Aggregate daily volume, no-show, mean/P90 wait |

## Prioritized functional requirements

| ID | Requirement | Acceptance criterion | Status in delivered source |
|---|---|---|---|
| FR-01 | Staff authenticate | Login returns JWT; `/me` returns correct role | Implemented (not run here) |
| FR-02 | Admin provisions staff | Anonymous request to `/register` must be 401 | Implemented (not run here) |
| FR-03 | Reception registers patient | Required fields validated; created record returned | Existing backend and new UI |
| FR-04 | Reception checks in patient | Unique doctor/date token; duplicate active check-in returns 409 | Code + DB constraint; migration pending |
| FR-05 | Doctor advances status | Only valid WAITING/IN_CONSULTATION transitions, invalid returns 409 | Existing state machine + improvements |
| FR-06 | Doctor accesses visit history | Chronologically ordered queue visits for patient | New endpoint and UI |
| FR-07 | Admin views KPIs | Correct denominator and wait definitions; aggregated data only | New SQL and UI; runtime pending |
| FR-08 | Support mobile and desktop screens | Responsive Flutter navigation and forms | New Flutter source; device test pending |
| FR-09 | Upload/preview X-ray and reports | Role-scoped storage and access checks | Planned only |
| FR-10 | Voice notes and transcription | Consent, permissions, redaction/review | Planned only |

## Non-functional requirements

- Real database constraints for concurrency, not only synchronized Java methods.
- No hardcoded secrets; role-controlled endpoints, HTTPS before external access.
- Synthetic demo dataset only until real health-data safeguards are added.
- Clear error handling and explicit loading/empty states.
- SQL migration must abort on ambiguous historical conflicts.

## UAT scripts (manual, local test DB)

1. **UA-01 Role gate:** Login as receptionist; attempt `POST /api/doctors`: 403; as admin: 201.
2. **UA-02 Privilege gate:** Attempt anonymous `POST /api/users/register` with role ADMIN: 401.
3. **UA-03 Queue numbering:** Check in two distinct patients to same doctor/day: sequential distinct tokens, stored.
4. **UA-04 Same-patient race:** Two simultaneous check-ins for same patient/doctor/day: exactly one active visit, other 409.
5. **UA-05 Doctor busy:** Two WAITING patients; advance first to IN_CONSULTATION, then second: 409 until first completes.
6. **UA-06 Patient history:** Complete visit, navigate to patient details as doctor: past visit is visible in reverse chronological order.
7. **UA-07 Analytics reconciliation:** Compare `GET /api/analytics/summary` to hand-written GROUP BY against the synthetic database.
8. **UA-08 Flutter responsiveness:** Run at narrow Android viewport and desktop Chrome; no clipped navigation or forms.
9. **UA-09 Backend restart:** Restart with same secret; login and API calls work. Restart with different secret, old token rejected.
10. **UA-10 Migration guard:** On an intentionally conflicting test-copy DB, migration aborts and does not remove rows.

Record screenshot, timestamp, expected/actual result, test environment and identified defects. These are planned tests, **not claims of execution**.
