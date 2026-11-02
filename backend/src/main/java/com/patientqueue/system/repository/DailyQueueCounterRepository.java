package com.patientqueue.system.repository;
import com.patientqueue.system.entity.DailyQueueCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;
public interface DailyQueueCounterRepository extends JpaRepository<DailyQueueCounter,Long> {
    Optional<DailyQueueCounter> findByDoctorIdAndServiceDate(Long doctorId,LocalDate serviceDate);
}
