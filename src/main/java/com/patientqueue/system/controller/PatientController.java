package com.patientqueue.system.controller;

import com.patientqueue.system.dto.PatientDTO;
import com.patientqueue.system.entity.Patient;
import com.patientqueue.system.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/patients")
public class PatientController {
    //private final PatientRepository patientRepository;
    private final PatientService patientService;

    public PatientController(PatientService patientService){
        //this.patientRepository = patientRepository;
        this.patientService = patientService;
    }

    @GetMapping
    public List<Patient> getAllPatients(){
        return patientService.getAllPatients();
    }

//    @GetMapping("/{id}")
//    public Optional<Patient> getPatientByID(@PathVariable Long id){
//        return patientService.getPatientByID(id);
//    }

    @GetMapping("/{id}")
    public ResponseEntity<Patient> getPatientByID(@PathVariable Long id){
        Optional<Patient> patient = patientService.getPatientByID(id);
        return patient.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Patient addPatient(@Valid @RequestBody PatientDTO newPatientDTO){
       // return patientRepository.save(newPatient);
        return patientService.registerPatient(newPatientDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Patient> updatePatientByID(@PathVariable Long id,@Valid @RequestBody PatientDTO dto){
        return patientService.updatePatientByID(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePatientByID(@PathVariable Long id){
        boolean deleted = patientService.deletePatientByID(id);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
