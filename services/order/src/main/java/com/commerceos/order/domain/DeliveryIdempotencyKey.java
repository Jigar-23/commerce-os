package com.commerceos.order.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "delivery_idempotency_keys", uniqueConstraints = {
    @UniqueConstraint(name = "uk_del_idem_key", columnNames = {"deliveryId", "idempotencyKey"})
})
public class DeliveryIdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String deliveryId;

    @Column(nullable = false, length = 128)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String resultingState;

    @Column(columnDefinition = "TEXT")
    private String responsePayloadJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public DeliveryIdempotencyKey() {}

    public DeliveryIdempotencyKey(Long id, String deliveryId, String idempotencyKey, String resultingState, String responsePayloadJson, Instant createdAt) {
        this.id = id;
        this.deliveryId = deliveryId;
        this.idempotencyKey = idempotencyKey;
        this.resultingState = resultingState;
        this.responsePayloadJson = responsePayloadJson;
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeliveryId() { return deliveryId; }
    public void setDeliveryId(String deliveryId) { this.deliveryId = deliveryId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getResultingState() { return resultingState; }
    public void setResultingState(String resultingState) { this.resultingState = resultingState; }

    public String getResponsePayloadJson() { return responsePayloadJson; }
    public void setResponsePayloadJson(String responsePayloadJson) { this.responsePayloadJson = responsePayloadJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static DeliveryIdempotencyKeyBuilder builder() {
        return new DeliveryIdempotencyKeyBuilder();
    }

    public static class DeliveryIdempotencyKeyBuilder {
        private Long id;
        private String deliveryId;
        private String idempotencyKey;
        private String resultingState;
        private String responsePayloadJson;
        private Instant createdAt;

        DeliveryIdempotencyKeyBuilder() {}

        public DeliveryIdempotencyKeyBuilder id(Long id) { this.id = id; return this; }
        public DeliveryIdempotencyKeyBuilder deliveryId(String deliveryId) { this.deliveryId = deliveryId; return this; }
        public DeliveryIdempotencyKeyBuilder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }
        public DeliveryIdempotencyKeyBuilder resultingState(String resultingState) { this.resultingState = resultingState; return this; }
        public DeliveryIdempotencyKeyBuilder responsePayloadJson(String responsePayloadJson) { this.responsePayloadJson = responsePayloadJson; return this; }
        public DeliveryIdempotencyKeyBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public DeliveryIdempotencyKey build() {
            return new DeliveryIdempotencyKey(id, deliveryId, idempotencyKey, resultingState, responsePayloadJson, createdAt);
        }
    }
}
