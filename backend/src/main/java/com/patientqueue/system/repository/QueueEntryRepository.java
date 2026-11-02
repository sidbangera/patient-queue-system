package com.patientqueue.system.repository;

import com.patientqueue.system.entity.Doctor;
import com.patientqueue.system.entity.QueueEntry;
import com.patientqueue.system.entity.QueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {
    // Native row lock avoids an eager outer-join FOR UPDATE and serializes state changes.
    @Query(value = "SELECT q.* FROM queue_entry q WHERE q.id = :id FOR UPDATE", nativeQuery = true)
    Optional<QueueEntry> findByIdForUpdate(@Param("id") Long id);

    List<QueueEntry> findByPatientIdOrderByCheckInTimeDesc(Long patientId);

    int countByDoctorAndCheckInTimeBetween(Doctor doctor, LocalDateTime start, LocalDateTime end);

    List<QueueEntry> findByDoctorAndStatusInAndCheckInTimeBetween(
            Doctor doctor, List<QueueStatus> statuses, LocalDateTime start, LocalDateTime end);

    List<QueueEntry> findByDoctorIdAndServiceDateAndStatusInOrderByTokenNumberAsc(
            Long doctorId, LocalDate serviceDate, List<QueueStatus> statuses);

    boolean existsByPatientIdAndDoctorIdAndServiceDateAndStatusIn(
            Long patientId, Long doctorId, LocalDate serviceDate, List<QueueStatus> statuses);

    Optional<QueueEntry> findByDoctorIdAndServiceDateAndStatus(
            Long doctorId, LocalDate serviceDate, QueueStatus status);
}
