package com.commerceos.inventory.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "inventory_holds", indexes = {
    @Index(name = "idx_hold_sku", columnList = "sku"),
    @Index(name = "idx_hold_expires", columnList = "expiresAt"),
    @Index(name = "idx_hold_order", columnList = "orderId"),
    @Index(name = "idx_hold_customer", columnList = "customerId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryHoldEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private String darkStoreId;

    @Column
    private String orderId;

    @Column
    private String customerId;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.status == null) {
            this.status = "ACTIVE_HOLD";
        }
        if (this.expiresAt == null) {
            this.expiresAt = Instant.now().plusSeconds(300);
        }
    }
}
