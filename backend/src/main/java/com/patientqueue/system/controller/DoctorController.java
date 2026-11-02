package com.patientqueue.system.controller;

import com.patientqueue.system.dto.DoctorDTO;
import com.patientqueue.system.entity.Doctor;
import com.patientqueue.system.service.DoctorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {
    private final DoctorService doctorService;
    public DoctorController(DoctorService doctorService){
        this.doctorService = doctorService;
    }
    @GetMapping
    public List<Doctor> getAllDoctors(){
        return doctorService.getAllDoctors();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Doctor> getDoctorByID(@PathVariable Long id){
        Optional<Doctor> queriedDoctor = doctorService.getDoctorByID(id);
        return queriedDoctor.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Doctor> addDoctor(@Valid @RequestBody DoctorDTO dto){
        Doctor doctor = doctorService.addDoctor(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(doctor);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Doctor> updateDoctorByID(@PathVariable Long id,@Valid @RequestBody DoctorDTO dto){
        return doctorService.updateDoctorByID(id,dto).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDoctorByID(@PathVariable Long id){
        boolean deleted = doctorService.deleteDoctorByID(id);
        if(deleted){
            return ResponseEntity.noContent().build();
        }
        else return ResponseEntity.notFound().build();
    }
}
