package com.commerceos.order.repository;

import com.commerceos.order.domain.MerchantStore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MerchantStoreRepository extends JpaRepository<MerchantStore, String> {
    Optional<MerchantStore> findBySellerId(String sellerId);
}
