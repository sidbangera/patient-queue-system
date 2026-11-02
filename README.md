# PQueue — Outpatient Queue Management & Operational Analytics

**A full-stack outpatient queue management system built with Spring Boot, Flutter, and PostgreSQL.**

PQueue helps healthcare staff manage outpatient check-ins, doctor queues, consultation progress, and daily operations through a unified application. It also provides operational analytics to help understand patient throughput, doctor workload, waiting times, and consultation completion trends.

The project combines backend development, database design, REST APIs, authentication, frontend development, automated testing, and reporting-focused data analysis.

> **Project status:** Functional academic/portfolio project with tested queue management and operational analytics. Designed for demonstration using synthetic patient data, not for production clinical deployment.

## 1. Key Features

### Patient and Doctor Management
- Register and manage patient information.
- Maintain doctor profiles and department information.
- View doctor-specific patient queues.
- Access patient visit and queue history.

### Outpatient Queue Management
- Register patient check-ins for a selected doctor.
- Allocate daily queue tokens using PostgreSQL-backed counters.
- Track patients through the supported consultation states.
- Enforce valid queue-state transitions on the backend.
- Handle duplicate check-ins and invalid operations.
- Support concurrency-safe token allocation.

### Authentication and Access Control
- JWT-based authentication.
- Role-based authorization for protected API operations.
- Backend request validation and structured error handling.
- Configurable authentication secrets through environment variables.

### Operational Insights Dashboard
- Daily and historical outpatient activity summaries.
- Patient check-in and consultation completion counts.
- Consultation completion rates.
- Average waiting-time metrics.
- Doctor workload analysis.
- Time-period comparisons and trend analysis.
- Data-quality indicators for reporting.
- CSV-based reporting and export workflows.

Analytics are calculated using backend business logic and PostgreSQL reporting views, with defined rules for aggregation and data quality.

## 2. Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot, Spring Web |
| Security | Spring Security, JWT |
| Persistence | Spring Data JPA / Hibernate |
| Database | PostgreSQL |
| Frontend | Flutter, Dart |
| API communication | REST / JSON |
| Database reporting | SQL views and aggregate queries |
| Build tools | Maven Wrapper, Flutter SDK |
| Testing | JUnit, Mockito, Spring Boot Test, Flutter Test |
| Development environment | Windows, PowerShell, VS Code |

## 3. System Architecture

PQueue follows a layered backend architecture with a Flutter client communicating through authenticated REST APIs.

```text
                   ┌───────────────────────────┐
                   │      Flutter Client       │
                   │                           │
                   │  Patients / Doctors       │
                   │  Queue Management         │
                   │  Operational Insights     │
                   └─────────────┬─────────────┘
                                 │
                            REST / JSON
                                 │
                   ┌─────────────▼─────────────┐
                   │      Spring Boot API      │
                   │                           │
                   │  Controllers              │
                   │  JWT / Security           │
                   │  Business Services        │
                   │  Validation               │
                   │  Analytics Services       │
                   └─────────────┬─────────────┘
                                 │
                         JPA / JDBC / SQL
                                 │
                   ┌─────────────▼─────────────┐
                   │        PostgreSQL         │
                   │                           │
                   │  Patients / Doctors       │
                   │  Queue Entries            │
                   │  Daily Token Counters     │
                   │  Reporting Views          │
                   └───────────────────────────┘
```

The backend is structured as a modular layered monolith rather than a microservices architecture. This approach keeps business rules, transactions, and queue consistency within a single deployable application.

For additional design details, see [Architecture and Design](backend/docs/PQUEUE_ARCHITECTURE_AND_DESIGN.md) and [Low-Level Design](backend/docs/LOW_LEVEL_DESIGN_V2.md).

## 4. Repository Structure

```text
PQueue/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   └── test/
│   ├── database/
│   ├── docs/
│   ├── scripts/
│   ├── pom.xml
│   ├── mvnw
│   └── mvnw.cmd
│
├── frontend/
│   ├── lib/
│   │   ├── main.dart
│   │   └── src/
│   ├── test/
│   ├── android/
│   ├── web/
│   ├── scripts/
│   ├── pubspec.yaml
│   └── pubspec.lock
│
├── .gitignore
└── README.md
```

The `backend/database/` directory contains SQL scripts for fresh installation, migration checks, reporting views, sample data, and validation. The `backend/docs/` directory contains architecture decisions, API documentation, testing evidence, and implementation notes.

## 5. Getting Started

### Prerequisites

Install the following:

- Java Development Kit 21
- PostgreSQL
- Flutter SDK
- Git
- A suitable IDE such as VS Code or IntelliJ IDEA

The commands below use Windows PowerShell.

### 5.1 Clone the repository

```powershell
git clone https://github.com/sidbangera/patient-queue-system.git
cd patient-queue-system
```

### 5.2 Configure PostgreSQL

Create a local PostgreSQL database named:

```text
patient_queue
```

For a new, empty database, review and apply the schema script:

```text
backend/database/00_fresh_install.sql
```

For an existing database, use the preflight and migration scripts instead:

```text
backend/database/01_existing_database_preflight.sql
backend/database/02_existing_database_migration.sql
```

Apply the reporting views when the required tables are available:

```text
backend/database/03_reporting_views.sql
```

The repository also includes:

- `04_demo_seed_FRESH_DB_ONLY.sql` — synthetic demonstration data for a fresh database only.
- `06_reporting_validation_READ_ONLY.sql` — read-only reporting validation queries.

**Important:** Do not run the fresh-install or demonstration-seed scripts against an existing database containing records you need to preserve. Review each SQL script before applying it.

For detailed instructions, consult [Windows Setup Guide](backend/docs/00_START_HERE_WINDOWS.md).

### 5.3 Configure the backend

Navigate to the backend:

```powershell
cd backend
```

Copy the example configuration:

```powershell
Copy-Item src/main/resources/application.properties.example `
          src/main/resources/application.properties
```

The configuration uses environment variables for database credentials, JWT signing, and application settings.

Example for a local PowerShell session:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/patient_queue'
$env:DB_USERNAME = 'postgres'
$env:DB_PASSWORD = '<your-local-database-password>'
$env:JWT_SECRET = '<your-secure-jwt-signing-secret>'
$env:CLINIC_ZONE = 'Asia/Kolkata'
```

Replace the placeholders with your own values. Use a cryptographically random, sufficiently long JWT signing secret.

**Never commit real database passwords, JWT secrets, or production configuration files.**

The project's Hibernate configuration uses schema validation rather than automatic schema creation or updates. Apply the required SQL scripts before starting the application.

### 5.4 Start Spring Boot

From `backend/`:

```powershell
.\mvnw.cmd spring-boot:run
```

The default backend API address is:

```text
http://localhost:8080
```

### 5.5 Start Flutter

Open a second PowerShell terminal:

```powershell
cd frontend
flutter pub get
flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:8080
```

Flutter runs the web interface in Chrome and communicates with the Spring Boot API.

An Android device or emulator may also be used. When running on Android, the backend address must be reachable from the device; `localhost` on the device does not refer to the development computer.

## 6. Queue Workflow

PQueue models the progression of a patient's outpatient visit through controlled states.

```text
Patient Registration
        |
        v
Doctor Selection
        |
        v
Patient Check-in
        |
        v
Daily Queue Token Allocation
        |
        v
     WAITING
        |
        v
   IN_CONSULTATION
        |
        v
    COMPLETED
```

The application also supports applicable cancellation and no-show handling according to its queue-state rules.

State transitions are validated on the backend. This prevents clients from arbitrarily changing the consultation workflow.

Daily queue tokens are allocated through a PostgreSQL-backed mechanism designed to avoid duplicate allocations under concurrent check-ins.

## 7. Operational Analytics

The Insights dashboard helps staff understand outpatient activity across selected reporting periods.

Key measurements include:

| Metric | Purpose |
|---|---|
| Check-ins | Number of recorded patient visits |
| Completed consultations | Number of consultations marked completed |
| Completion rate | Completed consultations relative to eligible check-ins |
| Average waiting time | Time spent waiting before consultation, subject to valid timestamps |
| Doctor workload | Distribution of outpatient activity by doctor |
| Period comparison | Changes between selected reporting periods |
| Data-quality indicators | Records with missing or inconsistent reporting information |

The analytics implementation separates business rules from presentation logic and includes SQL-based validation to support consistent reporting.

For definitions and API behavior, see [KPI and API Contract](backend/docs/PQUEUE_KPI_AND_API_CONTRACT.md).

## 8. Testing and Validation

The project includes backend unit tests, security tests, API/controller tests, queue-state tests, concurrency-related tests, database integration tests, and Flutter widget tests.

### Backend tests

From `backend/`:

```powershell
.\mvnw.cmd clean test
```

The latest verified local run completed with:

- **54 tests discovered**
- **50 passed**
- **0 failures**
- **4 skipped**

The skipped tests are opt-in PostgreSQL integration tests that require a separately configured test database.

### PostgreSQL integration tests

A dedicated synthetic test database is used for database-level integration testing.

The four opt-in PostgreSQL integration tests were separately executed and passed in the local development environment.

See the testing documentation for the required environment variables and database setup. Never run integration tests against a production database.

### Flutter tests

From `frontend/`:

```powershell
flutter analyze
flutter test
```

Latest verified local results:

- Flutter static analysis: **No issues found**
- Flutter tests: **8 passed**

### End-to-end validation

Local verification also covered:

- Patient registration and check-in.
- Consultation-state updates.
- Completed visit history.
- Doctor workload filtering.
- Insights dashboard updates after a completed patient visit.
- Period comparison and reporting calculations.
- SQL reporting-view reconciliation.

These results represent the tested local development environment, not a claim of production readiness.

See [QA Report](backend/docs/QA_REPORT.md) for details.

## 9. Engineering Decisions

Several implementation choices were made to support maintainability, correctness, and reporting reliability.

**Backend-controlled state transitions:** Consultation status changes are validated by business services rather than trusted directly from the frontend.

**Database-backed token allocation:** PostgreSQL is used to coordinate daily queue-token allocation and reduce concurrency-related inconsistencies.

**Explicit schema management:** Database changes use reviewed SQL scripts instead of relying on automatic Hibernate schema modifications.

**Role-based access control:** Protected API operations require authentication and appropriate permissions.

**Reporting-focused design:** Operational KPIs use documented calculation rules and reporting views, with attention to missing or inconsistent timestamps.

**Layered architecture:** Controllers, services, repositories, and database entities have distinct responsibilities.

## 10. Current Limitations and Future Scope

PQueue is a functional demonstration system, but it is not a certified medical product or production-ready hospital platform.

Current limitations and planned improvements include:

- Patient document and X-ray attachment management.
- Voice-note recording and storage for consultations.
- Additional frontend usability and accessibility improvements.
- Expanded automated end-to-end testing.
- Deployment hardening, observability, and infrastructure automation.
- Further analytics and business intelligence integrations.
- Production-grade privacy, retention, access auditing, and clinical data governance.

Patient file attachments and voice notes are **design-stage features** and are not part of the currently deployed implementation.

See [Future Patient Files and Voice Design](backend/docs/FUTURE_PATIENT_FILES_AND_VOICE_DESIGN.md).

## 11. Documentation

Additional documentation is available in the repository:

| Document | Description |
|---|---|
| [Windows Setup Guide](backend/docs/00_START_HERE_WINDOWS.md) | Local installation and execution |
| [Architecture and Design](backend/docs/PQUEUE_ARCHITECTURE_AND_DESIGN.md) | System components and architectural decisions |
| [High-Level Design](backend/docs/HIGH_LEVEL_DESIGN_V2.md) | System-level design |
| [Low-Level Design](backend/docs/LOW_LEVEL_DESIGN_V2.md) | Internal backend implementation |
| [API Contract](backend/docs/API_CONTRACT_V2.md) | REST API behavior |
| [KPI and API Contract](backend/docs/PQUEUE_KPI_AND_API_CONTRACT.md) | Reporting metrics and analytics APIs |
| [Product Requirements and UAT](backend/docs/PRODUCT_REQUIREMENTS_AND_UAT.md) | Functional requirements and acceptance criteria |
| [QA Report](backend/docs/QA_REPORT.md) | Testing and validation |
| [Technical References](backend/docs/TECHNICAL_REFERENCES.md) | Supporting technical references |

Earlier design and implementation notes are retained under `backend/docs/archive_m0/`.

## 12. Project Background

PQueue was developed as a hands-on software engineering project to explore the intersection of backend systems, relational databases, user-facing applications, and operational analytics.

The project demonstrates practical experience with:

- Full-stack application development.
- REST API design and integration.
- Relational data modelling.
- SQL reporting and validation.
- Authentication and authorization.
- Business rules and workflow modelling.
- Automated testing and debugging.
- Functional requirements, technical documentation, and user acceptance testing.

The emphasis is on building a system whose behavior can be explained, tested, and validated—not just demonstrated through a user interface.

---

**PQueue — Spring Boot + Flutter + PostgreSQL**

*Built for learning, engineering practice, and portfolio demonstration using synthetic healthcare workflow data.*