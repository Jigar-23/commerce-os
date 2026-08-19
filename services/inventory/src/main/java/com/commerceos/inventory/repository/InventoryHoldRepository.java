package com.commerceos.inventory.repository;

import com.commerceos.inventory.domain.InventoryHoldEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryHoldRepository extends JpaRepository<InventoryHoldEntity, String> {

    @Query("SELECT COALESCE(SUM(h.quantity), 0) FROM InventoryHoldEntity h WHERE h.sku = :sku AND h.status = 'ACTIVE_HOLD' AND h.expiresAt > :now")
    int sumActiveHoldsForSku(@Param("sku") String sku, @Param("now") Instant now);

    @Query("SELECT COALESCE(SUM(h.quantity), 0) FROM InventoryHoldEntity h WHERE h.sku = :sku AND h.darkStoreId = :storeId AND h.status = 'ACTIVE_HOLD' AND h.expiresAt > :now")
    int sumActiveHoldsForSkuAndStore(@Param("sku") String sku, @Param("storeId") String storeId, @Param("now") Instant now);

    List<InventoryHoldEntity> findBySkuAndStatus(String sku, String status);

    Optional<InventoryHoldEntity> findByIdAndCustomerId(String id, String customerId);

    List<InventoryHoldEntity> findByOrderIdAndCustomerIdAndStatus(String orderId, String customerId, String status);
}
