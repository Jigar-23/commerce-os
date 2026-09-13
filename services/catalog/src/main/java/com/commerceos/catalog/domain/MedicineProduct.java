package com.commerceos.catalog.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "medicine_products", indexes = {
    @Index(name = "idx_medicine_sku", columnList = "sku", unique = true),
    @Index(name = "idx_medicine_rx_requirement", columnList = "rxRequirement"),
    @Index(name = "idx_medicine_therapeutic_category", columnList = "therapeuticCategory")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicineProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 255)
    private String brandName;

    @Column(nullable = false, length = 255)
    private String manufacturer;

    @Column(nullable = false, length = 64)
    private String dosageForm; // TABLET, CAPSULE, SYRUP, etc.

    @Column(nullable = false, length = 128)
    private String packSize; // e.g., Strip of 15 Tablets

    @Column(nullable = false, length = 64)
    private String rxRequirement; // OTC, PRESCRIPTION_REQUIRED, CONTROLLED_DRUG

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price; // MRP

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountedPrice;

    @Column(nullable = false)
    private Integer discountPercentage;

    @Column(nullable = false)
    private Boolean inStock;

    @Column(nullable = false)
    private Integer stockCount;

    @Column(nullable = false)
    private Boolean coldChainRequired;

    @Column(nullable = false, length = 128)
    private String therapeuticCategory;

    @Column(nullable = false)
    private Integer expressDeliverySlaMins; // 10 or 15 mins for quick commerce

    @Column(columnDefinition = "TEXT")
    private String saltCompositionsJson; // JSON representation of salts

    @Column(columnDefinition = "TEXT")
    private String usesJson;

    @Column(columnDefinition = "TEXT")
    private String sideEffectsJson;

    @Column(columnDefinition = "TEXT")
    private String warningsJson;

    @Column(nullable = false)
    private Integer substitutesCount;

    @Column(nullable = false)
    private BigDecimal rating;

    @Column(nullable = false)
    private Integer reviewCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
