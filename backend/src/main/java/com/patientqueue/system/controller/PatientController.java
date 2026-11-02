package com.patientqueue.system.controller;

import com.patientqueue.system.dto.PatientDTO;
import com.patientqueue.system.entity.Patient;
import com.patientqueue.system.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping
    public List<Patient> getAllPatients() {
        return patientService.getAllPatients();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Patient> getPatientByID(@PathVariable Long id) {
        Optional<Patient> patient = patientService.getPatientByID(id);
        return patient.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Patient> addPatient(@Valid @RequestBody PatientDTO newPatientDTO) {
        Patient patient = patientService.registerPatient(newPatientDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(patient);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Patient> updatePatientByID(@PathVariable Long id, @Valid @RequestBody PatientDTO dto) {
        return patientService.updatePatientByID(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePatientByID(@PathVariable Long id) {
        boolean deleted = patientService.deletePatientByID(id);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
