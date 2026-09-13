package com.commerceos.return.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "return_requests", indexes = {
    @Index(name = "idx_return_order_id", columnList = "orderId"),
    @Index(name = "idx_return_customer_id", columnList = "customerId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnRequestEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal calculatedRefundAmount;

    @Column(nullable = false, length = 64)
    private String status;

    private Instant pickupScheduledAt;

    private String inspectedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = "ret_" + UUID.randomUUID().toString().substring(0, 8);
        }
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        if (this.status == null) {
            this.status = "RETURN_REQUESTED_PENDING_INSPECTION";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
