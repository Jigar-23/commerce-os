package com.commerceos.identity;

import com.commerceos.identity.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private final String secret = "CommerceOSSecretKeyForJWTTokenGeneration2026SuperSecureKey!";

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(secret, 900000L, 2592000000L);
    }

    @Test
    void testGenerateAndValidateAccessToken() {
        String userId = "usr_test_12345";
        String email = "test.user@commerceos.io";
        Set<String> roles = Set.of("ROLE_CUSTOMER");

        String token = jwtTokenProvider.generateAccessToken(userId, email, roles, "COMMERCEOS_CUSTOMER_RETAIL");
        assertNotNull(token);

        boolean isValid = jwtTokenProvider.validateToken(token);
        assertTrue(isValid);

        String extractedUserId = jwtTokenProvider.getUserIdFromToken(token);
        assertEquals(userId, extractedUserId);
    }
}
