package com.patientqueue.system.service;

import com.patientqueue.system.dto.DoctorDTO;
import com.patientqueue.system.entity.Doctor;
import com.patientqueue.system.repository.DoctorRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DoctorService {
    private DoctorRepository doctorRepository;

    public DoctorService(DoctorRepository doctorRepository){
        this.doctorRepository = doctorRepository;
    }
    public List<Doctor> getAllDoctors(){
        return doctorRepository.findAll();
    }

    public Optional<Doctor> getDoctorByID(Long id){
        return doctorRepository.findById(id);
    }

    public Doctor addDoctor(DoctorDTO dto){
        Doctor doctor = new Doctor(null,dto.getName(),dto.getAge(),dto.getDepartment());
        return doctorRepository.save(doctor);
    }

    public Optional<Doctor> updateDoctorByID(Long id,DoctorDTO dto){
        Optional<Doctor> existingDoctor = getDoctorByID(id);
        if(existingDoctor.isEmpty()){
            return Optional.empty();
        }
        Doctor doctor = existingDoctor.get();
        doctor.setName(dto.getName());
        doctor.setAge(dto.getAge());
        doctor.setDepartment(dto.getDepartment());
        return Optional.of(doctorRepository.save(doctor));
    }

    public boolean deleteDoctorByID(Long id){
        Optional<Doctor> doctor = getDoctorByID(id);
        if(doctor.isEmpty()) return false;
        doctorRepository.delete(doctor.get());
        return true;
    }

}
