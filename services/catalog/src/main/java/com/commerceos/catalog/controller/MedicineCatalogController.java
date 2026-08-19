package com.commerceos.catalog.controller;

import com.commerceos.catalog.domain.MedicineProduct;
import com.commerceos.catalog.repository.MedicineProductRepository;
import com.commerceos.catalog.security.JwtAuthValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog/medicines")
@RequiredArgsConstructor
public class MedicineCatalogController {

    private final MedicineProductRepository medicineProductRepository;
    private final JwtAuthValidator jwtAuthValidator;

    private boolean isAuthorizedCatalogAdmin(String authHeader) {
        Optional<JwtAuthValidator.AuthenticatedPrincipal> principalOpt = jwtAuthValidator.authenticate(authHeader);
        if (principalOpt.isEmpty()) {
            return false;
        }
        return principalOpt.get().hasRole("ROLE_ADMIN") ||
               principalOpt.get().hasRole("ROLE_CATALOG_MANAGER") ||
               principalOpt.get().hasRole("ROLE_SYSTEM");
    }

    @GetMapping
    public ResponseEntity<Page<MedicineProduct>> getMedicines(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(medicineProductRepository.findAll(PageRequest.of(page, size)));
    }

    @PostMapping
    public ResponseEntity<?> createMedicine(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody MedicineProduct medicine
    ) {
        if (!isAuthorizedCatalogAdmin(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("FORBIDDEN: Catalog mutations strictly require ROLE_ADMIN or ROLE_CATALOG_MANAGER credentials.");
        }
        if (medicine.getId() == null) {
            medicine.setId(UUID.randomUUID());
        }
        MedicineProduct saved = medicineProductRepository.save(medicine);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MedicineProduct> getMedicineById(@PathVariable UUID id) {
        return medicineProductRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public ResponseEntity<Page<MedicineProduct>> searchMedicines(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(medicineProductRepository.searchMedicines(query, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}/substitutes")
    public ResponseEntity<List<MedicineProduct>> getGenericSubstitutes(@PathVariable UUID id) {
        return medicineProductRepository.findById(id)
                .map(product -> ResponseEntity.ok(
                        medicineProductRepository.findSubstitutesBySalts(product.getSaltCompositionsJson(), product.getId())
                ))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/savings-comparison")
    public ResponseEntity<SubstituteSavingsResponse> getSubstituteSavings(@PathVariable UUID id) {
        return medicineProductRepository.findById(id).map(brandMed -> {
            List<MedicineProduct> substitutes = medicineProductRepository.findSubstitutesBySalts(
                    brandMed.getSaltCompositionsJson(), brandMed.getId()
            );

            java.math.BigDecimal brandPrice = brandMed.getDiscountedPrice() != null ? brandMed.getDiscountedPrice() : brandMed.getPrice();
            java.math.BigDecimal cheapestSubstitutePrice = brandPrice;
            String cheapestName = brandMed.getName();

            for (MedicineProduct sub : substitutes) {
                java.math.BigDecimal subPrice = sub.getDiscountedPrice() != null ? sub.getDiscountedPrice() : sub.getPrice();
                if (subPrice.compareTo(cheapestSubstitutePrice) < 0) {
                    cheapestSubstitutePrice = subPrice;
                    cheapestName = sub.getName();
                }
            }

            java.math.BigDecimal savingsAmount = brandPrice.subtract(cheapestSubstitutePrice).max(java.math.BigDecimal.ZERO);
            double savingsPercent = brandPrice.doubleValue() > 0 ? (savingsAmount.doubleValue() / brandPrice.doubleValue()) * 100.0 : 0.0;

            return ResponseEntity.ok(SubstituteSavingsResponse.builder()
                    .brandMedicineId(brandMed.getId().toString())
                    .brandMedicineName(brandMed.getName())
                    .brandPrice(brandPrice)
                    .recommendedGenericName(cheapestName)
                    .recommendedGenericPrice(cheapestSubstitutePrice)
                    .savingsAmount(savingsAmount)
                    .savingsPercentage(Math.round(savingsPercent * 10.0) / 10.0)
                    .substitutesCount(substitutes.size())
                    .build());
        }).orElse(ResponseEntity.notFound().build());
    }

    @lombok.Data
    @lombok.Builder
    public static class SubstituteSavingsResponse {
        private String brandMedicineId;
        private String brandMedicineName;
        private java.math.BigDecimal brandPrice;
        private String recommendedGenericName;
        private java.math.BigDecimal recommendedGenericPrice;
        private java.math.BigDecimal savingsAmount;
        private double savingsPercentage;
        private int substitutesCount;
    }
}
