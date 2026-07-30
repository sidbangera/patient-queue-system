package com.patientqueue.system.service;

import com.patientqueue.system.dto.PatientDTO;
import com.patientqueue.system.entity.Patient;
import com.patientqueue.system.repository.PatientRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PatientService {
    private final PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository){
        this.patientRepository = patientRepository;
    }

    public Patient registerPatient(PatientDTO dto){
        Patient patient = new Patient(dto.getName(),dto.getAge(),dto.getContact());
        return patientRepository.save(patient);
    }

    public List<Patient> getAllPatients(){
        return patientRepository.findAll();
    }

    public Optional<Patient> getPatientByID(Long id){
        return patientRepository.findById(id);
                //.orElseThrow(() -> new RuntimeException("Patient not found with ID:"+id));
    }

    public Optional<Patient> updatePatientByID(Long id,PatientDTO dto){
        Optional<Patient> existingPatient = getPatientByID(id);
        if(existingPatient.isEmpty()){
            return Optional.empty();
        }
        Patient patient = existingPatient.get();
        patient.setName(dto.getName());
        patient.setAge(dto.getAge());
        patient.setContact(dto.getContact());

        return Optional.of(patientRepository.save(patient));
    }

    public boolean deletePatientByID(Long id){
        Optional<Patient> patient = getPatientByID(id);
        if(patient.isEmpty()){
            return false;
        }
        patientRepository.delete(patient.get());
        return true;
    }

}
