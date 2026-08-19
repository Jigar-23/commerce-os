package com.commerceos.order.security;

import com.commerceos.order.domain.SseTicketEntity;
import com.commerceos.order.repository.SseTicketRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired(required = false)
    private SseTicketRepository sseTicketRepository;

    public static String getJwtSecret() {
        String secret = System.getenv("JWT_SECRET");
        if (secret != null && !secret.isBlank()) return secret.trim();
        secret = System.getProperty("JWT_SECRET");
        if (secret != null && !secret.isBlank()) return secret.trim();
        return "commerceos_master_jwt_secret_key_2026_production";
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        String sseTicketParam = request.getParameter("sseTicket");

        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        } else if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("auth_token".equals(cookie.getName()) || "jwt".equals(cookie.getName()) || "sessionToken".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        // Support single-use SSE ticket if ticket provided
        if (token == null && sseTicketParam != null && !sseTicketParam.isBlank()) {
            String path = request.getRequestURI();
            String requestedId = null;
            if (path != null) {
                String[] segments = path.split("/");
                for (int i = 0; i < segments.length; i++) {
                    if (("order".equals(segments[i]) || "session".equals(segments[i])) && i + 1 < segments.length) {
                        requestedId = segments[i + 1];
                        break;
                    }
                }
            }
            token = validateAndConsumeSseTicket(sseTicketParam.trim(), requestedId);
        }

        if (token != null && !token.isBlank()) {
            try {
                JwtClaims claims = parseAndVerifyJwt(token);
                if (claims != null && !claims.isExpired()) {
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    if (claims.roles != null && !claims.roles.isEmpty()) {
                        for (String r : claims.roles) {
                            String roleName = r.startsWith("ROLE_") ? r : "ROLE_" + r;
                            authorities.add(new SimpleGrantedAuthority(roleName));
                        }
                    } else if (claims.role != null && !claims.role.isBlank()) {
                        String roleName = claims.role.startsWith("ROLE_") ? claims.role : "ROLE_" + claims.role;
                        authorities.add(new SimpleGrantedAuthority(roleName));
                    }
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                            claims.subject,
                            null,
                            authorities
                        );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    SecurityContextHolder.clearContext();
                }
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
            }
        } else {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    public static String generateJwtToken(String subject, String role, long ttlMillis) {
        try {
            long now = System.currentTimeMillis() / 1000L;
            long exp = now + (ttlMillis / 1000L);

            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = Map.of(
                "sub", subject,
                "role", role,
                "roles", List.of(role),
                "iss", "https://auth.commerceos.io",
                "aud", "https://api.commerceos.io",
                "iat", now,
                "exp", exp
            );

            String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(OBJECT_MAPPER.writeValueAsBytes(header));
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(OBJECT_MAPPER.writeValueAsBytes(payload));
            String dataToSign = encodedHeader + "." + encodedPayload;
            String secret = getJwtSecret();
            String signature = hmacSha256(dataToSign, secret);

            return dataToSign + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT", e);
        }
    }

    public static JwtClaims parseAndVerifyJwt(String jwtToken) {
        try {
            String[] parts = jwtToken.split("\\.", -1);
            if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
                return null;
            }

            String encodedHeader = parts[0];
            String encodedPayload = parts[1];
            String signature = parts[2];

            // Typed Jackson Parsing: Header algorithm enforcement
            JsonNode headerNode = OBJECT_MAPPER.readTree(Base64.getUrlDecoder().decode(encodedHeader));
            String alg = headerNode.path("alg").asText();
            if (!"HS256".equals(alg)) {
                return null;
            }

            // Cryptographic HMAC signature check
            String expectedSig = hmacSha256(encodedHeader + "." + encodedPayload, getJwtSecret());
            if (!constantTimeEquals(expectedSig, signature)) {
                return null;
            }

            // Typed Jackson Parsing: Payload validation
            JsonNode payloadNode = OBJECT_MAPPER.readTree(Base64.getUrlDecoder().decode(encodedPayload));
            String sub = payloadNode.path("sub").asText(null);
            if (sub == null || sub.isBlank()) {
                return null;
            }

            if (!payloadNode.has("exp") || !payloadNode.get("exp").isNumber()) {
                return null;
            }
            long expSeconds = payloadNode.get("exp").asLong();
            if ((System.currentTimeMillis() / 1000L) >= expSeconds) {
                return null; // Expired token
            }

            String iss = payloadNode.path("iss").asText(null);
            if (iss == null || (!"https://auth.commerceos.io".equals(iss) && !"commerce-os-auth".equals(iss))) {
                return null; // Mandatory issuer enforcement
            }

            String aud = payloadNode.path("aud").asText(null);
            if (aud == null || (!"https://api.commerceos.io".equals(aud) && !"commerce-os-api".equals(aud))) {
                return null; // Mandatory audience enforcement
            }

            String role = payloadNode.path("role").asText("ROLE_CUSTOMER");
            List<String> roles = new ArrayList<>();
            if (payloadNode.has("roles") && payloadNode.get("roles").isArray()) {
                for (JsonNode r : payloadNode.get("roles")) {
                    roles.add(r.asText());
                }
            } else {
                roles.add(role);
            }

            String storeId = payloadNode.path("storeId").asText(null);
            String sellerId = payloadNode.path("sellerId").asText(null);
            String riderId = payloadNode.path("riderId").asText(null);

            return new JwtClaims(sub, role, roles, storeId, sellerId, riderId, expSeconds * 1000L);
        } catch (Exception e) {
            return null;
        }
    }

    private static String hmacSha256(String data, String secret) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] raw = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }

    public static class JwtClaims {
        public final String subject;
        public final String role;
        public final List<String> roles;
        public final String storeId;
        public final String sellerId;
        public final String riderId;
        public final long expirationMs;

        public JwtClaims(String subject, String role, List<String> roles, String storeId, String sellerId, String riderId, long expirationMs) {
            this.subject = subject;
            this.role = role;
            this.roles = roles;
            this.storeId = storeId;
            this.sellerId = sellerId;
            this.riderId = riderId;
            this.expirationMs = expirationMs;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expirationMs;
        }
    }

    private static final Map<String, SseTicketEntry> memoryTicketStore = new java.util.concurrent.ConcurrentHashMap<>();

    public static String createSseTicket(String token, String targetId) {
        String ticket = "tkt_" + UUID.randomUUID();
        memoryTicketStore.put(ticket, new SseTicketEntry(token, targetId, System.currentTimeMillis() + 10000L));
        return ticket;
    }

    public static String createSseTicket(String token) {
        return createSseTicket(token, null);
    }

    public static void clearSseTicketsForTesting() {
        memoryTicketStore.clear();
    }

    public String validateAndConsumeSseTicket(String ticket, String requestedId) {
        if (sseTicketRepository != null) {
            Optional<SseTicketEntity> entityOpt = sseTicketRepository.findActiveTicketForUpdate(ticket);
            if (entityOpt.isPresent()) {
                SseTicketEntity entity = entityOpt.get();
                if (System.currentTimeMillis() <= entity.getExpiresAt()) {
                    if (entity.getTargetId() != null && !entity.getTargetId().isBlank() && requestedId != null && !requestedId.isBlank()) {
                        if (!entity.getTargetId().trim().equals(requestedId.trim())) {
                            return null;
                        }
                    }
                    entity.setConsumed(true);
                    sseTicketRepository.save(entity);
                    return entity.getToken();
                }
            }
        }
        SseTicketEntry entry = memoryTicketStore.remove(ticket);
        if (entry != null && System.currentTimeMillis() <= entry.expirationMs) {
            if (entry.targetId != null && !entry.targetId.isBlank() && requestedId != null && !requestedId.isBlank()) {
                if (!entry.targetId.trim().equals(requestedId.trim())) {
                    return null;
                }
            }
            return entry.token;
        }
        return null;
    }

    private static class SseTicketEntry {
        final String token;
        final String targetId;
        final long expirationMs;
        SseTicketEntry(String token, String targetId, long expirationMs) {
            this.token = token;
            this.targetId = targetId;
            this.expirationMs = expirationMs;
        }
    }
}
