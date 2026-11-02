# ADR-002: Modular Layered Monolith Architecture vs. Microservices

**Status**: Accepted
**Date**: October 24, 2026
**Context**:
Portfolio projects often prematurely introduce microservices (separate services for Auth, Queue, Patients, Doctors, Notifications) alongside Kafka, Redis, and Eureka to appeal to recruiters, which causes severe deployment fragility, distributed transaction headaches, and operational overhead.

**Decision**:
Build and maintain the backend as a single **Modular Layered Monolith** in Spring Boot 4.x / Java 21, with strict internal package boundaries (`controller`, `service`, `repository`, `dto`, `entity`, `config`).

**Alternatives Considered**:
1. *Microservices Split*: Separate Spring Boot services communicating over REST/gRPC or message brokers. Rejected due to dual-write consistency issues, network latency, complex Docker compose setups, and zero operational necessity for an outpatient clinic.

**Consequences & Tradeoffs**:
- *Positive*: ACID transactions across queue and patient state in a single PostgreSQL database; simple deployment; immediate debugging; zero network hop latency between domain services.
- *Negative*: Codebase must enforce disciplined internal module boundaries to prevent tight coupling.
