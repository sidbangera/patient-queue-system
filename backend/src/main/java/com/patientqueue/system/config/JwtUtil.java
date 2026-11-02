package com.patientqueue.system.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration-ms:36000000}") long expirationMs) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32 ||
                secret.startsWith("default_dev_secret")) {
            throw new IllegalArgumentException("Set JWT_SECRET to a unique secret with at least 32 UTF-8 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String username, String role) { return generateToken(username, role, expirationMs); }
    public String generateToken(String username, String role, long ttlMs) {
        Date now = new Date();
        return Jwts.builder().subject(username).claim("role", role).issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs)).signWith(key).compact();
    }
    public Claims extractAllClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
    public String extractUsername(String token) { return extractAllClaims(token).getSubject(); }
    public String extractRole(String token) { return extractAllClaims(token).get("role", String.class); }
    public boolean isTokenExpired(String token) {
        try { return !extractAllClaims(token).getExpiration().after(new Date()); }
        catch (JwtException | IllegalArgumentException e) { return true; }
    }
    public boolean validateToken(String token, String username) {
        try { return username.equals(extractUsername(token)) && !isTokenExpired(token); }
        catch (JwtException | IllegalArgumentException e) { return false; }
    }
}
