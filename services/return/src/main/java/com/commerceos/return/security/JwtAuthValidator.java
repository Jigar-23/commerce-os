package com.commerceos.return.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Component
public class JwtAuthValidator {

    private final String jwtSecret;
    private final String expectedIssuer;
    private final String expectedAudience;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthValidator() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            secret = System.getenv("JWT_SECRET_KEY");
        }
        if (secret == null || secret.isBlank()) {
            secret = System.getProperty("jwt.secret");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("CRITICAL SECURITY ERROR: JWT_SECRET environment variable is strictly required. Application refusing startup.");
        }
        this.jwtSecret = secret.trim();

        String issuer = System.getenv("JWT_ISSUER");
        this.expectedIssuer = (issuer != null && !issuer.isBlank()) ? issuer.trim() : "commerceos-auth";

        String audience = System.getenv("JWT_AUDIENCE");
        this.expectedAudience = (audience != null && !audience.isBlank()) ? audience.trim() : "commerceos-api";
    }

    public Optional<AuthenticatedPrincipal> authenticate(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }

        String token = authHeader.substring(7).trim();
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }

        String headerB64 = parts[0];
        String payloadB64 = parts[1];
        String signatureB64 = parts[2];

        try {
            byte[] headerBytes = Base64.getUrlDecoder().decode(headerB64);
            JsonNode headerNode = objectMapper.readTree(headerBytes);
            String alg = headerNode.path("alg").asText("");
            if (!"HS256".equalsIgnoreCase(alg)) {
                return Optional.empty();
            }

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] calculatedSig = mac.doFinal((headerB64 + "." + payloadB64).getBytes(StandardCharsets.UTF_8));
            byte[] providedSig = Base64.getUrlDecoder().decode(signatureB64);

            if (!MessageDigest.isEqual(calculatedSig, providedSig)) {
                return Optional.empty();
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadB64);
            JsonNode payloadNode = objectMapper.readTree(payloadBytes);

            long nowSeconds = Instant.now().getEpochSecond();

            if (payloadNode.has("exp")) {
                long exp = payloadNode.get("exp").asLong();
                if (exp > 0 && exp < nowSeconds) {
                    return Optional.empty();
                }
            }

            if (payloadNode.has("nbf")) {
                long nbf = payloadNode.get("nbf").asLong();
                if (nbf > 0 && nbf > nowSeconds) {
                    return Optional.empty();
                }
            }

            String subject = payloadNode.path("sub").asText("");
            if (subject.isBlank()) {
                subject = payloadNode.path("userId").asText("");
            }
            if (subject.isBlank()) {
                return Optional.empty();
            }

            Set<String> roles = new HashSet<>();
            if (payloadNode.has("role")) {
                roles.add(payloadNode.get("role").asText());
            }
            if (payloadNode.has("roles") && payloadNode.get("roles").isArray()) {
                for (JsonNode roleNode : payloadNode.get("roles")) {
                    roles.add(roleNode.asText());
                }
            }

            String tenantId = payloadNode.path("tenantId").asText("default");

            return Optional.of(new AuthenticatedPrincipal(subject, roles, tenantId));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static class AuthenticatedPrincipal {
        private final String subject;
        private final Set<String> roles;
        private final String tenantId;

        public AuthenticatedPrincipal(String subject, Set<String> roles, String tenantId) {
            this.subject = subject;
            this.roles = roles;
            this.tenantId = tenantId;
        }

        public String getSubject() { return subject; }
        public Set<String> getRoles() { return roles; }
        public String getTenantId() { return tenantId; }

        public boolean hasRole(String role) {
            return roles.contains(role) || roles.contains("ROLE_ADMIN") || roles.contains("ROLE_SYSTEM");
        }

        public boolean isOwnerOrAdmin(String targetId) {
            return (subject != null && subject.equalsIgnoreCase(targetId)) || hasRole("ROLE_ADMIN") || hasRole("ROLE_SYSTEM");
        }
    }
}
