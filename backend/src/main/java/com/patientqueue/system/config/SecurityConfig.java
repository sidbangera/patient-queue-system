package com.patientqueue.system.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.patientqueue.system.repository.UserRepository;
import org.springframework.security.core.userdetails.User;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final JwtAuthFilter filter;
    private final String origins;
    public SecurityConfig(JwtAuthFilter filter, @Value("${app.cors.allowed-origins}") String origins) {
        this.filter = filter; this.origins = origins;
    }
    @Bean public UserDetailsService userDetailsService(UserRepository repository) {
        return username -> repository.findByUsername(username)
                .<UserDetails>map(u -> User.withUsername(u.getUsername()).password(u.getPassword())
                        .roles(u.getRole().name()).build())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
    @Bean public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable()).cors(c -> c.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req,res,exc) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    res.getWriter().write("{\"error\":\"Authentication required or token invalid\"}");
                })
                .accessDeniedHandler((req,res,exc) -> {
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    res.getWriter().write("{\"error\":\"Insufficient permissions\"}");
                }))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.POST, "/api/users/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/users/register").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/users/me").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/queue/checkin").hasAnyRole("RECEPTIONIST", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/queue/doctor/**").hasAnyRole("DOCTOR", "NURSE", "RECEPTIONIST", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/queue/patient/**").hasAnyRole("DOCTOR", "NURSE", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/queue/*/call-next", "/api/queue/*/complete", "/api/queue/*/no-show").hasAnyRole("DOCTOR", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/analytics/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/patients/**").hasAnyRole("DOCTOR", "RECEPTIONIST", "NURSE", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/patients").hasAnyRole("RECEPTIONIST", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/patients/**").hasAnyRole("RECEPTIONIST", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/patients/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/doctors/**").hasAnyRole("DOCTOR", "RECEPTIONIST", "NURSE", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/doctors").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/doctors/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/doctors/**").hasRole("ADMIN")
                .anyRequest().denyAll())
            .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    @Bean public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(Arrays.stream(origins.split(",")).map(String::trim).toList());
        cfg.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type", "Accept"));
        cfg.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
