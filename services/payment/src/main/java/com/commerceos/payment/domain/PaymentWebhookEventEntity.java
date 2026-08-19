package com.commerceos.payment.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_webhook_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_webhook_event_id", columnNames = {"eventId"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentWebhookEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 128)
    private String eventId;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(length = 64)
    private String paymentId;

    @Column(length = 128)
    private String payloadHash;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant receivedAt;

    @Column
    private Instant processedAt;

    @Column(nullable = false, length = 32)
    private String status;
}
