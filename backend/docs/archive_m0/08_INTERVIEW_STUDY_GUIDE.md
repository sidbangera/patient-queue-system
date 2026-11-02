# Technical & Functional Analyst Interview Study Guide

**Author**: Antigravity Technical Mentor
**Target Assessment**: Thomson Reuters Internship Online Assessment & Technical Interviews (SQL, Fundamentals, Architecture, BA/Product Thinking)
**System Analyzed**: Outpatient Patient Queue Management System 2.0

---

## 1. End-to-End Request/Data Path Walkthrough

When an interviewer asks: *"Explain what happens under the hood when a Doctor taps 'Call Next' on their mobile device or browser"*:

```mermaid
sequenceDiagram
    autonumber
    participant UI as Flutter App (Dart)
    participant Net as HTTP Client / Interceptor
    participant Gateway as Tomcat Web Server
    participant Sec as SecurityFilterChain & JwtFilter
    participant Ctrl as QueueEntryController
    participant Svc as QueueEntryService
    participant Repo as QueueEntryRepository
    participant DB as PostgreSQL 18.x

    UI->>Net: User taps "Call Next" on Token #14
    Net->>Gateway: POST /api/queue/542/call-next (Header: Authorization: Bearer <jwt>)
    Gateway->>Sec: Passes HttpServletRequest to Filter Chain
    Sec->>Sec: JwtAuthFilter extracts token, validates HMAC-SHA256 signature & exp
    Sec->>Sec: Sets Authentication in SecurityContextHolder (Role: ROLE_DOCTOR)
    Sec->>Ctrl: Dispatches request to @PostMapping("/{id}/call-next")
    Ctrl->>Svc: Calls queueEntryService.changeState(542, IN_CONSULTATION)
    Note over Svc: Starts database transaction boundary
    Svc->>Repo: findById(542)
    Repo->>DB: SELECT * FROM queue_entry WHERE id = 542
    DB-->>Repo: Returns QueueEntry entity
    Svc->>Svc: Executes State Machine: entry.getStatus().canTransitionTo(IN_CONSULTATION)
    alt Transition is invalid (e.g. from COMPLETED)
        Svc-->>Ctrl: Throws IllegalStateException
        Ctrl-->>Gateway: Caught by GlobalExceptionHandler
        Gateway-->>UI: HTTP 400 Bad Request ("Cannot transition from COMPLETED to IN_CONSULTATION")
    else Transition is valid
        Svc->>Svc: entry.setStatus(IN_CONSULTATION); entry.setConsultationStartTime(LocalDateTime.now())
        Svc->>Repo: save(entry)
        Repo->>DB: UPDATE queue_entry SET status='IN_CONSULTATION', consultation_start_time=... WHERE id=542
        DB-->>Repo: Row updated
        Repo-->>Svc: Persisted entity
        Svc-->>Ctrl: Returns QueueEntry
        Ctrl-->>Gateway: Returns ResponseEntity.ok(entry)
        Gateway-->>UI: HTTP 200 OK with JSON representation
        UI->>UI: Updates widget state; patient turns "IN CONSULTATION"
    end
```

---

## 2. Core Concepts: Short Answers & Deep Architectural Explanations

### Concept 1: REST vs RPC vs WebSockets
- **Short Answer**: REST is an architectural style based on stateless, standardized HTTP verbs (GET, POST, PUT, DELETE) operating on resource URIs. RPC invokes remote procedures. WebSockets maintain a persistent, bidirectional TCP connection.
- **Deep Interview Follow-Up**: *"Why did you use HTTP polling / REST instead of WebSockets for the patient queue?"*
  - *Answer*: Outpatient clinic queues operate on human timescales (seconds to minutes, not milliseconds like algorithmic trading). WebSockets introduce heavy operational complexity: connection drops on mobile Wi-Fi, heartbeat timers, memory overhead on the server maintaining open sockets, and load-balancer sticky session requirements. REST with lightweight polling or user-driven refreshes is completely reliable, horizontally scalable, stateless, and trivial to cache.

### Concept 2: Database ACID Properties in Queue Management
- **Atomicity**: The check-in operation either completely allocates the token and inserts the queue entry, or rolls back entirely. No partial records exist.
- **Consistency**: Database constraints (foreign keys on `patient_id` and `doctor_id`, status check constraints) are preserved before and after every transaction.
- **Isolation**: Concurrent check-ins by two front-desk receptionists must not read uncommitted token counts or overwrite each other (Isolation levels: Read Committed vs Repeatable Read vs Row-level pessimistic locks).
- **Durability**: Once a patient's consultation is marked `COMPLETED` and committed, the timestamp and outcome remain permanently on disk in the PostgreSQL WAL (Write-Ahead Log) even if power fails immediately after.

### Concept 3: Stateless JWT Authentication vs Stateful Sessions
- **Short Answer**: Stateful sessions store user state in server memory (HTTP Session) and send a `JSESSIONID` cookie. JWT encodes user claims and signature into a self-contained token passed in the `Authorization: Bearer` header.
- **Deep Interview Follow-Up**: *"What was the critical bug in your initial JWT implementation, and how did you resolve it?"*
  - *Answer*: In the initial codebase, `JwtUtil` initialized its HMAC secret using `Jwts.SIG.HS256.key().build()`, generating a random 256-bit key in memory on JVM startup. This meant whenever the backend rebooted or auto-scaled, all existing tokens became invalid, forcing every doctor and receptionist to re-login. We resolved this by externalizing a stable, Base64-encoded secret into `application.properties` or environment variables, while ensuring the secret is never committed to Git.

### Concept 4: The State Pattern in Java Enums
- **Short Answer**: Enums in Java are full classes that can declare abstract methods and override them per enum constant.
- **Deep Interview Follow-Up**: *"Why implement the State Pattern using an Enum rather than separate classes implementing a State interface?"*
  - *Answer*: For a fixed, closed set of states (`WAITING`, `IN_CONSULTATION`, `COMPLETED`, `NO_SHOW`), the classic GoF pattern requires 5 separate class files and extra allocation overhead. Java enums provide compile-time safety, built-in serialization, singleton guarantees per constant, and encapsulate transition logic directly (`canTransitionTo(next)`), preventing illegal transitions while keeping code concise and maintainable.

---

## 3. Five Realistic Interview Follow-Up Questions (Source-Grounded)

### Question 1: *"How do you prevent two receptionists from generating the exact same token number for the same doctor under high concurrency?"*
- **Source Answer**: In the initial code, token allocation was calculated as `countByDoctorAndCheckInTimeBetween() + 1`. This creates a classic Read-Compute-Write race condition. In M1, we introduce an isolated transaction (`@Transactional`) that locks the doctor's daily sequence or queries `SELECT COALESCE(MAX(token_number), 0) + 1 FROM queue_entry WHERE doctor_id = :id AND check_in_time >= CURRENT_DATE FOR UPDATE`. This row-level exclusive lock serializes concurrent check-in attempts for the same doctor. Additionally, a database unique constraint `UNIQUE (doctor_id, check_in_date, token_number)` acts as a bulletproof safety net at the storage layer.

### Question 2: *"Why does `PatientController` return HTTP 200 on creation while `DoctorController` returns HTTP 201?"*
- **Source Answer**: This was an inconsistency discovered during our M0 audit. `DoctorController.java` correctly returns `ResponseEntity.status(HttpStatus.CREATED).body(doctor)`. `PatientController.java` directly returned the entity `Patient` from the service, which Spring MVC defaults to HTTP 200 OK. In accordance with REST best practices, newly created resources must return HTTP 201 Created with the representation or `Location` header. We documented this in `04_API_CONTRACT.md` and reconciled it.

### Question 3: *"Why did you separate the Flutter application into its own sibling folder rather than keeping it inside `patient-queue-system/frontend`?"*
- **Source Answer**: Separation of concerns and independent deployment lifecycles. The Spring Boot backend and Flutter frontend are decoupled systems communicating strictly over documented JSON REST APIs. Keeping them in separate repositories prevents bloated Maven builds, prevents frontend toolchains (Dart, Gradle, CocoaPods) from interfering with Java CI/CD pipelines, and treats the Flutter client as an untrusted third-party consumer.

### Question 4: *"Why do we use Median and P90 waiting times instead of just the Mean/Average in hospital analytics?"*
- **Source Answer**: Outpatient waiting times are heavily right-skewed. If 9 patients wait 10 minutes and 1 emergency delay causes 1 patient to wait 180 minutes, the mean is 27 minutes—which misrepresents the experience of 90% of patients. Median (P50) reflects the typical patient wait, while P90 (90th percentile) reflects the service-level agreement (SLA) threshold, highlighting long-tail delays for clinic operational improvement.

### Question 5: *"How did you prevent Privilege Escalation in your registration endpoint?"*
- **Source Answer**: In the uncommitted local code, `RegisterDTO` accepted an unconstrained `Role role` field directly from the client JSON payload, allowing any anonymous user to send `{"role": "ADMIN"}`. In M1, public self-registration is either restricted strictly to client-level roles (or defaults hardcoded to `RECEPTIONIST`), and creating administrative accounts (`ADMIN`, `DOCTOR`) requires existing administrative authentication (`hasRole('ADMIN')`).

---

## 4. Hands-On SQL Practice Exercise (Thomson Reuters Assessment Preparation)

### Problem Scenario:
The hospital operations director wants to identify peak arrival congestion hours. Write a PostgreSQL query that calculates for each hour of the day (00:00 to 23:00):
1. The total number of patient check-ins.
2. The percentage of daily check-ins that occurred in that hour.
3. The running cumulative check-in count throughout the day.
4. The average wait time (in minutes) for patients who checked in during that hour and eventually reached consultation.

### Target Schema:
`queue_entry (id, doctor_id, patient_id, status, check_in_time, consultation_start_time)`

### Solution Query:
```sql
WITH hourly_stats AS (
    SELECT
        EXTRACT(HOUR FROM check_in_time)::INTEGER AS arrival_hour,
        COUNT(id) AS hourly_check_ins,
        AVG(
            CASE
                WHEN consultation_start_time IS NOT NULL AND consultation_start_time >= check_in_time
                THEN EXTRACT(EPOCH FROM (consultation_start_time - check_in_time)) / 60.0
                ELSE NULL
            END
        ) AS avg_wait_minutes
    FROM queue_entry
    WHERE check_in_time >= CURRENT_DATE
      AND check_in_time < CURRENT_DATE + INTERVAL '1 day'
    GROUP BY EXTRACT(HOUR FROM check_in_time)::INTEGER
)
SELECT
    arrival_hour,
    hourly_check_ins,
    ROUND(
        (hourly_check_ins::DECIMAL / SUM(hourly_check_ins) OVER ()) * 100.0,
        2
    ) AS pct_of_daily_volume,
    SUM(hourly_check_ins) OVER (
        ORDER BY arrival_hour
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    ) AS running_cumulative_check_ins,
    ROUND(avg_wait_minutes::NUMERIC, 1) AS avg_wait_minutes
FROM hourly_stats
ORDER BY arrival_hour ASC;
```

### Explanation of Advanced SQL Techniques Used:
- `EXTRACT(HOUR FROM check_in_time)`: Extracts the hour component (0-23) for cohort grouping.
- `SUM(hourly_check_ins) OVER ()`: Window function without `ORDER BY` computes the grand total across all hours for percentage calculation without needing a self-join.
- `SUM(hourly_check_ins) OVER (ORDER BY arrival_hour)`: Window function with `ORDER BY` computes the running cumulative sum.
- `CASE` inside `AVG()`: Discards `NO_SHOW` and unserved patients from wait-time averages without dropping their check-in volume count.
