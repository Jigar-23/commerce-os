package com.commerceos.inventory.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_batches", indexes = {
    @Index(name = "idx_inventory_batch_sku", columnList = "sku"),
    @Index(name = "idx_inventory_batch_expiry", columnList = "expiryDate")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryBatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 64)
    private String batchNumber;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false)
    private Integer availableQty;

    @Column(nullable = false)
    private LocalDate expiryDate;

    @Column(nullable = false, length = 64)
    private String darkStoreId;

    @Column(nullable = false)
    private Boolean coldChain;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        if (this.availableQty == null) this.availableQty = 0;
        if (this.coldChain == null) this.coldChain = false;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
