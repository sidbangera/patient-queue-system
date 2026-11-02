package com.patientqueue.system.controller;

import com.patientqueue.system.dto.*;
import com.patientqueue.system.entity.User;
import com.patientqueue.system.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/users")
public class UserController {
    private final UserService service;
    public UserController(UserService service) { this.service=service; }
    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> register(@Valid @RequestBody RegisterDTO dto, Authentication auth) {
        User u = service.registerUser(dto.getUsername(),dto.getPassword(),dto.getRole(),auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(new UserResponseDTO(u.getId(),u.getUsername(),u.getRole()));
    }
    @PostMapping("/login")
    public LoginResponseDTO login(@Valid @RequestBody LoginDTO dto) {
        return new LoginResponseDTO(service.login(dto));
    }
    @GetMapping("/me")
    public UserResponseDTO me(Authentication auth) {
        User u=service.getUser(auth.getName());
        return new UserResponseDTO(u.getId(),u.getUsername(),u.getRole());
    }
}
