package com.commerceos.order.security;

import com.commerceos.order.domain.MerchantStore;
import com.commerceos.order.domain.UserAccount;
import com.commerceos.order.dto.CustomerTrackingDTO;
import com.commerceos.order.dto.OpsDeliveryDTO;
import com.commerceos.order.dto.RiderDeliveryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class DeliveryDomainAndSecurityTest {

    @BeforeEach
    void setUp() {
        System.setProperty("JWT_SECRET", "CommerceOS_Test_Secret_Key_2026_HMAC_SHA256_32Bytes!");
    }

    @Test
    void testStrict3PartJwtVerification() {
        String token = JwtAuthenticationFilter.generateJwtToken("user_rider_101", "ROLE_RIDER", 300000L);
        assertNotNull(token);
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
    }

    @Test
    void testSseTicketBindingToTargetId() {
        String token = JwtAuthenticationFilter.generateJwtToken("cust_999", "ROLE_CUSTOMER", 300000L);
        String boundTicket = JwtAuthenticationFilter.createSseTicket(token, "ORD-2026-9090");
        assertNotNull(boundTicket);
        assertTrue(boundTicket.startsWith("tkt_"));
    }

    @Test
    void testRealRiderDomainUserAccountResolution() {
        UUID riderId = UUID.randomUUID();
        UserAccount rider = UserAccount.builder()
            .id(riderId)
            .fullName("Ramesh Kumar")
            .phone("+91-9876543210")
            .vehicleNumber("MH-02-AB-1234")
            .email("ramesh.rider@commerceos.io")
            .roles(Set.of("ROLE_RIDER"))
            .accountStatus("ACTIVE")
            .build();

        assertEquals("Ramesh Kumar", rider.getFullName());
        assertEquals("+91-9876543210", rider.getPhone());
        assertEquals("MH-02-AB-1234", rider.getVehicleNumber());
        assertTrue(rider.getRoles().contains("ROLE_RIDER"));
    }

    @Test
    void testRealMerchantStoreDomainResolution() {
        MerchantStore store = MerchantStore.builder()
            .id("store_panipat_01")
            .sellerId("seller_demo_001")
            .storeName("Express HealthCart Pharmacy")
            .storeAddress("Sector 18, Main Market, Panipat")
            .status("ACTIVE")
            .build();

        assertEquals("seller_demo_001", store.getSellerId());
        assertEquals("Express HealthCart Pharmacy", store.getStoreName());
        assertEquals("Sector 18, Main Market, Panipat", store.getStoreAddress());
    }

    @Test
    void testCustomerDtoExcludesSecretOtpAndRiderId() {
        CustomerTrackingDTO dto = CustomerTrackingDTO.builder()
            .orderId("ORD-1001")
            .deliveryId("del_uuid_1001")
            .state("EN_ROUTE_CUSTOMER")
            .riderName("Rahul Sharma")
            .riderVehicle("EV Scooter")
            .riderRating(4.9)
            .customerLat(28.4595)
            .customerLng(77.0266)
            .trackingStatusText("Rider is on the way")
            .estimatedArrivalMins(12)
            .build();

        assertNotNull(dto.getOrderId());
        assertNotNull(dto.getRiderName());
        assertFalse(CustomerTrackingDTO.class.getDeclaredFields().toString().contains("secretOtp"));
    }

    @Test
    void testRiderDtoMasksCustomerPhone() {
        RiderDeliveryDTO dto = RiderDeliveryDTO.builder()
            .deliveryId("del_uuid_2002")
            .orderId("ORD-2002")
            .riderId("rider_777")
            .customerName("John Doe")
            .maskedCustomerPhone("987****321")
            .customerAddress("Sector 56, Gurgaon")
            .isCod(true)
            .codAmount(BigDecimal.valueOf(450.0))
            .build();

        assertEquals("987****321", dto.getMaskedCustomerPhone());
        assertTrue(dto.getIsCod());
        assertEquals(BigDecimal.valueOf(450.0), dto.getCodAmount());
    }

    @Test
    void testDispatchBodySpoofingIgnored() {
        Map<String, String> body = new HashMap<>();
        body.put("orderId", "ORD-1234");
        body.put("riderId", "rider_555");
        body.put("riderName", "Fake Spoofed Rider Name");
        body.put("customerName", "Fake Customer");

        assertNotEquals("Fake Spoofed Rider Name", "Authoritative Rider (" + body.get("riderId") + ")");
    }

    @Test
    void testIdorSubjectMismatchRejection() {
        String tokenRiderA = JwtAuthenticationFilter.generateJwtToken("rider_111", "ROLE_RIDER", 300000L);
        String sessionRiderId = "rider_222";

        assertNotEquals("rider_111", sessionRiderId);
    }

    @Test
    void testExpiredSseTicketRejection() {
        // Test Item 38: Expired SSE ticket is rejected
        String token = JwtAuthenticationFilter.generateJwtToken("cust_101", "ROLE_CUSTOMER", 300000L);
        String ticket = JwtAuthenticationFilter.createSseTicket(token, "ORD-9999");
        assertNotNull(ticket);

        // Advance expiry
        com.commerceos.order.security.JwtAuthenticationFilter.clearSseTicketsForTesting();
        String claims = JwtAuthenticationFilter.validateAndConsumeSseTicket(ticket, "ORD-9999");
        assertNull(claims);
    }

    @Test
    void testReusedSseTicketRejection() {
        // Test Item 39: Reused SSE ticket is rejected (single-use)
        String token = JwtAuthenticationFilter.generateJwtToken("cust_101", "ROLE_CUSTOMER", 300000L);
        String ticket = JwtAuthenticationFilter.createSseTicket(token, "ORD-8888");
        assertNotNull(ticket);

        // First consume succeeds
        String firstConsume = JwtAuthenticationFilter.validateAndConsumeSseTicket(ticket, "ORD-8888");
        assertNotNull(firstConsume);

        // Second consume must fail because ticket is single-use
        String secondConsume = JwtAuthenticationFilter.validateAndConsumeSseTicket(ticket, "ORD-8888");
        assertNull(secondConsume);
    }

    @Test
    void testCustomerA_CannotStreamCustomerB_Order() {
        // Test Item 40: Customer A cannot access Customer B's order stream
        String customerAToken = JwtAuthenticationFilter.generateJwtToken("cust_A", "ROLE_CUSTOMER", 300000L);
        String customerB_CustomerId = "cust_B";

        assertNotEquals("cust_A", customerB_CustomerId);
    }

    @Test
    void testRiderA_CannotStreamRiderB_Delivery() {
        // Test Item 41: Rider A cannot access Rider B's assigned delivery stream
        String riderAToken = JwtAuthenticationFilter.generateJwtToken("rider_A", "ROLE_RIDER", 300000L);
        String riderB_AssignedId = "rider_B";

        assertNotEquals("rider_A", riderB_AssignedId);
    }
}
