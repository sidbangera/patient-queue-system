package com.patientqueue.system.controller;

import com.patientqueue.system.config.JwtUtil;
import com.patientqueue.system.entity.Role;
import com.patientqueue.system.entity.User;
import com.patientqueue.system.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual Spring security chain + controller; identity repository is a mock, reporting DB is real/read-only. */
@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @MockitoBean UserRepository users;

    @BeforeEach void identities() {
        when(users.findByUsername("qa_admin")).thenReturn(Optional.of(new User("qa_admin", "unused", Role.ADMIN)));
        when(users.findByUsername("qa_nurse")).thenReturn(Optional.of(new User("qa_nurse", "unused", Role.NURSE)));
    }
    @Test void unauthenticatedAnalyticsIs401() throws Exception {
        mvc.perform(get("/api/analytics/dashboard")).andExpect(status().isUnauthorized());
    }
    @Test void nurseCannotReadAnalytics() throws Exception {
        mvc.perform(get("/api/analytics/dashboard").header("Authorization", "Bearer " + jwt.generateToken("qa_nurse", "NURSE")))
            .andExpect(status().isForbidden());
    }
    @Test void oldOrEscalatedRoleClaimDoesNotBypassDatabaseRole() throws Exception {
        mvc.perform(get("/api/analytics/dashboard").header("Authorization", "Bearer " + jwt.generateToken("qa_nurse", "ADMIN")))
            .andExpect(status().isUnauthorized());
    }
    @Test void adminReceivesSnapshotAndInvalidWindowIsRejected() throws Exception {
        String token = "Bearer " + jwt.generateToken("qa_admin", "ADMIN");
        mvc.perform(get("/api/analytics/dashboard").param("from", "2026-10-01").param("to", "2026-10-31")
            .header("Authorization", token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.total").isNumber()).andExpect(jsonPath("$.daily").isArray())
            .andExpect(jsonPath("$.clinicZone").isString());
        mvc.perform(get("/api/analytics/dashboard").param("from", "2026-10-31").param("to", "2026-10-01")
            .header("Authorization", token)).andExpect(status().isBadRequest());
    }
}
