package com.patientqueue.system;

import com.patientqueue.system.entity.Doctor;
import com.patientqueue.system.repository.DoctorRepository;
import com.patientqueue.system.repository.PatientRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import javax.sound.midi.Soundbank;
import java.sql.SQLOutput;

@SpringBootApplication
public class PatientQueueSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(PatientQueueSystemApplication.class, args);
	}
    @Bean
    public CommandLineRunner demo(PatientRepository patientRepository, DoctorRepository doctorRepository) {
        return args -> {

            System.out.println("--- App Started! Populating Database ---");

            // --- Your existing Patient Dummy Data can stay here ---

            // 1. Create two new Doctor objects using your exact variables
            Doctor doc1 = new Doctor();
            doc1.setName("Dr. Gregory House");
            doc1.setAge(52);
            doc1.setDepartment("Diagnostics");

            Doctor doc2 = new Doctor();
            doc2.setName("Dr. James Wilson");
            doc2.setAge(48);
            doc2.setDepartment("Oncology");

            // 2. Save them to PostgreSQL
            doctorRepository.save(doc1);
            doctorRepository.save(doc2);

            System.out.println("--- Doctors Successfully Saved! ---");
        };
    }
}
