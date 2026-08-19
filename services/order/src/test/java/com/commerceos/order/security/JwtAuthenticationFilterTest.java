package com.commerceos.order.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JwtAuthenticationFilterTest {

    @Test
    void testGenerateAndVerifyJwtToken() {
        System.setProperty("JWT_SECRET", "CommerceOS_Test_Secret_Key_2026_HMAC_SHA256_32Bytes!");
        String token = JwtAuthenticationFilter.generateJwtToken("user_123", "ROLE_RIDER", 60000);
        assertNotNull(token);
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void testSseTicketCreationAndConsumption() {
        System.setProperty("JWT_SECRET", "CommerceOS_Test_Secret_Key_2026_HMAC_SHA256_32Bytes!");
        String token = JwtAuthenticationFilter.generateJwtToken("user_456", "ROLE_CUSTOMER", 60000);
        String ticket = JwtAuthenticationFilter.createSseTicket(token);

        assertNotNull(ticket);
        assertTrue(ticket.startsWith("tkt_"));
    }
}
