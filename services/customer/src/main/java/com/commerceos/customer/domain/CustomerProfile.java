package com.commerceos.customer.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "customer_profiles", indexes = {
    @Index(name = "idx_customer_identity_id", columnList = "identityId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID identityId; // Decoupled Identity ID from DOM-IAM

    @Column(nullable = false, length = 128)
    private String fullName;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 32)
    private String phone;

    private Integer age;

    @Column(length = 16)
    private String gender; // MALE, FEMALE, OTHER

    @Column(length = 8)
    private String bloodGroup;

    @Column(columnDefinition = "TEXT")
    private String knownAllergiesJson;

    @Column(columnDefinition = "TEXT")
    private String chronicConditionsJson;

    @Column(columnDefinition = "TEXT")
    private String communicationPreferencesJson;

    @Column(nullable = false, length = 32)
    private String status; // ACTIVE, SUSPENDED, ERASED_GDPR

    @Version
    private Long version;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, mappedBy = "customerProfile")
    private List<CustomerAddress> addresses;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        if (this.status == null) this.status = "ACTIVE";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
