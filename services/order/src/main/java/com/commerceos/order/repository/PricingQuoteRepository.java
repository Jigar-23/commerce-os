package com.commerceos.order.repository;

import com.commerceos.order.domain.PricingQuoteEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PricingQuoteRepository extends JpaRepository<PricingQuoteEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM PricingQuoteEntity q WHERE q.quoteId = :quoteId AND q.status = 'ACTIVE'")
    Optional<PricingQuoteEntity> findActiveQuoteForUpdate(String quoteId);
}
