package com.commerceos.payment.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_refunds", indexes = {
        @Index(name = "idx_refund_payment_id", columnList = "paymentId"),
        @Index(name = "idx_refund_idempotency", columnList = "idempotencyKey", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRefundEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID paymentId;

    @Column(nullable = false, length = 128)
    private String providerRefundId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(nullable = false, length = 32)
    private String status; // REFUND_SUBMITTED, REFUND_SETTLED, REFUND_FAILED

    @Column(nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(length = 256)
    private String reason;

    @Column(length = 128)
    private String requestedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant settledAt;
}
