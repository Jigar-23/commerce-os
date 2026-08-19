package com.commerceos.identity.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_audit_logs", indexes = {
    @Index(name = "idx_audit_user_id", columnList = "userId"),
    @Index(name = "idx_audit_event_type", columnList = "eventType")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;

    @Column(nullable = false, length = 64)
    private String eventType; // USER_REGISTERED, USER_LOGGED_IN, PASSWORD_CHANGED, SESSION_REVOKED, MFA_ENABLED, PASSKEY_REGISTERED, ACCOUNT_DELETED

    @Column(nullable = false, length = 128)
    private String ipAddress;

    @Column(nullable = false, length = 255)
    private String userAgent;

    @Column(nullable = false, length = 32)
    private String status; // SUCCESS, FAILURE

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
