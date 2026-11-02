package com.patientqueue.system.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "queue_entry",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_doctor_date_token", columnNames = {"doctor_id", "service_date", "token_number"})
        }
)
@Getter
@Setter
@NoArgsConstructor
public class QueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_number", nullable = false)
    private int tokenNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QueueStatus status;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "check_in_time", nullable = false)
    private LocalDateTime checkInTime;

    @Column(name = "consultation_start_time")
    private LocalDateTime consultationStartTime;

    @Column(name = "consultation_end_time")
    private LocalDateTime consultationEndTime;

    public QueueEntry(int tokenNumber, Doctor doctor, Patient patient) {
        this.tokenNumber = tokenNumber;
        this.doctor = doctor;
        this.patient = patient;
        this.status = QueueStatus.WAITING;
        this.checkInTime = LocalDateTime.now();
        this.serviceDate = this.checkInTime.toLocalDate();
    }

    public QueueEntry(int tokenNumber, Doctor doctor, Patient patient, LocalDate serviceDate, LocalDateTime checkInTime) {
        this.tokenNumber = tokenNumber;
        this.doctor = doctor;
        this.patient = patient;
        this.status = QueueStatus.WAITING;
        this.serviceDate = serviceDate;
        this.checkInTime = checkInTime;
    }
}
