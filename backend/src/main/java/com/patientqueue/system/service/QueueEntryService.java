package com.patientqueue.system.service;

import com.patientqueue.system.entity.Doctor;
import com.patientqueue.system.entity.Patient;
import com.patientqueue.system.entity.QueueEntry;
import com.patientqueue.system.entity.QueueStatus;
import com.patientqueue.system.exception.DuplicateCheckInException;
import org.springframework.dao.DataIntegrityViolationException;
import com.patientqueue.system.repository.DoctorRepository;
import com.patientqueue.system.repository.PatientRepository;
import com.patientqueue.system.repository.QueueEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class QueueEntryService {

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final QueueTokenAllocator tokenAllocator;
    private final Clock clinicClock;

    @Autowired
    public QueueEntryService(
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            QueueEntryRepository queueEntryRepository,
            QueueTokenAllocator tokenAllocator, Clock clinicClock) {
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.tokenAllocator = tokenAllocator;
        this.clinicClock = clinicClock;
    }

    /** Compatibility constructor for existing direct unit-test fixtures. Spring uses the injected Clock. */
    public QueueEntryService(PatientRepository patients, DoctorRepository doctors,
            QueueEntryRepository entries, QueueTokenAllocator tokens) {
        this(patients, doctors, entries, tokens, Clock.system(ZoneId.of("Asia/Kolkata")));
    }

    /**
     * Checks in a patient to a doctor's queue for today.
     * Atomic per-doctor, per-day allocation participates in the check-in transaction.
     * Enforces duplicate active check-in prevention.
     */
    @Transactional
    public QueueEntry checkIn(Long patientId, Long doctorId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient Not Found with ID:" + patientId));
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Doctor Not Found with ID:" + doctorId));

        LocalDateTime checkInTime = LocalDateTime.now(clinicClock);
        LocalDate serviceDate = checkInTime.toLocalDate();

        // 1. Prevent duplicate active check-ins for the same patient and doctor on the same day
        boolean alreadyActive = queueEntryRepository.existsByPatientIdAndDoctorIdAndServiceDateAndStatusIn(
                patientId,
                doctorId,
                serviceDate,
                List.of(QueueStatus.WAITING, QueueStatus.IN_CONSULTATION)
        );

        if (alreadyActive) {
            // Doctor profiles may already include the honorific (e.g. "Dr. Gregory House").
            String doctorName = doctor.getName() == null ? "doctor" : doctor.getName().trim();
            if (!doctorName.matches("(?i)^dr\\.?\\s+.*")) {
                doctorName = "Dr. " + doctorName;
            }
            throw new DuplicateCheckInException(
                    String.format("Patient %s (ID: %d) already has an active queue entry with %s today.",
                            patient.getName(), patientId, doctorName)
            );
        }

        // 2. Concurrency-safe atomic token increment per-doctor, per-day
        int tokenNumber = tokenAllocator.nextToken(doctorId, serviceDate);

        // 3. Persist queue entry with unique (doctor_id, service_date, token_number) constraint
        QueueEntry entry = new QueueEntry(tokenNumber, doctor, patient, serviceDate, checkInTime);
        try {
            return queueEntryRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCheckInException("Concurrent or duplicate active check-in was rejected by the database");
        }
    }

    /**
     * Advances the queue state for a patient visit.
     * Enforces state machine transitions and enforces at most one active consultation per doctor and service date.
     */
    @Transactional
    public QueueEntry changeState(Long id, QueueStatus newStatus) {
        QueueEntry entry = queueEntryRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Queue Entry Not Found with ID:" + id));

        if (entry.getCheckInTime() == null
        || entry.getDoctor() == null
        || entry.getServiceDate() == null) {
    throw new IllegalStateException(
            "Visit has incomplete legacy data; review it before changing status"
    );
}

if (newStatus == null || entry.getStatus() == null) {
    throw new IllegalStateException("Queue state is missing or invalid");
}

// Check whether the requested transition is allowed first.
boolean canTransition = entry.getStatus().canTransitionTo(newStatus);

if (!canTransition) {
    throw new IllegalStateException(
            "Cannot transition from " + entry.getStatus() + " to " + newStatus
    );
}

// Validate required timestamps only after confirming a valid transition.
if (newStatus == QueueStatus.COMPLETED
        && entry.getConsultationStartTime() == null) {
    throw new IllegalStateException(
            "Cannot complete a consultation without a start time"
    );
}

        if (newStatus == QueueStatus.IN_CONSULTATION) {
            // Check if doctor already has an active consultation with another patient
            Optional<QueueEntry> activeConsult = queueEntryRepository.findByDoctorIdAndServiceDateAndStatus(
                    entry.getDoctor().getId(),
                    entry.getServiceDate(),
                    QueueStatus.IN_CONSULTATION
            );

            if (activeConsult.isPresent() && !activeConsult.get().getId().equals(entry.getId())) {
                throw new IllegalStateException(
                        String.format("Doctor %s already has patient (Token #%d) in consultation. Conclude that visit first.",
                                entry.getDoctor().getName(), activeConsult.get().getTokenNumber())
                );
            }

            entry.setConsultationStartTime(LocalDateTime.now(clinicClock));
        } else if (newStatus == QueueStatus.COMPLETED) {
            entry.setConsultationEndTime(LocalDateTime.now(clinicClock));
        }

        if (entry.getConsultationStartTime() != null &&
                entry.getConsultationStartTime().isBefore(entry.getCheckInTime()))
            throw new IllegalStateException("Clinic clock is before the check-in time; review system time");
        if (entry.getConsultationEndTime() != null &&
                entry.getConsultationEndTime().isBefore(entry.getConsultationStartTime()))
            throw new IllegalStateException("Clinic clock is before the consultation start; review system time");
        entry.setStatus(newStatus);
        try {
            return queueEntryRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Doctor already has an active consultation, or queue data conflicts");
        }
    }

    /**
     * Returns today's active queue (WAITING and IN_CONSULTATION) for a doctor, ordered by token.
     */
    public List<QueueEntry> getPatientVisits(Long patientId) {
        if (!patientRepository.existsById(patientId))
            throw new IllegalArgumentException("Patient not found: " + patientId);
        return queueEntryRepository.findByPatientIdOrderByCheckInTimeDesc(patientId);
    }

    public List<QueueEntry> getDoctorQueue(Long doctorId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found with ID:" + doctorId));

        LocalDate serviceDate = LocalDate.now(clinicClock);
        List<QueueStatus> activeStatuses = List.of(QueueStatus.WAITING, QueueStatus.IN_CONSULTATION);
        return queueEntryRepository.findByDoctorIdAndServiceDateAndStatusInOrderByTokenNumberAsc(
                doctor.getId(),
                serviceDate,
                activeStatuses
        );
    }
}
