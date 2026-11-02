package com.patientqueue.system.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(
        name = "daily_queue_counter",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_doctor_service_date", columnNames = {"doctor_id", "service_date"})
        }
)
@Getter
@Setter
@NoArgsConstructor
public class DailyQueueCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "last_token_number", nullable = false)
    private int lastTokenNumber;

    public DailyQueueCounter(Long doctorId, LocalDate serviceDate, int lastTokenNumber) {
        this.doctorId = doctorId;
        this.serviceDate = serviceDate;
        this.lastTokenNumber = lastTokenNumber;
    }
}
