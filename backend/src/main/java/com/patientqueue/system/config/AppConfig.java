package com.patientqueue.system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {
    @Bean
    public Clock clinicClock(@Value("${app.clinic-zone:Asia/Kolkata}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }
}
