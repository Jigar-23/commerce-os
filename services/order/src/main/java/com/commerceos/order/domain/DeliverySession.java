package com.commerceos.order.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Authoritative Delivery Session entity persisted in PostgreSQL.
 * Note: Never persists plaintext delivery OTP at rest; stores salted SHA-256 secretOtpHash.
 */
@Entity
@Table(name = "delivery_sessions", indexes = {
        @Index(name = "idx_del_sess_order_id", columnList = "orderId", unique = true),
        @Index(name = "idx_del_sess_rider_id", columnList = "riderId"),
        @Index(name = "idx_del_sess_state", columnList = "state")
})
@Getter
@Setter
public class DeliverySession {

    @Id
    @Column(nullable = false, length = 64)
    private String deliveryId;

    @Version
    private Long version;

    @Column(nullable = false, length = 64)
    private String orderId;

    @Column(length = 64)
    private String riderId;

    private String riderName;
    private String riderPhone;
    private String riderVehicle;

    @Column(nullable = false, length = 64)
    private String customerId;

    private String customerName;
    private String customerPhone;
    private String customerAddress;
    private Double customerLat;
    private Double customerLng;

    private String merchantName;
    private String merchantAddress;
    private Double merchantLat;
    private Double merchantLng;

    @Column(nullable = false, length = 64)
    private String state;

    @Column(length = 128)
    private String secretOtpHash;

    private Integer otpAttemptsLeft;
    private Boolean otpVerified;

    private Boolean isCod;

    @Column(precision = 12, scale = 2)
    private BigDecimal codAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal codCollectedAmount;

    private Boolean codReconciled;

    private Double latestLatitude;
    private Double latestLongitude;
    private Float latestSpeedKmh;
    private Float latestHeading;
    private Float latestAccuracyMeters;
    private Long latestSequenceNumber;
    private Long latestServerTimestamp;
    private Long latestClientTimestamp;
    private Boolean isStale;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public DeliverySession() {}

    public DeliverySession(String deliveryId, Long version, String orderId, String riderId, String riderName, String riderPhone, String riderVehicle, String customerId, String customerName, String customerPhone, String customerAddress, Double customerLat, Double customerLng, String merchantName, String merchantAddress, Double merchantLat, Double merchantLng, String state, String secretOtpHash, Integer otpAttemptsLeft, Boolean otpVerified, Boolean isCod, BigDecimal codAmount, BigDecimal codCollectedAmount, Boolean codReconciled, Double latestLatitude, Double latestLongitude, Float latestSpeedKmh, Float latestHeading, Float latestAccuracyMeters, Long latestSequenceNumber, Long latestServerTimestamp, Long latestClientTimestamp, Boolean isStale, Instant createdAt, Instant updatedAt) {
        this.deliveryId = deliveryId;
        this.version = version;
        this.orderId = orderId;
        this.riderId = riderId;
        this.riderName = riderName;
        this.riderPhone = riderPhone;
        this.riderVehicle = riderVehicle;
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.customerAddress = customerAddress;
        this.customerLat = customerLat;
        this.customerLng = customerLng;
        this.merchantName = merchantName;
        this.merchantAddress = merchantAddress;
        this.merchantLat = merchantLat;
        this.merchantLng = merchantLng;
        this.state = state;
        this.secretOtpHash = secretOtpHash;
        this.otpAttemptsLeft = otpAttemptsLeft;
        this.otpVerified = otpVerified;
        this.isCod = isCod;
        this.codAmount = codAmount;
        this.codCollectedAmount = codCollectedAmount;
        this.codReconciled = codReconciled;
        this.latestLatitude = latestLatitude;
        this.latestLongitude = latestLongitude;
        this.latestSpeedKmh = latestSpeedKmh;
        this.latestHeading = latestHeading;
        this.latestAccuracyMeters = latestAccuracyMeters;
        this.latestSequenceNumber = latestSequenceNumber;
        this.latestServerTimestamp = latestServerTimestamp;
        this.latestClientTimestamp = latestClientTimestamp;
        this.isStale = isStale;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static DeliverySessionBuilder builder() {
        return new DeliverySessionBuilder();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
        if (this.updatedAt == null) this.updatedAt = Instant.now();
        if (this.version == null) this.version = 0L;
        if (this.otpAttemptsLeft == null) this.otpAttemptsLeft = 3;
        if (this.otpVerified == null) this.otpVerified = false;
        if (this.codReconciled == null) this.codReconciled = false;
        if (this.isStale == null) this.isStale = false;
        if (this.latestSequenceNumber == null) this.latestSequenceNumber = 0L;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public static class DeliverySessionBuilder {
        private String deliveryId;
        private Long version;
        private String orderId;
        private String riderId;
        private String riderName;
        private String riderPhone;
        private String riderVehicle;
        private String customerId;
        private String customerName;
        private String customerPhone;
        private String customerAddress;
        private Double customerLat;
        private Double customerLng;
        private String merchantName;
        private String merchantAddress;
        private Double merchantLat;
        private Double merchantLng;
        private String state;
        private String secretOtpHash;
        private Integer otpAttemptsLeft;
        private Boolean otpVerified;
        private Boolean isCod;
        private BigDecimal codAmount;
        private BigDecimal codCollectedAmount;
        private Boolean codReconciled;
        private Double latestLatitude;
        private Double latestLongitude;
        private Float latestSpeedKmh;
        private Float latestHeading;
        private Float latestAccuracyMeters;
        private Long latestSequenceNumber;
        private Long latestServerTimestamp;
        private Long latestClientTimestamp;
        private Boolean isStale;
        private Instant createdAt;
        private Instant updatedAt;

        public DeliverySessionBuilder deliveryId(String deliveryId) { this.deliveryId = deliveryId; return this; }
        public DeliverySessionBuilder version(Long version) { this.version = version; return this; }
        public DeliverySessionBuilder orderId(String orderId) { this.orderId = orderId; return this; }
        public DeliverySessionBuilder riderId(String riderId) { this.riderId = riderId; return this; }
        public DeliverySessionBuilder riderName(String riderName) { this.riderName = riderName; return this; }
        public DeliverySessionBuilder riderPhone(String riderPhone) { this.riderPhone = riderPhone; return this; }
        public DeliverySessionBuilder riderVehicle(String riderVehicle) { this.riderVehicle = riderVehicle; return this; }
        public DeliverySessionBuilder customerId(String customerId) { this.customerId = customerId; return this; }
        public DeliverySessionBuilder customerName(String customerName) { this.customerName = customerName; return this; }
        public DeliverySessionBuilder customerPhone(String customerPhone) { this.customerPhone = customerPhone; return this; }
        public DeliverySessionBuilder customerAddress(String customerAddress) { this.customerAddress = customerAddress; return this; }
        public DeliverySessionBuilder customerLat(Double customerLat) { this.customerLat = customerLat; return this; }
        public DeliverySessionBuilder customerLng(Double customerLng) { this.customerLng = customerLng; return this; }
        public DeliverySessionBuilder merchantName(String merchantName) { this.merchantName = merchantName; return this; }
        public DeliverySessionBuilder merchantAddress(String merchantAddress) { this.merchantAddress = merchantAddress; return this; }
        public DeliverySessionBuilder merchantLat(Double merchantLat) { this.merchantLat = merchantLat; return this; }
        public DeliverySessionBuilder merchantLng(Double merchantLng) { this.merchantLng = merchantLng; return this; }
        public DeliverySessionBuilder state(String state) { this.state = state; return this; }
        public DeliverySessionBuilder secretOtpHash(String secretOtpHash) { this.secretOtpHash = secretOtpHash; return this; }
        public DeliverySessionBuilder otpAttemptsLeft(Integer otpAttemptsLeft) { this.otpAttemptsLeft = otpAttemptsLeft; return this; }
        public DeliverySessionBuilder otpVerified(Boolean otpVerified) { this.otpVerified = otpVerified; return this; }
        public DeliverySessionBuilder isCod(Boolean isCod) { this.isCod = isCod; return this; }
        public DeliverySessionBuilder codAmount(BigDecimal codAmount) { this.codAmount = codAmount; return this; }
        public DeliverySessionBuilder codCollectedAmount(BigDecimal codCollectedAmount) { this.codCollectedAmount = codCollectedAmount; return this; }
        public DeliverySessionBuilder codReconciled(Boolean codReconciled) { this.codReconciled = codReconciled; return this; }
        public DeliverySessionBuilder latestLatitude(Double latestLatitude) { this.latestLatitude = latestLatitude; return this; }
        public DeliverySessionBuilder latestLongitude(Double latestLongitude) { this.latestLongitude = latestLongitude; return this; }
        public DeliverySessionBuilder latestSpeedKmh(Float latestSpeedKmh) { this.latestSpeedKmh = latestSpeedKmh; return this; }
        public DeliverySessionBuilder latestHeading(Float latestHeading) { this.latestHeading = latestHeading; return this; }
        public DeliverySessionBuilder latestAccuracyMeters(Float latestAccuracyMeters) { this.latestAccuracyMeters = latestAccuracyMeters; return this; }
        public DeliverySessionBuilder latestSequenceNumber(Long latestSequenceNumber) { this.latestSequenceNumber = latestSequenceNumber; return this; }
        public DeliverySessionBuilder latestServerTimestamp(Long latestServerTimestamp) { this.latestServerTimestamp = latestServerTimestamp; return this; }
        public DeliverySessionBuilder latestClientTimestamp(Long latestClientTimestamp) { this.latestClientTimestamp = latestClientTimestamp; return this; }
        public DeliverySessionBuilder isStale(Boolean isStale) { this.isStale = isStale; return this; }
        public DeliverySessionBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public DeliverySessionBuilder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public DeliverySession build() {
            return new DeliverySession(deliveryId, version, orderId, riderId, riderName, riderPhone, riderVehicle, customerId, customerName, customerPhone, customerAddress, customerLat, customerLng, merchantName, merchantAddress, merchantLat, merchantLng, state, secretOtpHash, otpAttemptsLeft, otpVerified, isCod, codAmount, codCollectedAmount, codReconciled, latestLatitude, latestLongitude, latestSpeedKmh, latestHeading, latestAccuracyMeters, latestSequenceNumber, latestServerTimestamp, latestClientTimestamp, isStale, createdAt, updatedAt);
        }
    }
}
