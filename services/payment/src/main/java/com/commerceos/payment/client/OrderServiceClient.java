package com.commerceos.payment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Reads the authoritative order aggregate from the Order Service. The payment
 * amount is NEVER taken from the client request — it is derived from the order's
 * server-owned total, so "Pay ₹1" from a tampered client can never settle.
 */
@Component
public class OrderServiceClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String orderServiceBase;

    public OrderServiceClient(@Value("${app.order-service.base-url:http://localhost:8083}") String orderServiceBase) {
        this.orderServiceBase = orderServiceBase.endsWith("/")
                ? orderServiceBase.substring(0, orderServiceBase.length() - 1)
                : orderServiceBase;
    }

    /** Fetches the authoritative total and owning customer for an order. */
    public Optional<OrderTotals> fetchOrderTotals(String orderId) {
        try {
            String url = orderServiceBase + "/api/v1/orders/" + orderId;
            String body = restTemplate.exchange(url, HttpMethod.GET, null, String.class).getBody();
            if (body == null || body.isBlank()) return Optional.empty();
            JsonNode node = objectMapper.readTree(body);
            String customerId = node.path("customerId").asText();
            if (customerId.isBlank()) return Optional.empty();
            BigDecimal total = node.path("totalAmount").decimalValue();
            return Optional.of(new OrderTotals(customerId, total));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public record OrderTotals(String customerId, BigDecimal total) {}
}