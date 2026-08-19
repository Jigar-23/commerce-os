package com.commerceos.order.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_stores", indexes = {
    @Index(name = "idx_store_seller_id", columnList = "sellerId")
})
public class MerchantStore {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String sellerId;

    @Column(nullable = false, length = 128)
    private String storeName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String storeAddress;

    @Column(nullable = false, length = 32)
    private String status;

    private Double latitude;
    private Double longitude;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public MerchantStore() {}

    public MerchantStore(String id, String sellerId, String storeName, String storeAddress, String status, Double latitude, Double longitude, Instant createdAt) {
        this.id = id;
        this.sellerId = sellerId;
        this.storeName = storeName;
        this.storeAddress = storeAddress;
        this.status = status;
        this.latitude = latitude;
        this.longitude = longitude;
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.status == null) this.status = "ACTIVE";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }

    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }

    public String getStoreAddress() { return storeAddress; }
    public void setStoreAddress(String storeAddress) { this.storeAddress = storeAddress; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static MerchantStoreBuilder builder() {
        return new MerchantStoreBuilder();
    }

    public static class MerchantStoreBuilder {
        private String id;
        private String sellerId;
        private String storeName;
        private String storeAddress;
        private String status;
        private Double latitude;
        private Double longitude;
        private Instant createdAt;

        MerchantStoreBuilder() {}

        public MerchantStoreBuilder id(String id) { this.id = id; return this; }
        public MerchantStoreBuilder sellerId(String sellerId) { this.sellerId = sellerId; return this; }
        public MerchantStoreBuilder storeName(String storeName) { this.storeName = storeName; return this; }
        public MerchantStoreBuilder storeAddress(String storeAddress) { this.storeAddress = storeAddress; return this; }
        public MerchantStoreBuilder status(String status) { this.status = status; return this; }
        public MerchantStoreBuilder latitude(Double latitude) { this.latitude = latitude; return this; }
        public MerchantStoreBuilder longitude(Double longitude) { this.longitude = longitude; return this; }
        public MerchantStoreBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public MerchantStore build() {
            return new MerchantStore(id, sellerId, storeName, storeAddress, status, latitude, longitude, createdAt);
        }
    }
}
