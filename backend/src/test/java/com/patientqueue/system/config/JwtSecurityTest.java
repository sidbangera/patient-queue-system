package com.patientqueue.system.config;

import com.patientqueue.system.entity.Role;
import com.patientqueue.system.entity.User;
import com.patientqueue.system.repository.UserRepository;
import com.patientqueue.system.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Security & JWT Authentication Unit Tests")
class JwtSecurityTest {

    private static final String TEST_SECRET = "test_dev_secret_key_patient_queue_management_system_minimum_256_bits_length!";
    private JwtUtil jwtUtil;
    private InMemoryUserRepository fakeUserRepository;
    private FakePasswordEncoder fakePasswordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET, 3600000); // 1 hour expiration
        fakeUserRepository = new InMemoryUserRepository();
        fakePasswordEncoder = new FakePasswordEncoder();
        userService = new UserService(fakeUserRepository, fakePasswordEncoder, jwtUtil);
    }

    @Test
    @DisplayName("JWT generates and validates claims with stable secret")
    void testTokenGenerationAndValidation() {
        String token = jwtUtil.generateToken("dr_priya", "DOCTOR");
        assertNotNull(token);

        assertEquals("dr_priya", jwtUtil.extractUsername(token));
        assertEquals("DOCTOR", jwtUtil.extractRole(token));
        assertTrue(jwtUtil.validateToken(token, "dr_priya"));
        assertFalse(jwtUtil.isTokenExpired(token));
    }

    @Test
    @DisplayName("Expired JWT token is rejected")
    void testExpiredTokenHandling() {
        // Generate a token with negative expiration (already expired)
        String expiredToken = jwtUtil.generateToken("dr_priya", "DOCTOR", -1000L);
        assertNotNull(expiredToken);

        assertTrue(jwtUtil.isTokenExpired(expiredToken), "Token should be marked expired");
        assertFalse(jwtUtil.validateToken(expiredToken, "dr_priya"), "Expired token must fail validation");
    }

    @Test
    @DisplayName("Tampered JWT signature fails validation")
    void testTamperedTokenFails() {
        String token = jwtUtil.generateToken("dr_priya", "DOCTOR");
        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + ".eyJzdWIiOiJoYWNrZXIiLCJyb2xlIjoiQURNSU4ifQ." + parts[2];

        assertFalse(jwtUtil.validateToken(tamperedToken, "dr_priya"));
    }

    @Test
    @DisplayName("Anonymous caller cannot self-assign ADMIN role (Privilege Escalation Prevention)")
    void testAnonymousCannotRegisterAsAdmin() {
        assertThrows(AccessDeniedException.class, () ->
                userService.registerUser("attacker", "password123", Role.ADMIN, null)
        );
        assertTrue(fakeUserRepository.users.isEmpty(), "User must not be saved when escalation is attempted");
    }

    @Test
    @DisplayName("Anonymous caller cannot self-assign DOCTOR role")
    void testAnonymousCannotRegisterAsDoctor() {
        assertThrows(AccessDeniedException.class, () ->
                userService.registerUser("attacker", "password123", Role.DOCTOR, null)
        );
        assertTrue(fakeUserRepository.users.isEmpty());
    }

    @Test
    @DisplayName("Authenticated ADMIN can register a new DOCTOR")
    void testAdminCanRegisterDoctor() {
        Authentication adminAuth = new UsernamePasswordAuthenticationToken(
                "super_admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        User registered = userService.registerUser("new_doc", "securePass123", Role.DOCTOR, adminAuth);
        assertNotNull(registered);
        assertEquals(Role.DOCTOR, registered.getRole());
        assertEquals("new_doc", registered.getUsername());
        assertTrue(fakeUserRepository.findByUsername("new_doc").isPresent());
    }

    @Test
    @DisplayName("Anonymous registration is rejected even without a role")
    void testAnonymousRegistrationDenied() {
        assertThrows(AccessDeniedException.class, () ->
                userService.registerUser("front_desk", "deskPass123", null, null));
        assertTrue(fakeUserRepository.users.isEmpty());
    }

    // Lightweight in-memory stub avoiding ByteBuddy dynamic agents
    static class InMemoryUserRepository implements UserRepository {
        Map<String, User> users = new HashMap<>();
        long idSeq = 1;

        @Override
        public Optional<User> findByUsername(String username) {
            return Optional.ofNullable(users.get(username));
        }

        @Override
        public <S extends User> S save(S entity) {
            if (entity.getId() == null) {
                entity.setId(idSeq++);
            }
            users.put(entity.getUsername(), entity);
            return entity;
        }

        @Override public void flush() {}
        @Override public <S extends User> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends User> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<User> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> longs) {}
        @Override public void deleteAllInBatch() {}
        @Override public User getOne(Long aLong) { return null; }
        @Override public User getById(Long aLong) { return null; }
        @Override public User getReferenceById(Long aLong) { return null; }
        @Override public <S extends User> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends User> List<S> findAll(Example<S> example) { return null; }
        @Override public <S extends User> List<S> findAll(Example<S> example, Sort sort) { return null; }
        @Override public <S extends User> Page<S> findAll(Example<S> example, Pageable pageable) { return null; }
        @Override public <S extends User> long count(Example<S> example) { return 0; }
        @Override public <S extends User> boolean exists(Example<S> example) { return false; }
        @Override public <S extends User, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends User> List<S> saveAll(Iterable<S> entities) { return null; }
        @Override public Optional<User> findById(Long aLong) { return Optional.empty(); }
        @Override public boolean existsById(Long aLong) { return false; }
        @Override public List<User> findAll() { return new ArrayList<>(users.values()); }
        @Override public List<User> findAllById(Iterable<Long> longs) { return null; }
        @Override public long count() { return users.size(); }
        @Override public void deleteById(Long aLong) {}
        @Override public void delete(User entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> longs) {}
        @Override public void deleteAll(Iterable<? extends User> entities) {}
        @Override public void deleteAll() { users.clear(); }
        @Override public List<User> findAll(Sort sort) { return null; }
        @Override public Page<User> findAll(Pageable pageable) { return null; }
    }

    static class FakePasswordEncoder implements PasswordEncoder {
        @Override
        public String encode(CharSequence rawPassword) {
            return "hashed_" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return ("hashed_" + rawPassword).equals(encodedPassword);
        }
    }
}
