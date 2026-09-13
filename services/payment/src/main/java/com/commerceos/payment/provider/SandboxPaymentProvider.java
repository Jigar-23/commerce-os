package com.commerceos.payment.provider;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Component("sandboxPaymentProvider")
public class SandboxPaymentProvider implements PaymentProvider {

    private static final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String getProviderName() {
        return "SANDBOX";
    }

    @Override
    public ProviderIntentResult createIntent(BigDecimal amount, String currency, String orderId, String customerId, String idempotencyKey) {
        byte[] tokenBytes = new byte[24];
        secureRandom.nextBytes(tokenBytes);
        String intentId = "sb_pi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String clientSecret = intentId + "_secret_sandbox_" + Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        return ProviderIntentResult.builder()
                .success(true)
                .providerIntentId(intentId)
                .providerClientSecret(clientSecret)
                .isSandbox(true)
                .build();
    }

    @Override
    public ProviderCaptureResult capture(String providerPaymentId, BigDecimal amount, String currency) {
        return ProviderCaptureResult.builder()
                .success(true)
                .providerTransactionId("sb_txn_" + UUID.randomUUID().toString().substring(0, 12))
                .status("CAPTURED")
                .build();
    }

    @Override
    public ProviderRefundResult refund(String providerPaymentId, BigDecimal amount, String currency, String reason, String idempotencyKey) {
        String refundId = "sb_ref_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return ProviderRefundResult.builder()
                .success(true)
                .providerRefundId(refundId)
                .status("REFUND_SETTLED")
                .build();
    }

    @Override
    public WebhookVerificationResult verifyWebhook(byte[] rawBodyBytes, Map<String, String> headers) {
        String signature = headers.get("x-webhook-signature");
        String timestamp = headers.get("x-webhook-timestamp");
        String secret = System.getenv("PAYMENT_GATEWAY_WEBHOOK_SECRET");
        if (secret == null || secret.isBlank()) {
            secret = System.getProperty("payment.webhook.secret", "commerceos_sandbox_webhook_secret");
        }

        if (signature == null || timestamp == null) {
            return WebhookVerificationResult.builder().valid(false).errorMessage("MISSING_SIGNATURE_OR_TIMESTAMP").build();
        }

        try {
            long ts = Long.parseLong(timestamp);
            if (Math.abs(System.currentTimeMillis() - ts) > 300_000) {
                return WebhookVerificationResult.builder().valid(false).errorMessage("TIMESTAMP_EXPIRED").build();
            }

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] calculatedHmac = mac.doFinal(rawBodyBytes);
            String calculatedHex = HexFormat.of().formatHex(calculatedHmac);

            if (!MessageDigest.isEqual(calculatedHex.getBytes(StandardCharsets.UTF_8), signature.toLowerCase().getBytes(StandardCharsets.UTF_8))) {
                return WebhookVerificationResult.builder().valid(false).errorMessage("INVALID_HMAC_SIGNATURE").build();
            }

            return WebhookVerificationResult.builder().valid(true).build();
        } catch (Exception e) {
            return WebhookVerificationResult.builder().valid(false).errorMessage("VERIFICATION_EXCEPTION: " + e.getMessage()).build();
        }
    }
}
