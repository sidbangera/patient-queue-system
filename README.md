# Patient Queue Management System

A Spring Boot + Flutter backend/mobile system for managing outpatient (OP) queues,
patient records, and doctor scheduling — built as a placement-season portfolio project.

## Tech Stack

- **Backend**: Spring Boot 4.1.0, Java 21
- **Database**: PostgreSQL
- **ORM**: Spring Data JPA / Hibernate
- **Validation**: Jakarta Bean Validation (`spring-boot-starter-validation`)
- **Boilerplate reduction**: Lombok (used selectively — some classes use hand-written
  constructors/getters/setters deliberately, for learning purposes)
- **Frontend (planned)**: Flutter (mobile, doctors/nurses) + Flutter Web or similar (staff desktop)
- **Build tool**: Maven

## Project Status

Actively in development — see `ARCHITECTURE.md` for design details and progress log.

## Features — Current (MVP, in progress)

- [x] Patient CRUD (register, view, update, delete)
- [x] Doctor CRUD (register, view, update, delete)
- [ ] Queue management with state-based transitions (in progress)
- [ ] Check-in / call-next / mark no-show endpoints
- [ ] Elapsed time tracking for waiting patients

## Features — Planned (Phase 2+, not yet built)

- Voice notes (nurse-recorded, transcribed) attached to patient visits
- X-ray / report file uploads
- Spring Security (JWT-based auth, role-based access: doctor / nurse / receptionist)
- Referral to another department (generates a queue entry in that department)
- Referral to another hospital (record update, not actual data transfer)
- "Stop new patient influx" toggle for a doctor
- Patient file display on a TV/monitor, switchable views
- Emergency alert to doctor (push notification + SMS fallback)
- AI-assisted feature: voice note → structured clinical note (LLM API call)

## Explicitly Out of Scope (documented, not built)

- Real-time vitals from IoT/medical devices — deferred indefinitely, not part of this project's scope
- Cross-hospital data transfer (actual file/record exchange between separate hospital systems) —
  only a status/note update is implemented, not real inter-hospital integration
- Surgery scheduling/calendar — deferred to a possible future phase
- Microservices split — deliberately built as a monolith; see `ARCHITECTURE.md` for reasoning

## How to Run

1. Install PostgreSQL locally, create a database (see `application.properties` for expected name)
2. Copy `application.properties.example` to `src/main/resources/application.properties` and fill in your own PostgreSQL credentials
3. `./mvnw spring-boot:run` (or run `PatientQueueSystemApplication` from your IDE)
4. API available at `http://localhost:8080`

## Package Structure

```
com.patientqueue.system
├── controller   → REST endpoints (HTTP layer only)
├── service      → business logic
├── repository   → Spring Data JPA interfaces
├── dto          → request/response data shapes
└── entity       → JPA entities (database tables)
```

See `ARCHITECTURE.md` for why this layered structure was chosen.
