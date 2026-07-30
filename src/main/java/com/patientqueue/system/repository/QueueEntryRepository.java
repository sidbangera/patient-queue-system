package com.patientqueue.system.repository;

import com.patientqueue.system.entity.Doctor;
import com.patientqueue.system.entity.QueueEntry;
import com.patientqueue.system.entity.QueueStatus;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface QueueEntryRepository extends JpaRepository<QueueEntry,Long> {
    int countByDoctorAndCheckInTimeBetween(Doctor doctor, LocalDateTime start, LocalDateTime end);
    List<QueueEntry> findByDoctorAndStatusInAndCheckInTimeBetween(
            Doctor doctor, List<QueueStatus> statuses, LocalDateTime start, LocalDateTime end);
}
