package com.commerceos.return.controller;

import com.commerceos.return.domain.ReturnRequestEntity;
import com.commerceos.return.repository.ReturnRequestRepository;
import com.commerceos.return.security.JwtAuthValidator;
import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/returns")
public class ReturnController {

    private final ReturnRequestRepository returnRepository;
    private final JwtAuthValidator jwtAuthValidator;

    public ReturnController(ReturnRequestRepository returnRepository, JwtAuthValidator jwtAuthValidator) {
        this.returnRepository = returnRepository;
        this.jwtAuthValidator = jwtAuthValidator;
    }

    private boolean isAuthorizedCaller(String authHeader, String targetCustomerId) {
        Optional<JwtAuthValidator.AuthenticatedPrincipal> principalOpt = jwtAuthValidator.authenticate(authHeader);
        if (principalOpt.isEmpty()) {
            return false;
        }
        return principalOpt.get().isOwnerOrAdmin(targetCustomerId);
    }

    private boolean isInspectorOrAdmin(String authHeader) {
        Optional<JwtAuthValidator.AuthenticatedPrincipal> principalOpt = jwtAuthValidator.authenticate(authHeader);
        if (principalOpt.isEmpty()) {
            return false;
        }
        return principalOpt.get().hasRole("ROLE_INSPECTOR") || principalOpt.get().hasRole("ROLE_ADMIN");
    }

    @PostMapping("/request")
    public ResponseEntity<?> createReturnRequest(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ReturnRequestDto request
    ) {
        if (request.getOrderId() == null || request.getOrderId().isBlank()) {
            return ResponseEntity.badRequest().body("ORDER_ID_REQUIRED");
        }
        if (request.getCustomerId() == null || request.getCustomerId().isBlank()) {
            return ResponseEntity.badRequest().body("CUSTOMER_ID_REQUIRED");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            return ResponseEntity.badRequest().body("RETURN_REASON_REQUIRED");
        }

        if (!isAuthorizedCaller(authHeader, request.getCustomerId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("FORBIDDEN: Return creation requires authenticated customer ownership.");
        }

        // Server-authoritative calculation based on item price and quantity
        BigDecimal itemPrice = (request.getItemPrice() != null && request.getItemPrice().compareTo(BigDecimal.ZERO) > 0)
                ? request.getItemPrice()
                : BigDecimal.ZERO;
        int qty = Math.max(1, request.getQuantity());
        BigDecimal calculatedRefund = itemPrice.multiply(BigDecimal.valueOf(qty));

        ReturnRequestEntity entity = ReturnRequestEntity.builder()
                .orderId(request.getOrderId())
                .customerId(request.getCustomerId())
                .reason(request.getReason())
                .calculatedRefundAmount(calculatedRefund)
                .status("RETURN_REQUESTED_PENDING_INSPECTION")
                .pickupScheduledAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        ReturnRequestEntity saved = returnRepository.save(entity);
        return ResponseEntity.ok(toResponse(saved));
    }

    @GetMapping("/{returnId}")
    public ResponseEntity<?> getReturnStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String returnId
    ) {
        return returnRepository.findById(returnId).map(entity -> {
            if (!isAuthorizedCaller(authHeader, entity.getCustomerId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.ok(toResponse(entity));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{returnId}/approve")
    public ResponseEntity<?> approveReturn(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String returnId,
            @RequestParam String inspectorId
    ) {
        if (!isInspectorOrAdmin(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("FORBIDDEN: Return approval requires inspector or admin role.");
        }

        return returnRepository.findById(returnId).map(entity -> {
            entity.setStatus("APPROVED_FOR_PICKUP");
            entity.setInspectedBy(inspectorId);
            ReturnRequestEntity updated = returnRepository.save(entity);
            return ResponseEntity.ok(toResponse(updated));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{returnId}/receive-and-quality-check")
    public ResponseEntity<?> receiveAndQualityCheck(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String returnId,
            @RequestParam boolean passed,
            @RequestParam String notes
    ) {
        if (!isInspectorOrAdmin(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("FORBIDDEN: Quality check requires inspector or admin role.");
        }

        return returnRepository.findById(returnId).map(entity -> {
            if (passed) {
                entity.setStatus("QUALITY_PASSED_REFUND_AUTHORIZED");
            } else {
                entity.setStatus("QUALITY_REJECTED");
            }
            ReturnRequestEntity updated = returnRepository.save(entity);
            return ResponseEntity.ok(toResponse(updated));
        }).orElse(ResponseEntity.notFound().build());
    }

    private ReturnRequestResponse toResponse(ReturnRequestEntity entity) {
        return ReturnRequestResponse.builder()
                .returnId(entity.getId())
                .orderId(entity.getOrderId())
                .customerId(entity.getCustomerId())
                .reason(entity.getReason())
                .refundAmount(entity.getCalculatedRefundAmount())
                .status(entity.getStatus())
                .pickupScheduledAt(entity.getPickupScheduledAt() != null ? entity.getPickupScheduledAt().toString() : null)
                .createdAt(entity.getCreatedAt().toString())
                .build();
    }

    @Data
    public static class ReturnRequestDto {
        private String orderId;
        private String customerId;
        private String reason;
        private BigDecimal itemPrice;
        private int quantity = 1;
    }

    @Data
    @Builder
    public static class ReturnRequestResponse {
        private String returnId;
        private String orderId;
        private String customerId;
        private String reason;
        private BigDecimal refundAmount;
        private String status;
        private String pickupScheduledAt;
        private String createdAt;
    }
}
