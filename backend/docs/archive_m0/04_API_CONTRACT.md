# Integration REST API Contract Specification

**Document Version**: 2.1 (Milestone M1 Implemented & Verified Contract)
**Base URL**: `http://localhost:8080` (Local) / `http://<lan-ip>:8080` (Mobile Emulator/Device)
**Content-Type**: `application/json`
**Authentication**: HTTP Authorization Header with Bearer Token (`Authorization: Bearer <jwt_token>`)

---

## 1. Authentication Endpoints (`/api/users`)

### 1.1 User Login
- **Method**: `POST`
- **Path**: `/api/users/login`
- **Permitted Roles**: Public (`permitAll()`)
- **Request Body**:
```json
{
  "username": "dr_house",
  "password": "Password123!"
}
```
- **Responses**:
  - `200 OK`:
    ```json
    {
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
    }
    ```
  - `401 Unauthorized` (Invalid credentials):
    ```json
    "Invalid Username or Password"
    ```
  - `400 Bad Request` (Validation failure):
    ```json
    {
      "username": "Username must not be blank"
    }
    ```

### 1.2 User Registration (Server-Controlled Roles)
- **Method**: `POST`
- **Path**: `/api/users/register`
- **Permitted Roles**: Public (Default: `RECEPTIONIST`); `ADMIN` authority required to register `ADMIN` or `DOCTOR`.
- **Request Body**:
```json
{
  "username": "desk_staff1",
  "password": "SecurePassword2026!",
  "role": "RECEPTIONIST"
}
```
- **Responses**:
  - `201 Created`:
    ```json
    {
      "id": 4,
      "username": "desk_staff1",
      "role": "RECEPTIONIST"
    }
    ```
  - `403 Forbidden` (Anonymous caller attempting to assign `ADMIN` or `DOCTOR` role):
    ```json
    "Access denied: Cannot assign privileged role 'ADMIN' without ADMIN privileges"
    ```

---

## 2. Patient Management Endpoints (`/api/patients`)

### 2.1 Register New Patient
- **Method**: `POST`
- **Path**: `/api/patients`
- **Permitted Roles**: `RECEPTIONIST`, `ADMIN`
- **Request Body**:
```json
{
  "name": "Synthetic Patient Alpha",
  "age": 34,
  "contact": "+91-9876543210"
}
```
- **Responses**:
  - `201 Created` (Verified in M1):
    ```json
    {
      "id": 101,
      "name": "Synthetic Patient Alpha",
      "age": 34,
      "contact": "+91-9876543210"
    }
    ```
  - `400 Bad Request`: Field validation error (e.g. negative age, blank name).

### 2.2 Get Patient By ID
- **Method**: `GET`
- **Path**: `/api/patients/{id}`
- **Permitted Roles**: `DOCTOR`, `RECEPTIONIST`, `NURSE`, `ADMIN`
- **Responses**:
  - `200 OK`: Patient JSON object.
  - `404 Not Found`: Patient with given ID does not exist.

### 2.3 List All Patients
- **Method**: `GET`
- **Path**: `/api/patients`
- **Permitted Roles**: `DOCTOR`, `RECEPTIONIST`, `NURSE`, `ADMIN`
- **Responses**:
  - `200 OK`: Array of Patient JSON objects.

### 2.4 Delete Patient
- **Method**: `DELETE`
- **Path**: `/api/patients/{id}`
- **Permitted Roles**: `ADMIN`
- **Responses**:
  - `204 No Content`: Successful deletion.
  - `403 Forbidden`: Non-admin caller.
  - `404 Not Found`: Patient missing.

---

## 3. Doctor Management Endpoints (`/api/doctors`)

### 3.1 Register Doctor
- **Method**: `POST`
- **Path**: `/api/doctors`
- **Permitted Roles**: `ADMIN`
- **Request Body**:
```json
{
  "name": "Dr. Gregory House",
  "age": 52,
  "department": "Diagnostics"
}
```
- **Responses**:
  - `201 Created`: Doctor JSON object.
  - `403 Forbidden`: Non-admin caller.

### 3.2 List All Doctors
- **Method**: `GET`
- **Path**: `/api/doctors`
- **Permitted Roles**: `authenticated()`
- **Responses**:
  - `200 OK`: Array of Doctor JSON objects.

---

## 4. Queue Management Endpoints (`/api/queue`)

### 4.1 Check-In Patient (Concurrency-Safe & Collision-Free)
- **Method**: `POST`
- **Path**: `/api/queue/checkin`
- **Permitted Roles**: `RECEPTIONIST`, `ADMIN`
- **Request Body**:
```json
{
  "patientId": 101,
  "doctorId": 1
}
```
- **Responses**:
  - `201 Created`:
    ```json
    {
      "id": 542,
      "tokenNumber": 1,
      "doctor": {
        "id": 1,
        "name": "Dr. Gregory House",
        "department": "Diagnostics"
      },
      "patient": {
        "id": 101,
        "name": "Synthetic Patient Alpha",
        "age": 34
      },
      "status": "WAITING",
      "serviceDate": "2026-10-24",
      "checkInTime": "2026-10-24T10:15:30.123456",
      "consultationStartTime": null,
      "consultationEndTime": null
    }
    ```
  - `409 Conflict` (Duplicate check-in):
    ```json
    "Patient Synthetic Patient Alpha (ID: 101) already has an active queue entry with Dr. Dr. Gregory House today."
    ```
  - `404 Not Found`: Patient or Doctor ID missing.

### 4.2 Get Doctor's Active Queue (Today)
- **Method**: `GET`
- **Path**: `/api/queue/doctor/{doctorId}`
- **Permitted Roles**: `DOCTOR`, `NURSE`, `RECEPTIONIST`, `ADMIN`
- **Description**: Returns all queue entries for the doctor on current calendar date with status `WAITING` or `IN_CONSULTATION`, sorted ascending by token number.
- **Responses**:
  - `200 OK`: Array of active QueueEntry objects.

### 4.3 Advance State: Call Next (`WAITING` → `IN_CONSULTATION`)
- **Method**: `POST`
- **Path**: `/api/queue/{id}/call-next`
- **Permitted Roles**: `DOCTOR`, `ADMIN`
- **Responses**:
  - `200 OK`: Updated QueueEntry with `status: "IN_CONSULTATION"` and `consultationStartTime` populated.
  - `400 Bad Request` (Illegal state transition):
    ```json
    "Cannot transition from COMPLETED to IN_CONSULTATION"
    ```
  - `400 Bad Request` (Doctor already has active consultation):
    ```json
    "Doctor Dr. Gregory House already has patient (Token #1) in consultation. Conclude that visit first."
    ```

### 4.4 Advance State: Complete Consultation (`IN_CONSULTATION` → `COMPLETED`)
- **Method**: `POST`
- **Path**: `/api/queue/{id}/complete`
- **Permitted Roles**: `DOCTOR`, `ADMIN`
- **Responses**:
  - `200 OK`: Updated QueueEntry with `status: "COMPLETED"` and `consultationEndTime` populated.
  - `400 Bad Request`: `Cannot transition from WAITING to COMPLETED`.

### 4.5 Advance State: Mark No-Show (`WAITING` → `NO_SHOW`)
- **Method**: `POST`
- **Path**: `/api/queue/{id}/no-show`
- **Permitted Roles**: `DOCTOR`, `ADMIN`
- **Responses**:
  - `200 OK`: Updated QueueEntry with `status: "NO_SHOW"`.
  - `400 Bad Request`: `Cannot transition from IN_CONSULTATION to NO_SHOW`.
