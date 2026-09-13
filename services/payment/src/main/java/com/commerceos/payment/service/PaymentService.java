package com.commerceos.payment.service;

import com.commerceos.payment.client.OrderServiceClient;
import com.commerceos.payment.domain.PaymentIntent;
import com.commerceos.payment.domain.PaymentRefundEntity;
import com.commerceos.payment.domain.PaymentWebhookEventEntity;
import com.commerceos.payment.provider.PaymentProvider;
import com.commerceos.payment.repository.PaymentIntentRepository;
import com.commerceos.payment.repository.PaymentRefundRepository;
import com.commerceos.payment.repository.PaymentWebhookEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentIntentRepository repository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final PaymentRefundRepository refundRepository;
    private final OrderServiceClient orderClient;
    private final PaymentProvider paymentProvider;

    public PaymentService(
            PaymentIntentRepository repository,
            PaymentWebhookEventRepository webhookEventRepository,
            PaymentRefundRepository refundRepository,
            OrderServiceClient orderClient,
            PaymentProvider paymentProvider) {
        this.repository = repository;
        this.webhookEventRepository = webhookEventRepository;
        this.refundRepository = refundRepository;
        this.orderClient = orderClient;
        this.paymentProvider = paymentProvider;
    }

    @Transactional
    public Result initiate(String orderId, String clientAmount, String paymentMethod, String authenticatedCustomerId, boolean isPrivileged, String idempotencyKey) {
        UUID orderUuid;
        try {
            orderUuid = UUID.fromString(orderId);
        } catch (IllegalArgumentException e) {
            return Result.failure(400, "INVALID_ORDER_ID", "Order reference is not a valid id");
        }

        OrderServiceClient.OrderTotals totals = orderClient.fetchOrderTotals(orderId).orElse(null);
        if (totals == null) {
            return Result.failure(404, "ORDER_NOT_FOUND", "Order not found or order service unavailable");
        }

        // Ownership Gate
        if (!isPrivileged && authenticatedCustomerId != null && !authenticatedCustomerId.equalsIgnoreCase(totals.customerId())) {
            return Result.failure(403, "FORBIDDEN", "Authenticated customer identity does not match order owner.");
        }

        Optional<PaymentIntent> existing = repository.findByOrderId(orderUuid);
        if (existing.isPresent()) {
            return Result.ok(existing.get());
        }

        if (clientAmount != null && !clientAmount.isBlank()) {
            try {
                BigDecimal supplied = new BigDecimal(clientAmount);
                if (supplied.compareTo(totals.total()) != 0) {
                    return Result.failure(
                        409, "PRICE_MISMATCH",
                        "Requested amount " + supplied + " does not match order total " + totals.total()
                    );
                }
            } catch (NumberFormatException e) {
                return Result.failure(400, "INVALID_AMOUNT", "Amount is not a valid number");
            }
        }

        // Invoke Payment Provider to generate real provider intent
        PaymentProvider.ProviderIntentResult providerIntent = paymentProvider.createIntent(
                totals.total(), "INR", orderId, totals.customerId(), idempotencyKey
        );

        PaymentIntent intent = PaymentIntent.builder()
                .orderId(orderUuid)
                .customerId(UUID.fromString(totals.customerId()))
                .amount(totals.total())
                .currency("INR")
                .paymentMethod(paymentMethod != null && !paymentMethod.isBlank() ? paymentMethod : "UPI_INSTANT")
                .status("AUTHORIZED")
                .provider(paymentProvider.getProviderName())
                .providerIntentId(providerIntent != null ? providerIntent.getProviderIntentId() : null)
                .providerClientSecret(providerIntent != null ? providerIntent.getProviderClientSecret() : null)
                .createdAt(Instant.now())
                .webhookVerified(Boolean.FALSE)
                .build();

        repository.save(intent);
        return Result.ok(intent);
    }

    @Transactional
    public Result capture(String paymentId) {
        return capture(paymentId, null, null, true);
    }

    @Transactional
    public Result capture(String paymentId, String providerTxId, String authenticatedCustomerId, boolean isPrivileged) {
        UUID id;
        try {
            id = UUID.fromString(paymentId);
        } catch (IllegalArgumentException e) {
            return Result.failure(400, "INVALID_PAYMENT_ID", "Payment id is not a valid id");
        }
        PaymentIntent intent = repository.findByIdForUpdate(id).orElse(null);
        if (intent == null) {
            return Result.failure(404, "PAYMENT_NOT_FOUND", "Payment not found");
        }
        if (!isPrivileged && authenticatedCustomerId != null && !authenticatedCustomerId.equalsIgnoreCase(intent.getCustomerId().toString())) {
            return Result.failure(403, "FORBIDDEN", "Access to payment capture denied.");
        }
        if ("CAPTURED".equals(intent.getStatus())) {
            return Result.ok(intent); // Idempotent capture
        }
        if (!"AUTHORIZED".equals(intent.getStatus())) {
            return Result.failure(409, "INVALID_PAYMENT_STATE", "Cannot capture in state " + intent.getStatus());
        }

        // Authoritative capture through provider
        PaymentProvider.ProviderCaptureResult capRes = paymentProvider.capture(paymentId, intent.getAmount(), intent.getCurrency());
        if (!capRes.isSuccess()) {
            return Result.failure(502, "PROVIDER_CAPTURE_FAILED", capRes.getErrorMessage());
        }

        intent.setStatus("CAPTURED");
        intent.setCapturedAt(Instant.now());
        if (providerTxId != null && !providerTxId.isBlank()) {
            intent.setProviderIntentId(providerTxId);
        }
        repository.save(intent);
        return Result.ok(intent);
    }

    @Transactional(readOnly = true)
    public Result status(String paymentId, String authenticatedCustomerId, boolean isPrivileged) {
        UUID id;
        try {
            id = UUID.fromString(paymentId);
        } catch (IllegalArgumentException e) {
            return Result.failure(400, "INVALID_PAYMENT_ID", "Payment id is not a valid id");
        }
        PaymentIntent intent = repository.findById(id).orElse(null);
        if (intent == null) {
            return Result.failure(404, "PAYMENT_NOT_FOUND", "Payment not found");
        }

        if (!isPrivileged && authenticatedCustomerId != null && !authenticatedCustomerId.equalsIgnoreCase(intent.getCustomerId().toString())) {
            return Result.failure(403, "FORBIDDEN", "Access to payment record denied.");
        }

        return Result.ok(intent);
    }

    @Transactional
    public Result applyWebhookCapture(String eventId, String paymentId, BigDecimal expectedAmount, String currency, String provider) {
        // 1. Atomic Event Claiming: Prevent check-then-act duplicate races
        if (eventId != null && !eventId.isBlank()) {
            PaymentWebhookEventEntity eventEntity = PaymentWebhookEventEntity.builder()
                    .eventId(eventId)
                    .provider(provider != null ? provider : paymentProvider.getProviderName())
                    .eventType("payment.captured")
                    .paymentId(paymentId)
                    .processedAt(Instant.now())
                    .status("PROCESSING")
                    .build();
            try {
                webhookEventRepository.saveAndFlush(eventEntity);
            } catch (Exception e) {
                // Event already claimed by competing duplicate webhook delivery
                Optional<PaymentIntent> existing = repository.findByProviderIntentId(paymentId)
                        .or(() -> {
                            try { return repository.findById(UUID.fromString(paymentId)); } catch (Exception ex) { return Optional.empty(); }
                        });
                return existing.map(Result::ok).orElseGet(() -> Result.failure(404, "PAYMENT_NOT_FOUND", "Payment not found"));
            }
        }

        // 2. Resolve PaymentIntent by internal UUID OR provider payment/intent ID
        PaymentIntent intent = null;
        try {
            UUID id = UUID.fromString(paymentId);
            intent = repository.findByIdForUpdate(id).orElse(null);
        } catch (IllegalArgumentException e) {
            // Not a UUID; resolve by providerIntentId
            intent = repository.findByProviderIntentIdForUpdate(paymentId).orElse(null);
        }

        if (intent == null) {
            intent = repository.findByProviderIntentIdForUpdate(paymentId).orElse(null);
        }

        if (intent == null) {
            return Result.failure(404, "PAYMENT_NOT_FOUND", "Payment intent not found for reference: " + paymentId);
        }

        // 3. Financial Reconciliation Gate
        if (expectedAmount != null && intent.getAmount().compareTo(expectedAmount) != 0) {
            return Result.failure(409, "AMOUNT_MISMATCH", "Webhook amount " + expectedAmount + " does not match intent amount " + intent.getAmount());
        }

        if (currency != null && !currency.isBlank() && intent.getCurrency() != null && !intent.getCurrency().equalsIgnoreCase(currency.trim())) {
            return Result.failure(409, "CURRENCY_MISMATCH", "Webhook currency " + currency + " does not match intent currency " + intent.getCurrency());
        }

        // 4. Authoritative State Transition
        if ("AUTHORIZED".equals(intent.getStatus())) {
            intent.setStatus("CAPTURED");
            intent.setCapturedAt(Instant.now());
            intent.setWebhookVerified(Boolean.TRUE);
            repository.save(intent);
        }

        return Result.ok(intent);
    }

    /**
     * Authoritative, Idempotent Financial Refund Execution through PaymentProvider with DB row lock.
     */
    @Transactional
    public Result refund(String paymentId, BigDecimal requestedAmount, String reason, String requestedBy, String idempotencyKey) {
        UUID id;
        try {
            id = UUID.fromString(paymentId);
        } catch (IllegalArgumentException e) {
            return Result.failure(400, "INVALID_PAYMENT_ID", "Payment id is not a valid id");
        }
        PaymentIntent intent = repository.findByIdForUpdate(id).orElse(null);
        if (intent == null) {
            return Result.failure(404, "PAYMENT_NOT_FOUND", "Payment not found");
        }
        if (!"CAPTURED".equals(intent.getStatus()) && !"PARTIALLY_REFUNDED".equals(intent.getStatus())) {
            return Result.failure(409, "PAYMENT_NOT_CAPTURED", "Cannot refund an uncaptured payment.");
        }

        // 1. Check Idempotency Key
        String idemKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey
                : ("ref_idem_" + paymentId + "_" + (requestedAmount != null ? requestedAmount : intent.getAmount()));

        Optional<PaymentRefundEntity> existingRefund = refundRepository.findByIdempotencyKey(idemKey);
        if (existingRefund.isPresent()) {
            return Result.okRefund(intent, existingRefund.get()); // Idempotent repeat
        }

        // 2. Transactionally Enforce totalRefunded + requested <= capturedAmount
        BigDecimal totalAlreadyRefunded = refundRepository.sumTotalRefundedForPayment(id);
        BigDecimal remainingRefundable = intent.getAmount().subtract(totalAlreadyRefunded).max(BigDecimal.ZERO);

        BigDecimal refundAmt = (requestedAmount != null && requestedAmount.compareTo(BigDecimal.ZERO) > 0)
                ? requestedAmount
                : remainingRefundable;

        if (refundAmt.compareTo(remainingRefundable) > 0 || refundAmt.compareTo(BigDecimal.ZERO) <= 0) {
            return Result.failure(400, "OVER_REFUND_ATTEMPT", "Requested refund amount " + refundAmt + " exceeds remaining refundable balance " + remainingRefundable);
        }

        // 3. Execute with Provider
        PaymentProvider.ProviderRefundResult providerRes = paymentProvider.refund(
                paymentId, refundAmt, intent.getCurrency(), reason, idemKey
        );
        if (!providerRes.isSuccess()) {
            return Result.failure(502, "PROVIDER_REFUND_FAILED", providerRes.getErrorMessage());
        }

        // 4. Record Immutable Refund Entry
        PaymentRefundEntity refundEntity = PaymentRefundEntity.builder()
                .paymentId(id)
                .providerRefundId(providerRes.getProviderRefundId())
                .amount(refundAmt)
                .currency(intent.getCurrency())
                .status(providerRes.getStatus())
                .idempotencyKey(idemKey)
                .reason(reason != null ? reason : "Customer refund request")
                .requestedBy(requestedBy)
                .settledAt(Instant.now())
                .build();
        refundRepository.save(refundEntity);

        // 5. Update Intent Status
        BigDecimal newTotalRefunded = totalAlreadyRefunded.add(refundAmt);
        if (newTotalRefunded.compareTo(intent.getAmount()) >= 0) {
            intent.setStatus("REFUNDED");
        } else {
            intent.setStatus("PARTIALLY_REFUNDED");
        }
        intent.setRefundedAt(Instant.now());
        repository.save(intent);

        return Result.okRefund(intent, refundEntity);
    }

    public static final class Result {
        private final int httpStatus;
        private final String code;
        private final String message;
        private final PaymentIntent intent;
        private final PaymentRefundEntity refundEntity;

        private Result(int httpStatus, String code, String message, PaymentIntent intent, PaymentRefundEntity refundEntity) {
            this.httpStatus = httpStatus;
            this.code = code;
            this.message = message;
            this.intent = intent;
            this.refundEntity = refundEntity;
        }

        public static Result ok(PaymentIntent intent) {
            return new Result(200, "OK", null, intent, null);
        }

        public static Result okRefund(PaymentIntent intent, PaymentRefundEntity refundEntity) {
            return new Result(200, "OK", null, intent, refundEntity);
        }

        public static Result failure(int httpStatus, String code, String message) {
            return new Result(httpStatus, code, message, null, null);
        }

        public boolean isSuccess() {
            return intent != null;
        }

        public int httpStatus() {
            return httpStatus;
        }

        public String code() {
            return code;
        }

        public String message() {
            return message;
        }

        public PaymentIntent intent() {
            return intent;
        }

        public PaymentRefundEntity refundEntity() {
            return refundEntity;
        }
    }
}