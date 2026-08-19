package com.commerceos.order.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user_accounts", indexes = {
    @Index(name = "idx_user_email", columnList = "email", unique = true),
    @Index(name = "idx_user_phone", columnList = "phone", unique = true)
})
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(unique = true, length = 32)
    private String phone;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 128)
    private String fullName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    private Set<String> roles;

    @Column(nullable = false, length = 32)
    private String accountStatus;

    @Column(length = 64)
    private String vehicleNumber;

    @Column(nullable = false)
    private Boolean emailVerified;

    @Column(nullable = false)
    private Boolean phoneVerified;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public UserAccount() {}

    public UserAccount(UUID id, String email, String phone, String passwordHash, String fullName, Set<String> roles, String accountStatus, String vehicleNumber, Boolean emailVerified, Boolean phoneVerified, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.roles = roles;
        this.accountStatus = accountStatus;
        this.vehicleNumber = vehicleNumber;
        this.emailVerified = emailVerified;
        this.phoneVerified = phoneVerified;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        if (this.accountStatus == null) this.accountStatus = "ACTIVE";
        if (this.emailVerified == null) this.emailVerified = false;
        if (this.phoneVerified == null) this.phoneVerified = false;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }

    public String getAccountStatus() { return accountStatus; }
    public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }

    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }

    public Boolean getEmailVerified() { return emailVerified; }
    public void setEmailVerified(Boolean emailVerified) { this.emailVerified = emailVerified; }

    public Boolean getPhoneVerified() { return phoneVerified; }
    public void setPhoneVerified(Boolean phoneVerified) { this.phoneVerified = phoneVerified; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static UserAccountBuilder builder() {
        return new UserAccountBuilder();
    }

    public static class UserAccountBuilder {
        private UUID id;
        private String email;
        private String phone;
        private String passwordHash;
        private String fullName;
        private Set<String> roles;
        private String accountStatus;
        private String vehicleNumber;
        private Boolean emailVerified;
        private Boolean phoneVerified;
        private Instant createdAt;
        private Instant updatedAt;

        UserAccountBuilder() {}

        public UserAccountBuilder id(UUID id) { this.id = id; return this; }
        public UserAccountBuilder email(String email) { this.email = email; return this; }
        public UserAccountBuilder phone(String phone) { this.phone = phone; return this; }
        public UserAccountBuilder passwordHash(String passwordHash) { this.passwordHash = passwordHash; return this; }
        public UserAccountBuilder fullName(String fullName) { this.fullName = fullName; return this; }
        public UserAccountBuilder roles(Set<String> roles) { this.roles = roles; return this; }
        public UserAccountBuilder accountStatus(String accountStatus) { this.accountStatus = accountStatus; return this; }
        public UserAccountBuilder vehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; return this; }
        public UserAccountBuilder emailVerified(Boolean emailVerified) { this.emailVerified = emailVerified; return this; }
        public UserAccountBuilder phoneVerified(Boolean phoneVerified) { this.phoneVerified = phoneVerified; return this; }
        public UserAccountBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public UserAccountBuilder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public UserAccount build() {
            return new UserAccount(id, email, phone, passwordHash, fullName, roles, accountStatus, vehicleNumber, emailVerified, phoneVerified, createdAt, updatedAt);
        }
    }
}
