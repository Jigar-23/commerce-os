package com.commerceos.identity.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.access-expiration-ms:900000}") long accessExpirationMs,
            @Value("${app.jwt.refresh-expiration-ms:2592000000}") long refreshExpirationMs
    ) {
        String resolvedSecret = System.getenv("JWT_SECRET");
        if (resolvedSecret == null || resolvedSecret.isBlank()) {
            resolvedSecret = System.getProperty("JWT_SECRET");
        }
        if (resolvedSecret == null || resolvedSecret.isBlank()) {
            resolvedSecret = secret;
        }
        if (resolvedSecret == null || resolvedSecret.isBlank()) {
            throw new IllegalStateException("FATAL: JWT_SECRET environment variable is missing or blank. Must be set in secure environment.");
        }
        this.secretKey = Keys.hmacShaKeyFor(resolvedSecret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(String userId, String email, Set<String> roles, String tenantId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessExpirationMs);
        String primaryRole = roles != null && !roles.isEmpty() ? roles.iterator().next() : "ROLE_CUSTOMER";

        return Jwts.builder()
                .setHeaderParam("alg", "HS256")
                .setHeaderParam("typ", "JWT")
                .setSubject(userId)
                .claim("email", email)
                .claim("roles", roles)
                .claim("role", primaryRole)
                .claim("tenant_id", tenantId)
                .setIssuer("https://auth.commerceos.io")
                .setAudience("https://api.commerceos.io")
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(String userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpirationMs);

        return Jwts.builder()
                .setHeaderParam("alg", "HS256")
                .setHeaderParam("typ", "JWT")
                .setSubject(userId)
                .setIssuer("https://auth.commerceos.io")
                .setAudience("https://api.commerceos.io")
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getUserIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }
}
