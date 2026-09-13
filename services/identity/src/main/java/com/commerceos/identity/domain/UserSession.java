package com.commerceos.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_sessions", indexes = {
    @Index(name = "idx_session_user_id", columnList = "userId"),
    @Index(name = "idx_session_refresh_token_hash", columnList = "refreshTokenHash", unique = true)
})
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 255)
    private String refreshTokenHash;

    @Column(nullable = false, length = 128)
    private String ipAddress;

    @Column(nullable = false, length = 255)
    private String userAgent;

    @Column(nullable = false, length = 64)
    private String deviceId;

    @Column(length = 128)
    private String deviceFingerprintHash;

    private UUID activeTenantId;

    @Column(nullable = false, length = 32)
    private String sessionStatus;

    @Column(nullable = false)
    private Integer tokenGeneration;

    @Column(nullable = false)
    private Boolean isRevoked;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastActiveAt;

    public UserSession() {}

    public UserSession(UUID id, UUID userId, String refreshTokenHash, String ipAddress, String userAgent, String deviceId, String deviceFingerprintHash, UUID activeTenantId, String sessionStatus, Integer tokenGeneration, Boolean isRevoked, Instant expiresAt, Instant createdAt, Instant lastActiveAt) {
        this.id = id;
        this.userId = userId;
        this.refreshTokenHash = refreshTokenHash;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.deviceId = deviceId;
        this.deviceFingerprintHash = deviceFingerprintHash;
        this.activeTenantId = activeTenantId;
        this.sessionStatus = sessionStatus;
        this.tokenGeneration = tokenGeneration;
        this.isRevoked = isRevoked;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.lastActiveAt = lastActiveAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.lastActiveAt = Instant.now();
        if (this.isRevoked == null) this.isRevoked = false;
        if (this.sessionStatus == null) this.sessionStatus = "ACTIVE";
        if (this.tokenGeneration == null) this.tokenGeneration = 1;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getRefreshTokenHash() { return refreshTokenHash; }
    public void setRefreshTokenHash(String refreshTokenHash) { this.refreshTokenHash = refreshTokenHash; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getDeviceFingerprintHash() { return deviceFingerprintHash; }
    public void setDeviceFingerprintHash(String deviceFingerprintHash) { this.deviceFingerprintHash = deviceFingerprintHash; }

    public UUID getActiveTenantId() { return activeTenantId; }
    public void setActiveTenantId(UUID activeTenantId) { this.activeTenantId = activeTenantId; }

    public String getSessionStatus() { return sessionStatus; }
    public void setSessionStatus(String sessionStatus) { this.sessionStatus = sessionStatus; }

    public Integer getTokenGeneration() { return tokenGeneration; }
    public void setTokenGeneration(Integer tokenGeneration) { this.tokenGeneration = tokenGeneration; }

    public Boolean getIsRevoked() { return isRevoked; }
    public void setIsRevoked(Boolean isRevoked) { this.isRevoked = isRevoked; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    public static UserSessionBuilder builder() {
        return new UserSessionBuilder();
    }

    public static class UserSessionBuilder {
        private UUID id;
        private UUID userId;
        private String refreshTokenHash;
        private String ipAddress;
        private String userAgent;
        private String deviceId;
        private String deviceFingerprintHash;
        private UUID activeTenantId;
        private String sessionStatus;
        private Integer tokenGeneration;
        private Boolean isRevoked;
        private Instant expiresAt;
        private Instant createdAt;
        private Instant lastActiveAt;

        UserSessionBuilder() {}

        public UserSessionBuilder id(UUID id) { this.id = id; return this; }
        public UserSessionBuilder userId(UUID userId) { this.userId = userId; return this; }
        public UserSessionBuilder refreshTokenHash(String refreshTokenHash) { this.refreshTokenHash = refreshTokenHash; return this; }
        public UserSessionBuilder ipAddress(String ipAddress) { this.ipAddress = ipAddress; return this; }
        public UserSessionBuilder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        public UserSessionBuilder deviceId(String deviceId) { this.deviceId = deviceId; return this; }
        public UserSessionBuilder deviceFingerprintHash(String deviceFingerprintHash) { this.deviceFingerprintHash = deviceFingerprintHash; return this; }
        public UserSessionBuilder activeTenantId(UUID activeTenantId) { this.activeTenantId = activeTenantId; return this; }
        public UserSessionBuilder sessionStatus(String sessionStatus) { this.sessionStatus = sessionStatus; return this; }
        public UserSessionBuilder tokenGeneration(Integer tokenGeneration) { this.tokenGeneration = tokenGeneration; return this; }
        public UserSessionBuilder isRevoked(Boolean isRevoked) { this.isRevoked = isRevoked; return this; }
        public UserSessionBuilder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }
        public UserSessionBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public UserSessionBuilder lastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; return this; }

        public UserSession build() {
            return new UserSession(id, userId, refreshTokenHash, ipAddress, userAgent, deviceId, deviceFingerprintHash, activeTenantId, sessionStatus, tokenGeneration, isRevoked, expiresAt, createdAt, lastActiveAt);
        }
    }
}
