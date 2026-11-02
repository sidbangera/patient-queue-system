package com.patientqueue.system.dto;

import com.patientqueue.system.entity.Role;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserResponseDTO {
    private Long id;
    private String username;
    private Role role;
    public UserResponseDTO(Long id, String username, Role role) {
        this.id = id;
        this.username = username;
        this.role = role;
    }
}
