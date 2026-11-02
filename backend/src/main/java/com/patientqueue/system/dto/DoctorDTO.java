package com.patientqueue.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DoctorDTO {

    @NotBlank(message = "Doctor name is required")
    @Size(max = 120)
    private String name;

    @Min(value = 18, message = "Doctor age must be at least 18")
    @Max(value = 100, message = "Doctor age must not exceed 100")
    private int age;

    @NotBlank(message = "Department is required")
    @Size(max = 100)
    private String department;
}
