package com.commerceos.order.repository;

import com.commerceos.order.domain.DeliveryIdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface DeliveryIdempotencyKeyRepository extends JpaRepository<DeliveryIdempotencyKey, Long> {
    Optional<DeliveryIdempotencyKey> findByDeliveryIdAndIdempotencyKey(String deliveryId, String idempotencyKey);
}
