package com.commerceos.return.repository;

import com.commerceos.return.domain.ReturnRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequestEntity, String> {
    List<ReturnRequestEntity> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    Optional<ReturnRequestEntity> findByOrderId(String orderId);
}
