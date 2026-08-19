package com.commerceos.order.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_orders", indexes = {
    @Index(name = "idx_order_customer_id", columnList = "customerId"),
    @Index(name = "idx_order_seller_id", columnList = "sellerId"),
    @Index(name = "idx_order_status", columnList = "orderStatus")
})
@Getter
@Setter
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID customerId;

    @Column(length = 128)
    private String pricingQuoteId;

    @Column(nullable = false, length = 64)
    private String orderType;

    @Column(nullable = false, length = 64)
    private String orderStatus;

    @Column(precision = 12, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveryFee;

    @Column(precision = 12, scale = 2)
    private BigDecimal coldChainFee;

    @Column(precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(nullable = false)
    private Boolean prescriptionRequired;

    private UUID prescriptionId;
    private String pharmacistLicenseNo;

    @Column(nullable = false, length = 64)
    private String paymentMethod;

    @Column(nullable = false, length = 64)
    private String paymentStatus;

    @Column(precision = 12, scale = 2)
    private BigDecimal codAmountToCollect;

    @Column(precision = 12, scale = 2)
    private BigDecimal codCollectedAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal codShortageAmount;

    private String codCollectorId;
    private Instant codCollectedAt;
    private String cancellationReason;
    private String cancelledBy;
    private Instant cancelledAt;

    @Column(length = 64)
    private String deliveryModel;

    @Column(length = 64)
    private String logisticsProvider;

    private String consignmentNumber;
    private String sellerId;

    @Column(columnDefinition = "TEXT")
    private String deliveryAddressJson;

    @Column(nullable = false)
    private Integer deliverySlaMins;

    @JsonIgnore
    @Column(length = 128)
    private String deliveryOtpHash;

    @JsonIgnore
    @Column(length = 64)
    private String deliveryOtpSalt;

    @Column(nullable = false)
    private Boolean deliveryOtpConsumed;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public CustomerOrder() {}

    public CustomerOrder(UUID id, UUID customerId, String pricingQuoteId, String orderType, String orderStatus, BigDecimal subtotalAmount, BigDecimal totalAmount, BigDecimal taxAmount, BigDecimal deliveryFee, BigDecimal coldChainFee, BigDecimal discountAmount, Boolean prescriptionRequired, UUID prescriptionId, String pharmacistLicenseNo, String paymentMethod, String paymentStatus, BigDecimal codAmountToCollect, BigDecimal codCollectedAmount, BigDecimal codShortageAmount, String codCollectorId, Instant codCollectedAt, String cancellationReason, String cancelledBy, Instant cancelledAt, String deliveryModel, String logisticsProvider, String consignmentNumber, String sellerId, String deliveryAddressJson, Integer deliverySlaMins, String deliveryOtpHash, String deliveryOtpSalt, Boolean deliveryOtpConsumed, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.pricingQuoteId = pricingQuoteId;
        this.orderType = orderType;
        this.orderStatus = orderStatus;
        this.subtotalAmount = subtotalAmount;
        this.totalAmount = totalAmount;
        this.taxAmount = taxAmount;
        this.deliveryFee = deliveryFee;
        this.coldChainFee = coldChainFee;
        this.discountAmount = discountAmount;
        this.prescriptionRequired = prescriptionRequired;
        this.prescriptionId = prescriptionId;
        this.pharmacistLicenseNo = pharmacistLicenseNo;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = paymentStatus;
        this.codAmountToCollect = codAmountToCollect;
        this.codCollectedAmount = codCollectedAmount;
        this.codShortageAmount = codShortageAmount;
        this.codCollectorId = codCollectorId;
        this.codCollectedAt = codCollectedAt;
        this.cancellationReason = cancellationReason;
        this.cancelledBy = cancelledBy;
        this.cancelledAt = cancelledAt;
        this.deliveryModel = deliveryModel;
        this.logisticsProvider = logisticsProvider;
        this.consignmentNumber = consignmentNumber;
        this.sellerId = sellerId;
        this.deliveryAddressJson = deliveryAddressJson;
        this.deliverySlaMins = deliverySlaMins;
        this.deliveryOtpHash = deliveryOtpHash;
        this.deliveryOtpSalt = deliveryOtpSalt;
        this.deliveryOtpConsumed = deliveryOtpConsumed;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static CustomerOrderBuilder builder() {
        return new CustomerOrderBuilder();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
        if (this.updatedAt == null) this.updatedAt = Instant.now();
        if (this.orderStatus == null) this.orderStatus = "CREATED";
        if (this.paymentStatus == null) this.paymentStatus = "PENDING";
        if (this.deliverySlaMins == null) this.deliverySlaMins = 15;
        if (this.prescriptionRequired == null) this.prescriptionRequired = false;
        if (this.deliveryFee == null) this.deliveryFee = BigDecimal.ZERO;
        if (this.taxAmount == null) this.taxAmount = BigDecimal.ZERO;
        if (this.deliveryOtpConsumed == null) this.deliveryOtpConsumed = false;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public static class CustomerOrderBuilder {
        private UUID id;
        private UUID customerId;
        private String pricingQuoteId;
        private String orderType;
        private String orderStatus;
        private BigDecimal subtotalAmount;
        private BigDecimal totalAmount;
        private BigDecimal taxAmount;
        private BigDecimal deliveryFee;
        private BigDecimal coldChainFee;
        private BigDecimal discountAmount;
        private Boolean prescriptionRequired;
        private UUID prescriptionId;
        private String pharmacistLicenseNo;
        private String paymentMethod;
        private String paymentStatus;
        private BigDecimal codAmountToCollect;
        private BigDecimal codCollectedAmount;
        private BigDecimal codShortageAmount;
        private String codCollectorId;
        private Instant codCollectedAt;
        private String cancellationReason;
        private String cancelledBy;
        private Instant cancelledAt;
        private String deliveryModel;
        private String logisticsProvider;
        private String consignmentNumber;
        private String sellerId;
        private String deliveryAddressJson;
        private Integer deliverySlaMins;
        private String deliveryOtpHash;
        private String deliveryOtpSalt;
        private Boolean deliveryOtpConsumed;
        private Instant createdAt;
        private Instant updatedAt;

        public CustomerOrderBuilder id(UUID id) { this.id = id; return this; }
        public CustomerOrderBuilder customerId(UUID customerId) { this.customerId = customerId; return this; }
        public CustomerOrderBuilder pricingQuoteId(String pricingQuoteId) { this.pricingQuoteId = pricingQuoteId; return this; }
        public CustomerOrderBuilder orderType(String orderType) { this.orderType = orderType; return this; }
        public CustomerOrderBuilder orderStatus(String orderStatus) { this.orderStatus = orderStatus; return this; }
        public CustomerOrderBuilder subtotalAmount(BigDecimal subtotalAmount) { this.subtotalAmount = subtotalAmount; return this; }
        public CustomerOrderBuilder totalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; return this; }
        public CustomerOrderBuilder taxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; return this; }
        public CustomerOrderBuilder deliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; return this; }
        public CustomerOrderBuilder coldChainFee(BigDecimal coldChainFee) { this.coldChainFee = coldChainFee; return this; }
        public CustomerOrderBuilder discountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; return this; }
        public CustomerOrderBuilder prescriptionRequired(Boolean prescriptionRequired) { this.prescriptionRequired = prescriptionRequired; return this; }
        public CustomerOrderBuilder prescriptionId(UUID prescriptionId) { this.prescriptionId = prescriptionId; return this; }
        public CustomerOrderBuilder pharmacistLicenseNo(String pharmacistLicenseNo) { this.pharmacistLicenseNo = pharmacistLicenseNo; return this; }
        public CustomerOrderBuilder paymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; return this; }
        public CustomerOrderBuilder paymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; return this; }
        public CustomerOrderBuilder codAmountToCollect(BigDecimal codAmountToCollect) { this.codAmountToCollect = codAmountToCollect; return this; }
        public CustomerOrderBuilder codCollectedAmount(BigDecimal codCollectedAmount) { this.codCollectedAmount = codCollectedAmount; return this; }
        public CustomerOrderBuilder codShortageAmount(BigDecimal codShortageAmount) { this.codShortageAmount = codShortageAmount; return this; }
        public CustomerOrderBuilder codCollectorId(String codCollectorId) { this.codCollectorId = codCollectorId; return this; }
        public CustomerOrderBuilder codCollectedAt(Instant codCollectedAt) { this.codCollectedAt = codCollectedAt; return this; }
        public CustomerOrderBuilder cancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; return this; }
        public CustomerOrderBuilder cancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; return this; }
        public CustomerOrderBuilder cancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; return this; }
        public CustomerOrderBuilder deliveryModel(String deliveryModel) { this.deliveryModel = deliveryModel; return this; }
        public CustomerOrderBuilder logisticsProvider(String logisticsProvider) { this.logisticsProvider = logisticsProvider; return this; }
        public CustomerOrderBuilder consignmentNumber(String consignmentNumber) { this.consignmentNumber = consignmentNumber; return this; }
        public CustomerOrderBuilder sellerId(String sellerId) { this.sellerId = sellerId; return this; }
        public CustomerOrderBuilder deliveryAddressJson(String deliveryAddressJson) { this.deliveryAddressJson = deliveryAddressJson; return this; }
        public CustomerOrderBuilder deliverySlaMins(Integer deliverySlaMins) { this.deliverySlaMins = deliverySlaMins; return this; }
        public CustomerOrderBuilder deliveryOtpHash(String deliveryOtpHash) { this.deliveryOtpHash = deliveryOtpHash; return this; }
        public CustomerOrderBuilder deliveryOtpSalt(String deliveryOtpSalt) { this.deliveryOtpSalt = deliveryOtpSalt; return this; }
        public CustomerOrderBuilder deliveryOtpConsumed(Boolean deliveryOtpConsumed) { this.deliveryOtpConsumed = deliveryOtpConsumed; return this; }
        public CustomerOrderBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public CustomerOrderBuilder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public CustomerOrder build() {
            return new CustomerOrder(id, customerId, pricingQuoteId, orderType, orderStatus, subtotalAmount, totalAmount, taxAmount, deliveryFee, coldChainFee, discountAmount, prescriptionRequired, prescriptionId, pharmacistLicenseNo, paymentMethod, paymentStatus, codAmountToCollect, codCollectedAmount, codShortageAmount, codCollectorId, codCollectedAt, cancellationReason, cancelledBy, cancelledAt, deliveryModel, logisticsProvider, consignmentNumber, sellerId, deliveryAddressJson, deliverySlaMins, deliveryOtpHash, deliveryOtpSalt, deliveryOtpConsumed, createdAt, updatedAt);
        }
    }
}
