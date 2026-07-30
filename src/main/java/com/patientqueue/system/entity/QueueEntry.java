package com.patientqueue.system.entity;

import com.patientqueue.system.repository.QueueEntryRepository;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class QueueEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private int tokenNumber;

    @ManyToOne
    @JoinColumn(name = "doctor_id")
    private Doctor doctor;

    @ManyToOne
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Enumerated(EnumType.STRING)
    private QueueStatus status;

    private LocalDateTime checkInTime;
    private LocalDateTime consultationStartTime;
    private LocalDateTime consultationEndTime;

    public QueueEntry(int tokenNumber,Doctor doctor,Patient patient){
        this.tokenNumber = tokenNumber;
        this.doctor = doctor;
        this.patient = patient;
        this.status = QueueStatus.WAITING;
        this.checkInTime = LocalDateTime.now();
    }

}
