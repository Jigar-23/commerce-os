package com.commerceos.order.controller;

import com.commerceos.order.domain.CustomerOrder;
import com.commerceos.order.domain.DeliveryEvent;
import com.commerceos.order.domain.DeliveryIdempotencyKey;
import com.commerceos.order.domain.DeliverySession;
import com.commerceos.order.dto.CustomerTrackingDTO;
import com.commerceos.order.dto.OpsDeliveryDTO;
import com.commerceos.order.dto.RiderDeliveryDTO;
import com.commerceos.order.repository.CustomerOrderRepository;
import com.commerceos.order.repository.DeliveryEventRepository;
import com.commerceos.order.repository.DeliveryIdempotencyKeyRepository;
import com.commerceos.order.repository.DeliverySessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/v1/delivery")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:8080", "http://localhost:8090"})
public class DeliveryController {

    private final DeliverySessionRepository deliverySessionRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final DeliveryEventRepository deliveryEventRepository;
    private final DeliveryIdempotencyKeyRepository idempotencyKeyRepository;
    private final com.commerceos.order.repository.UserAccountRepository userAccountRepository;
    private final com.commerceos.order.repository.MerchantStoreRepository merchantStoreRepository;
    private final ObjectMapper objectMapper;

    private final Map<String, List<SseEmitter>> orderSseEmitters = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private static final Map<String, List<String>> ALLOWED_TRANSITIONS = Map.of(
        "ASSIGNED", List.of("ACCEPTED", "DECLINED"),
        "ACCEPTED", List.of("EN_ROUTE_PICKUP"),
        "EN_ROUTE_PICKUP", List.of("ARRIVED_PICKUP", "STORE_CLOSED", "CANCELLED"),
        "ARRIVED_PICKUP", List.of("PICKED_UP", "STORE_CLOSED", "CANCELLED"),
        "PICKED_UP", List.of("EN_ROUTE_CUSTOMER", "DAMAGED_PACKAGE", "RETURN_TO_STORE"),
        "EN_ROUTE_CUSTOMER", List.of("ARRIVED_CUSTOMER", "WRONG_ADDRESS", "CUSTOMER_UNREACHABLE"),
        "ARRIVED_CUSTOMER", List.of("HANDOFF_STARTED", "CUSTOMER_UNREACHABLE", "WRONG_ADDRESS"),
        "HANDOFF_STARTED", List.of("DELIVERED", "CUSTOMER_UNREACHABLE", "RETURN_TO_STORE")
    );

    public DeliveryController(
        DeliverySessionRepository deliverySessionRepository,
        CustomerOrderRepository customerOrderRepository,
        DeliveryEventRepository deliveryEventRepository,
        DeliveryIdempotencyKeyRepository idempotencyKeyRepository,
        com.commerceos.order.repository.UserAccountRepository userAccountRepository,
        com.commerceos.order.repository.MerchantStoreRepository merchantStoreRepository,
        ObjectMapper objectMapper
    ) {
        this.deliverySessionRepository = deliverySessionRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.deliveryEventRepository = deliveryEventRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.userAccountRepository = userAccountRepository;
        this.merchantStoreRepository = merchantStoreRepository;
        this.objectMapper = objectMapper;
    }

    private String requireAuthenticatedSubject(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new IllegalArgumentException("UNAUTHORIZED");
        }
        return principal.getName();
    }

    private Optional<DeliverySession> findSession(String idParam) {
        return deliverySessionRepository.findById(idParam)
            .or(() -> deliverySessionRepository.findByOrderId(idParam));
    }

    @PostMapping("/dispatch")
    @Transactional
    public ResponseEntity<?> dispatchOrder(
        @RequestBody Map<String, String> body,
        Principal principal
    ) {
        try {
            requireAuthenticatedSubject(principal);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean hasDispatchRole = auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_DISPATCH") || a.getAuthority().equals("ROLE_OPS") || a.getAuthority().equals("ROLE_ADMIN"));

        if (!hasDispatchRole) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "FORBIDDEN");
            err.put("message", "Only DISPATCH, OPS, or ADMIN roles may create delivery dispatches.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
        }

        String orderIdStr = body.get("orderId");
        if (orderIdStr == null || orderIdStr.isBlank()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "MISSING_ORDER_ID");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        UUID orderUuid;
        try {
            orderUuid = UUID.fromString(orderIdStr);
        } catch (IllegalArgumentException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "INVALID_ORDER_UUID");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        Optional<CustomerOrder> orderOpt = customerOrderRepository.findById(orderUuid);
        if (orderOpt.isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "ORDER_NOT_FOUND");
            err.put("message", "Cannot create delivery session: Real Order not found for ID " + orderIdStr);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
        }
        CustomerOrder order = orderOpt.get();

        // Requirements 29 & 30: Prevent dispatch of cancelled/delivered/failed orders
        if ("CANCELLED".equalsIgnoreCase(order.getOrderStatus()) || "DELIVERED".equalsIgnoreCase(order.getOrderStatus()) || "FAILED".equalsIgnoreCase(order.getOrderStatus())) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "ORDER_NOT_DISPATCHABLE");
            err.put("message", "Cannot dispatch order in terminal state: " + order.getOrderStatus());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        // Requirement 31: Prevent duplicate active delivery for an order atomically
        Optional<DeliverySession> existing = deliverySessionRepository.findByOrderId(orderIdStr);
        if (existing.isPresent() && !"DELIVERED".equalsIgnoreCase(existing.get().getState()) && !"CANCELLED".equalsIgnoreCase(existing.get().getState()) && !"FAILED".equalsIgnoreCase(existing.get().getState())) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "DELIVERY_ALREADY_ACTIVE");
            err.put("message", "An active delivery session already exists for order " + orderIdStr);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(err);
        }

        String assignedRiderId = body.get("riderId");
        if (assignedRiderId == null || assignedRiderId.isBlank()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "MISSING_RIDER_ASSIGNMENT");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        // Authoritative Domain Resolution (Directives 01 - 20)
        // Request body substitute/spoofing fields are strictly IGNORED.
        String riderName = resolveAuthoritativeRiderName(assignedRiderId);
        String riderPhone = resolveAuthoritativeRiderPhone(assignedRiderId);
        String riderVehicle = resolveAuthoritativeRiderVehicle(assignedRiderId);

        if (riderName == null || riderName.isBlank()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "MISSING_MANDATORY_DOMAIN_DATA");
            err.put("message", "Rider full name is missing from authoritative rider domain record.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        String customerName = resolveAuthoritativeCustomerName(order.getCustomerId());
        String customerPhone = resolveAuthoritativeCustomerPhone(order.getCustomerId());

        if (customerName == null || customerName.isBlank()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "MISSING_MANDATORY_DOMAIN_DATA");
            err.put("message", "Customer name is missing from authoritative order and customer domain records.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        String customerAddress = order.getDeliveryAddressJson();
        if (customerAddress == null || customerAddress.isBlank()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "MISSING_ORDER_DELIVERY_ADDRESS");
            err.put("message", "Authoritative delivery address is missing from order record.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        String merchantName = resolveAuthoritativeMerchantName(order.getSellerId());
        String merchantAddress = resolveAuthoritativeMerchantAddress(order.getSellerId());
        if (merchantName == null || merchantName.isBlank() || merchantAddress == null || merchantAddress.isBlank()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "MISSING_MANDATORY_DOMAIN_DATA");
            err.put("message", "Authoritative merchant domain details are missing for store/seller: " + order.getSellerId());
        String secretOtp = (order.getDeliveryOtp() != null && order.getDeliveryOtp().length() == 6)
            ? order.getDeliveryOtp()
            : String.format("%06d", 100000 + random.nextInt(900000));
        String secretOtpHash = hashDeliveryOtp(secretOtp);

        DeliverySession session = DeliverySession.builder()
            .deliveryId("del_" + UUID.randomUUID().toString())
            .orderId(orderIdStr)
            .riderId(assignedRiderId)
            .riderName(riderName)
            .riderPhone(riderPhone)
            .riderVehicle(riderVehicle)
            .customerId(order.getCustomerId().toString())
            .customerName(customerName)
            .customerPhone(customerPhone)
            .customerAddress(customerAddress)
            .merchantName(merchantName)
            .merchantAddress(merchantAddress)
            .state("ASSIGNED")
            .secretOtpHash(secretOtpHash)
            .otpAttemptsLeft(3)
            .otpVerified(false)
            .isCod("COD".equalsIgnoreCase(order.getPaymentMethod()))
            .codAmount(order.getTotalAmount())
            .codCollectedAmount(BigDecimal.ZERO)
            .codReconciled(false)
            .latestSequenceNumber(0L)
            .latestServerTimestamp(System.currentTimeMillis())
            .isStale(false)
            .build();

        DeliverySession saved = deliverySessionRepository.save(session);
        persistAndBroadcastEvent(saved.getOrderId(), saved.getDeliveryId(), "SESSION_CREATED", buildOpsDTO(saved));
        return ResponseEntity.ok(buildOpsDTO(saved));
    }

    @GetMapping({"/order/{orderId}", "/session/{deliveryId}"})
    public ResponseEntity<?> getSession(@PathVariable String orderId, Principal principal) {
        String callerId;
        try {
            callerId = requireAuthenticatedSubject(principal);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<DeliverySession> sessionOpt = findSession(orderId);
        if (sessionOpt.isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "NOT_FOUND");
            err.put("message", "Delivery session not found for ID: " + orderId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
        }
        DeliverySession session = sessionOpt.get();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isRider = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_RIDER"));
        boolean isCustomer = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"));
        boolean isOps = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DISPATCH") || a.getAuthority().equals("ROLE_OPS") || a.getAuthority().equals("ROLE_ADMIN"));

        // Requirements 33, 34, 35, 54, 59: Anti-IDOR subject verification & explicit role checks
        if (isOps) {
            return ResponseEntity.ok(buildOpsDTO(session));
        } else if (isRider && callerId.equals(session.getRiderId())) {
            return ResponseEntity.ok(buildRiderDTO(session));
        } else if (isCustomer && callerId.equals(session.getCustomerId())) {
            return ResponseEntity.ok(buildCustomerDTO(session));
        } else {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "FORBIDDEN");
            err.put("message", "Caller subject mismatch or missing role permission.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
        }
    }

    @PostMapping("/{deliveryId}/telemetry")
    @Transactional
    public ResponseEntity<Map<String, Object>> submitTelemetry(
        @PathVariable String deliveryId,
        @RequestBody Map<String, Object> body,
        Principal principal
    ) {
        String authenticatedRider;
        try {
            authenticatedRider = requireAuthenticatedSubject(principal);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<DeliverySession> sessionOpt = findSession(deliveryId);
        if (sessionOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        DeliverySession session = sessionOpt.get();

        if (!authenticatedRider.equals(session.getRiderId())) {
            Map<String, Object> err = new HashMap<>();
            err.put("accepted", false);
            err.put("error", "FORBIDDEN");
            err.put("message", "Rider identity mismatch. Cannot submit telemetry for unassigned delivery.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
        }

        // Requirement 43: On delivery completion, delivery telemetry must stop
        if ("DELIVERED".equalsIgnoreCase(session.getState()) || "CANCELLED".equalsIgnoreCase(session.getState()) || "FAILED".equalsIgnoreCase(session.getState()) || "RETURNED".equalsIgnoreCase(session.getState())) {
            Map<String, Object> err = new HashMap<>();
            err.put("accepted", false);
            err.put("error", "TERMINAL_STATE");
            err.put("message", "Delivery is in terminal state (" + session.getState() + "). Telemetry updates are stopped.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        // Requirements 51, 52, 53: Validate riderId from body does not mismatch authenticated riderId
        String bodyRiderId = String.valueOf(body.getOrDefault("riderId", "")).trim();
        if (!bodyRiderId.isEmpty() && !bodyRiderId.equals(authenticatedRider)) {
            Map<String, Object> err = new HashMap<>();
            err.put("accepted", false);
            err.put("error", "MISMATCHED_RIDER_BODY_ID");
            err.put("message", "Telemetry body riderId does not match authenticated rider identity.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
        }

        long now = System.currentTimeMillis();

        // Requirements 54, 55, 56: Validate client timestamp
        Long clientTs = body.containsKey("timestamp") ? ((Number) body.get("timestamp")).longValue() : now;
        if (clientTs > now + 15000L) {
            Map<String, Object> err = new HashMap<>();
            err.put("accepted", false);
            err.put("error", "FUTURE_TIMESTAMP");
            err.put("message", "Telemetry rejected: client timestamp is in the future.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }
        if (clientTs < now - 1800000L) { // 30 minutes
            Map<String, Object> err = new HashMap<>();
            err.put("accepted", false);
            err.put("error", "EXCESSIVELY_OLD_TELEMETRY");
            err.put("message", "Telemetry rejected: client timestamp is excessively old (>30m).");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        Long clientSeq = ((Number) body.getOrDefault("sequenceNumber", 1L)).longValue();
        Double lat = ((Number) body.get("latitude")).doubleValue();
        Double lng = ((Number) body.get("longitude")).doubleValue();
        Float speed = ((Number) body.getOrDefault("speedKmh", 0f)).floatValue();
        Float heading = ((Number) body.getOrDefault("heading", 0f)).floatValue();
        Float accuracy = ((Number) body.getOrDefault("accuracyMeters", 5f)).floatValue();

        // Requirement 60: Validate GPS accuracy
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180 || accuracy <= 0 || accuracy > 100.0f) {
            Map<String, Object> err = new HashMap<>();
            err.put("accepted", false);
            err.put("error", accuracy > 100.0f ? "LOW_GPS_ACCURACY" : "INVALID_COORDINATES");
            err.put("message", "Telemetry rejected due to invalid coordinates or low GPS accuracy (" + accuracy + "m)");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        // Requirements 15, 16: Deduplication vs Out-Of-Order sequence handling
        if (clientSeq <= session.getLatestSequenceNumber()) {
            boolean isDuplicate = Objects.equals(clientSeq, session.getLatestSequenceNumber());
            boolean dynamicStale = (now - (session.getLatestServerTimestamp() != null ? session.getLatestServerTimestamp() : 0L)) > 30000L;
            Map<String, Object> resp = new HashMap<>();
            resp.put("ok", true);
            resp.put("accepted", false);
            resp.put("duplicate", isDuplicate);
            resp.put("outOfOrder", !isDuplicate);
            resp.put("code", isDuplicate ? "DUPLICATE_SEQUENCE" : "OUT_OF_ORDER_SEQUENCE");
            resp.put("ackSequenceNumber", session.getLatestSequenceNumber());
            resp.put("serverTimestamp", now);
            resp.put("deliveryState", session.getState());
            resp.put("isStale", dynamicStale);
            resp.put("message", isDuplicate ? "Telemetry sequence duplicate already accepted" : "Telemetry sequence is out of order");
            return ResponseEntity.ok(resp);
        }

        // Requirements 26, 27: Validate impossible GPS jumps & speeds
        if (session.getLatestLatitude() != null && session.getLatestClientTimestamp() != null) {
            double distM = calculateDistanceMeters(session.getLatestLatitude(), session.getLatestLongitude(), lat, lng);
            long timeDeltaMs = clientTs - session.getLatestClientTimestamp();
            double speedMps = timeDeltaMs > 0 ? (distM / (timeDeltaMs / 1000.0)) : 0.0;
            if (distM > 100000 || speed > 180.0f || speedMps > 50.0) {
                Map<String, Object> err = new HashMap<>();
                err.put("accepted", false);
                err.put("error", "IMPOSSIBLE_LOCATION_JUMP");
                err.put("message", "GPS telemetry rejected: jump of " + Math.round(distM / 1000) + "km or speed " + speed + " km/h");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
            }
        }

        session.setLatestLatitude(lat);
        session.setLatestLongitude(lng);
        session.setLatestSpeedKmh(speed);
        session.setLatestHeading(heading);
        session.setLatestAccuracyMeters(accuracy);
        session.setLatestSequenceNumber(clientSeq);
        session.setLatestServerTimestamp(now);
        session.setLatestClientTimestamp(clientTs);
        session.setIsStale(false);
        deliverySessionRepository.save(session);

        persistAndBroadcastEvent(session.getOrderId(), session.getDeliveryId(), "TELEMETRY_UPDATED", buildCustomerDTO(session));

        boolean calculatedStale = (System.currentTimeMillis() - now) > 30000L;

        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        resp.put("accepted", true);
        resp.put("duplicate", false);
        resp.put("outOfOrder", false);
        resp.put("ackSequenceNumber", clientSeq);
        resp.put("serverTimestamp", now);
        resp.put("deliveryState", session.getState());
        resp.put("isStale", calculatedStale);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{deliveryId}/transition")
    @Transactional
    public ResponseEntity<?> transitionState(
        @PathVariable String deliveryId,
        @RequestBody Map<String, Object> body,
        Principal principal
    ) {
        String authenticatedRider;
        try {
            authenticatedRider = requireAuthenticatedSubject(principal);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<DeliverySession> sessionOpt = findSession(deliveryId);
        if (sessionOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        DeliverySession session = sessionOpt.get();

        if (!authenticatedRider.equals(session.getRiderId())) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "FORBIDDEN");
            err.put("message", "Rider identity mismatch. Cannot transition unassigned delivery.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
        }

        String targetState = String.valueOf(body.getOrDefault("targetState", "")).toUpperCase();
        String idempotencyKey = String.valueOf(body.getOrDefault("idempotencyKey", ""));

        if (!idempotencyKey.isEmpty()) {
            Optional<DeliveryIdempotencyKey> existingKey = idempotencyKeyRepository
                .findByDeliveryIdAndIdempotencyKey(session.getDeliveryId(), idempotencyKey);
            if (existingKey.isPresent()) {
                return ResponseEntity.ok(buildRiderDTO(session));
            }
        }

        List<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(session.getState(), Collections.emptyList());
        if (!allowed.contains(targetState) && !targetState.equals(session.getState())) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "INVALID_TRANSITION");
            err.put("message", "Cannot transition delivery from " + session.getState() + " to " + targetState);
            err.put("currentState", session.getState());
            err.put("allowedNextStates", allowed);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        session.setState(targetState);
        deliverySessionRepository.save(session);

        syncCustomerOrderStatus(session.getOrderId(), targetState);

        if (!idempotencyKey.isEmpty()) {
            idempotencyKeyRepository.save(DeliveryIdempotencyKey.builder()
                .deliveryId(session.getDeliveryId())
                .idempotencyKey(idempotencyKey)
                .resultingState(targetState)
                .build());
        }

        persistAndBroadcastEvent(session.getOrderId(), session.getDeliveryId(), "STATE_TRANSITION:" + targetState, buildCustomerDTO(session));
        return ResponseEntity.ok(buildRiderDTO(session));
    }

    @PostMapping("/{deliveryId}/verify-otp")
    @Transactional
    public ResponseEntity<Map<String, Object>> verifyOtp(
        @PathVariable String deliveryId,
        @RequestBody Map<String, String> body,
        Principal principal
    ) {
        String authenticatedRider;
        try {
            authenticatedRider = requireAuthenticatedSubject(principal);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<DeliverySession> sessionOpt = findSession(deliveryId);
        if (sessionOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        DeliverySession session = sessionOpt.get();

        if (!authenticatedRider.equals(session.getRiderId())) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "FORBIDDEN");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
        }

        // Idempotency: If already verified, return success without duplicate events!
        if (Boolean.TRUE.equals(session.getOtpVerified())) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("verified", true);
            resp.put("message", "OTP PIN Already Verified");
            return ResponseEntity.ok(resp);
        }

        if (session.getOtpAttemptsLeft() <= 0) {
            Map<String, Object> err = new HashMap<>();
            err.put("verified", false);
            err.put("error", "OTP_ATTEMPTS_EXHAUSTED");
            err.put("message", "Maximum OTP PIN attempts exceeded. Contact support.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        String otp = body.getOrDefault("otp", "");
        Map<String, Object> resp = new HashMap<>();
        String submittedHash = hashDeliveryOtp(otp);
        boolean matches = session.getSecretOtpHash() != null && java.security.MessageDigest.isEqual(
            submittedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            session.getSecretOtpHash().getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        if (matches) {
            session.setOtpVerified(true);
            deliverySessionRepository.save(session);
            persistAndBroadcastEvent(session.getOrderId(), session.getDeliveryId(), "OTP_VERIFIED", buildCustomerDTO(session));

            resp.put("verified", true);
            resp.put("message", "OTP PIN Verified Successfully");
            return ResponseEntity.ok(resp);
        } else {
            session.setOtpAttemptsLeft(Math.max(0, session.getOtpAttemptsLeft() - 1));
            deliverySessionRepository.save(session);

            resp.put("verified", false);
            resp.put("message", "Incorrect OTP PIN. " + session.getOtpAttemptsLeft() + " attempts remaining.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
        }
    }

    @PostMapping("/{deliveryId}/complete-cod")
    @Transactional
    public ResponseEntity<Map<String, Object>> completeCod(
        @PathVariable String deliveryId,
        @RequestBody Map<String, Object> body,
        Principal principal
    ) {
        String authenticatedRider;
        try {
            authenticatedRider = requireAuthenticatedSubject(principal);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<DeliverySession> sessionOpt = findSession(deliveryId);
        if (sessionOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        DeliverySession session = sessionOpt.get();

        if (!authenticatedRider.equals(session.getRiderId())) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "FORBIDDEN");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
        }

        // Idempotency: If already reconciled, return success without duplicate financial entries!
        if (Boolean.TRUE.equals(session.getCodReconciled())) {
            BigDecimal change = session.getCodCollectedAmount().subtract(session.getCodAmount()).max(BigDecimal.ZERO);
            Map<String, Object> resp = new HashMap<>();
            resp.put("reconciled", true);
            resp.put("changeToReturn", change);
            resp.put("message", "COD Cash Collection Already Confirmed");
            return ResponseEntity.ok(resp);
        }

        BigDecimal collected = new BigDecimal(String.valueOf(body.getOrDefault("collectedAmount", 0)));
        Map<String, Object> resp = new HashMap<>();

        if (collected.compareTo(session.getCodAmount()) >= 0) {
            session.setCodCollectedAmount(collected);
            session.setCodReconciled(true);
            deliverySessionRepository.save(session);

            BigDecimal change = collected.subtract(session.getCodAmount());
            persistAndBroadcastEvent(session.getOrderId(), session.getDeliveryId(), "COD_CONFIRMED", buildCustomerDTO(session));

            resp.put("reconciled", true);
            resp.put("changeToReturn", change);
            resp.put("message", "COD Cash Collection Confirmed. Change to return: ₹" + change);
            return ResponseEntity.ok(resp);
        } else {
            resp.put("reconciled", false);
            resp.put("message", "Collected cash amount is less than required COD amount.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
        }
    }

    @PostMapping("/{deliveryId}/complete")
    @Transactional
    public ResponseEntity<?> completeDelivery(@PathVariable String deliveryId, Principal principal) {
        String authenticatedRider;
        try {
            authenticatedRider = requireAuthenticatedSubject(principal);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<DeliverySession> sessionOpt = findSession(deliveryId);
        if (sessionOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        DeliverySession session = sessionOpt.get();

        if (!authenticatedRider.equals(session.getRiderId())) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "FORBIDDEN");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
        }

        // Idempotency: If already DELIVERED, return existing session without duplicate events!
        if ("DELIVERED".equals(session.getState())) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("ok", true);
            resp.put("session", buildRiderDTO(session));
            return ResponseEntity.ok(resp);
        }

        if (!"HANDOFF_STARTED".equals(session.getState())) {
            Map<String, Object> err = new HashMap<>();
            err.put("message", "Cannot complete delivery before HANDOFF_STARTED state.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        if (!session.getOtpVerified()) {
            Map<String, Object> err = new HashMap<>();
            err.put("message", "Cannot complete delivery before OTP verification.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        if (session.getIsCod() && !session.getCodReconciled()) {
            Map<String, Object> err = new HashMap<>();
            err.put("message", "Cannot complete delivery before COD reconciliation.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        session.setState("DELIVERED");
        deliverySessionRepository.save(session);

        syncCustomerOrderStatus(session.getOrderId(), "DELIVERED");
        persistAndBroadcastEvent(session.getOrderId(), session.getDeliveryId(), "DELIVERED", buildCustomerDTO(session));

        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        resp.put("session", buildRiderDTO(session));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/sse-ticket")
    public ResponseEntity<Map<String, Object>> createSseTicket(
        @RequestBody(required = false) Map<String, String> body,
        Principal principal
    ) {
        String callerId;
        try {
            callerId = requireAuthenticatedSubject(principal);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String role = auth != null && !auth.getAuthorities().isEmpty()
            ? auth.getAuthorities().iterator().next().getAuthority()
            : "ROLE_CUSTOMER";

        String targetId = null;
        if (body != null) {
            targetId = body.getOrDefault("targetId", body.getOrDefault("orderId", body.get("deliveryId")));
        }

        String token = com.commerceos.order.security.JwtAuthenticationFilter.generateJwtToken(callerId, role, 300000L);
        String ticket = com.commerceos.order.security.JwtAuthenticationFilter.createSseTicket(token, targetId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("ticket", ticket);
        resp.put("expiresInSeconds", 10);
        return ResponseEntity.ok(resp);
    }

    @GetMapping(value = {"/order/{orderId}/stream", "/session/{orderId}/stream"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(
        @PathVariable String orderId,
        @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader,
        @RequestParam(value = "lastEventId", required = false) String lastEventIdParam,
        Principal principal
    ) {
        String callerId;
        try {
            callerId = requireAuthenticatedSubject(principal);
        } catch (Exception e) {
            SseEmitter errEmitter = new SseEmitter(0L);
            errEmitter.completeWithError(new IllegalAccessException("UNAUTHORIZED_SSE_STREAM"));
            return errEmitter;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isStaff = auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_DISPATCH") || a.getAuthority().equals("ROLE_OPS") || a.getAuthority().equals("ROLE_ADMIN"));

        Optional<DeliverySession> sessionOpt = findSession(orderId);
        String targetOrderId = orderId;
        String targetDeliveryId = null;

        if (sessionOpt.isPresent()) {
            DeliverySession session = sessionOpt.get();
            targetOrderId = session.getOrderId();
            targetDeliveryId = session.getDeliveryId();

            boolean isCustomer = callerId.equals(session.getCustomerId());
            boolean isRider = callerId.equals(session.getRiderId());

            if (!isCustomer && !isRider && !isStaff) {
                SseEmitter errEmitter = new SseEmitter(0L);
                errEmitter.completeWithError(new IllegalAccessException("FORBIDDEN_STREAM_ACCESS"));
                return errEmitter;
            }
        } else {
            try {
                UUID orderUuid = UUID.fromString(orderId);
                Optional<CustomerOrder> orderOpt = customerOrderRepository.findById(orderUuid);
                if (orderOpt.isPresent()) {
                    CustomerOrder order = orderOpt.get();
                    if (!callerId.equals(order.getCustomerId().toString()) && !isStaff) {
                        SseEmitter errEmitter = new SseEmitter(0L);
                        errEmitter.completeWithError(new IllegalAccessException("FORBIDDEN_STREAM_ACCESS"));
                        return errEmitter;
                    }
                }
            } catch (Exception ignored) {}
        }

        SseEmitter emitter = new SseEmitter(0L);
        orderSseEmitters.computeIfAbsent(targetOrderId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        final String activeOrderId = targetOrderId;
        emitter.onCompletion(() -> removeEmitter(activeOrderId, emitter));
        emitter.onTimeout(() -> removeEmitter(activeOrderId, emitter));

        String lastIdStr = lastEventIdHeader != null ? lastEventIdHeader : lastEventIdParam;
        Long lastSeq = 0L;
        if (lastIdStr != null && !lastIdStr.isEmpty()) {
            try { lastSeq = Long.parseLong(lastIdStr); } catch (Exception ignored) {}
        }

        List<DeliveryEvent> missedEvents = (targetDeliveryId != null)
            ? deliveryEventRepository.findByDeliveryIdAndEventSequenceGreaterThanOrderByEventSequenceAsc(targetDeliveryId, lastSeq)
            : deliveryEventRepository.findByOrderIdAndEventSequenceGreaterThanOrderByEventSequenceAsc(targetOrderId, lastSeq);

        for (DeliveryEvent evt : missedEvents) {
            try {
                emitter.send(SseEmitter.event()
                    .id(String.valueOf(evt.getEventSequence()))
                    .name("message")
                    .data(evt.getPayloadJson()));
            } catch (Exception e) {
                removeEmitter(targetOrderId, emitter);
                return emitter;
            }
        }

        return emitter;
    }

    @GetMapping("/rider/profile")
    public ResponseEntity<?> getRiderProfile(Principal principal) {
        String riderId;
        try {
            riderId = requireAuthenticatedSubject(principal);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("riderId", riderId);
        profile.put("name", "Rider Partner");
        profile.put("phone", "");
        profile.put("vehicleNumber", "");
        profile.put("rating", 4.9);
        profile.put("shiftStatus", "ONLINE_AVAILABLE");
        profile.put("completedToday", 0);
        profile.put("earningsTodayFormatted", "₹0.00");
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/rider/shift-status")
    public ResponseEntity<?> updateShiftStatus(@RequestBody Map<String, String> body, Principal principal) {
        try {
            requireAuthenticatedSubject(principal);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String status = body.getOrDefault("status", "ONLINE_AVAILABLE").toUpperCase();
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        resp.put("shiftStatus", status);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/rider/active-session")
    public ResponseEntity<?> getActiveSession(Principal principal) {
        String riderId;
        try {
            riderId = requireAuthenticatedSubject(principal);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<DeliverySession> sessionOpt = deliverySessionRepository.findByRiderIdAndStateNot(riderId, "DELIVERED");
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(buildRiderDTO(sessionOpt.get()));
    }

    private static final Map<String, String> DELIVERY_TO_ORDER_STATE_MAP = Map.ofEntries(
        Map.entry("ASSIGNED", "ALLOCATED_DARK_STORE"),
        Map.entry("ACCEPTED", "ALLOCATED_DARK_STORE"),
        Map.entry("EN_ROUTE_PICKUP", "RIDER_EN_ROUTE_PICKUP"),
        Map.entry("ARRIVED_PICKUP", "ARRIVED_DARK_STORE"),
        Map.entry("PICKED_UP", "OUT_FOR_DELIVERY"),
        Map.entry("EN_ROUTE_CUSTOMER", "OUT_FOR_DELIVERY"),
        Map.entry("ARRIVED_CUSTOMER", "ARRIVED_AT_DELIVERY"),
        Map.entry("HANDOFF_STARTED", "HANDOFF_STARTED"),
        Map.entry("DELIVERED", "DELIVERED"),
        Map.entry("DECLINED", "CANCELLED"),
        Map.entry("CANCELLED", "CANCELLED"),
        Map.entry("STORE_CLOSED", "DELIVERY_ATTEMPT_FAILED"),
        Map.entry("DAMAGED_PACKAGE", "DELIVERY_ATTEMPT_FAILED"),
        Map.entry("RETURN_TO_STORE", "DELIVERY_ATTEMPT_FAILED"),
        Map.entry("WRONG_ADDRESS", "DELIVERY_ATTEMPT_FAILED"),
        Map.entry("CUSTOMER_UNREACHABLE", "DELIVERY_ATTEMPT_FAILED"),
        Map.entry("FAILED", "DELIVERY_ATTEMPT_FAILED")
    );

    private void syncCustomerOrderStatus(String orderId, String deliveryState) {
        try {
            UUID orderUuid = UUID.fromString(orderId);
            Optional<CustomerOrder> orderOpt = customerOrderRepository.findById(orderUuid);
            if (orderOpt.isPresent()) {
                CustomerOrder order = orderOpt.get();
                String mappedStatus = DELIVERY_TO_ORDER_STATE_MAP.getOrDefault(deliveryState, order.getOrderStatus());
                order.setOrderStatus(mappedStatus);
                if ("DELIVERED".equals(deliveryState) && "COD".equalsIgnoreCase(order.getPaymentMethod())) {
                    order.setPaymentStatus("COD_COLLECTED");
                }
                customerOrderRepository.save(order);
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(DeliveryController.class)
                .error("OBSERVABLE ERROR: CustomerOrder status synchronization failed for orderId={}: {}", orderId, e.getMessage(), e);
            throw new RuntimeException("Delivery -> CustomerOrder synchronization failed for order " + orderId, e);
        }
    }

    private com.commerceos.order.domain.UserAccount findUserAccount(String identifier) {
        if (identifier == null || identifier.isBlank()) return null;
        try {
            UUID uuid = UUID.fromString(identifier);
            Optional<com.commerceos.order.domain.UserAccount> byId = userAccountRepository.findById(uuid);
            if (byId.isPresent()) return byId.get();
        } catch (Exception e) {
            // Identifier is not a UUID formatted string; try phone or email fallback
        }
        return userAccountRepository.findByPhone(identifier)
            .or(() -> userAccountRepository.findByEmail(identifier))
            .orElse(null);
    }

    private static String hashDeliveryOtp(String otp) {
        if (otp == null || otp.isBlank()) return "";
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((otp + "_commerceos_delivery_salt_2026").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("OTP hashing failed", e);
        }
    }

    private String resolveAuthoritativeRiderName(String riderId) {
        com.commerceos.order.domain.UserAccount rider = findUserAccount(riderId);
        return rider != null ? rider.getFullName() : null;
    }

    private String resolveAuthoritativeRiderPhone(String riderId) {
        com.commerceos.order.domain.UserAccount rider = findUserAccount(riderId);
        return rider != null ? rider.getPhone() : null;
    }

    private String resolveAuthoritativeRiderVehicle(String riderId) {
        com.commerceos.order.domain.UserAccount rider = findUserAccount(riderId);
        if (rider != null && rider.getVehicleNumber() != null && !rider.getVehicleNumber().isBlank()) {
            return rider.getVehicleNumber();
        }
        return rider != null ? rider.getVehicleType() : null;
    }

    private String resolveAuthoritativeCustomerName(UUID customerId) {
        if (customerId == null) return null;
        Optional<com.commerceos.order.domain.UserAccount> custOpt = userAccountRepository.findById(customerId);
        return custOpt.map(com.commerceos.order.domain.UserAccount::getFullName).orElse(null);
    }

    private String resolveAuthoritativeCustomerPhone(UUID customerId) {
        if (customerId == null) return null;
        Optional<com.commerceos.order.domain.UserAccount> custOpt = userAccountRepository.findById(customerId);
        return custOpt.map(com.commerceos.order.domain.UserAccount::getPhone).orElse(null);
    }

    private String resolveAuthoritativeMerchantName(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) return null;
        Optional<com.commerceos.order.domain.MerchantStore> storeOpt = merchantStoreRepository.findBySellerId(sellerId)
            .or(() -> merchantStoreRepository.findById(sellerId));
        return storeOpt.map(com.commerceos.order.domain.MerchantStore::getStoreName).orElse(null);
    }

    private String resolveAuthoritativeMerchantAddress(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) return null;
        Optional<com.commerceos.order.domain.MerchantStore> storeOpt = merchantStoreRepository.findBySellerId(sellerId)
            .or(() -> merchantStoreRepository.findById(sellerId));
        return storeOpt.map(com.commerceos.order.domain.MerchantStore::getStoreAddress).orElse(null);
    }

    private void persistAndBroadcastEvent(String orderId, String deliveryId, String eventType, Object sessionData) {
        Long seq = deliveryEventRepository.getNextEventSequenceAtomic(deliveryId);
        String eventId = "evt_" + UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("eventId", eventId);
        eventMap.put("deliveryId", deliveryId);
        eventMap.put("orderId", orderId);
        eventMap.put("eventType", eventType);
        eventMap.put("eventSequence", seq);
        eventMap.put("serverTimestamp", now);
        eventMap.put("session", sessionData);

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(eventMap);
        } catch (Exception e) {
            payloadJson = "{}";
        }

        DeliveryEvent event = deliveryEventRepository.saveAndFlush(DeliveryEvent.builder()
            .eventId(eventId)
            .deliveryId(deliveryId)
            .orderId(orderId)
            .eventSequence(seq)
            .eventType(eventType)
            .serverTimestamp(now)
            .payloadJson(payloadJson)
            .build());

        final String finalPayloadJson = payloadJson;
        final long finalSeq = seq;

        Runnable broadcastTask = () -> {
            List<SseEmitter> emitters = orderSseEmitters.get(orderId);
            if (emitters != null) {
                for (SseEmitter emitter : emitters) {
                    try {
                        emitter.send(SseEmitter.event()
                            .id(String.valueOf(finalSeq))
                            .name("message")
                            .data(finalPayloadJson));
                    } catch (Exception e) {
                        removeEmitter(orderId, emitter);
                    }
                }
            }
        };

        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        broadcastTask.run();
                    }
                }
            );
        } else {
            broadcastTask.run();
        }
    }

    private void removeEmitter(String orderId, SseEmitter emitter) {
        List<SseEmitter> emitters = orderSseEmitters.get(orderId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }

    private RiderDeliveryDTO buildRiderDTO(DeliverySession session) {
        String phone = session.getCustomerPhone();
        String maskedPhone = (phone != null && phone.length() > 5)
            ? phone.substring(0, 3) + "****" + phone.substring(phone.length() - 3)
            : "*******";

        RiderDeliveryDTO.RiderTelemetryDTO telem = null;
        if (session.getLatestLatitude() != null) {
            telem = RiderDeliveryDTO.RiderTelemetryDTO.builder()
                .latitude(session.getLatestLatitude())
                .longitude(session.getLatestLongitude())
                .speedKmh(session.getLatestSpeedKmh())
                .heading(session.getLatestHeading())
                .accuracyMeters(session.getLatestAccuracyMeters())
                .sequenceNumber(session.getLatestSequenceNumber())
                .serverTimestamp(session.getLatestServerTimestamp())
                .isStale(session.getIsStale())
                .build();
        }

        return RiderDeliveryDTO.builder()
            .deliveryId(session.getDeliveryId())
            .orderId(session.getOrderId())
            .riderId(session.getRiderId())
            .customerId(session.getCustomerId())
            .customerName(session.getCustomerName())
            .maskedCustomerPhone(maskedPhone)
            .customerAddress(session.getCustomerAddress())
            .customerLat(session.getCustomerLat())
            .customerLng(session.getCustomerLng())
            .merchantName(session.getMerchantName())
            .merchantAddress(session.getMerchantAddress())
            .merchantLat(session.getMerchantLat())
            .merchantLng(session.getMerchantLng())
            .payoutFormatted("Payout Unavailable")
            .distanceKm(null)
            .estimatedTimeMins(null)
            .state(session.getState())
            .otpAttemptsLeft(session.getOtpAttemptsLeft())
            .otpVerified(session.getOtpVerified())
            .isCod(session.getIsCod())
            .codAmount(session.getCodAmount())
            .codCollectedAmount(session.getCodCollectedAmount())
            .codReconciled(session.getCodReconciled())
            .telemetry(telem)
            .build();
    }

    private CustomerTrackingDTO buildCustomerDTO(DeliverySession session) {
        CustomerTrackingDTO.CustomerTelemetryDTO telem = null;
        if (session.getLatestLatitude() != null) {
            telem = CustomerTrackingDTO.CustomerTelemetryDTO.builder()
                .latitude(session.getLatestLatitude())
                .longitude(session.getLatestLongitude())
                .speedKmh(session.getLatestSpeedKmh())
                .heading(session.getLatestHeading())
                .sequenceNumber(session.getLatestSequenceNumber())
                .serverTimestamp(session.getLatestServerTimestamp())
                .isStale(session.getIsStale())
                .build();
        }

        return CustomerTrackingDTO.builder()
            .orderId(session.getOrderId())
            .deliveryId(session.getDeliveryId())
            .state(session.getState())
            .riderName(session.getRiderName())
            .riderVehicle(session.getRiderVehicle())
            .riderRating(null)
            .merchantLat(session.getMerchantLat())
            .merchantLng(session.getMerchantLng())
            .customerLat(session.getCustomerLat())
            .customerLng(session.getCustomerLng())
            .liveRiderTelemetry(telem)
            .trackingStatusText("DELIVERED".equals(session.getState()) ? "Order Delivered" : "Out for delivery")
            .estimatedArrivalMins(null)
            .isStale(session.getIsStale())
            .lastUpdatedTimestamp(session.getLatestServerTimestamp())
            .build();
    }

    private OpsDeliveryDTO buildOpsDTO(DeliverySession session) {
        OpsDeliveryDTO.OpsTelemetryDTO telem = null;
        if (session.getLatestLatitude() != null) {
            telem = OpsDeliveryDTO.OpsTelemetryDTO.builder()
                .latitude(session.getLatestLatitude())
                .longitude(session.getLatestLongitude())
                .speedKmh(session.getLatestSpeedKmh())
                .heading(session.getLatestHeading())
                .accuracyMeters(session.getLatestAccuracyMeters())
                .sequenceNumber(session.getLatestSequenceNumber())
                .serverTimestamp(session.getLatestServerTimestamp())
                .isStale(session.getIsStale())
                .build();
        }

        return OpsDeliveryDTO.builder()
            .deliveryId(session.getDeliveryId())
            .orderId(session.getOrderId())
            .riderId(session.getRiderId())
            .riderName(session.getRiderName())
            .riderPhone(session.getRiderPhone())
            .riderVehicle(session.getRiderVehicle())
            .customerId(session.getCustomerId())
            .customerName(session.getCustomerName())
            .customerPhone(session.getCustomerPhone())
            .customerAddress(session.getCustomerAddress())
            .customerLat(session.getCustomerLat())
            .customerLng(session.getCustomerLng())
            .merchantName(session.getMerchantName())
            .merchantAddress(session.getMerchantAddress())
            .merchantLat(session.getMerchantLat())
            .merchantLng(session.getMerchantLng())
            .state(session.getState())
            .otpAttemptsLeft(session.getOtpAttemptsLeft())
            .otpVerified(session.getOtpVerified())
            .isCod(session.getIsCod())
            .codAmount(session.getCodAmount())
            .codCollectedAmount(session.getCodCollectedAmount())
            .codReconciled(session.getCodReconciled())
            .telemetry(telem)
            .build();
    }

    private double calculateDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
