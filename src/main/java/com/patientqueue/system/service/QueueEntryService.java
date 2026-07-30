package com.patientqueue.system.service;

import com.patientqueue.system.entity.Doctor;
import com.patientqueue.system.entity.Patient;
import com.patientqueue.system.entity.QueueEntry;
import com.patientqueue.system.entity.QueueStatus;
import com.patientqueue.system.repository.DoctorRepository;
import com.patientqueue.system.repository.PatientRepository;
import com.patientqueue.system.repository.QueueEntryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Queue;

import static com.patientqueue.system.entity.QueueStatus.COMPLETED;
import static com.patientqueue.system.entity.QueueStatus.IN_CONSULTATION;

@Service
public class QueueEntryService {

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final QueueEntryRepository queueEntryRepository;

    public QueueEntryService(PatientRepository patientRepository,DoctorRepository doctorRepository,QueueEntryRepository queueEntryRepository){
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.queueEntryRepository = queueEntryRepository;
    }
    public QueueEntry checkIn(Long patientId,Long doctorId){
         Patient patient = patientRepository.findById(patientId)
                 .orElseThrow(() -> new IllegalArgumentException("Patient Not Found with ID:"+patientId));
         Doctor doctor = doctorRepository.findById(doctorId)
                 .orElseThrow(() -> new IllegalArgumentException("Doctor Not Found with ID:"+doctorId));
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        int tokenNumber = queueEntryRepository.countByDoctorAndCheckInTimeBetween(doctor,startOfDay,endOfDay)+1;

        QueueEntry entry = new QueueEntry(tokenNumber,doctor,patient);

        return queueEntryRepository.save(entry);
    }
    public QueueEntry changeState(Long id, QueueStatus newStatus) {
        QueueEntry entry = queueEntryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Queue Entry Not Found with ID:" + id));

        boolean canTransition = entry.getStatus().canTransitionTo(newStatus);

        if (!canTransition) {
            throw new IllegalStateException(
                    "Cannot transition from " + entry.getStatus() + " to " + newStatus);
        }

        entry.setStatus(newStatus);

        if (newStatus == QueueStatus.IN_CONSULTATION) {
            entry.setConsultationStartTime(LocalDateTime.now());
        } else if (newStatus == QueueStatus.COMPLETED) {
            entry.setConsultationEndTime(LocalDateTime.now());
        }

        return queueEntryRepository.save(entry);
    }

    public List<QueueEntry> getDoctorQueue(Long doctorId){
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(()->new IllegalArgumentException("Doctor not found with ID:"+doctorId));
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<QueueStatus> activeStatuses = List.of(QueueStatus.WAITING, QueueStatus.IN_CONSULTATION);
        return queueEntryRepository.findByDoctorAndStatusInAndCheckInTimeBetween(
                doctor, activeStatuses, startOfDay, endOfDay);
    }
}
