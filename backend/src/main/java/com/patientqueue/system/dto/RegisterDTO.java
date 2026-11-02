package com.patientqueue.system.dto;
import com.patientqueue.system.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class RegisterDTO {
    @NotBlank @Size(min=3,max=50) private String username;
    @NotBlank @Size(min=12) private String password;
    @NotNull private Role role;
}
