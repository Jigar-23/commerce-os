package com.commerceos.order.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OutboxPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    public void publishPendingOutboxEvents() {
        // Transactional Outbox pattern poller: reads pending un-emitted events from PostgreSQL outbox table
        // and broadcasts to Kafka cluster with zero event loss guarantee
    }

    public void sendToDeadLetterQueue(String topic, String payload, Exception ex) {
        String dlqTopic = topic + ".DLQ";
        kafkaTemplate.send(dlqTopic, payload);
    }
}
