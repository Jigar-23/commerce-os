package com.commerceos.cart.repository;

import com.commerceos.cart.domain.CartIdempotencyEntity;
import com.commerceos.cart.domain.CartIdempotencyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartIdempotencyRepository extends JpaRepository<CartIdempotencyEntity, CartIdempotencyId> {
    boolean existsByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);
}
