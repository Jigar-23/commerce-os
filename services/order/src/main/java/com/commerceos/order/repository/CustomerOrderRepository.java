package com.commerceos.order.repository;

import com.commerceos.order.domain.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {
    List<CustomerOrder> findByCustomerId(UUID customerId);
    List<CustomerOrder> findBySellerId(String sellerId);
    List<CustomerOrder> findByOrderStatus(String orderStatus);

    @Modifying
    @Transactional
    @Query("UPDATE CustomerOrder o SET o.deliveryOtpConsumed = true, o.orderStatus = 'DELIVERED', o.updatedAt = :now " +
           "WHERE o.id = :orderId AND o.deliveryOtpConsumed = false AND o.orderStatus = 'OUT_FOR_DELIVERY' AND o.deliveryOtpHash = :otpHash")
    int atomicallyConsumeDeliveryOtpAndDeliver(
            @Param("orderId") UUID orderId,
            @Param("otpHash") String otpHash,
            @Param("now") Instant now
    );
}
