package com.patientqueue.system.config;

import com.patientqueue.system.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserRepository users;
    public JwtAuthFilter(JwtUtil jwtUtil, UserRepository users) {
        this.jwtUtil = jwtUtil; this.users = users;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            try {
                if (!jwtUtil.isTokenExpired(token)) {
                    String username = jwtUtil.extractUsername(token);
                    String claimRole = jwtUtil.extractRole(token);
                    // Roles are checked against the database, not trusted indefinitely from old JWT claims.
                    users.findByUsername(username).filter(u -> u.getRole() != null && u.getRole().name().equals(claimRole))
                         .ifPresent(u -> SecurityContextHolder.getContext().setAuthentication(
                                 new UsernamePasswordAuthenticationToken(username, null,
                                         List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())))));
                } else { request.setAttribute("jwt_error", "Invalid or expired token"); }
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
                request.setAttribute("jwt_error", "Invalid or expired token");
            }
        }
        filterChain.doFilter(request, response);
    }
}
