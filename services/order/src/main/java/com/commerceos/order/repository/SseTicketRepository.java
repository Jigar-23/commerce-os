package com.commerceos.order.repository;

import com.commerceos.order.domain.SseTicketEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SseTicketRepository extends JpaRepository<SseTicketEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM SseTicketEntity t WHERE t.ticketId = :ticketId AND t.consumed = false")
    Optional<SseTicketEntity> findActiveTicketForUpdate(String ticketId);
}
