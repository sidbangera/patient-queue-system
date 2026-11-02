package com.patientqueue.system.service;

import com.patientqueue.system.config.JwtUtil;
import com.patientqueue.system.dto.LoginDTO;
import com.patientqueue.system.entity.Role;
import com.patientqueue.system.entity.User;
import com.patientqueue.system.exception.InvalidCredentialsException;
import com.patientqueue.system.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtUtil jwt;
    public UserService(UserRepository users, PasswordEncoder passwords, JwtUtil jwt) {
        this.users=users;this.passwords=passwords;this.jwt=jwt;
    }
    public User registerUser(String username, String password, Role role, Authentication currentAuth) {
        boolean admin = currentAuth != null && currentAuth.isAuthenticated() &&
                currentAuth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!admin) throw new AccessDeniedException("Only administrators can create staff accounts");
        if (role == null) throw new IllegalArgumentException("A staff role is required");
        if (users.findByUsername(username).isPresent()) throw new IllegalStateException("Username already exists");
        return users.save(new User(username, passwords.encode(password), role));
    }
    public String login(LoginDTO dto) {
        User u = users.findByUsername(dto.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));
        if (!passwords.matches(dto.getPassword(), u.getPassword()))
            throw new InvalidCredentialsException("Invalid username or password");
        return jwt.generateToken(u.getUsername(),u.getRole().name());
    }
    public User getUser(String username) {
        return users.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
