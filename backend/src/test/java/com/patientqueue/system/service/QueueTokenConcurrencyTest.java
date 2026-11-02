package com.patientqueue.system.service;

import com.patientqueue.system.entity.Doctor;
import com.patientqueue.system.entity.Patient;
import com.patientqueue.system.entity.QueueEntry;

import com.patientqueue.system.repository.DoctorRepository;
import com.patientqueue.system.repository.PatientRepository;
import com.patientqueue.system.repository.QueueEntryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Queue Token Multi-Threaded Concurrency Tests")
class QueueTokenConcurrencyTest {

    @Test
    @DisplayName("Simultaneous concurrent check-ins allocate strictly unique sequential tokens")
    void concurrentCheckInsAllocateUniqueTokens() throws InterruptedException {
        // Setup in-memory repositories with thread-safe data structures
        ConcurrentStubPatientRepository patientRepo = new ConcurrentStubPatientRepository();
        ConcurrentStubDoctorRepository doctorRepo = new ConcurrentStubDoctorRepository();
        ConcurrentStubQueueEntryRepository queueRepo = new ConcurrentStubQueueEntryRepository();
        ConcurrentStubDailyQueueCounterRepository counterRepo = new ConcurrentStubDailyQueueCounterRepository();

        QueueEntryService service = new QueueEntryService(patientRepo, doctorRepo, queueRepo, counterRepo);

        Long doctorId = 1L;
        Doctor doctor = new Doctor(doctorId, "Dr. Gregory House", 52, "Diagnostics");
        doctorRepo.doctors.put(doctorId, doctor);

        int concurrentPatientCount = 20;
        for (long i = 1; i <= concurrentPatientCount; i++) {
            Patient p = new Patient("Patient " + i, 20 + (int) i, "+1000000" + i);
            p.setId(i);
            patientRepo.patients.put(i, p);
        }

        ExecutorService executor = Executors.newFixedThreadPool(concurrentPatientCount);
        CountDownLatch readyLatch = new CountDownLatch(concurrentPatientCount);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentPatientCount);

        List<QueueEntry> allocatedEntries = new CopyOnWriteArrayList<>();
        List<Throwable> exceptions = new CopyOnWriteArrayList<>();

        for (long i = 1; i <= concurrentPatientCount; i++) {
            final long patientId = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    // All threads wait on startGun so they execute at the exact same microsecond
                    startGun.await();
                    QueueEntry entry = service.checkIn(patientId, doctorId);
                    allocatedEntries.add(entry);
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startGun.countDown(); // Fire start gun!
        boolean finishedInTime = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finishedInTime, "All concurrent check-ins must complete within timeout");
        assertTrue(exceptions.isEmpty(), "Zero exceptions expected during valid concurrent check-ins: " + exceptions);
        assertEquals(concurrentPatientCount, allocatedEntries.size(), "All check-ins must return an entry");

        // Verify token uniqueness
        Set<Integer> uniqueTokens = new HashSet<>();
        List<Integer> duplicates = new ArrayList<>();

        for (QueueEntry entry : allocatedEntries) {
            int token = entry.getTokenNumber();
            if (!uniqueTokens.add(token)) {
                duplicates.add(token);
            }
        }

        assertTrue(duplicates.isEmpty(), "Duplicate tokens detected under concurrency: " + duplicates);
        assertEquals(concurrentPatientCount, uniqueTokens.size(), "Every token must be strictly unique");

        List<Integer> expectedTokens = IntStream.rangeClosed(1, concurrentPatientCount).boxed().toList();
        assertTrue(uniqueTokens.containsAll(expectedTokens), "Tokens must cover the complete 1 to 20 range");
    }

    // --- Thread-Safe Concurrent Stubs ---

    static class ConcurrentStubPatientRepository extends QueueEntryServiceTest.StubBaseRepository<Patient, Long> implements PatientRepository {
        Map<Long, Patient> patients = new ConcurrentHashMap<>();
        @Override public Optional<Patient> findById(Long id) { return Optional.ofNullable(patients.get(id)); }
    }

    static class ConcurrentStubDoctorRepository extends QueueEntryServiceTest.StubBaseRepository<Doctor, Long> implements DoctorRepository {
        Map<Long, Doctor> doctors = new ConcurrentHashMap<>();
        @Override public Optional<Doctor> findById(Long id) { return Optional.ofNullable(doctors.get(id)); }
    }

    static class ConcurrentStubDailyQueueCounterRepository implements QueueTokenAllocator {
        Map<String, java.util.concurrent.atomic.AtomicInteger> counters = new ConcurrentHashMap<>();

        @Override
        public int nextToken(Long doctorId, LocalDate serviceDate) {
            String key = doctorId + "_" + serviceDate;
            // Atomically compute and increment
            return counters.computeIfAbsent(key, k -> new java.util.concurrent.atomic.AtomicInteger(0))
                    .incrementAndGet();
        }
    }

    static class ConcurrentStubQueueEntryRepository extends QueueEntryServiceTest.StubBaseRepository<QueueEntry, Long> implements QueueEntryRepository {
        @Override public Optional<QueueEntry> findByIdForUpdate(Long id) { return findById(id); }
        Map<Long, QueueEntry> entries = new ConcurrentHashMap<>();
        java.util.concurrent.atomic.AtomicLong idSeq = new java.util.concurrent.atomic.AtomicLong(1);

        @Override
        public <S extends QueueEntry> S saveAndFlush(S entity) { return save(entity); }

        @Override
        public <S extends QueueEntry> S save(S entity) {
            if (entity.getId() == null) {
                entity.setId(idSeq.getAndIncrement());
            }
            entries.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public List<QueueEntry> findByPatientIdOrderByCheckInTimeDesc(Long patientId) {
            return entries.values().stream()
                    .filter(e -> e.getPatient().getId().equals(patientId))
                    .sorted(Comparator.comparing(QueueEntry::getCheckInTime).reversed())
                    .toList();
        }

        @Override
        public Optional<QueueEntry> findById(Long id) {
            return Optional.ofNullable(entries.get(id));
        }

        @Override
        public boolean existsByPatientIdAndDoctorIdAndServiceDateAndStatusIn(Long patientId, Long doctorId, LocalDate serviceDate, List<com.patientqueue.system.entity.QueueStatus> statuses) {
            return entries.values().stream().anyMatch(e ->
                    e.getPatient().getId().equals(patientId) &&
                            e.getDoctor().getId().equals(doctorId) &&
                            e.getServiceDate().equals(serviceDate) &&
                            statuses.contains(e.getStatus())
            );
        }

        @Override
        public Optional<QueueEntry> findByDoctorIdAndServiceDateAndStatus(Long doctorId, LocalDate serviceDate, com.patientqueue.system.entity.QueueStatus status) {
            return entries.values().stream().filter(e ->
                    e.getDoctor().getId().equals(doctorId) &&
                            e.getServiceDate().equals(serviceDate) &&
                            e.getStatus() == status
            ).findFirst();
        }

        @Override
        public List<QueueEntry> findByDoctorIdAndServiceDateAndStatusInOrderByTokenNumberAsc(Long doctorId, LocalDate serviceDate, List<com.patientqueue.system.entity.QueueStatus> statuses) {
            return entries.values().stream()
                    .filter(e -> e.getDoctor().getId().equals(doctorId) &&
                            e.getServiceDate().equals(serviceDate) &&
                            statuses.contains(e.getStatus()))
                    .sorted(Comparator.comparingInt(QueueEntry::getTokenNumber))
                    .toList();
        }

        @Override public int countByDoctorAndCheckInTimeBetween(Doctor doctor, java.time.LocalDateTime start, java.time.LocalDateTime end) { return 0; }
        @Override public List<QueueEntry> findByDoctorAndStatusInAndCheckInTimeBetween(Doctor doctor, List<com.patientqueue.system.entity.QueueStatus> statuses, java.time.LocalDateTime start, java.time.LocalDateTime end) { return List.of(); }
    }
}
