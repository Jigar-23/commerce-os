package com.commerceos.payment.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

@Component("razorpayPaymentProvider")
public class RazorpayPaymentProvider implements PaymentProvider {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getProviderName() {
        return "RAZORPAY";
    }

    private String getBasicAuthHeader(String keyId, String keySecret) {
        String token = keyId + ":" + keySecret;
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public ProviderIntentResult createIntent(BigDecimal amount, String currency, String orderId, String customerId, String idempotencyKey) {
        String keyId = System.getenv("RAZORPAY_KEY_ID");
        String keySecret = System.getenv("RAZORPAY_KEY_SECRET");

        if (keyId == null || keySecret == null || keyId.isBlank() || keySecret.isBlank()) {
            return ProviderIntentResult.builder()
                    .success(false)
                    .errorMessage("RAZORPAY_CREDENTIALS_UNCONFIGURED")
                    .build();
        }

        long amountInPaise = amount.multiply(BigDecimal.valueOf(100)).longValue();
        String receipt = "rcpt_" + (orderId.length() > 20 ? orderId.substring(0, 20) : orderId);

        try {
            Map<String, Object> reqBody = Map.of(
                    "amount", amountInPaise,
                    "currency", currency != null ? currency : "INR",
                    "receipt", receipt,
                    "notes", Map.of("customerId", customerId != null ? customerId : "", "orderId", orderId)
            );
            String jsonPayload = objectMapper.writeValueAsString(reqBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.razorpay.com/v1/orders"))
                    .header("Authorization", getBasicAuthHeader(keyId, keySecret))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                String providerOrderId = root.path("id").asText();
                return ProviderIntentResult.builder()
                        .success(true)
                        .providerIntentId(providerOrderId)
                        .providerClientSecret(keyId + ":" + providerOrderId)
                        .isSandbox(false)
                        .build();
            } else {
                return ProviderIntentResult.builder()
                        .success(false)
                        .errorMessage("RAZORPAY_ORDER_CREATION_FAILED: HTTP " + response.statusCode() + " -> " + response.body())
                        .build();
            }
        } catch (Exception e) {
            return ProviderIntentResult.builder()
                    .success(false)
                    .errorMessage("RAZORPAY_API_EXCEPTION: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public ProviderCaptureResult capture(String providerPaymentId, BigDecimal amount, String currency) {
        String keyId = System.getenv("RAZORPAY_KEY_ID");
        String keySecret = System.getenv("RAZORPAY_KEY_SECRET");

        if (keyId == null || keySecret == null || keyId.isBlank() || keySecret.isBlank()) {
            return ProviderCaptureResult.builder()
                    .success(false)
                    .errorMessage("RAZORPAY_CREDENTIALS_UNCONFIGURED")
                    .build();
        }

        long amountInPaise = amount.multiply(BigDecimal.valueOf(100)).longValue();

        try {
            Map<String, Object> reqBody = Map.of(
                    "amount", amountInPaise,
                    "currency", currency != null ? currency : "INR"
            );
            String jsonPayload = objectMapper.writeValueAsString(reqBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.razorpay.com/v1/payments/" + providerPaymentId + "/capture"))
                    .header("Authorization", getBasicAuthHeader(keyId, keySecret))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                String txId = root.path("id").asText();
                String status = root.path("status").asText();
                return ProviderCaptureResult.builder()
                        .success(true)
                        .providerTransactionId(txId)
                        .status("captured".equalsIgnoreCase(status) ? "CAPTURED" : status)
                        .build();
            } else {
                return ProviderCaptureResult.builder()
                        .success(false)
                        .errorMessage("RAZORPAY_CAPTURE_FAILED: HTTP " + response.statusCode() + " -> " + response.body())
                        .build();
            }
        } catch (Exception e) {
            return ProviderCaptureResult.builder()
                    .success(false)
                    .errorMessage("RAZORPAY_API_EXCEPTION: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public ProviderRefundResult refund(String providerPaymentId, BigDecimal amount, String currency, String reason, String idempotencyKey) {
        String keyId = System.getenv("RAZORPAY_KEY_ID");
        String keySecret = System.getenv("RAZORPAY_KEY_SECRET");

        if (keyId == null || keySecret == null || keyId.isBlank() || keySecret.isBlank()) {
            return ProviderRefundResult.builder()
                    .success(false)
                    .errorMessage("RAZORPAY_CREDENTIALS_UNCONFIGURED")
                    .build();
        }

        long amountInPaise = amount.multiply(BigDecimal.valueOf(100)).longValue();

        try {
            Map<String, Object> reqBody = Map.of(
                    "amount", amountInPaise,
                    "notes", Map.of("reason", reason != null ? reason : "Customer refund request")
            );
            String jsonPayload = objectMapper.writeValueAsString(reqBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.razorpay.com/v1/payments/" + providerPaymentId + "/refund"))
                    .header("Authorization", getBasicAuthHeader(keyId, keySecret))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                String refundId = root.path("id").asText();
                String status = root.path("status").asText();
                return ProviderRefundResult.builder()
                        .success(true)
                        .providerRefundId(refundId)
                        .status("processed".equalsIgnoreCase(status) ? "REFUND_SETTLED" : "REFUND_SUBMITTED")
                        .build();
            } else {
                return ProviderRefundResult.builder()
                        .success(false)
                        .errorMessage("RAZORPAY_REFUND_FAILED: HTTP " + response.statusCode() + " -> " + response.body())
                        .build();
            }
        } catch (Exception e) {
            return ProviderRefundResult.builder()
                    .success(false)
                    .errorMessage("RAZORPAY_REFUND_EXCEPTION: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public WebhookVerificationResult verifyWebhook(byte[] rawBodyBytes, Map<String, String> headers) {
        String signature = headers.get("x-razorpay-signature");
        String secret = System.getenv("RAZORPAY_WEBHOOK_SECRET");
        if (secret == null || secret.isBlank()) {
            secret = System.getenv("PAYMENT_GATEWAY_WEBHOOK_SECRET");
        }

        if (signature == null || secret == null || secret.isBlank()) {
            return WebhookVerificationResult.builder().valid(false).errorMessage("MISSING_RAZORPAY_SIGNATURE_OR_SECRET").build();
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] calculatedHmac = mac.doFinal(rawBodyBytes);
            String calculatedHex = HexFormat.of().formatHex(calculatedHmac);

            if (!MessageDigest.isEqual(calculatedHex.getBytes(StandardCharsets.UTF_8), signature.toLowerCase().getBytes(StandardCharsets.UTF_8))) {
                return WebhookVerificationResult.builder().valid(false).errorMessage("INVALID_RAZORPAY_HMAC_SIGNATURE").build();
            }

            return WebhookVerificationResult.builder().valid(true).build();
        } catch (Exception e) {
            return WebhookVerificationResult.builder().valid(false).errorMessage("VERIFICATION_EXCEPTION: " + e.getMessage()).build();
        }
    }
}
