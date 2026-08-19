package com.commerceos.payment.repository;

import com.commerceos.payment.domain.PaymentWebhookEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEventEntity, UUID> {
    Optional<PaymentWebhookEventEntity> findByEventId(String eventId);
    boolean existsByEventId(String eventId);
}
