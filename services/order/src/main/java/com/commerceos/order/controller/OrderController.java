package com.commerceos.order.controller;

import com.commerceos.order.domain.CustomerOrder;
import com.commerceos.order.domain.PricingQuoteEntity;
import com.commerceos.order.dto.CustomerOrderResponse;
import com.commerceos.order.repository.CustomerOrderRepository;
import com.commerceos.order.repository.PricingQuoteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CustomerOrderRepository orderRepository;
    private final PricingQuoteRepository pricingQuoteRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderController(
            CustomerOrderRepository orderRepository,
            PricingQuoteRepository pricingQuoteRepository,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.orderRepository = orderRepository;
        this.pricingQuoteRepository = pricingQuoteRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    private static String getDeliveryOtpPepper() {
        String pepper = System.getenv("DELIVERY_OTP_PEPPER");
        if (pepper == null || pepper.isBlank()) {
            pepper = System.getProperty("delivery.otp.pepper");
        }
        if (pepper == null || pepper.isBlank()) {
            String env = System.getenv("APP_ENV");
            if ("production".equalsIgnoreCase(env) || "prod".equalsIgnoreCase(env)) {
                throw new IllegalStateException("CRITICAL_SECURITY_CONFIGURATION_ERROR: Mandatory production secret DELIVERY_OTP_PEPPER is unconfigured.");
            }
            return "commerceos_delivery_dev_pepper_default_2026";
        }
        return pepper;
    }

    private static String computeOtpCredential(String otp, String orderId, String salt) {
        if (otp == null || otp.isBlank()) return "";
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(getDeliveryOtpPepper().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac.init(key);
            String payload = otp.trim() + ":" + orderId + ":" + salt;
            byte[] raw = hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new RuntimeException("OTP credential calculation failed", e);
        }
    }

    private String getAuthenticatedSubject(Principal principal) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            return principal.getName();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().isBlank() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return null;
    }

    private boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equalsIgnoreCase(role) || a.getAuthority().equalsIgnoreCase("ROLE_" + role));
    }

    @PostMapping("/checkout-from-cart/{customerId}")
    public ResponseEntity<?> checkoutFromCart(
            @PathVariable String customerId,
            @RequestBody CartCheckoutRequest request,
            Principal principal
    ) {
        String authSub = getAuthenticatedSubject(principal);
        if (authSub == null || authSub.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "UNAUTHORIZED", "message", "Authentication required for checkout."));
        }
        if (!authSub.equalsIgnoreCase(customerId) && !hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "FORBIDDEN", "message", "Cannot checkout cart belonging to another customer"));
        }

        if (request.getSellerId() == null || request.getSellerId().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "INVALID_SELLER", "message", "Authoritative sellerId is strictly required."));
        }

        // P0 Mandate: Authoritative PricingQuote resolution (Zero controller formulas)
        String quoteId = request.getPricingQuoteId();
        if (quoteId == null || quoteId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "PRICING_QUOTE_REQUIRED", "message", "An authoritative pricingQuoteId is strictly required."));
        }

        Optional<PricingQuoteEntity> quoteOpt = pricingQuoteRepository.findActiveQuoteForUpdate(quoteId);
        if (quoteOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "INVALID_PRICING_QUOTE", "message", "Active pricing quote not found or already consumed."));
        }

        PricingQuoteEntity quote = quoteOpt.get();
        if (!quote.getCustomerId().equalsIgnoreCase(authSub) && !hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "FORBIDDEN", "message", "Cannot checkout quote belonging to another customer."));
        }
        if (quote.getExpiresAt() <= System.currentTimeMillis()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "EXPIRED_PRICING_QUOTE", "message", "Pricing quote has expired. Please re-evaluate cart."));
        }

        // Lock & finalize quote
        quote.setStatus("LOCKED");
        pricingQuoteRepository.save(quote);

        boolean rxNeeded = request.isPrescriptionRequired();
        if (rxNeeded && (request.getPrescriptionId() == null || request.getPrescriptionId().isBlank())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "PRESCRIPTION_REQUIRED", "message", "Prescription verification is mandatory for Rx items."));
        }

        boolean isCod = "COD".equalsIgnoreCase(request.getPaymentMethod());
        String initialPaymentStatus = isCod ? "COD_PENDING_COLLECTION" : "PENDING_PAYMENT_AUTHORIZATION";
        String initialStatus = rxNeeded ? "PRESCRIPTION_VERIFICATION_PENDING" : "PLACED";

        // 6-digit cryptographic PIN bound to Order ID with unique per-OTP salt
        String rawOtp = String.format("%06d", 100000 + SECURE_RANDOM.nextInt(900000));
        String otpSalt = UUID.randomUUID().toString();
        UUID orderId = UUID.randomUUID();
        String hashedOtp = computeOtpCredential(rawOtp, orderId.toString(), otpSalt);

        CustomerOrder order = CustomerOrder.builder()
                .id(orderId)
                .customerId(UUID.fromString(customerId))
                .pricingQuoteId(quote.getQuoteId())
                .orderType("QUICK_COMMERCE_10MIN")
                .orderStatus(initialStatus)
                .subtotalAmount(quote.getSubtotal())
                .totalAmount(quote.getTotal())
                .taxAmount(quote.getTax())
                .deliveryFee(quote.getDeliveryFee())
                .coldChainFee(quote.getColdChainFee())
                .discountAmount(quote.getDiscount())
                .prescriptionRequired(rxNeeded)
                .prescriptionId(rxNeeded ? UUID.fromString(request.getPrescriptionId()) : null)
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus(initialPaymentStatus)
                .codAmountToCollect(isCod ? quote.getTotal() : BigDecimal.ZERO)
                .deliveryAddressJson(request.getDeliveryAddressJson())
                .deliverySlaMins(10)
                .deliveryOtpHash(hashedOtp)
                .deliveryOtpSalt(otpSalt)
                .deliveryOtpConsumed(false)
                .deliveryModel("CHECKPOINT")
                .logisticsProvider(isCod ? "SELLER_MANAGED" : "INDIA_POST")
                .sellerId(request.getSellerId().trim())
                .build();

        orderRepository.save(order);
        kafkaTemplate.send("prod.orders.order.placed.v1", order.getId().toString(), "OrderCreated: " + order.getId());

        // Return raw OTP ONCE in creation response only!
        return ResponseEntity.ok(CustomerOrderResponse.fromEntity(order, rawOtp));
    }

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request, Principal principal) {
        String authSub = getAuthenticatedSubject(principal);
        if (authSub == null || authSub.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "UNAUTHORIZED", "message", "Authenticated customer identity is strictly required."));
        }

        if (request.getCustomerId() != null && !request.getCustomerId().isBlank() && !authSub.equalsIgnoreCase(request.getCustomerId()) && !hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "FORBIDDEN", "message", "Client cannot impersonate another customer ID."));
        }

        if (request.getSellerId() == null || request.getSellerId().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "INVALID_SELLER", "message", "Authoritative sellerId is strictly required."));
        }

        // Validate supported payment methods
        List<String> allowedPayments = List.of("COD", "UPI_INSTANT", "CARD", "WALLET", "NET_BANKING", "CREDIT_CARD", "DEBIT_CARD", "UPI", "CARD_CREDIT_DEBIT");
        if (request.getPaymentMethod() != null && !allowedPayments.contains(request.getPaymentMethod().toUpperCase())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "INVALID_PAYMENT_METHOD", "error", "INVALID_PAYMENT_METHOD", "message", "Unsupported payment method: " + request.getPaymentMethod()));
        }

        // P0 Mandate: Authoritative PricingQuote resolution (Zero client money trust)
        String quoteId = request.getPricingQuoteId();
        if (quoteId == null || quoteId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "PRICING_QUOTE_REQUIRED", "message", "An authoritative pricingQuoteId is strictly required."));
        }

        Optional<PricingQuoteEntity> quoteOpt = pricingQuoteRepository.findActiveQuoteForUpdate(quoteId);
        if (quoteOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "INVALID_PRICING_QUOTE", "message", "Active pricing quote not found or already consumed."));
        }

        PricingQuoteEntity quote = quoteOpt.get();
        if (!quote.getCustomerId().equalsIgnoreCase(authSub) && !hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "FORBIDDEN", "message", "Cannot checkout quote belonging to another customer."));
        }
        if (quote.getExpiresAt() <= System.currentTimeMillis()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "EXPIRED_PRICING_QUOTE", "message", "Pricing quote has expired."));
        }

        // Lock & consume quote
        quote.setStatus("LOCKED");
        pricingQuoteRepository.save(quote);

        boolean rxNeeded = request.getPrescriptionId() != null && !request.getPrescriptionId().isBlank();
        boolean isCod = "COD".equalsIgnoreCase(request.getPaymentMethod());
        String initialPaymentStatus = isCod ? "COD_PENDING_COLLECTION" : "PENDING_PAYMENT_AUTHORIZATION";
        String initialStatus = rxNeeded ? "PRESCRIPTION_VERIFICATION_PENDING" : "PLACED";

        // 6-digit cryptographic PIN bound to Order ID with unique per-OTP salt
        String rawOtp = String.format("%06d", 100000 + SECURE_RANDOM.nextInt(900000));
        String otpSalt = UUID.randomUUID().toString();
        UUID orderId = UUID.randomUUID();
        String hashedOtp = computeOtpCredential(rawOtp, orderId.toString(), otpSalt);

        CustomerOrder order = CustomerOrder.builder()
                .id(orderId)
                .customerId(UUID.fromString(authSub))
                .pricingQuoteId(quote.getQuoteId())
                .orderType(request.getOrderType() != null ? request.getOrderType() : "QUICK_COMMERCE_10MIN")
                .orderStatus(initialStatus)
                .subtotalAmount(quote.getSubtotal())
                .totalAmount(quote.getTotal())
                .taxAmount(quote.getTax())
                .deliveryFee(quote.getDeliveryFee())
                .coldChainFee(quote.getColdChainFee())
                .discountAmount(quote.getDiscount())
                .prescriptionRequired(rxNeeded)
                .prescriptionId(rxNeeded ? UUID.fromString(request.getPrescriptionId()) : null)
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "UPI_INSTANT")
                .paymentStatus(initialPaymentStatus)
                .codAmountToCollect(isCod ? quote.getTotal() : BigDecimal.ZERO)
                .deliveryAddressJson(request.getDeliveryAddressJson())
                .deliverySlaMins(10)
                .deliveryOtpHash(hashedOtp)
                .deliveryOtpSalt(otpSalt)
                .deliveryOtpConsumed(false)
                .deliveryModel("CHECKPOINT")
                .logisticsProvider(isCod ? "SELLER_MANAGED" : "INDIA_POST")
                .sellerId(request.getSellerId().trim())
                .build();

        orderRepository.save(order);
        kafkaTemplate.send("prod.orders.order.placed.v1", order.getId().toString(), "OrderCreated: " + order.getId());

        // Return raw OTP ONCE in creation response only!
        return ResponseEntity.ok(CustomerOrderResponse.fromEntity(order, rawOtp));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrderById(@PathVariable UUID orderId, Principal principal) {
        String authSub = getAuthenticatedSubject(principal);
        Optional<CustomerOrder> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("code", "ORDER_NOT_FOUND", "message", "Order not found."));
        }
        CustomerOrder order = orderOpt.get();
        if (authSub != null && !authSub.equalsIgnoreCase(order.getCustomerId().toString()) 
                && !authSub.equalsIgnoreCase(order.getSellerId()) && !hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "FORBIDDEN", "message", "Access denied: Cannot access order belonging to another customer or seller."));
        }
        // Returns CustomerOrderResponse where deliveryOtp is strictly null
        return ResponseEntity.ok(CustomerOrderResponse.fromEntity(order));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<?> getCustomerOrders(@PathVariable UUID customerId, Principal principal) {
        String authSub = getAuthenticatedSubject(principal);
        if (authSub != null && !authSub.equalsIgnoreCase(customerId.toString()) && !hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "FORBIDDEN", "message", "Access denied: Cannot access orders belonging to another customer."));
        }
        List<CustomerOrder> orders = orderRepository.findByCustomerId(customerId);
        List<CustomerOrderResponse> dtos = orders.stream().map(CustomerOrderResponse::fromEntity).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMyOrders(Principal principal) {
        String authSub = getAuthenticatedSubject(principal);
        if (authSub == null || authSub.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "UNAUTHORIZED", "message", "Authentication required."));
        }
        List<CustomerOrder> orders = orderRepository.findByCustomerId(UUID.fromString(authSub));
        List<CustomerOrderResponse> dtos = orders.stream().map(CustomerOrderResponse::fromEntity).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/seller")
    public ResponseEntity<?> getSellerOrders(Principal principal) {
        String authSub = getAuthenticatedSubject(principal);
        if (authSub == null || authSub.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "UNAUTHORIZED", "message", "Authentication required."));
        }
        if (!hasRole("SELLER") && !hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "FORBIDDEN", "message", "Access denied: Seller role required."));
        }
        List<CustomerOrder> orders = orderRepository.findBySellerId(authSub);
        List<CustomerOrderResponse> dtos = orders.stream().map(CustomerOrderResponse::fromEntity).toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(
            @PathVariable UUID orderId,
            @RequestBody CancelOrderRequest request,
            Principal principal
    ) {
        String authSub = getAuthenticatedSubject(principal);
        return orderRepository.findById(orderId).map(order -> {
            boolean isOwner = authSub != null && authSub.equalsIgnoreCase(order.getCustomerId().toString());
            boolean isSeller = authSub != null && authSub.equalsIgnoreCase(order.getSellerId());
            boolean isAdmin = hasRole("ADMIN");

            if (authSub != null && !isOwner && !isSeller && !isAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("code", "FORBIDDEN", "message", "Unauthorized to cancel this order."));
            }

            String derivedActor = isAdmin ? "ADMIN" : (isSeller ? "SELLER" : "CUSTOMER");
            String current = order.getOrderStatus();
            if ("DELIVERED".equals(current) || "CANCELLED".equals(current) || "RETURNED_TO_SELLER".equals(current)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("code", "ORDER_IRREVERSIBLE", "message", "Cannot cancel in state " + current));
            }
            // CUSTOMER may only self-cancel before packing; SELLER/ADMIN may cancel up to out-for-delivery.
            boolean canSelfCancel = "PLACED".equals(current) || "SELLER_ACCEPTED".equals(current);
            if ("CUSTOMER".equals(derivedActor) && !canSelfCancel) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("code", "CANCEL_WINDOW_CLOSED", "message", "Customer can only cancel before order is packed."));
            }
            order.setOrderStatus("CANCELLED");
            order.setCancellationReason(request.getReason() != null ? request.getReason() : "Cancelled by " + derivedActor);
            order.setCancelledBy(derivedActor);
            order.setCancelledAt(Instant.now());
            if ("COD".equalsIgnoreCase(order.getPaymentMethod())) {
                order.setPaymentStatus("COD_CANCELLED");
            }
            orderRepository.save(order);
            kafkaTemplate.send("prod.orders.order.cancelled.v1", order.getId().toString(), "OrderCancelled: " + order.getId());
            return ResponseEntity.ok(CustomerOrderResponse.fromEntity(order));
        }).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NOT_FOUND", "message", "Order not found.")));
    }

    @PostMapping("/{orderId}/deliver-with-otp")
    public ResponseEntity<?> verifyOtpAndDeliver(
            @PathVariable UUID orderId,
            @RequestBody DeliverWithOtpRequest request,
            Principal principal
    ) {
        String authSub = getAuthenticatedSubject(principal);
        Optional<CustomerOrder> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NOT_FOUND", "message", "Order not found."));
        }
        CustomerOrder order = orderOpt.get();

        if ("DELIVERED".equals(order.getOrderStatus()) || "CANCELLED".equals(order.getOrderStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("code", "ORDER_NOT_OPEN", "message", "Order is already " + order.getOrderStatus()));
        }
        if (!"OUT_FOR_DELIVERY".equals(order.getOrderStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("code", "ORDER_NOT_OUT_FOR_DELIVERY", "message", "Current state " + order.getOrderStatus()));
        }
        if (Boolean.TRUE.equals(order.getDeliveryOtpConsumed())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("code", "OTP_ALREADY_CONSUMED", "message", "Delivery OTP credential has already been consumed."));
        }

        // COD gate: cash must be collected & reconciled BEFORE the handoff OTP is honoured.
        if ("COD".equalsIgnoreCase(order.getPaymentMethod()) && !"COD_COLLECTED".equals(order.getPaymentStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("code", "COD_NOT_COLLECTED", "message", "Collect and reconcile cash before confirming handoff."));
        }

        String submittedOtp = request.getSubmittedOtp() == null ? "" : request.getSubmittedOtp().trim();
        String submittedHash = computeOtpCredential(submittedOtp, order.getId().toString(), order.getDeliveryOtpSalt());

        // Atomic DB Update
        int updated = orderRepository.atomicallyConsumeDeliveryOtpAndDeliver(orderId, submittedHash, Instant.now());
        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "INVALID_OTP", "message", "Submitted OTP PIN does not match customer handoff PIN or was already consumed."));
        }

        return ResponseEntity.ok(Map.of("status", "DELIVERY_CONFIRMED_STRICT_OTP_VERIFIED", "orderId", order.getId()));
    }

    public static class CartCheckoutRequest {
        private String pricingQuoteId;
        private boolean prescriptionRequired;
        private String prescriptionId;
        private String paymentMethod;
        private String deliveryAddressJson;
        private String sellerId;

        public String getPricingQuoteId() { return pricingQuoteId; }
        public void setPricingQuoteId(String pricingQuoteId) { this.pricingQuoteId = pricingQuoteId; }
        public boolean isPrescriptionRequired() { return prescriptionRequired; }
        public void setPrescriptionRequired(boolean prescriptionRequired) { this.prescriptionRequired = prescriptionRequired; }
        public String getPrescriptionId() { return prescriptionId; }
        public void setPrescriptionId(String prescriptionId) { this.prescriptionId = prescriptionId; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public String getDeliveryAddressJson() { return deliveryAddressJson; }
        public void setDeliveryAddressJson(String deliveryAddressJson) { this.deliveryAddressJson = deliveryAddressJson; }
        public String getSellerId() { return sellerId; }
        public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    }

    public static class CreateOrderRequest {
        private String customerId;
        private String sellerId;
        private String pricingQuoteId;
        private String orderType;
        private String prescriptionId;
        private String paymentMethod;
        private String deliveryAddressJson;
        private Integer deliverySlaMins;
        private String deliveryModel;
        private String logisticsProvider;

        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public String getSellerId() { return sellerId; }
        public void setSellerId(String sellerId) { this.sellerId = sellerId; }
        public String getPricingQuoteId() { return pricingQuoteId; }
        public void setPricingQuoteId(String pricingQuoteId) { this.pricingQuoteId = pricingQuoteId; }
        public String getOrderType() { return orderType; }
        public void setOrderType(String orderType) { this.orderType = orderType; }
        public String getPrescriptionId() { return prescriptionId; }
        public void setPrescriptionId(String prescriptionId) { this.prescriptionId = prescriptionId; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public String getDeliveryAddressJson() { return deliveryAddressJson; }
        public void setDeliveryAddressJson(String deliveryAddressJson) { this.deliveryAddressJson = deliveryAddressJson; }
        public Integer getDeliverySlaMins() { return deliverySlaMins; }
        public void setDeliverySlaMins(Integer deliverySlaMins) { this.deliverySlaMins = deliverySlaMins; }
        public String getDeliveryModel() { return deliveryModel; }
        public void setDeliveryModel(String deliveryModel) { this.deliveryModel = deliveryModel; }
        public String getLogisticsProvider() { return logisticsProvider; }
        public void setLogisticsProvider(String logisticsProvider) { this.logisticsProvider = logisticsProvider; }
    }

    public static class CancelOrderRequest {
        private String reason;
        private String cancelledBy;
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getCancelledBy() { return cancelledBy; }
        public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }
    }

    public static class DeliverWithOtpRequest {
        private String submittedOtp;
        public String getSubmittedOtp() { return submittedOtp; }
        public void setSubmittedOtp(String submittedOtp) { this.submittedOtp = submittedOtp; }
    }
}
