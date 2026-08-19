package com.commerceos.cart.controller;

import com.commerceos.cart.client.CatalogServiceClient;
import com.commerceos.cart.domain.CartItemEntity;
import com.commerceos.cart.repository.CartItemRepository;
import com.commerceos.cart.security.JwtAuthValidator;
import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private static final BigDecimal DELIVERY_FEE = new BigDecimal("2.00");
    private static final BigDecimal COLD_CHAIN_FEE = new BigDecimal("1.50");
    private static final BigDecimal FREE_DELIVERY_THRESHOLD = new BigDecimal("199.00");

    private final CartItemRepository repository;
    private final CatalogServiceClient catalogClient;
    private final JwtAuthValidator jwtAuthValidator;

    public CartController(
            CartItemRepository repository,
            CatalogServiceClient catalogClient,
            JwtAuthValidator jwtAuthValidator) {
        this.repository = repository;
        this.catalogClient = catalogClient;
        this.jwtAuthValidator = jwtAuthValidator;
    }

    private boolean isAuthorizedCustomerOrAdmin(String authHeader, String targetCustomerId) {
        Optional<JwtAuthValidator.AuthenticatedPrincipal> principalOpt = jwtAuthValidator.authenticate(authHeader);
        if (principalOpt.isEmpty()) {
            return false;
        }
        return principalOpt.get().isOwnerOrAdmin(targetCustomerId);
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<?> getCart(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String customerId
    ) {
        if (!isAuthorizedCustomerOrAdmin(authHeader, customerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("FORBIDDEN: Cart access requires authenticated customer ownership.");
        }
        List<CartItemEntity> entities = repository.findByCustomerIdOrderByCreatedAtAsc(customerId);
        return ResponseEntity.ok(buildResponse(customerId, entities));
    }

    /**
     * Authoritative Cart Add Item.
     * Accepts ONLY sku and quantity from the client.
     * All prices, MRP, brand, packSize, and Rx/cold-chain flags are strictly resolved
     * from the authoritative Catalog Service.
     */
    @PostMapping("/{customerId}/items")
    public ResponseEntity<?> addItem(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String customerId,
            @RequestBody AddCartItemRequest request
    ) {
        if (!isAuthorizedCustomerOrAdmin(authHeader, customerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("FORBIDDEN: Cart mutation requires authenticated customer ownership.");
        }

        if (request.getSku() == null || request.getSku().isBlank()) {
            return ResponseEntity.badRequest().body("SKU_REQUIRED");
        }

        // Authoritative Catalog Resolution
        Optional<CatalogServiceClient.ProductMetadata> productOpt = catalogClient.getProductBySku(request.getSku());
        if (productOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PRODUCT_NOT_FOUND: SKU " + request.getSku() + " does not exist in authoritative catalog.");
        }

        CatalogServiceClient.ProductMetadata product = productOpt.get();
        int requestedQty = Math.max(1, request.getQuantity());

        CartItemEntity existing = repository.findByCustomerIdAndSku(customerId, product.getSku()).orElse(null);
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + requestedQty);
            // Refresh canonical price & metadata from catalog
            existing.setUnitPrice(product.getUnitPrice());
            existing.setMrp(product.getMrp());
            repository.save(existing);
        } else {
            repository.save(CartItemEntity.builder()
                    .customerId(customerId)
                    .medicineId(product.getSku())
                    .sku(product.getSku())
                    .name(product.getName())
                    .brand(product.getBrand())
                    .packSize(product.getPackSize())
                    .unitPrice(product.getUnitPrice())
                    .mrp(product.getMrp())
                    .quantity(requestedQty)
                    .prescriptionRequired(product.isPrescriptionRequired())
                    .coldChain(product.isColdChain())
                    .image(product.getImage())
                    .build());
        }
        return getCart(authHeader, customerId);
    }

    @PatchMapping("/{customerId}/items/{sku}")
    public ResponseEntity<?> updateItemQuantity(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String customerId,
            @PathVariable String sku,
            @RequestBody CartQuantityUpdateRequest request
    ) {
        if (!isAuthorizedCustomerOrAdmin(authHeader, customerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("FORBIDDEN: Cart mutation requires authenticated customer ownership.");
        }
        CartItemEntity existing = repository.findByCustomerIdAndSku(customerId, sku).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        int quantity = request.getQuantity();
        if (quantity < 1 || quantity > 99) {
            return ResponseEntity.badRequest().body("INVALID_QUANTITY");
        }
        existing.setQuantity(quantity);
        repository.save(existing);
        return getCart(authHeader, customerId);
    }

    @DeleteMapping("/{customerId}/items/{sku}")
    public ResponseEntity<?> removeItem(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String customerId,
            @PathVariable String sku
    ) {
        if (!isAuthorizedCustomerOrAdmin(authHeader, customerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("FORBIDDEN: Cart mutation requires authenticated customer ownership.");
        }
        repository.deleteByCustomerIdAndSku(customerId, sku);
        return getCart(authHeader, customerId);
    }

    @DeleteMapping("/{customerId}")
    public ResponseEntity<?> clearCart(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String customerId
    ) {
        if (!isAuthorizedCustomerOrAdmin(authHeader, customerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("FORBIDDEN: Cart mutation requires authenticated customer ownership.");
        }
        repository.deleteByCustomerId(customerId);
        return getCart(authHeader, customerId);
    }

    private CartResponse buildResponse(String customerId, List<CartItemEntity> entities) {
        List<CartItem> items = entities.stream().map(e -> CartItem.builder()
                .medicineId(e.getMedicineId())
                .sku(e.getSku())
                .name(e.getName())
                .brand(e.getBrand())
                .packSize(e.getPackSize())
                .unitPrice(e.getUnitPrice())
                .mrp(e.getMrp())
                .quantity(e.getQuantity())
                .prescriptionRequired(e.isPrescriptionRequired())
                .coldChain(e.isColdChain())
                .image(e.getImage())
                .build()).toList();

        BigDecimal subtotal = items.stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal mrpTotal = items.stream()
                .map(i -> (i.getMrp() != null ? i.getMrp() : i.getUnitPrice()).multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean hasColdChain = items.stream().anyMatch(CartItem::isColdChain);
        boolean hasRx = items.stream().anyMatch(CartItem::isPrescriptionRequired);

        BigDecimal deliveryFee = subtotal.compareTo(FREE_DELIVERY_THRESHOLD) >= 0 || items.isEmpty()
                ? BigDecimal.ZERO
                : DELIVERY_FEE;

        BigDecimal coldChainFee = hasColdChain && !items.isEmpty() ? COLD_CHAIN_FEE : BigDecimal.ZERO;
        BigDecimal totalSavings = mrpTotal.subtract(subtotal).max(BigDecimal.ZERO);
        BigDecimal total = subtotal.add(deliveryFee).add(coldChainFee);

        return CartResponse.builder()
                .customerId(customerId)
                .items(items)
                .itemCount(items.stream().mapToInt(CartItem::getQuantity).sum())
                .subtotal(subtotal)
                .mrpTotal(mrpTotal)
                .deliveryFee(deliveryFee)
                .coldChainFee(coldChainFee)
                .totalSavings(totalSavings)
                .total(total)
                .hasColdChain(hasColdChain)
                .hasPrescriptionItems(hasRx)
                .freeDeliveryThreshold(FREE_DELIVERY_THRESHOLD)
                .amountNeededForFreeDelivery(FREE_DELIVERY_THRESHOLD.subtract(subtotal).max(BigDecimal.ZERO))
                .build();
    }

    @Data
    public static class CartQuantityUpdateRequest {
        private int quantity;
    }

    @Data
    public static class AddCartItemRequest {
        private String sku;
        private int quantity = 1;
    }

    @Data
    @Builder
    public static class CartItem {
        private String medicineId;
        private String sku;
        private String name;
        private String brand;
        private String packSize;
        private BigDecimal unitPrice;
        private BigDecimal mrp;
        private int quantity;
        private boolean prescriptionRequired;
        private boolean coldChain;
        private String image;
    }

    @Data
    @Builder
    public static class CartResponse {
        private String customerId;
        private List<CartItem> items;
        private int itemCount;
        private BigDecimal subtotal;
        private BigDecimal mrpTotal;
        private BigDecimal deliveryFee;
        private BigDecimal coldChainFee;
        private BigDecimal totalSavings;
        private BigDecimal total;
        private boolean hasColdChain;
        private boolean hasPrescriptionItems;
        private BigDecimal freeDeliveryThreshold;
        private BigDecimal amountNeededForFreeDelivery;
    }
}
