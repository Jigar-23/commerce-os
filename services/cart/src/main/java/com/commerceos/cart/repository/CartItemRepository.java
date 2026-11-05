package com.commerceos.cart.repository;

import com.commerceos.cart.domain.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItemEntity, UUID> {

    List<CartItemEntity> findByCustomerIdOrderByCreatedAtAsc(String customerId);

    Optional<CartItemEntity> findByCustomerIdAndSku(String customerId, String sku);

    void deleteByCustomerIdAndSku(String customerId, String sku);

    void deleteByCustomerId(String customerId);
}
