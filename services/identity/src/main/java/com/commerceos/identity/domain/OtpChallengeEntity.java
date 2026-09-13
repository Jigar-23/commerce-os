package com.commerceos.identity.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "otp_challenges", indexes = {
        @Index(name = "idx_otp_challenge_id", columnList = "challengeId", unique = true),
        @Index(name = "idx_otp_phone_hash", columnList = "phoneHash"),
        @Index(name = "idx_otp_ip_address", columnList = "ipAddress")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpChallengeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String challengeId;

    @Column(nullable = false, length = 128)
    private String phoneHash;

    @Column(nullable = false, length = 128)
    private String otpHash;

    @Column(length = 128)
    private String providerSessionId;

    @Column(nullable = false)
    private Integer attemptsLeft;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant resendAvailableAt;

    @Column(nullable = false, length = 32)
    private String status; // ACTIVE, VERIFIED, CONSUMED, LOCKED, EXPIRED

    @Column(length = 64)
    private String ipAddress;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant verifiedAt;
}
