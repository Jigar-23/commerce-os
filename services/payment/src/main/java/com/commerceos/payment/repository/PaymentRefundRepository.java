package com.commerceos.payment.repository;

import com.commerceos.payment.domain.PaymentRefundEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRefundRepository extends JpaRepository<PaymentRefundEntity, UUID> {

    List<PaymentRefundEntity> findByPaymentId(UUID paymentId);

    Optional<PaymentRefundEntity> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM PaymentRefundEntity r WHERE r.paymentId = :paymentId AND r.status IN ('REFUND_SUBMITTED', 'REFUND_SETTLED')")
    BigDecimal sumTotalRefundedForPayment(@Param("paymentId") UUID paymentId);
}
