package com.commerceos.identity.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "passkey_credentials", indexes = {
    @Index(name = "idx_passkey_credential_id", columnList = "credentialId", unique = true),
    @Index(name = "idx_passkey_user_id", columnList = "userId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasskeyCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true, length = 255)
    private String credentialId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String publicKeyBase64;

    @Column(nullable = false)
    private Long signCount;

    @Column(length = 128)
    private String deviceName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastUsedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.lastUsedAt = Instant.now();
        if (this.signCount == null) this.signCount = 0L;
    }
}
