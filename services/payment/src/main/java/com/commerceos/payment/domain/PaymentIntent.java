package com.commerceos.payment.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Authoritative Payment Intent entity persisted in PostgreSQL.
 * Tracks both internal commerce order references and external provider identifiers.
 */
@Entity
@Table(name = "payment_intents", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_order_id", columnNames = "orderId")
}, indexes = {
        @Index(name = "idx_provider_intent_id", columnList = "providerIntentId"),
        @Index(name = "idx_provider_order_id", columnList = "providerOrderId")
})
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PaymentIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Order we are charging. Exactly one live intent per order (DB unique constraint). */
    @Column(nullable = false, unique = true)
    private UUID orderId;

    @Column(nullable = false)
    private UUID customerId;

    /** Server-authoritative charge amount, taken directly from catalog/order. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 64)
    private String paymentMethod;

    /** AUTHORIZED -> CAPTURED (money moved) -> PARTIALLY_REFUNDED -> REFUNDED. */
    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant capturedAt;
    private Instant refundedAt;

    /** Configured payment provider: STRIPE, RAZORPAY, SANDBOX */
    @Column(length = 32)
    private String provider;

    /** External provider intent / transaction identifier (e.g. pi_..., pay_..., order_...) */
    @Column(length = 128)
    private String providerIntentId;

    /** Provider client secret for secure frontend SDK handoff */
    @Column(length = 256)
    private String providerClientSecret;

    @Column(length = 128)
    private String providerCustomerId;

    @Column(length = 128)
    private String providerOrderId;

    @Column(length = 64)
    private String providerStatus;

    @Column(columnDefinition = "TEXT")
    private String providerMetadata;

    /** Gateway event id that settled this intent (set by webhook reconciliation). */
    private String gatewayEventId;

    /** Whether the gateway webhook independently verified the capture. */
    @Column(nullable = false)
    private Boolean webhookVerified = Boolean.FALSE;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.status == null) this.status = "AUTHORIZED";
        if (this.currency == null) this.currency = "INR";
    }

    @PreUpdate
    protected void onUpdate() {
        // no-op hook kept for symmetry / future audit columns
    }
}