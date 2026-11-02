# ADR-003: JWT Signing Key Lifecycle & Stateless Session Management

**Status**: Accepted
**Date**: October 24, 2026
**Context**:
In the initial uncommitted local JWT implementation, `JwtUtil.java` generated a random HMAC key in memory on startup: `Jwts.SIG.HS256.key().build()`. As a result, restarting the backend invalidates all active JWT tokens, disconnecting logged-in clinic staff.

**Decision**:
1. Replace ephemeral key generation with an externally injected, stable HMAC-SHA256 signing secret configured via `jwt.secret` in `application.properties` or environment variable `JWT_SECRET`.
2. Keep the raw secret strictly out of Git (`application.properties` remains in `.gitignore`). Provide a documented dummy placeholder in `application.properties.example`.
3. Set Spring Security session policy to `SessionCreationPolicy.STATELESS`.
4. Wrap token extraction and validation in `JwtAuthFilter` with structured exception handling (`ExpiredJwtException`, `MalformedJwtException`, `SecurityException`) to return HTTP 401 instead of letting unhandled filter errors bubble up.

**Alternatives Considered**:
1. *Asymmetric RSA / Public-Private Key Pair (RS256)*: Superior for multi-service architectures where separate resource servers verify tokens without seeing the private signing key. Overkill for this monolith where the auth issuer and resource server are identical.

**Consequences & Tradeoffs**:
- *Positive*: Seamless token validity across server restarts; verifiable token lifecycle; robust error reporting on expired tokens.
- *Negative*: Administrator must ensure a securely generated, high-entropy 256-bit secret is configured during initial setup.
