package com.commerceos.cart.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "cart_idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(CartIdempotencyId.class)
public class CartIdempotencyEntity {

    @Id
    @Column(nullable = false, length = 64)
    private String customerId;

    @Id
    @Column(nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_payload_hash", length = 64)
    private String requestPayloadHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
