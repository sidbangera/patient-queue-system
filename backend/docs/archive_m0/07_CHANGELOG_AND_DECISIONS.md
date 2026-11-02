# Milestone Changelog, Architecture Decisions & Audit Log

**Project Name**: Outpatient Patient Queue Management System 2.0
**Repository**: `E:\Java SpringBoot\patient-queue-system`

---

## Milestone M0: Baseline Audit & Preservation Package (October 24, 2026)

### 1. Context & Motivation
The project owner initiated a comprehensive upgrade of an existing outpatient queue system. Prior to making source code changes or initializing the Flutter frontend, an exhaustive non-destructive audit was executed to inventory local uncommitted Git changes, inspect toolchains, verify compilation, detect security flaws, and establish a baseline documentation package.

### 2. Workspace Safety & Scope Confirmation
- **Authorized Scope**:
  1. `E:\Java SpringBoot\patient-queue-system` (Existing backend)
  2. `E:\Java SpringBoot\patient-queue-flutter` (New empty frontend directory)
- **Out of Scope (Isolated)**:
  - `E:\Java SpringBoot\demo` (Strictly untouched)
  - `E:\Java SpringBoot\dynamodemo` (Strictly untouched)
  - `E:\Java SpringBoot\store` (Strictly untouched)
- **Action Taken**: Explicitly confirmed workspace boundary and refused any workspace-wide actions that touch unrelated folders.

### 3. Baseline Audit Findings
1. **Uncommitted JWT Implementation**:
   - `pom.xml` modified with Spring Security and JJWT 0.12.6.
   - `GlobalExceptionHandler.java` modified to handle `InvalidCredentialsException`.
   - 14 untracked files in `config/`, `controller/`, `dto/`, `entity/`, `repository/`, and `service/` implementing basic registration, login, and token generation.
2. **Defects & Vulnerabilities Detected**:
   - **Ephemeral JWT Secret**: `JwtUtil.java` instantiates a new random HMAC key in memory on every startup (`Jwts.SIG.HS256.key().build()`). This breaks token persistence across server restarts.
   - **Unprotected Admin Registration**: `UserController.java#registerUser` does not restrict the `role` field from `RegisterDTO`. Any client can register as an `ADMIN`.
   - **Check-in Token Race Condition**: `QueueEntryService.java#checkIn` computes token numbers via `countByDoctorAndCheckInTimeBetween() + 1` without `@Transactional` or locking, leading to duplicate tokens under concurrent check-ins.
   - **Missing CORS**: `SecurityConfig.java` has no CORS configuration, which will block Flutter Web clients.
   - **Inconsistent REST Statuses**: `PatientController.java#addPatient` returns HTTP `200 OK` instead of standard `201 CREATED`.
   - **Zero Test Coverage**: Only an empty Spring Boot `contextLoads()` test exists.
   - **Offline Maven Surefire Dependency**: Running `mvn test` failed because Surefire plugin 3.5.6 is missing from local cache `~/.m2` and attempted internet download inside the sandbox.

### 4. Files Audited (Zero Application Source Modified)
- `pom.xml`
- `src/main/resources/application.properties` & `.example`
- `src/main/java/com/patientqueue/system/PatientQueueSystemApplication.java`
- `src/main/java/com/patientqueue/system/HelloController.java`
- `src/main/java/com/patientqueue/system/config/AppConfig.java`
- `src/main/java/com/patientqueue/system/config/JwtAuthFilter.java`
- `src/main/java/com/patientqueue/system/config/JwtUtil.java`
- `src/main/java/com/patientqueue/system/config/SecurityConfig.java`
- `src/main/java/com/patientqueue/system/controller/DoctorController.java`
- `src/main/java/com/patientqueue/system/controller/PatientController.java`
- `src/main/java/com/patientqueue/system/controller/QueueEntryController.java`
- `src/main/java/com/patientqueue/system/controller/UserController.java`
- `src/main/java/com/patientqueue/system/dto/*`
- `src/main/java/com/patientqueue/system/entity/*`
- `src/main/java/com/patientqueue/system/exception/*`
- `src/main/java/com/patientqueue/system/repository/*`
- `src/main/java/com/patientqueue/system/service/*`
- `src/test/java/com/patientqueue/system/PatientQueueEntrySystemApplicationTests.java`

### 5. Documentation Deliverables Created
- `docs/00_IMPLEMENTATION_INVENTORY.md`: Component status matrix and risk inventory.
- `docs/01_BUSINESS_REQUIREMENTS.md`: BRD, FRD, personas, user stories, acceptance criteria, traceability matrix.
- `docs/02_HIGH_LEVEL_DESIGN.md`: C4 Context & Component diagrams, security trust boundaries, deployment topology.
- `docs/03_LOW_LEVEL_DESIGN.md`: Concrete entity schemas, DTOs, token allocation algorithm, Flutter client tree.
- `docs/04_API_CONTRACT.md`: Full REST endpoint specification with concrete request/response JSON payloads.
- `docs/05_DATABASE_AND_KPIS.md`: Data dictionary, KPI mathematics, production PostgreSQL queries, synthetic test fixtures.
- `docs/06_TEST_PLAN_AND_EVIDENCE.md`: Baseline test execution results and planned M1 test suites.
- `docs/07_CHANGELOG_AND_DECISIONS.md`: This document.
- `docs/08_INTERVIEW_STUDY_GUIDE.md`: Deep interview preparation guide with 5 realistic questions and SQL practice.
- `docs/09_DOCKER_LEARNING_LAB.md`: Learner-led step-by-step containerization tutorial (no agent-created dockerfiles).
- `docs/10_BACKLOG_AND_LIMITATIONS.md`: Known gaps, deferred features, and next-step execution plan.
- `docs/adr/ADR-001` through `ADR-006`: Architecture Decision Records.

### 6. Source Code Changes in M0
**None**. M0 was executed strictly under read-only guidelines. No application source code was modified, no database migrations were run, and the Flutter project was not initialized.
