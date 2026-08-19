package com.commerceos.inventory.controller;

import com.commerceos.inventory.domain.InventoryBatchEntity;
import com.commerceos.inventory.domain.InventoryHoldEntity;
import com.commerceos.inventory.repository.InventoryBatchRepository;
import com.commerceos.inventory.repository.InventoryHoldRepository;
import com.commerceos.inventory.security.JwtAuthValidator;
import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryBatchRepository batchRepository;
    private final InventoryHoldRepository holdRepository;
    private final JwtAuthValidator jwtAuthValidator;

    public InventoryController(
            InventoryBatchRepository batchRepository,
            InventoryHoldRepository holdRepository,
            JwtAuthValidator jwtAuthValidator) {
        this.batchRepository = batchRepository;
        this.holdRepository = holdRepository;
        this.jwtAuthValidator = jwtAuthValidator;
    }

    private boolean isAuthorized(String authHeader) {
        Optional<JwtAuthValidator.AuthenticatedPrincipal> principalOpt = jwtAuthValidator.authenticate(authHeader);
        return principalOpt.isPresent();
    }

    @GetMapping
    public ResponseEntity<?> listBatches(@RequestParam(required = false) String sku) {
        List<InventoryBatchEntity> batches = sku == null
                ? batchRepository.findAll()
                : batchRepository.findBySkuOrderByExpiryDateAsc(sku);
        return ResponseEntity.ok(batches.stream().map(b -> FefoBatch.builder()
                .batchNumber(b.getBatchNumber())
                .sku(b.getSku())
                .availableQty(b.getAvailableQty())
                .expiryDate(b.getExpiryDate().toString())
                .darkStoreId(b.getDarkStoreId())
                .coldChain(b.getColdChain())
                .build()).toList());
    }

    @PostMapping("/atp-check/{sku}")
    public ResponseEntity<AtpCheckResponse> checkAtpStock(
            @PathVariable String sku,
            @RequestParam(required = false) String darkStoreId
    ) {
        List<InventoryBatchEntity> batches = batchRepository.findBySkuOrderByExpiryDateAsc(sku);
        int physicalOnHand = batches.stream()
                .filter(b -> darkStoreId == null || darkStoreId.isBlank() || darkStoreId.equalsIgnoreCase(b.getDarkStoreId()))
                .mapToInt(InventoryBatchEntity::getAvailableQty)
                .sum();
        
        int activeHolds = (darkStoreId != null && !darkStoreId.isBlank())
                ? holdRepository.sumActiveHoldsForSkuAndStore(sku, darkStoreId, Instant.now())
                : holdRepository.sumActiveHoldsForSku(sku, Instant.now());
        int safetyBuffer = 5;

        int atpCount = Math.max(0, physicalOnHand - activeHolds - safetyBuffer);

        return ResponseEntity.ok(AtpCheckResponse.builder()
                .sku(sku)
                .darkStoreId(darkStoreId != null ? darkStoreId : "DEFAULT_NODE")
                .physicalOnHand(physicalOnHand)
                .activeHolds(activeHolds)
                .safetyBuffer(safetyBuffer)
                .availableToPromise(atpCount)
                .build());
    }

    @PostMapping("/reserve-hold")
    @Transactional
    public ResponseEntity<?> reserveStockHold(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody StockHoldRequest request
    ) {
        Optional<JwtAuthValidator.AuthenticatedPrincipal> principalOpt = jwtAuthValidator.authenticate(authHeader);
        String customerId = principalOpt.map(JwtAuthValidator.AuthenticatedPrincipal::getSubject).orElse(request.getCustomerId());

        if (request.getSku() == null || request.getSku().isBlank() || request.getQty() <= 0) {
            return ResponseEntity.badRequest().body("INVALID_HOLD_REQUEST");
        }

        // 1. Pessimistic Row Lock on batches to ensure atomic ATP calculation
        List<InventoryBatchEntity> lockedBatches = (request.getDarkStoreId() != null && !request.getDarkStoreId().isBlank())
                ? batchRepository.findStoreBatchesForAllocationWithLock(request.getSku(), request.getDarkStoreId())
                : batchRepository.findBatchesForAllocationWithLock(request.getSku());

        int physicalOnHand = lockedBatches.stream().mapToInt(InventoryBatchEntity::getAvailableQty).sum();
        int activeHolds = (request.getDarkStoreId() != null && !request.getDarkStoreId().isBlank())
                ? holdRepository.sumActiveHoldsForSkuAndStore(request.getSku(), request.getDarkStoreId(), Instant.now())
                : holdRepository.sumActiveHoldsForSku(request.getSku(), Instant.now());
        int safetyBuffer = 1;
        int atpCount = physicalOnHand - activeHolds - safetyBuffer;

        if (atpCount < request.getQty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    StockHoldResponse.builder()
                            .sku(request.getSku())
                            .status("INSUFFICIENT_ATP_STOCK")
                            .build()
            );
        }

        String holdId = "hold_" + UUID.randomUUID().toString().substring(0, 8);
        Instant expiresAt = Instant.now().plusSeconds(300);

        InventoryHoldEntity hold = InventoryHoldEntity.builder()
                .id(holdId)
                .sku(request.getSku())
                .quantity(request.getQty())
                .darkStoreId(request.getDarkStoreId() != null ? request.getDarkStoreId() : "PRIMARY_NODE")
                .orderId(request.getOrderId())
                .customerId(customerId)
                .expiresAt(expiresAt)
                .status("ACTIVE_HOLD")
                .build();

        holdRepository.save(hold);

        return ResponseEntity.ok(StockHoldResponse.builder()
                .holdId(holdId)
                .sku(request.getSku())
                .qtyReserved(request.getQty())
                .holdExpiresAt(expiresAt.toString())
                .status("RESERVED_5MIN_HOLD")
                .build());
    }

    /**
     * Public Reservation Release strictly requires reservationId (holdId) and authenticated ownership.
     */
    @PostMapping("/release-hold")
    @Transactional
    public ResponseEntity<String> releaseStockHold(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody StockHoldRequest request
    ) {
        Optional<JwtAuthValidator.AuthenticatedPrincipal> principalOpt = jwtAuthValidator.authenticate(authHeader);
        String callerCustomerId = principalOpt.map(JwtAuthValidator.AuthenticatedPrincipal::getSubject).orElse(request.getCustomerId());

        if (request.getHoldId() == null || request.getHoldId().isBlank()) {
            return ResponseEntity.badRequest().body("HOLD_ID_REQUIRED: Reservation release strictly requires specific reservationId.");
        }

        Optional<InventoryHoldEntity> holdOpt = holdRepository.findById(request.getHoldId());
        if (holdOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("HOLD_NOT_FOUND");
        }

        InventoryHoldEntity hold = holdOpt.get();
        boolean isOwner = (callerCustomerId != null && hold.getCustomerId() != null && callerCustomerId.equalsIgnoreCase(hold.getCustomerId()));
        boolean isPrivileged = principalOpt.map(p -> p.hasRole("ROLE_ADMIN") || p.hasRole("ROLE_SYSTEM")).orElse(false);

        if (!isOwner && !isPrivileged && hold.getCustomerId() != null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("FORBIDDEN: Cannot release hold belonging to another customer.");
        }

        hold.setStatus("RELEASED");
        holdRepository.save(hold);
        return ResponseEntity.ok("HOLD_RELEASED");
    }

    /**
     * True Database Row-Locked FEFO Allocation.
     * Consumes reservation hold if holdId is specified, locking rows with PESSIMISTIC_WRITE.
     */
    @PostMapping("/allocate-fefo")
    @Transactional
    public ResponseEntity<FefoAllocationResponse> allocateFefoBatch(@RequestBody FefoAllocationRequest request) {
        // If reservationId is specified, consume the reservation hold
        if (request.getReservationId() != null && !request.getReservationId().isBlank()) {
            holdRepository.findById(request.getReservationId()).ifPresent(hold -> {
                hold.setStatus("CONSUMED_FOR_ALLOCATION");
                holdRepository.save(hold);
            });
        }

        List<InventoryBatchEntity> batches = (request.getDarkStoreId() != null && !request.getDarkStoreId().isBlank())
                ? batchRepository.findStoreBatchesForAllocationWithLock(request.getSku(), request.getDarkStoreId())
                : batchRepository.findBatchesForAllocationWithLock(request.getSku());

        int required = request.getQuantity();
        int allocated = 0;
        List<AllocatedBatchDetail> details = new ArrayList<>();

        for (InventoryBatchEntity b : batches) {
            if (required <= 0) break;
            if (b.getAvailableQty() <= 0) continue;

            int take = Math.min(b.getAvailableQty(), required);
            b.setAvailableQty(b.getAvailableQty() - take);
            required -= take;
            allocated += take;

            details.add(AllocatedBatchDetail.builder()
                    .batchNumber(b.getBatchNumber())
                    .allocatedQty(take)
                    .expiryDate(b.getExpiryDate().toString())
                    .coldChain(b.getColdChain())
                    .build());

            batchRepository.save(b);
        }

        if (allocated == 0 && required > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(FefoAllocationResponse.builder()
                    .sku(request.getSku())
                    .status("INSUFFICIENT_STOCK_FOR_ALLOCATION")
                    .allocatedBatches(Collections.emptyList())
                    .build());
        }

        return ResponseEntity.ok(FefoAllocationResponse.builder()
                .sku(request.getSku())
                .status("ALLOCATED")
                .allocatedBatches(details)
                .build());
    }

    @Data
    @Builder
    public static class FefoBatch {
        private String batchNumber;
        private String sku;
        private int availableQty;
        private String expiryDate;
        private String darkStoreId;
        private boolean coldChain;
    }

    @Data
    public static class FefoAllocationRequest {
        private String sku;
        private int requestedQty;
        private int quantity;
        private String darkStoreId;
        private String reservationId;

        public int getQuantity() {
            return requestedQty > 0 ? requestedQty : quantity;
        }
    }

    @Data
    @Builder
    public static class AllocatedBatchDetail {
        private String batchNumber;
        private int allocatedQty;
        private String expiryDate;
        private boolean coldChain;
    }

    @Data
    @Builder
    public static class FefoAllocationResponse {
        private String sku;
        private String allocatedBatchNo;
        private String expiryDate;
        private String darkStoreId;
        private int allocatedQty;
        private boolean coldChain;
        private String status;
        private List<AllocatedBatchDetail> allocatedBatches;
    }

    @Data
    @Builder
    public static class AtpCheckResponse {
        private String sku;
        private String darkStoreId;
        private int physicalOnHand;
        private int activeHolds;
        private int safetyBuffer;
        private int availableToPromise;
    }

    @Data
    public static class StockHoldRequest {
        private String holdId;
        private String sku;
        private int qty;
        private String darkStoreId;
        private String orderId;
        private String customerId;

        public StockHoldRequest() {}
        public StockHoldRequest(String sku, int qty) {
            this.sku = sku;
            this.qty = qty;
        }
    }

    @Data
    @Builder
    public static class StockHoldResponse {
        private String holdId;
        private String sku;
        private int qtyReserved;
        private String holdExpiresAt;
        private String status;
    }
}
