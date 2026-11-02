package com.patientqueue.system.config;

import com.patientqueue.system.entity.Role;
import com.patientqueue.system.entity.User;
import com.patientqueue.system.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    public AdminBootstrap(UserRepository users, PasswordEncoder encoder) { this.users=users; this.encoder=encoder; }
    @Override public void run(ApplicationArguments args) {
        String name=System.getenv("BOOTSTRAP_ADMIN_USERNAME");
        String pass=System.getenv("BOOTSTRAP_ADMIN_PASSWORD");
        if (name == null && pass == null) return;
        if (name == null || name.isBlank() || pass == null || pass.length() < 12)
            throw new IllegalStateException("Admin bootstrap requires a username and a password of at least 12 characters");
        if (users.findAll().stream().anyMatch(u -> u.getRole()==Role.ADMIN)) return;
        if (users.findByUsername(name).isPresent()) throw new IllegalStateException("Bootstrap username already exists");
        users.save(new User(name,encoder.encode(pass),Role.ADMIN));
    }
}
