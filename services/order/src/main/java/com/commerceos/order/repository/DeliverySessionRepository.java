package com.commerceos.order.repository;

import com.commerceos.order.domain.DeliverySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface DeliverySessionRepository extends JpaRepository<DeliverySession, String> {
    Optional<DeliverySession> findByOrderId(String orderId);
    Optional<DeliverySession> findByRiderIdAndStateNot(String riderId, String state);
}
