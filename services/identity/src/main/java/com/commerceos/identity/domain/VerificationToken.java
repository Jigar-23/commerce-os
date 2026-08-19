package com.commerceos.identity.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_tokens", indexes = {
    @Index(name = "idx_verification_token_code", columnList = "tokenCode", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 64)
    private String tokenType; // EMAIL_VERIFICATION, PHONE_OTP, PASSWORD_RESET

    @Column(nullable = false, unique = true, length = 128)
    private String tokenCode;

    @Column(nullable = false)
    private Boolean isUsed;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.isUsed == null) this.isUsed = false;
    }
}
