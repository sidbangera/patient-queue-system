# Implementation Inventory & Verification Matrix

**Last Updated**: October 24, 2026 (Milestone M1 Completion)
**Auditor & Implementer**: Antigravity Assistant
**Backend Scope**: `E:\Java SpringBoot\patient-queue-system`
**Frontend Scope**: `E:\Java SpringBoot\patient-queue-flutter` (Target Flutter workspace; confirmed empty directory, reserved for M2)
**Workspace Protection**: Sibling projects (`demo`, `dynamodemo`, `store`) are strictly isolated and untouched.

---

## 1. Classification Legend

- **TESTED & VERIFIED**: Fully implemented, compiled, and validated with passing automated unit/concurrency/integration tests.
- **VERIFIED IMPLEMENTED**: Code exists and executes in runtime, with verified behavior.
- **SOURCE INSPECTION FINDING**: Baseline structure observed during code audit prior to refactoring.
- **PLANNED CHANGES**: Architecture items targeted for upcoming milestones.
- **UNRESOLVED ISSUES**: Known environmental limitations, external dependencies, or technical debt tracked in backlog.

---

## 2. Feature & Component Inventory Matrix

| Feature / Artifact | Path(s) | Status | Test / Verification Evidence & Operational Notes |
|---|---|---|---|
| **Queue State Machine** | `entity/QueueStatus.java` | **TESTED & VERIFIED** | Validated via `QueueStatusTest` (12 test cases). Verified `WAITING -> IN_CONSULTATION`, `WAITING -> NO_SHOW`, `IN_CONSULTATION -> COMPLETED`, and all invalid/terminal transitions. |
| **Concurrency-Safe Token Allocation** | `entity/DailyQueueCounter.java`, `repository/DailyQueueCounterRepository.java`, `service/QueueEntryService.java` | **TESTED & VERIFIED** | Implemented per-doctor, per-day counter table with PostgreSQL atomic UPSERT (`ON CONFLICT DO UPDATE ... RETURNING`). Validated via `QueueTokenConcurrencyTest` with 20 parallel threads (100% collision-free tokens 1 to 20). |
| **Duplicate Active Check-In Prevention** | `service/QueueEntryService.java`, `exception/DuplicateCheckInException.java` | **TESTED & VERIFIED** | Rejects check-in if patient is currently `WAITING` or `IN_CONSULTATION` for the doctor on the same service date. Validated in `QueueEntryServiceTest`. Returns HTTP `409 Conflict`. |
| **Consultation Concurrency Discipline** | `service/QueueEntryService.java` | **TESTED & VERIFIED** | Prevents calling next patient if doctor already has an active consultation. Validated in `QueueEntryServiceTest`. Returns HTTP `400 Bad Request`. |
| **JWT Key Lifecycle & External Secret** | `config/JwtUtil.java`, `resources/application.properties` | **TESTED & VERIFIED** | Replaced ephemeral in-memory secret with externalized stable HMAC-SHA256 secret (`jwt.secret`). Verified via `JwtSecurityTest` across token generation, validation, and expiration. |
| **JWT Exception Handling in Filter** | `config/JwtAuthFilter.java` | **TESTED & VERIFIED** | Catches `JwtException` (expired, malformed, invalid signature). Clears security context and delegates to custom `AuthenticationEntryPoint` returning HTTP `401 Unauthorized` JSON. |
| **Privilege Escalation Prevention** | `service/UserService.java`, `controller/UserController.java`, `dto/RegisterDTO.java` | **TESTED & VERIFIED** | Server-side role assignment: anonymous users cannot self-assign `ADMIN` or `DOCTOR` (throws `AccessDeniedException` -> HTTP 403). Non-privileged default is `RECEPTIONIST`. Validated in `JwtSecurityTest`. |
| **Stateless Security & CORS Filter** | `config/SecurityConfig.java` | **TESTED & VERIFIED** | Configured `SessionCreationPolicy.STATELESS` and CORS allowed origins (`http://localhost:*`, `http://127.0.0.1:*`) for Flutter Web. Custom 401 and 403 JSON handlers wired. |
| **Patient Registration REST Semantics** | `controller/PatientController.java` | **TESTED & VERIFIED** | Changed `POST /api/patients` to return HTTP `201 Created` with representation, aligning with `DoctorController`. |
| **Database Schema Unique Constraints** | `entity/QueueEntry.java`, `entity/DailyQueueCounter.java` | **TESTED & VERIFIED** | Enforces `uk_doctor_service_date` on `daily_queue_counter` and `uk_doctor_date_token` on `queue_entry`. Verified in live PostgreSQL 18 schema creation. |
| **Automated Test Suite** | `src/test/java/**` | **TESTED & VERIFIED** | 27 automated tests passing (`mvn test`): 7 security tests, 12 state machine tests, 6 service tests, 1 concurrency test, 1 context integration test. |
| **Flutter Mobile/Web Client** | `E:\Java SpringBoot\patient-queue-flutter` | **PLANNED (M2)** | Directory confirmed clean. Scaffolding and client implementation reserved for Milestone M2. |
| **Operational BI & KPI Reporting** | `docs/05_DATABASE_AND_KPIS.md` | **PLANNED (M3)** | SQL CTE queries and synthetic fixtures designed; backend endpoints and CSV export scheduled for Milestone M3. |
| **Clinical Files & Voice Notes** | `docs/adr/ADR-006` | **DESIGN ONLY (M4/M5)** | Documented security and metadata design. Deferred to stretch phases. |
| **Containerization Lab** | `docs/09_DOCKER_LEARNING_LAB.md` | **DESIGN ONLY** | Educational tutorial for learner implementation; zero agent-created container files. |

---

## 3. Host Toolchain & Environmental Status

| Dependency | Inspected State | Current Status |
|---|---|---|
| **Java JDK** | JetBrains OpenJDK `21.0.8+1` | **VERIFIED RUNNING** (`JAVA_HOME` pointed to JBR) |
| **Maven** | Apache Maven `3.9.16` | **VERIFIED RUNNING** (`BUILD SUCCESS` on compile and test) |
| **PostgreSQL** | PostgreSQL `18.1` on `localhost:5432` | **VERIFIED RUNNING** (HikariCP connected, Hibernate DDL updated) |
| **Dart SDK** | Dart `3.12.2 (stable)` | **VERIFIED RUNNING** |
| **Flutter SDK** | `E:\dev\flutter\bin\flutter.bat` | **INSTALLED** (Requires network/filesystem approval for initial create in M2) |

---

## 4. Unresolved Issues & Technical Debt Backlog

1. **Flutter Disk Name Convention**: Disk folder is `patient-queue-flutter` (hyphen). Dart packages typically prefer `patient_queue_flutter` (underscore). Scaffolding in M2 will run from inside `patient-queue-flutter` with project name `patient_queue_flutter`.
2. **DTO Projection for Doctor Queue**: `QueueEntryController#getDoctorQueue` currently returns raw JPA entities (`List<QueueEntry>`). While functional, migrating to a dedicated `QueueEntryResponseDTO` in M2 will provide a cleaner, decoupled API boundary.
