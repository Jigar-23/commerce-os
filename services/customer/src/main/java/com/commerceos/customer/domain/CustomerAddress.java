package com.commerceos.customer.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "customer_addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    @JsonIgnore
    private CustomerProfile customerProfile;

    @Column(nullable = false, length = 32)
    private String addressType; // HOME, WORK, PHARMACY_PICKUP

    @Column(nullable = false, length = 128)
    private String recipientName;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(nullable = false, length = 255)
    private String streetAddress;

    @Column(nullable = false, length = 128)
    private String city;

    @Column(nullable = false, length = 128)
    private String state;

    @Column(nullable = false, length = 32)
    private String postalCode;

    @Column(length = 2)
    private String countryCode;

    private Double latitude;
    private Double longitude;

    @Column(nullable = false)
    private Boolean isDefault;

    @Column(nullable = false)
    private Boolean isDefaultShipping;

    @Column(nullable = false)
    private Boolean isDefaultBilling;

    @Column(length = 255)
    private String deliveryNotes;

    @PrePersist
    protected void onCreate() {
        if (this.isDefault == null) this.isDefault = false;
        if (this.isDefaultShipping == null) this.isDefaultShipping = this.isDefault;
        if (this.isDefaultBilling == null) this.isDefaultBilling = false;
        if (this.countryCode == null) this.countryCode = "US";
    }
}
