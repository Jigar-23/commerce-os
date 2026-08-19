package com.commerceos.order.dto;

import com.commerceos.order.domain.CustomerOrder;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerOrderResponse {

    private UUID orderId;
    private UUID customerId;
    private String pricingQuoteId;
    private String orderType;
    private String orderStatus;
    private BigDecimal subtotalAmount;
    private BigDecimal totalAmount;
    private BigDecimal taxAmount;
    private BigDecimal deliveryFee;
    private BigDecimal coldChainFee;
    private BigDecimal discountAmount;
    private Boolean prescriptionRequired;
    private UUID prescriptionId;
    private String paymentMethod;
    private String paymentStatus;
    private BigDecimal codAmountToCollect;
    private String deliveryModel;
    private String logisticsProvider;
    private Integer deliverySlaMins;
    private String sellerId;
    private String deliveryAddressJson;
    
    // Only populated ONCE during the initial order creation response; strictly null on subsequent GETs!
    private String deliveryOtp;

    private Instant createdAt;
    private Instant updatedAt;

    public static CustomerOrderResponse fromEntity(CustomerOrder entity) {
        return fromEntity(entity, null);
    }

    public static CustomerOrderResponse fromEntity(CustomerOrder entity, String oneTimeRawOtp) {
        if (entity == null) return null;
        return CustomerOrderResponse.builder()
                .orderId(entity.getId())
                .customerId(entity.getCustomerId())
                .pricingQuoteId(entity.getPricingQuoteId())
                .orderType(entity.getOrderType())
                .orderStatus(entity.getOrderStatus())
                .subtotalAmount(entity.getSubtotalAmount())
                .totalAmount(entity.getTotalAmount())
                .taxAmount(entity.getTaxAmount())
                .deliveryFee(entity.getDeliveryFee())
                .coldChainFee(entity.getColdChainFee())
                .discountAmount(entity.getDiscountAmount())
                .prescriptionRequired(entity.getPrescriptionRequired())
                .prescriptionId(entity.getPrescriptionId())
                .paymentMethod(entity.getPaymentMethod())
                .paymentStatus(entity.getPaymentStatus())
                .codAmountToCollect(entity.getCodAmountToCollect())
                .deliveryModel(entity.getDeliveryModel())
                .logisticsProvider(entity.getLogisticsProvider())
                .deliverySlaMins(entity.getDeliverySlaMins())
                .sellerId(entity.getSellerId())
                .deliveryAddressJson(entity.getDeliveryAddressJson())
                .deliveryOtp(oneTimeRawOtp) // One-time handoff PIN returned only at creation
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
