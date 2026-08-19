package com.commerceos.payment.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

@Component("stripePaymentProvider")
public class StripePaymentProvider implements PaymentProvider {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getProviderName() {
        return "STRIPE";
    }

    @Override
    public ProviderIntentResult createIntent(BigDecimal amount, String currency, String orderId, String customerId, String idempotencyKey) {
        String secretKey = System.getenv("STRIPE_SECRET_KEY");
        if (secretKey == null || secretKey.isBlank()) {
            return ProviderIntentResult.builder()
                    .success(false)
                    .errorMessage("STRIPE_SECRET_KEY_UNCONFIGURED")
                    .build();
        }

        long amountInSmallestUnit = amount.multiply(BigDecimal.valueOf(100)).longValue();
        String curr = currency != null ? currency.toLowerCase() : "inr";

        try {
            String formBody = "amount=" + amountInSmallestUnit
                    + "&currency=" + URLEncoder.encode(curr, StandardCharsets.UTF_8)
                    + "&metadata[orderId]=" + URLEncoder.encode(orderId != null ? orderId : "", StandardCharsets.UTF_8)
                    + "&metadata[customerId]=" + URLEncoder.encode(customerId != null ? customerId : "", StandardCharsets.UTF_8)
                    + "&automatic_payment_methods[enabled]=true";

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.stripe.com/v1/payment_intents"))
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .timeout(Duration.ofSeconds(10));

            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                reqBuilder.header("Idempotency-Key", idempotencyKey);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                String intentId = root.path("id").asText();
                String clientSecret = root.path("client_secret").asText();
                return ProviderIntentResult.builder()
                        .success(true)
                        .providerIntentId(intentId)
                        .providerClientSecret(clientSecret)
                        .isSandbox(false)
                        .build();
            } else {
                return ProviderIntentResult.builder()
                        .success(false)
                        .errorMessage("STRIPE_INTENT_CREATION_FAILED: HTTP " + response.statusCode() + " -> " + response.body())
                        .build();
            }
        } catch (Exception e) {
            return ProviderIntentResult.builder()
                    .success(false)
                    .errorMessage("STRIPE_API_EXCEPTION: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public ProviderCaptureResult capture(String providerPaymentId, BigDecimal amount, String currency) {
        String secretKey = System.getenv("STRIPE_SECRET_KEY");
        if (secretKey == null || secretKey.isBlank()) {
            return ProviderCaptureResult.builder()
                    .success(false)
                    .errorMessage("STRIPE_SECRET_KEY_UNCONFIGURED")
                    .build();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.stripe.com/v1/payment_intents/" + providerPaymentId + "/capture"))
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                String intentId = root.path("id").asText();
                String status = root.path("status").asText();
                return ProviderCaptureResult.builder()
                        .success(true)
                        .providerTransactionId(intentId)
                        .status("succeeded".equalsIgnoreCase(status) ? "CAPTURED" : status)
                        .build();
            } else {
                return ProviderCaptureResult.builder()
                        .success(false)
                        .errorMessage("STRIPE_CAPTURE_FAILED: HTTP " + response.statusCode() + " -> " + response.body())
                        .build();
            }
        } catch (Exception e) {
            return ProviderCaptureResult.builder()
                    .success(false)
                    .errorMessage("STRIPE_API_EXCEPTION: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public ProviderRefundResult refund(String providerPaymentId, BigDecimal amount, String currency, String reason, String idempotencyKey) {
        String secretKey = System.getenv("STRIPE_SECRET_KEY");
        if (secretKey == null || secretKey.isBlank()) {
            return ProviderRefundResult.builder()
                    .success(false)
                    .errorMessage("STRIPE_SECRET_KEY_UNCONFIGURED")
                    .build();
        }

        long amountInSmallestUnit = amount.multiply(BigDecimal.valueOf(100)).longValue();

        try {
            String formBody = "payment_intent=" + URLEncoder.encode(providerPaymentId, StandardCharsets.UTF_8)
                    + "&amount=" + amountInSmallestUnit;

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.stripe.com/v1/refunds"))
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .timeout(Duration.ofSeconds(10));

            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                reqBuilder.header("Idempotency-Key", idempotencyKey);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                String refundId = root.path("id").asText();
                String status = root.path("status").asText();
                return ProviderRefundResult.builder()
                        .success(true)
                        .providerRefundId(refundId)
                        .status("succeeded".equalsIgnoreCase(status) ? "REFUND_SETTLED" : "REFUND_SUBMITTED")
                        .build();
            } else {
                return ProviderRefundResult.builder()
                        .success(false)
                        .errorMessage("STRIPE_REFUND_FAILED: HTTP " + response.statusCode() + " -> " + response.body())
                        .build();
            }
        } catch (Exception e) {
            return ProviderRefundResult.builder()
                    .success(false)
                    .errorMessage("STRIPE_REFUND_EXCEPTION: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public WebhookVerificationResult verifyWebhook(byte[] rawBodyBytes, Map<String, String> headers) {
        String stripeSigHeader = headers.get("stripe-signature");
        if (stripeSigHeader == null) {
            stripeSigHeader = headers.get("x-webhook-signature");
        }
        String secret = System.getenv("STRIPE_WEBHOOK_SECRET");
        if (secret == null || secret.isBlank()) {
            secret = System.getenv("PAYMENT_GATEWAY_WEBHOOK_SECRET");
        }

        if (stripeSigHeader == null || secret == null || secret.isBlank()) {
            return WebhookVerificationResult.builder().valid(false).errorMessage("MISSING_STRIPE_SIGNATURE_OR_SECRET").build();
        }

        try {
            String timestamp = null;
            String signatureV1 = null;

            for (String part : stripeSigHeader.split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    if ("t".equalsIgnoreCase(kv[0].trim())) timestamp = kv[1].trim();
                    if ("v1".equalsIgnoreCase(kv[0].trim())) signatureV1 = kv[1].trim();
                }
            }

            if (timestamp == null || signatureV1 == null) {
                signatureV1 = stripeSigHeader;
                timestamp = headers.get("x-webhook-timestamp");
            }

            if (timestamp != null) {
                long ts = Long.parseLong(timestamp);
                if (Math.abs(System.currentTimeMillis() - (ts > 10000000000L ? ts : ts * 1000)) > 300_000) {
                    return WebhookVerificationResult.builder().valid(false).errorMessage("STRIPE_TIMESTAMP_EXPIRED").build();
                }
            }

            String signedPayload = (timestamp != null ? timestamp + "." : "") + new String(rawBodyBytes, StandardCharsets.UTF_8);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] calculatedHmac = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            String calculatedHex = HexFormat.of().formatHex(calculatedHmac);

            if (!MessageDigest.isEqual(calculatedHex.getBytes(StandardCharsets.UTF_8), signatureV1.toLowerCase().getBytes(StandardCharsets.UTF_8))) {
                return WebhookVerificationResult.builder().valid(false).errorMessage("INVALID_STRIPE_SIGNATURE").build();
            }

            return WebhookVerificationResult.builder().valid(true).build();
        } catch (Exception e) {
            return WebhookVerificationResult.builder().valid(false).errorMessage("STRIPE_VERIFICATION_EXCEPTION: " + e.getMessage()).build();
        }
    }
}
