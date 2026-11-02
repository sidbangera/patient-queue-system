# High-Level Architecture Design (HLD)

**Project Name**: Outpatient Patient Queue Management System 2.0
**Current Milestone**: Milestone M1 (Backend Reliability & Security Complete)
**Architecture Style**: Modular Layered Monolith (Spring Boot 4.x / Java 21) + Untrusted Multi-Platform Frontend (Flutter) + PostgreSQL

---

## 1. System Context Diagram (C4 Level 1)

```mermaid
flowchart TD
    subgraph Users ["Actors & Clients"]
        Doc["Doctor\n(Android / Web)"]
        Recep["Receptionist\n(Desktop Browser)"]
        Nurse["Nurse\n(Tablet / Mobile)"]
        Mgr["Operations Manager\n(Power BI / Excel)"]
    end

    subgraph TrustBoundary ["Trusted Clinic Network Perimeter"]
        FlutterApp["Flutter Cross-Platform Client\n[UNTRUSTED UI CLIENT - M2 TARGET]\n(Dart 3.x / Flutter)"]

        subgraph BackendSystem ["Patient Queue Core Backend\n[TRUSTED SERVICE ZONE - M1 COMPLETE]"]
            SpringBootApp["Spring Boot Modular Monolith\n(Java 21 / Spring Boot 4.x / Spring Data JPA)"]
        end

        Database[("PostgreSQL 18.x\n[SYSTEM OF RECORD]\n(ACID Relational Storage)")]
    end

    Doc -->|HTTPS / REST + JWT| FlutterApp
    Recep -->|HTTPS / REST + JWT| FlutterApp
    Nurse -->|HTTPS / REST + JWT| FlutterApp

    FlutterApp -->|JSON REST APIs / Bearer Auth| SpringBootApp
    SpringBootApp -->|HikariCP / JDBC Connection Pool| Database

    Mgr -.->|Privacy-Safe CSV / Curated Views [M3]| Database
```

### Module Status Legend
- **IMPLEMENTED & VERIFIED (Milestone M1)**:
  - Stable externalized JWT key engine (`JwtUtil`).
  - Stateless Spring Security filter chain with CORS (`SecurityConfig`).
  - Concurrency-safe daily queue token allocator with PostgreSQL atomic UPSERT (`DailyQueueCounter`).
  - Duplicate check-in prevention and consultation concurrency checks (`QueueEntryService`).
  - Centralized exception mapping (`GlobalExceptionHandler` handling 400, 401, 403, 404, 409).
- **PLANNED (Milestone M2)**:
  - Untrusted responsive Flutter application in `patient-queue-flutter`.
- **PLANNED (Milestone M3)**:
  - Operational analytics endpoints and privacy-safe CSV export for Power BI / Excel.
- **DEFERRED (Milestones M4-M5)**:
  - Private clinical file/imaging uploads with metadata tracking.
  - Nurse voice note recording and draft clinical transcription.

---

## 2. Component Diagram (C4 Level 2)

```mermaid
flowchart LR
    subgraph FlutterUI ["Flutter Untrusted Client Layer [M2 TARGET]"]
        F_Auth["Auth Feature\n(Login / Token Storage)"]
        F_Queue["Queue Feature\n(Doctor Live Queue / Status)"]
        F_Recep["Check-In Feature\n(Patient Reg / Token Card)"]
        F_HTTP["API Client / Transport\n(Dio / http with Interceptors)"]
    end

    subgraph SpringBootBackend ["Spring Boot 4.x Backend Container [M1 COMPLETE]"]
        subgraph SecurityModule ["Security & Auth Subsystem"]
            JwtFilter["JwtAuthFilter\n(Stateless Bearer Interceptor)"]
            JwtUtil["JwtUtil\n(Stable HMAC-SHA256 Token Engine)"]
            SecConfig["SecurityFilterChain\n(RBAC & CORS Rules)"]
        end

        subgraph CoreAPIs ["REST API Controllers"]
            AuthCtrl["UserController\n(/api/users)"]
            PatCtrl["PatientController\n(/api/patients)"]
            DocCtrl["DoctorController\n(/api/doctors)"]
            QueueCtrl["QueueEntryController\n(/api/queue)"]
            AnalyticsCtrl["AnalyticsController [M3]\n(/api/analytics)"]
        end

        subgraph ServiceLayer ["Business Logic Services"]
            UserSvc["UserService\n(Server-Controlled RBAC)"]
            PatSvc["PatientService"]
            DocSvc["DoctorService"]
            QueueSvc["QueueEntryService\n(State Machine & Token Engine)"]
            AnalyticsSvc["AnalyticsService [M3]"]
        end

        subgraph RepoLayer ["Spring Data Repositories"]
            UserRepo["UserRepository"]
            PatRepo["PatientRepository"]
            DocRepo["DoctorRepository"]
            QueueRepo["QueueEntryRepository"]
            CounterRepo["DailyQueueCounterRepository"]
        end
    end

    subgraph DataStore ["PostgreSQL Database (Active)"]
        T_User[("app_user")]
        T_Pat[("patient")]
        T_Doc[("doctor")]
        T_Queue[("queue_entry\n(uk_doctor_date_token)")]
        T_Counter[("daily_queue_counter\n(uk_doctor_service_date)")]
    end

    FlutterUI -->|REST / JSON| SecConfig
    SecConfig --> JwtFilter
    JwtFilter --> JwtUtil
    JwtFilter --> CoreAPIs

    AuthCtrl --> UserSvc
    PatCtrl --> PatSvc
    DocCtrl --> DocSvc
    QueueCtrl --> QueueSvc
    AnalyticsCtrl --> AnalyticsSvc

    UserSvc --> UserRepo
    PatSvc --> PatRepo
    DocSvc --> DocRepo
    QueueSvc --> QueueRepo
    QueueSvc --> CounterRepo
    AnalyticsSvc --> QueueRepo

    UserRepo --> T_User
    PatRepo --> T_Pat
    DocRepo --> T_Doc
    QueueRepo --> T_Queue
    CounterRepo --> T_Counter
```

---

## 3. End-to-End Sequence Workflows (Verified in M1)

### 3.1 Concurrency-Safe Check-In Sequence
```mermaid
sequenceDiagram
    autonumber
    actor Receptionist
    participant Ctrl as QueueEntryController
    participant Svc as QueueEntryService
    participant CounterRepo as DailyQueueCounterRepository
    participant QueueRepo as QueueEntryRepository
    participant DB as PostgreSQL 18.x

    Receptionist->>Ctrl: POST /api/queue/checkin {patientId: 101, doctorId: 5}
    Note over Ctrl: Validated JWT has ROLE_RECEPTIONIST or ROLE_ADMIN
    Ctrl->>Svc: checkIn(101, 5)
    Note over Svc: Starts @Transactional boundary
    Svc->>QueueRepo: existsByPatientIdAndDoctorIdAndServiceDateAndStatusIn(...)
    QueueRepo->>DB: Check for active entries (WAITING or IN_CONSULTATION)
    DB-->>QueueRepo: false (No active entry)

    Svc->>CounterRepo: incrementAndGetToken(5, '2026-10-24')
    CounterRepo->>DB: INSERT INTO daily_queue_counter ... ON CONFLICT DO UPDATE ... RETURNING last_token_number
    Note over DB: Atomically acquires row lock on (5, '2026-10-24') & increments
    DB-->>CounterRepo: Returns next token (e.g. 14)

    Svc->>QueueRepo: save(QueueEntry(token=14, serviceDate='2026-10-24', ...))
    QueueRepo->>DB: INSERT INTO queue_entry
    Note over DB: Enforces uk_doctor_date_token constraint
    DB-->>QueueRepo: Persisted QueueEntry (ID: 542)
    Svc-->>Ctrl: Returns QueueEntry
    Ctrl-->>Receptionist: 201 Created {id: 542, tokenNumber: 14, status: "WAITING"}
```

---

## 4. Security & Trust Boundaries

```mermaid
graph TD
    subgraph UntrustedZone ["Untrusted Client Zone (Flutter Web & Mobile)"]
        WebBrowser["Web Browser (Chrome/Edge)\nOrigin: http://localhost:*"]
        AndroidDevice["Physical Android Device / Emulator"]
    end

    subgraph Perimeter ["Security Filter Layer (Spring Security 6.x)"]
        CORS["CORS Policy Validation\n(Allowed: http://localhost:*, http://127.0.0.1:*)"]
        Filter["JwtAuthFilter\n- Stateless Bearer Interceptor\n- Catches Expired/Malformed Exceptions\n- Populates SecurityContextHolder"]
        AuthEntry["Custom AuthenticationEntryPoint (401 JSON)"]
        AccessDen["Custom AccessDeniedHandler (403 JSON)"]
    end

    subgraph SecureCore ["Trusted Application & Database Zone"]
        App["Services & Domain Logic\n- Server-Controlled RBAC\n- Concurrency Protection"]
        DB[("PostgreSQL 18.1\n- Unique Constraints\n- ACID Transaction Guarantees")]
    end

    WebBrowser --> CORS
    AndroidDevice --> Filter
    CORS --> Filter
    Filter -->|Valid Principal| App
    Filter -.->|Expired/Bad Token| AuthEntry
    App -.->|Insufficient Role| AccessDen
    App -->|Parameterized Queries & Atomic UPSERT| DB

    style UntrustedZone fill:#ffe6e6,stroke:#ff6666,stroke-width:2px;
    style Perimeter fill:#fff2cc,stroke:#d6b656,stroke-width:2px;
    style SecureCore fill:#e6ffe6,stroke:#66cc66,stroke-width:2px;
```
