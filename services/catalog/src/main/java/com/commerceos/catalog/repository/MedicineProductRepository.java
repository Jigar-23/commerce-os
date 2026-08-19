package com.commerceos.catalog.repository;

import com.commerceos.catalog.domain.MedicineProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MedicineProductRepository extends JpaRepository<MedicineProduct, UUID> {

    Optional<MedicineProduct> findBySku(String sku);

    Page<MedicineProduct> findByTherapeuticCategoryIgnoreCase(String category, Pageable pageable);

    @Query("SELECT m FROM MedicineProduct m WHERE LOWER(m.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(m.brandName) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(m.saltCompositionsJson) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<MedicineProduct> searchMedicines(@Param("query") String query, Pageable pageable);

    // Find generic substitutes matching identical salt composition but lower price
    @Query("SELECT m FROM MedicineProduct m WHERE m.saltCompositionsJson = :salts AND m.id <> :productId ORDER BY m.discountedPrice ASC")
    List<MedicineProduct> findSubstitutesBySalts(@Param("salts") String salts, @Param("productId") UUID productId);
}
