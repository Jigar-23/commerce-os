package com.commerceos.order.repository;

import com.commerceos.order.domain.DeliveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DeliveryEventRepository extends JpaRepository<DeliveryEvent, String> {
    List<DeliveryEvent> findByOrderIdAndEventSequenceGreaterThanOrderByEventSequenceAsc(String orderId, Long lastEventSequence);
    List<DeliveryEvent> findByDeliveryIdAndEventSequenceGreaterThanOrderByEventSequenceAsc(String deliveryId, Long lastEventSequence);
    List<DeliveryEvent> findByOrderIdOrderByEventSequenceAsc(String orderId);

    @Query("SELECT COALESCE(MAX(e.eventSequence), 0) FROM DeliveryEvent e WHERE e.deliveryId = :deliveryId")
    Long findMaxSequenceByDeliveryId(@Param("deliveryId") String deliveryId);

    @Query(value = "SELECT COALESCE(MAX(event_sequence), 0) + 1 FROM delivery_events WHERE delivery_id = :deliveryId", nativeQuery = true)
    Long getNextEventSequenceAtomic(@Param("deliveryId") String deliveryId);
}
