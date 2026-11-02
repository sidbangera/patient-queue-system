package com.patientqueue.system.controller;

import com.patientqueue.system.dto.QueueCheckInDTO;
import com.patientqueue.system.entity.QueueEntry;
import com.patientqueue.system.entity.QueueStatus;
import com.patientqueue.system.service.QueueEntryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Queue;

@RestController
@RequestMapping("/api/queue")
public class QueueEntryController {
    private final QueueEntryService queueEntryService;

    public QueueEntryController(QueueEntryService queueEntryService) {
        this.queueEntryService = queueEntryService;
    }

    @PostMapping("/checkin")
    public ResponseEntity<QueueEntry> checkIn(@Valid @RequestBody QueueCheckInDTO dto){
            QueueEntry queueEntry = queueEntryService.checkIn(dto.getPatientId(),dto.getDoctorId());
            return ResponseEntity.status(HttpStatus.CREATED).body(queueEntry);
    }

    @PostMapping("/{id}/call-next")
    public ResponseEntity<QueueEntry> callNext(@PathVariable Long id) {
            QueueEntry entry = queueEntryService.changeState(id, QueueStatus.IN_CONSULTATION);
            return ResponseEntity.ok(entry);
    }
    @PostMapping("/{id}/complete")
    public ResponseEntity<QueueEntry> completeConsultation(@PathVariable Long id) {
            QueueEntry entry = queueEntryService.changeState(id, QueueStatus.COMPLETED);
            return ResponseEntity.ok(entry);
    }

    @PostMapping("/{id}/no-show")
    public ResponseEntity<QueueEntry> markNoShow(@PathVariable Long id) {
            QueueEntry entry = queueEntryService.changeState(id, QueueStatus.NO_SHOW);
            return ResponseEntity.ok(entry);
    }

    @GetMapping("/patient/{patientId}")
    public List<QueueEntry> getPatientHistory(@PathVariable Long patientId) {
        return queueEntryService.getPatientVisits(patientId);
    }

    @GetMapping("/doctor/{doctorId}")
    public List<QueueEntry> getDoctorQueue(@PathVariable Long doctorId){
        return queueEntryService.getDoctorQueue(doctorId);
    }
}
