package com.commerceos.order.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pricing_quotes", indexes = {
    @Index(name = "idx_pricing_quote_id", columnList = "quoteId", unique = true),
    @Index(name = "idx_pricing_quote_customer", columnList = "customerId"),
    @Index(name = "idx_pricing_quote_expires", columnList = "expiresAt")
})
@Getter
@Setter
public class PricingQuoteEntity {

    @Id
    @Column(nullable = false, length = 128)
    private String quoteId;

    @Column(length = 128)
    private String cartId;

    @Column(nullable = false, length = 128)
    private String customerId;

    @Column(nullable = false, length = 128)
    private String storeId;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal tax;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveryFee;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal coldChainFee;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(nullable = false)
    private Long expiresAt;

    @Column(nullable = false)
    private Integer pricingVersion;

    @Column(nullable = false, length = 32)
    private String status; // ACTIVE, LOCKED, EXPIRED, CONSUMED

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public PricingQuoteEntity() {}

    public PricingQuoteEntity(String quoteId, String cartId, String customerId, String storeId, String currency, BigDecimal subtotal, BigDecimal tax, BigDecimal discount, BigDecimal deliveryFee, BigDecimal coldChainFee, BigDecimal total, Long expiresAt, Integer pricingVersion, String status) {
        this.quoteId = quoteId;
        this.cartId = cartId;
        this.customerId = customerId;
        this.storeId = storeId;
        this.currency = currency;
        this.subtotal = subtotal;
        this.tax = tax;
        this.discount = discount;
        this.deliveryFee = deliveryFee;
        this.coldChainFee = coldChainFee;
        this.total = total;
        this.expiresAt = expiresAt;
        this.pricingVersion = pricingVersion;
        this.status = status;
        this.createdAt = Instant.now();
    }
}
