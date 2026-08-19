package com.commerceos.payment.controller;

import com.commerceos.payment.domain.PaymentIntent;
import com.commerceos.payment.domain.PaymentRefundEntity;
import com.commerceos.payment.provider.PaymentProvider;
import com.commerceos.payment.security.JwtAuthValidator;
import com.commerceos.payment.service.PaymentService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final JwtAuthValidator jwtAuthValidator;
    private final PaymentProvider paymentProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaymentController(PaymentService paymentService, JwtAuthValidator jwtAuthValidator, PaymentProvider paymentProvider) {
        this.paymentService = paymentService;
        this.jwtAuthValidator = jwtAuthValidator;
        this.paymentProvider = paymentProvider;

        String secret = System.getenv("PAYMENT_GATEWAY_WEBHOOK_SECRET");
        if (secret == null || secret.isBlank()) {
            secret = System.getProperty("payment.webhook.secret");
        }
        if (secret == null || secret.isBlank()) {
            secret = System.getenv("RAZORPAY_WEBHOOK_SECRET");
        }
        if (secret == null || secret.isBlank()) {
            secret = System.getenv("STRIPE_WEBHOOK_SECRET");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("CRITICAL SECURITY ERROR: PAYMENT_GATEWAY_WEBHOOK_SECRET is strictly required. Application refusing startup.");
        }
    }

    private Optional<JwtAuthValidator.AuthenticatedPrincipal> getPrincipal(String authHeader) {
        return jwtAuthValidator.authenticate(authHeader);
    }

    @PostMapping("/initiate")
    public ResponseEntity<?> initiatePayment(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody PaymentIntentRequest request
    ) {
        Optional<JwtAuthValidator.AuthenticatedPrincipal> principalOpt = getPrincipal(authHeader);
        if (principalOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ErrorBody.builder().code("UNAUTHORIZED").message("Valid bearer token required").build()
            );
        }

        if (request.getOrderId() == null || request.getOrderId().isBlank()) {
            return ResponseEntity.badRequest().body(
                    ErrorBody.builder().code("MISSING_ORDER_ID").message("orderId is required").build()
            );
        }

        PaymentService.Result result = paymentService.initiate(
                request.getOrderId(),
                principalOpt.get().getSubject(),
                request.getPaymentMethod(),
                idempotencyKey
        );
        if (result.isSuccess()) {
            return ResponseEntity.ok(toIntentResponse(result.intent()));
        }
        return ResponseEntity.status(result.httpStatus()).body(
                ErrorBody.builder().code(result.code()).message(result.message()).build()
        );
    }

    @PostMapping("/capture")
    public ResponseEntity<?> capturePayment(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody CaptureRequest request
    ) {
        Optional<JwtAuthValidator.AuthenticatedPrincipal> principalOpt = getPrincipal(authHeader);
        if (principalOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean isPrivileged = principalOpt.get().hasRole("ROLE_ADMIN") ||
                               principalOpt.get().hasRole("ROLE_FINANCE_ADMIN") ||
                               principalOpt.get().hasRole("ROLE_SYSTEM");

        PaymentService.Result result = paymentService.capture(
                request.getPaymentId(),
                request.getProviderTransactionId(),
                principalOpt.get().getSubject(),
                isPrivileged
        );
        if (result.isSuccess()) {
            return ResponseEntity.ok(toStatusResponse(result.intent()));
        }
        return ResponseEntity.status(result.httpStatus()).body(
                ErrorBody.builder().code(result.code()).message(result.message()).build()
        );
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<?> getPaymentStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String paymentId
    ) {
        Optional<JwtAuthValidator.AuthenticatedPrincipal> principalOpt = getPrincipal(authHeader);
        if (principalOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean isPrivileged = principalOpt.get().hasRole("ROLE_ADMIN") ||
                               principalOpt.get().hasRole("ROLE_FINANCE_ADMIN") ||
                               principalOpt.get().hasRole("ROLE_SYSTEM");

        PaymentService.Result result = paymentService.status(paymentId, principalOpt.get().getSubject(), isPrivileged);
        if (result.isSuccess()) {
            return ResponseEntity.ok(toStatusResponse(result.intent()));
        }
        return ResponseEntity.status(result.httpStatus()).body(
                ErrorBody.builder().code(result.code()).message(result.message()).build()
        );
    }

    @PostMapping(value = {"/webhook", "/webhook/stripe", "/webhook/razorpay", "/webhook/stripe-razorpay"})
    public ResponseEntity<?> handleWebhook(
            @RequestHeader Map<String, String> headers,
            @RequestBody byte[] rawBodyBytes) {

        if (rawBodyBytes == null || rawBodyBytes.length == 0) {
            return ResponseEntity.badRequest().body(ErrorBody.builder().code("EMPTY_BODY").message("Webhook payload empty").build());
        }

        PaymentProvider.WebhookVerificationResult vResult = paymentProvider.verifyWebhook(rawBodyBytes, headers);
        if (!vResult.isValid()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorBody.builder().code("UNAUTHORIZED").message(vResult.getErrorMessage()).build());
        }

        try {
            WebhookEvent event = objectMapper.readValue(rawBodyBytes, WebhookEvent.class);
            if (event == null || event.getType() == null) {
                return ResponseEntity.badRequest().body(ErrorBody.builder().code("INVALID_EVENT").message("Missing event type").build());
            }

            if (!"payment.captured".equals(event.getType()) && !"payment_intent.succeeded".equals(event.getType()) && !"order.paid".equals(event.getType())) {
                return ResponseEntity.ok(Map.of("code", "EVENT_IGNORED", "message", "Event type not processed for capture"));
            }

            String eventId = headers.get("x-webhook-event-id");
            if (eventId == null && event.getData() != null) {
                eventId = event.getData().getEventId();
            }
            if (eventId == null) {
                eventId = "evt_" + UUID.nameUUIDFromBytes(rawBodyBytes).toString();
            }

            String chargedPaymentId = extractPaymentId(event);
            if (chargedPaymentId == null) {
                return ResponseEntity.badRequest().body(ErrorBody.builder().code("MISSING_PAYMENT_ID").message("No paymentId in event payload").build());
            }

            BigDecimal amount = event.getData() != null ? event.getData().getAmountPaid() : null;
            String currency = event.getData() != null ? event.getData().getCurrency() : "INR";

            PaymentService.Result result = paymentService.applyWebhookCapture(
                    eventId, chargedPaymentId, amount, currency, paymentProvider.getProviderName()
            );
            if (result.isSuccess()) {
                return ResponseEntity.ok(Map.of("code", "OK", "message", "WEBHOOK_PROCESSED"));
            }
            return ResponseEntity.status(result.httpStatus()).body(
                    ErrorBody.builder().code(result.code()).message(result.message()).build()
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorBody.builder().code("INVALID_WEBHOOK_PAYLOAD").message(e.getMessage()).build());
        }
    }

    @PostMapping("/refund")
    public ResponseEntity<?> processRefund(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody RefundRequest request
    ) {
        Optional<JwtAuthValidator.AuthenticatedPrincipal> principalOpt = getPrincipal(authHeader);
        if (principalOpt.isEmpty() || (!principalOpt.get().hasRole("ROLE_FINANCE_ADMIN") && !principalOpt.get().hasRole("ROLE_ADMIN"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorBody.builder().code("FORBIDDEN").message("Refund issuance requires ROLE_ADMIN or ROLE_FINANCE_ADMIN").build());
        }
        PaymentService.Result result = paymentService.refund(
                request.getPaymentId(),
                request.getAmount(),
                request.getReason(),
                principalOpt.get().getSubject(),
                idempotencyKey
        );
        if (result.isSuccess()) {
            PaymentRefundEntity refund = result.refundEntity();
            return ResponseEntity.ok(RefundResponse.builder()
                    .refundId(refund != null ? refund.getId().toString() : ("ref_" + result.intent().getId().toString().substring(0, 8)))
                    .providerRefundId(refund != null ? refund.getProviderRefundId() : null)
                    .paymentId(result.intent().getId().toString())
                    .orderId(result.intent().getOrderId().toString())
                    .refundAmount(refund != null ? refund.getAmount() : (request.getAmount() != null ? request.getAmount() : result.intent().getAmount()))
                    .status(refund != null ? refund.getStatus() : "REFUND_SETTLED")
                    .refundedAt(refund != null && refund.getSettledAt() != null ? refund.getSettledAt().toString() : Instant.now().toString())
                    .build());
        }
        return ResponseEntity.status(result.httpStatus()).body(
                ErrorBody.builder().code(result.code()).message(result.message()).build()
        );
    }

    private String extractPaymentId(WebhookEvent event) {
        if (event.getData() == null) return null;
        return event.getData().getPaymentId() != null
                ? event.getData().getPaymentId()
                : event.getData().getObjectId();
    }

    private static PaymentIntentResponse toIntentResponse(PaymentIntent intent) {
        return PaymentIntentResponse.builder()
                .paymentId(intent.getId().toString())
                .orderId(intent.getOrderId().toString())
                .amount(intent.getAmount())
                .currency(intent.getCurrency())
                .paymentMethod(intent.getPaymentMethod())
                .clientSecret(intent.getProviderClientSecret() != null ? intent.getProviderClientSecret() : (intent.getId().toString() + "_secret"))
                .status(intent.getStatus())
                .build();
    }

    private static PaymentStatusResponse toStatusResponse(PaymentIntent intent) {
        return PaymentStatusResponse.builder()
                .paymentId(intent.getId().toString())
                .orderId(intent.getOrderId().toString())
                .amount(intent.getAmount())
                .currency(intent.getCurrency())
                .paymentMethod(intent.getPaymentMethod())
                .status(intent.getStatus())
                .capturedAt(intent.getCapturedAt() != null ? intent.getCapturedAt().toString() : null)
                .build();
    }

    @Data
    public static class PaymentIntentRequest {
        private String orderId;
        private String paymentMethod;
    }

    @Data
    public static class CaptureRequest {
        private String paymentId;
        private String providerTransactionId;
    }

    @Data
    public static class RefundRequest {
        private String paymentId;
        private BigDecimal amount;
        private String reason;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookEvent {
        private String type;
        private WebhookData data;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookData {
        private String eventId;
        private String paymentId;
        @JsonProperty("object_id")
        private String objectId;
        private BigDecimal amountPaid;
        private String currency;
        private String status;
    }

    @Data
    @Builder
    public static class PaymentIntentResponse {
        private String paymentId;
        private String orderId;
        private BigDecimal amount;
        private String currency;
        private String paymentMethod;
        private String clientSecret;
        private String status;
    }

    @Data
    @Builder
    public static class PaymentStatusResponse {
        private String paymentId;
        private String orderId;
        private BigDecimal amount;
        private String currency;
        private String paymentMethod;
        private String status;
        private String capturedAt;
    }

    @Data
    @Builder
    public static class RefundResponse {
        private String refundId;
        private String providerRefundId;
        private String paymentId;
        private String orderId;
        private BigDecimal refundAmount;
        private String status;
        private String refundedAt;
    }

    @Data
    @Builder
    public static class ErrorBody {
        private String code;
        private String message;
    }
}