# API contract v2

Base URL `http://localhost:8080` for local-only development; production requires HTTPS. Request/response JSON. All endpoints except `POST /api/users/login` require `Authorization: Bearer <jwt>`.

| Method | Endpoint | Roles | Purpose |
|---|---|---|---|
| POST | `/api/users/login` | Public | Get JWT |
| GET | `/api/users/me` | Authenticated | Retrieve current staff username and role |
| POST | `/api/users/register` | ADMIN | Register STAFF role: ADMIN, DOCTOR, NURSE, RECEPTIONIST |
| GET | `/api/doctors` | All staff | List doctor profiles |
| GET | `/api/doctors/{id}` | All staff | Doctor profile |
| POST/PUT/DELETE | `/api/doctors` / `/api/doctors/{id}` | ADMIN | Manage doctor profiles |
| GET | `/api/patients` | All staff | List patients |
| GET | `/api/patients/{id}` | All staff | Patient profile |
| POST | `/api/patients` | ADMIN, RECEPTIONIST | Register patient |
| PUT | `/api/patients/{id}` | ADMIN, RECEPTIONIST | Edit patient |
| DELETE | `/api/patients/{id}` | ADMIN | Delete patient (FK constraints may reject if history exists) |
| POST | `/api/queue/checkin` | ADMIN, RECEPTIONIST | Allocate token and check in |
| GET | `/api/queue/doctor/{doctorId}` | All staff | List today's WAITING/IN_CONSULTATION entries |
| GET | `/api/queue/patient/{patientId}` | ADMIN, DOCTOR, NURSE | Visit history |
| POST | `/api/queue/{id}/call-next` | ADMIN, DOCTOR | WAITING -> IN_CONSULTATION |
| POST | `/api/queue/{id}/complete` | ADMIN, DOCTOR | IN_CONSULTATION -> COMPLETED |
| POST | `/api/queue/{id}/no-show` | ADMIN, DOCTOR | WAITING -> NO_SHOW |
| GET | `/api/analytics/summary` | ADMIN | Aggregated counts, average and P90 wait |
| GET | `/api/analytics/daily` | ADMIN | Daily check-ins, completed, no-show counts |

## Samples

Login:
```http
POST /api/users/login
Content-Type: application/json

{"username":"clinic_admin","password":"YOUR_LOCAL_PASSWORD"}
```
Response: `{"token":"<signed-jwt>"}`. Do not paste real JWTs into shared logs.

Create staff (ADMIN only):
```http
POST /api/users/register
Authorization: Bearer <admin-token>
Content-Type: application/json

{"username":"front_desk_1","password":"CHANGE_TO_UNIQUE_12_PLUS_CHARS","role":"RECEPTIONIST"}
```

Create patient:
```http
POST /api/patients
Authorization: Bearer <staff-token>
Content-Type: application/json

{"name":"Demo Patient","age":32,"contact":"DEMO-0001"}
```

Check in:
```http
POST /api/queue/checkin
Authorization: Bearer <receptionist-token>
Content-Type: application/json

{"patientId":1,"doctorId":1}
```
The success response includes queue-entry ID, token number, doctor, patient, status and timestamps. The first valid token of a new doctor/service-date is 1, except where legacy rows exist (counter migrated to historical maximum). Duplicate active check-in is a 409; unauthorized is 403; missing JWT is 401.

Insights:
```
GET /api/analytics/summary?from=2026-10-01&to=2026-10-25&doctorId=1
GET /api/analytics/daily?from=2026-10-01&to=2026-10-25
```
Each date is inclusive. The optional `doctorId` filters aggregates. The default range is the seven calendar days ending today. Maximum range: 366 days. Response example:
```json
{"total":25,"completed":18,"noShows":2,"waiting":4,"inConsultation":1,
 "avgWaitMinutes":21.4,"p90WaitMinutes":43.2,"noShowRatePct":8.0}
```
These are illustrative values, **not actual metrics** from the uploaded database.

## Error responses

- 400: invalid DTO fields, typically `{ "error":"Validation failed", "fields": {"name":"..."}}`
- 401: missing/invalid authentication or login credentials.
- 403: authenticated but insufficient role.
- 404: not found (some legacy services use `IllegalArgumentException`).
- 409: business conflict or data-integrity conflict, e.g. duplicate active check-in, invalid state transition.

**Important current contract limitation:** role checks do not establish doctor-profile ownership. Restrict clinical data to synthetic data until a real clinician/department access model is implemented.
