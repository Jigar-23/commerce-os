package com.commerceos.cart.client;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CatalogServiceClient {

    // Authoritative Canonical Product Reference Cache
    private static final Map<String, ProductMetadata> CATALOG_REGISTRY = new ConcurrentHashMap<>();

    static {
        CATALOG_REGISTRY.put("SKU-AUG-625", ProductMetadata.builder()
                .sku("SKU-AUG-625")
                .name("Augmentin 625 Duo Tablet")
                .brand("GSK")
                .packSize("10 tablets")
                .unitPrice(new BigDecimal("180.50"))
                .mrp(new BigDecimal("205.00"))
                .prescriptionRequired(true)
                .coldChain(false)
                .image("https://assets.commerceos.io/medicines/augmentin.png")
                .build());

        CATALOG_REGISTRY.put("SKU-DOLO-650", ProductMetadata.builder()
                .sku("SKU-DOLO-650")
                .name("Dolo 650 Tablet")
                .brand("Micro Labs")
                .packSize("15 tablets")
                .unitPrice(new BigDecimal("30.00"))
                .mrp(new BigDecimal("33.50"))
                .prescriptionRequired(false)
                .coldChain(false)
                .image("https://assets.commerceos.io/medicines/dolo.png")
                .build());

        CATALOG_REGISTRY.put("SKU-LANT-100", ProductMetadata.builder()
                .sku("SKU-LANT-100")
                .name("Lantus 100IU/ml Cartridge")
                .brand("Sanofi")
                .packSize("3ml cartridge")
                .unitPrice(new BigDecimal("650.00"))
                .mrp(new BigDecimal("720.00"))
                .prescriptionRequired(true)
                .coldChain(true)
                .image("https://assets.commerceos.io/medicines/lantus.png")
                .build());
    }

    public Optional<ProductMetadata> getProductBySku(String sku) {
        if (sku == null) return Optional.empty();
        return Optional.ofNullable(CATALOG_REGISTRY.get(sku.trim().toUpperCase()));
    }

    @Data
    @Builder
    public static class ProductMetadata {
        private String sku;
        private String name;
        private String brand;
        private String packSize;
        private BigDecimal unitPrice;
        private BigDecimal mrp;
        private boolean prescriptionRequired;
        private boolean coldChain;
        private String image;
    }
}
