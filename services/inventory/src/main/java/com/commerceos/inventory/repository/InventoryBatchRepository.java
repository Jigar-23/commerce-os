package com.commerceos.inventory.repository;

import com.commerceos.inventory.domain.InventoryBatchEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryBatchRepository extends JpaRepository<InventoryBatchEntity, UUID> {

    List<InventoryBatchEntity> findBySkuOrderByExpiryDateAsc(String sku);

    List<InventoryBatchEntity> findBySkuAndExpiryDateAfterOrderByExpiryDateAsc(String sku, LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM InventoryBatchEntity b WHERE b.sku = :sku AND b.expiryDate > :date ORDER BY b.expiryDate ASC")
    List<InventoryBatchEntity> findBatchesForAllocationWithLock(@Param("sku") String sku, @Param("date") LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM InventoryBatchEntity b WHERE b.sku = :sku AND b.darkStoreId = :darkStoreId AND b.expiryDate > :date ORDER BY b.expiryDate ASC")
    List<InventoryBatchEntity> findStoreBatchesForAllocationWithLock(
            @Param("sku") String sku,
            @Param("darkStoreId") String darkStoreId,
            @Param("date") LocalDate date
    );
}
