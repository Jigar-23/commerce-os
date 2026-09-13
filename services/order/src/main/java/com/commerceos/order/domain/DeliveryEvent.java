package com.commerceos.order.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "delivery_events", uniqueConstraints = {
    @UniqueConstraint(name = "uk_del_seq", columnNames = {"deliveryId", "eventSequence"})
}, indexes = {
    @Index(name = "idx_del_evt_order_id", columnList = "orderId"),
    @Index(name = "idx_del_evt_del_id", columnList = "deliveryId"),
    @Index(name = "idx_del_evt_seq", columnList = "eventSequence")
})
public class DeliveryEvent {

    @Id
    @Column(length = 64)
    private String eventId;

    @Column(nullable = false, length = 64)
    private String deliveryId;

    @Column(nullable = false, length = 64)
    private String orderId;

    @Column(nullable = false)
    private Long eventSequence;

    @Column(nullable = false, length = 64)
    private String eventType;

    private String actorId;

    @Column(nullable = false)
    private Long serverTimestamp;

    @Column(columnDefinition = "TEXT")
    private String payloadJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public DeliveryEvent() {}

    public DeliveryEvent(String eventId, String deliveryId, String orderId, Long eventSequence, String eventType, String actorId, Long serverTimestamp, String payloadJson, Instant createdAt) {
        this.eventId = eventId;
        this.deliveryId = deliveryId;
        this.orderId = orderId;
        this.eventSequence = eventSequence;
        this.eventType = eventType;
        this.actorId = actorId;
        this.serverTimestamp = serverTimestamp;
        this.payloadJson = payloadJson;
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getDeliveryId() { return deliveryId; }
    public void setDeliveryId(String deliveryId) { this.deliveryId = deliveryId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public Long getEventSequence() { return eventSequence; }
    public void setEventSequence(Long eventSequence) { this.eventSequence = eventSequence; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public Long getServerTimestamp() { return serverTimestamp; }
    public void setServerTimestamp(Long serverTimestamp) { this.serverTimestamp = serverTimestamp; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static DeliveryEventBuilder builder() {
        return new DeliveryEventBuilder();
    }

    public static class DeliveryEventBuilder {
        private String eventId;
        private String deliveryId;
        private String orderId;
        private Long eventSequence;
        private String eventType;
        private String actorId;
        private Long serverTimestamp;
        private String payloadJson;
        private Instant createdAt;

        DeliveryEventBuilder() {}

        public DeliveryEventBuilder eventId(String eventId) { this.eventId = eventId; return this; }
        public DeliveryEventBuilder deliveryId(String deliveryId) { this.deliveryId = deliveryId; return this; }
        public DeliveryEventBuilder orderId(String orderId) { this.orderId = orderId; return this; }
        public DeliveryEventBuilder eventSequence(Long eventSequence) { this.eventSequence = eventSequence; return this; }
        public DeliveryEventBuilder eventType(String eventType) { this.eventType = eventType; return this; }
        public DeliveryEventBuilder actorId(String actorId) { this.actorId = actorId; return this; }
        public DeliveryEventBuilder serverTimestamp(Long serverTimestamp) { this.serverTimestamp = serverTimestamp; return this; }
        public DeliveryEventBuilder payloadJson(String payloadJson) { this.payloadJson = payloadJson; return this; }
        public DeliveryEventBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public DeliveryEvent build() {
            return new DeliveryEvent(eventId, deliveryId, orderId, eventSequence, eventType, actorId, serverTimestamp, payloadJson, createdAt);
        }
    }
}
