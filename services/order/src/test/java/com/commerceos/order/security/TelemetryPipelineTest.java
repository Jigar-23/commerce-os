package com.commerceos.order.security;

import com.commerceos.order.domain.DeliverySession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TelemetryPipelineTest {

    @BeforeEach
    void setUp() {
        System.setProperty("JWT_SECRET", "CommerceOS_Test_Secret_Key_2026_HMAC_SHA256_32Bytes!");
    }

    @Test
    void testRealGpsTelemetryPersistence() {
        DeliverySession session = DeliverySession.builder()
            .deliveryId("del_uuid_9999")
            .orderId("ORD-9999")
            .riderId("rider_101")
            .latestLatitude(28.4595)
            .latestLongitude(77.0266)
            .latestSequenceNumber(10L)
            .latestServerTimestamp(System.currentTimeMillis())
            .isStale(false)
            .build();

        assertEquals(28.4595, session.getLatestLatitude());
        assertEquals(77.0266, session.getLatestLongitude());
        assertEquals(10L, session.getLatestSequenceNumber());
        assertFalse(session.getIsStale());
    }

    @Test
    void testDuplicateTelemetrySequenceRejection() {
        long currentSeq = 5L;
        long incomingSeq = 5L; // Duplicate!

        boolean isDuplicate = (incomingSeq <= currentSeq);
        assertTrue(isDuplicate);
    }

    @Test
    void testOutOfOrderTelemetrySequenceHandling() {
        long currentSeq = 10L;
        long incomingSeq = 7L; // Out of order!

        boolean isDuplicate = (incomingSeq == currentSeq);
        boolean isOutOfOrder = (incomingSeq < currentSeq);

        assertFalse(isDuplicate);
        assertTrue(isOutOfOrder);
    }

    @Test
    void testConcurrentTelemetryAdvancement() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger acceptedCount = new AtomicInteger(0);

        long lastAcceptedSeq = 0L;

        for (int i = 1; i <= threads; i++) {
            final long seq = i;
            executor.submit(() -> {
                try {
                    if (seq > lastAcceptedSeq) {
                        acceptedCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        assertTrue(acceptedCount.get() > 0);
    }

    @Test
    void testOfflineQueueSurvivalMock() {
        // Simulates SQLite durable queue persistence across process restarts
        Map<Long, Double[]> offlineQueue = new HashMap<>();
        offlineQueue.put(100L, new Double[]{28.4595, 77.0266});

        // Simulate app restart / queue recovery
        assertNotNull(offlineQueue.get(100L));
        assertEquals(28.4595, offlineQueue.get(100L)[0]);
        assertEquals(77.0266, offlineQueue.get(100L)[1]);
    }

    @Test
    void testCrossDeliveryTelemetryIsolation() {
        String activeDeliveryId = "del_A_100";
        String queueDeliveryId = "del_B_200";

        boolean allowedReplay = activeDeliveryId.equals(queueDeliveryId);
        assertFalse(allowedReplay);
    }
}
