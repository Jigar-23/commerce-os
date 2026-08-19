package com.commerceos.order.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "sse_tickets", indexes = {
    @Index(name = "idx_sse_ticket_id", columnList = "ticketId", unique = true),
    @Index(name = "idx_sse_ticket_expires_at", columnList = "expiresAt")
})
@Getter
@Setter
public class SseTicketEntity {

    @Id
    @Column(nullable = false, length = 128)
    private String ticketId;

    @Column(nullable = false, length = 2048)
    private String token;

    @Column(length = 128)
    private String targetId;

    @Column(nullable = false)
    private Long expiresAt;

    @Column(nullable = false)
    private Boolean consumed;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public SseTicketEntity() {}

    public SseTicketEntity(String ticketId, String token, String targetId, Long expiresAt) {
        this.ticketId = ticketId;
        this.token = token;
        this.targetId = targetId;
        this.expiresAt = expiresAt;
        this.consumed = false;
        this.createdAt = Instant.now();
    }
}
