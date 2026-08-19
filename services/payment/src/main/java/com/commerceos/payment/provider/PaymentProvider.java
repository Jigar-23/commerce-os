package com.commerceos.payment.provider;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Authoritative Payment Provider Abstraction for Gateway Integrations (Stripe, Razorpay, Sandbox).
 */
public interface PaymentProvider {

    String getProviderName();

    ProviderIntentResult createIntent(BigDecimal amount, String currency, String orderId, String customerId, String idempotencyKey);

    ProviderCaptureResult capture(String providerPaymentId, BigDecimal amount, String currency);

    ProviderRefundResult refund(String providerPaymentId, BigDecimal amount, String currency, String reason, String idempotencyKey);

    WebhookVerificationResult verifyWebhook(byte[] rawBodyBytes, Map<String, String> headers);

    @Data
    @Builder
    class ProviderIntentResult {
        private boolean success;
        private String providerIntentId;
        private String providerClientSecret;
        private String errorMessage;
        private boolean isSandbox;
    }

    @Data
    @Builder
    class ProviderCaptureResult {
        private boolean success;
        private String providerTransactionId;
        private String status;
        private String errorMessage;
    }

    @Data
    @Builder
    class ProviderRefundResult {
        private boolean success;
        private String providerRefundId;
        private String status; // REFUND_SETTLED, REFUND_PENDING, REFUND_FAILED
        private String errorMessage;
    }

    @Data
    @Builder
    class WebhookVerificationResult {
        private boolean valid;
        private String eventType;
        private String paymentId;
        private BigDecimal amount;
        private String currency;
        private String eventId;
        private String errorMessage;
    }
}
