package com.patientqueue.system.service;

import com.patientqueue.system.entity.Doctor;
import com.patientqueue.system.entity.Patient;
import com.patientqueue.system.entity.QueueEntry;
import com.patientqueue.system.entity.QueueStatus;
import com.patientqueue.system.exception.DuplicateCheckInException;

import com.patientqueue.system.repository.DoctorRepository;
import com.patientqueue.system.repository.PatientRepository;
import com.patientqueue.system.repository.QueueEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("QueueEntryService Unit Tests")
class QueueEntryServiceTest {

    private StubPatientRepository patientRepo;
    private StubDoctorRepository doctorRepo;
    private StubQueueEntryRepository queueRepo;
    private StubDailyQueueCounterRepository counterRepo;
    private QueueEntryService service;

    private Doctor testDoctor;
    private Patient testPatient1;
    private Patient testPatient2;

    @BeforeEach
    void setUp() {
        patientRepo = new StubPatientRepository();
        doctorRepo = new StubDoctorRepository();
        queueRepo = new StubQueueEntryRepository();
        counterRepo = new StubDailyQueueCounterRepository();

        service = new QueueEntryService(patientRepo, doctorRepo, queueRepo, counterRepo,
                java.time.Clock.fixed(java.time.Instant.parse("2026-10-31T20:00:00Z"), java.time.ZoneId.of("Asia/Kolkata")));

        testDoctor = new Doctor(1L, "Dr. Gregory House", 52, "Diagnostics");
        doctorRepo.doctors.put(1L, testDoctor);

        testPatient1 = new Patient("Patient One", 30, "+1234567890");
        testPatient1.setId(101L);
        patientRepo.patients.put(101L, testPatient1);

        testPatient2 = new Patient("Patient Two", 45, "+0987654321");
        testPatient2.setId(102L);
        patientRepo.patients.put(102L, testPatient2);
    }

    @Test
    @DisplayName("CheckIn assigns sequential token number from counter")
    void checkInAssignsSequentialToken() {
        QueueEntry entry1 = service.checkIn(101L, 1L);
        assertNotNull(entry1);
        assertEquals(1, entry1.getTokenNumber());
        assertEquals(QueueStatus.WAITING, entry1.getStatus());
        assertEquals(LocalDate.of(2026, 11, 1), entry1.getServiceDate());

        QueueEntry entry2 = service.checkIn(102L, 1L);
        assertNotNull(entry2);
        assertEquals(2, entry2.getTokenNumber());
        assertEquals(QueueStatus.WAITING, entry2.getStatus());
    }

    @Test
    @DisplayName("Duplicate active check-in for the same patient throws DuplicateCheckInException")
    void duplicateActiveCheckInThrowsException() {
        service.checkIn(101L, 1L);

        // Attempt second check-in while first is still WAITING
        DuplicateCheckInException ex = assertThrows(DuplicateCheckInException.class, () ->
                service.checkIn(101L, 1L)
        );
        assertTrue(ex.getMessage().contains("already has an active queue entry"));
    }

    @Test
    @DisplayName("ChangeState transitions WAITING to IN_CONSULTATION and sets start time")
    void changeStateWaitingToInConsultation() {
        QueueEntry entry = service.checkIn(101L, 1L);
        assertNull(entry.getConsultationStartTime());

        QueueEntry updated = service.changeState(entry.getId(), QueueStatus.IN_CONSULTATION);
        assertEquals(QueueStatus.IN_CONSULTATION, updated.getStatus());
        assertNotNull(updated.getConsultationStartTime());
    }

    @Test
    @DisplayName("ChangeState transitions IN_CONSULTATION to COMPLETED and sets end time")
    void changeStateInConsultationToCompleted() {
        QueueEntry entry = service.checkIn(101L, 1L);
        service.changeState(entry.getId(), QueueStatus.IN_CONSULTATION);

        QueueEntry completed = service.changeState(entry.getId(), QueueStatus.COMPLETED);
        assertEquals(QueueStatus.COMPLETED, completed.getStatus());
        assertNotNull(completed.getConsultationEndTime());
    }

    @Test
    @DisplayName("Invalid transition throws IllegalStateException")
    void invalidTransitionThrowsException() {
        QueueEntry entry = service.checkIn(101L, 1L);

        // WAITING directly to COMPLETED is forbidden
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.changeState(entry.getId(), QueueStatus.COMPLETED)
        );
        assertTrue(ex.getMessage().contains("Cannot transition from WAITING to COMPLETED"));
    }

    @Test
    @DisplayName("Cannot call next when another patient is already IN_CONSULTATION")
    void cannotCallNextWhenDoctorHasActiveConsultation() {
        QueueEntry entry1 = service.checkIn(101L, 1L);
        QueueEntry entry2 = service.checkIn(102L, 1L);

        // Doctor calls patient 1
        service.changeState(entry1.getId(), QueueStatus.IN_CONSULTATION);

        // Doctor tries to call patient 2 while patient 1 is still in consultation
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.changeState(entry2.getId(), QueueStatus.IN_CONSULTATION)
        );
        assertTrue(ex.getMessage().contains("already has patient (Token #1) in consultation"));
    }

    @Test void noShowIsTerminalAndDoesNotInventConsultationTimes() {
        QueueEntry entry=service.checkIn(101L,1L);
        QueueEntry noShow=service.changeState(entry.getId(),QueueStatus.NO_SHOW);
        assertNull(noShow.getConsultationStartTime()); assertNull(noShow.getConsultationEndTime());
        assertThrows(IllegalStateException.class,()->service.changeState(entry.getId(),QueueStatus.IN_CONSULTATION));
    }
    @Test void aNewVisitAfterCompletionGetsANewToken() {
        QueueEntry entry=service.checkIn(101L,1L);
        service.changeState(entry.getId(),QueueStatus.IN_CONSULTATION);
        service.changeState(entry.getId(),QueueStatus.COMPLETED);
        assertEquals(2,service.checkIn(101L,1L).getTokenNumber());
    }
    @Test void clinicClockControlsMidnightServiceDate() {
        QueueEntry entry=service.checkIn(101L,1L);
        assertEquals(LocalDate.of(2026,11,1),entry.getServiceDate());
        assertEquals(1,entry.getCheckInTime().getHour());
    }

    // --- Lightweight in-memory stubs ---

    static class StubPatientRepository extends StubBaseRepository<Patient, Long> implements PatientRepository {
        Map<Long, Patient> patients = new HashMap<>();
        @Override public Optional<Patient> findById(Long id) { return Optional.ofNullable(patients.get(id)); }
    }

    static class StubDoctorRepository extends StubBaseRepository<Doctor, Long> implements DoctorRepository {
        Map<Long, Doctor> doctors = new HashMap<>();
        @Override public Optional<Doctor> findById(Long id) { return Optional.ofNullable(doctors.get(id)); }
    }

    static class StubDailyQueueCounterRepository implements QueueTokenAllocator {
        Map<String, Integer> counters = new HashMap<>();

        @Override
        public synchronized int nextToken(Long doctorId, LocalDate serviceDate) {
            String key = doctorId + "_" + serviceDate;
            int next = counters.getOrDefault(key, 0) + 1;
            counters.put(key, next);
            return next;
        }
    }

    static class StubQueueEntryRepository extends StubBaseRepository<QueueEntry, Long> implements QueueEntryRepository {
        Map<Long, QueueEntry> entries = new HashMap<>();
        long idSeq = 1;

        @Override
        public <S extends QueueEntry> S saveAndFlush(S entity) { return save(entity); }

        @Override
        public <S extends QueueEntry> S save(S entity) {
            if (entity.getId() == null) {
                entity.setId(idSeq++);
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
        public Optional<QueueEntry> findByIdForUpdate(Long id) { return findById(id); }

        @Override
        public Optional<QueueEntry> findById(Long id) {
            return Optional.ofNullable(entries.get(id));
        }

        @Override
        public boolean existsByPatientIdAndDoctorIdAndServiceDateAndStatusIn(Long patientId, Long doctorId, LocalDate serviceDate, List<QueueStatus> statuses) {
            return entries.values().stream().anyMatch(e ->
                    e.getPatient().getId().equals(patientId) &&
                            e.getDoctor().getId().equals(doctorId) &&
                            e.getServiceDate().equals(serviceDate) &&
                            statuses.contains(e.getStatus())
            );
        }

        @Override
        public Optional<QueueEntry> findByDoctorIdAndServiceDateAndStatus(Long doctorId, LocalDate serviceDate, QueueStatus status) {
            return entries.values().stream().filter(e ->
                    e.getDoctor().getId().equals(doctorId) &&
                            e.getServiceDate().equals(serviceDate) &&
                            e.getStatus() == status
            ).findFirst();
        }

        @Override
        public List<QueueEntry> findByDoctorIdAndServiceDateAndStatusInOrderByTokenNumberAsc(Long doctorId, LocalDate serviceDate, List<QueueStatus> statuses) {
            return entries.values().stream()
                    .filter(e -> e.getDoctor().getId().equals(doctorId) &&
                            e.getServiceDate().equals(serviceDate) &&
                            statuses.contains(e.getStatus()))
                    .sorted(Comparator.comparingInt(QueueEntry::getTokenNumber))
                    .toList();
        }

        @Override public int countByDoctorAndCheckInTimeBetween(Doctor doctor, java.time.LocalDateTime start, java.time.LocalDateTime end) { return 0; }
        @Override public List<QueueEntry> findByDoctorAndStatusInAndCheckInTimeBetween(Doctor doctor, List<QueueStatus> statuses, java.time.LocalDateTime start, java.time.LocalDateTime end) { return List.of(); }
    }

    static class StubBaseRepository<T, ID> implements org.springframework.data.jpa.repository.JpaRepository<T, ID> {
        @Override public void flush() {}
        @Override public <S extends T> S saveAndFlush(S entity) { return entity; }
        @Override public <S extends T> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<T> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<ID> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public T getOne(ID id) { return null; }
        @Override public T getById(ID id) { return null; }
        @Override public T getReferenceById(ID id) { return null; }
        @Override public <S extends T> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends T> List<S> findAll(org.springframework.data.domain.Example<S> example) { return null; }
        @Override public <S extends T> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return null; }
        @Override public <S extends T> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return null; }
        @Override public <S extends T> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends T> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends T, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends T> List<S> saveAll(Iterable<S> entities) { return null; }
        @Override public Optional<T> findById(ID id) { return Optional.empty(); }
        @Override public boolean existsById(ID id) { return false; }
        @Override public List<T> findAll() { return List.of(); }
        @Override public List<T> findAllById(Iterable<ID> ids) { return null; }
        @Override public long count() { return 0; }
        @Override public void deleteById(ID id) {}
        @Override public void delete(T entity) {}
        @Override public void deleteAllById(Iterable<? extends ID> ids) {}
        @Override public void deleteAll(Iterable<? extends T> entities) {}
        @Override public void deleteAll() {}
        @Override public List<T> findAll(org.springframework.data.domain.Sort sort) { return null; }
        @Override public org.springframework.data.domain.Page<T> findAll(org.springframework.data.domain.Pageable pageable) { return null; }
        @Override public <S extends T> S save(S entity) { return entity; }
    }
}
