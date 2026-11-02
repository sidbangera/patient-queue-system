# ADR-004: Concurrency-Safe Daily Token Allocation Strategy

**Status**: Accepted (Implemented and Verified in Milestone M1)
**Date**: October 24, 2026

---

## 1. Context & Business Problem
In the baseline system, queue tokens were calculated via:
```java
int tokenNumber = queueEntryRepository.countByDoctorAndCheckInTimeBetween(doctor, startOfDay, endOfDay) + 1;
```
This naive read-compute-write pattern suffered from severe operational vulnerabilities:
1. **Race Conditions**: Two reception desks checking in patients for the same doctor simultaneously read the same count (e.g. 5) and both issued Token #6.
2. **Token Collisions on State Changes**: If entries were deleted or cancelled, `count + 1` could re-issue previously assigned token numbers.
3. **No Database Uniqueness Guarantee**: The storage layer lacked a composite uniqueness constraint on `(doctor_id, service_date, token_number)`.

---

## 2. Design Alternatives Considered

| Alternative | Mechanics | Pros | Cons / Rejection Rationale |
|---|---|---|---|
| **Alt 1: PostgreSQL Global Sequence** | `CREATE SEQUENCE queue_token_seq` | Atomic, monotonic, high throughput. | Cannot easily reset to 1 at midnight per individual doctor without complex dynamic DDL daily. |
| **Alt 2: `SELECT MAX(token_number) ... FOR UPDATE`** | Lock rows via aggregate query | Uses existing `queue_entry` table. | **Rejected**: In PostgreSQL and SQL standards, `SELECT MAX(...) ... FOR UPDATE` on an empty set or aggregate function cannot place a row lock because no rows exist yet. Susceptible to initialization race conditions when doctor queue starts at 0. |
| **Alt 3: In-Memory `ConcurrentHashMap<String, AtomicInteger>`** | Atomic counters in JVM memory | Sub-millisecond performance. | Non-persistent: server restart resets counters, desynchronizing with database. Fails if multiple backend instances run. |
| **Alt 4: Dedicated Per-Doctor, Per-Day Counter Table with Atomic UPSERT (Selected)** | `daily_queue_counter` table with `ON CONFLICT DO UPDATE ... RETURNING` | Persistent, transactional, handles initialization races natively, resets daily per doctor. | Requires 1 additional small counter table. |

---

## 3. Decision & Technical Implementation

We implemented **Alternative 4** using a dedicated entity `DailyQueueCounter` and table `daily_queue_counter`.

### 3.1 Counter Schema
```sql
CREATE TABLE daily_queue_counter (
    id BIGSERIAL PRIMARY KEY,
    doctor_id BIGINT NOT NULL,
    service_date DATE NOT NULL,
    last_token_number INT NOT NULL,
    CONSTRAINT uk_doctor_service_date UNIQUE (doctor_id, service_date)
);
```

### 3.2 Atomic Allocation Statement
```sql
INSERT INTO daily_queue_counter (doctor_id, service_date, last_token_number)
VALUES (:doctorId, :serviceDate, 1)
ON CONFLICT (doctor_id, service_date)
DO UPDATE SET last_token_number = daily_queue_counter.last_token_number + 1
RETURNING last_token_number;
```

### 3.3 How Key Concurrency Challenges Are Handled
1. **Initialization Race**:
   - If two receptionists check in patients for Dr. House at 08:00 AM on a new day, both execute the `INSERT ... ON CONFLICT`.
   - PostgreSQL detects the conflict on `uk_doctor_service_date`. Transaction 1 completes the insert and returns token `1`. Transaction 2 executes `DO UPDATE`, increments to `2`, and returns token `2`. Zero collisions, zero exceptions thrown to the application.
2. **Day Boundaries**:
   - `service_date` is a `LocalDate`. When midnight passes, the query automatically inserts a new row for `(doctor_id, next_date)` starting at 1.
3. **Transaction Rollback & Isolation**:
   - The check-in method in `QueueEntryService` is annotated with `@Transactional`. If patient persistence fails, the counter modification is rolled back.
4. **Duplicate Active Check-Ins**:
   - Before allocating a token, the service checks:
     ```java
     boolean alreadyActive = queueEntryRepository.existsByPatientIdAndDoctorIdAndServiceDateAndStatusIn(
             patientId, doctorId, serviceDate, List.of(QueueStatus.WAITING, QueueStatus.IN_CONSULTATION));
     if (alreadyActive) {
         throw new DuplicateCheckInException(...);
     }
     ```
5. **Storage Layer Safety Net**:
   - `queue_entry` enforces a composite unique constraint:
     `CONSTRAINT uk_doctor_date_token UNIQUE (doctor_id, service_date, token_number)`

---

## 4. Verification Evidence
- **Automated Test**: `QueueTokenConcurrencyTest.java`
- **Methodology**: 20 concurrent threads fired simultaneously via `CountDownLatch` for 20 distinct patients with the same doctor.
- **Result**: All 20 threads completed successfully with strictly unique, contiguous tokens from 1 to 20 (`Tests run: 1, Failures: 0, Errors: 0`).
