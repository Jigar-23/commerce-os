package com.commerceos.order.dto;

import java.math.BigDecimal;

/**
 * Authoritative Operational Delivery DTO for internal dispatch and ops governance.
 * Invariant: Never includes plaintext delivery OTP, salt, or hash secrets.
 */
public class OpsDeliveryDTO {
    private String deliveryId;
    private String orderId;
    private String riderId;
    private String riderName;
    private String riderPhone;
    private String riderVehicle;
    private String customerId;
    private String customerName;
    private String customerPhone;
    private String customerAddress;
    private Double customerLat;
    private Double customerLng;
    private String merchantName;
    private String merchantAddress;
    private Double merchantLat;
    private Double merchantLng;
    private String state;
    private Integer otpAttemptsLeft;
    private Boolean otpVerified;
    private Boolean isCod;
    private BigDecimal codAmount;
    private BigDecimal codCollectedAmount;
    private Boolean codReconciled;
    private OpsTelemetryDTO telemetry;

    public OpsDeliveryDTO() {}

    public OpsDeliveryDTO(String deliveryId, String orderId, String riderId, String riderName, String riderPhone, String riderVehicle, String customerId, String customerName, String customerPhone, String customerAddress, Double customerLat, Double customerLng, String merchantName, String merchantAddress, Double merchantLat, Double merchantLng, String state, Integer otpAttemptsLeft, Boolean otpVerified, Boolean isCod, BigDecimal codAmount, BigDecimal codCollectedAmount, Boolean codReconciled, OpsTelemetryDTO telemetry) {
        this.deliveryId = deliveryId;
        this.orderId = orderId;
        this.riderId = riderId;
        this.riderName = riderName;
        this.riderPhone = riderPhone;
        this.riderVehicle = riderVehicle;
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.customerAddress = customerAddress;
        this.customerLat = customerLat;
        this.customerLng = customerLng;
        this.merchantName = merchantName;
        this.merchantAddress = merchantAddress;
        this.merchantLat = merchantLat;
        this.merchantLng = merchantLng;
        this.state = state;
        this.otpAttemptsLeft = otpAttemptsLeft;
        this.otpVerified = otpVerified;
        this.isCod = isCod;
        this.codAmount = codAmount;
        this.codCollectedAmount = codCollectedAmount;
        this.codReconciled = codReconciled;
        this.telemetry = telemetry;
    }

    public static OpsDeliveryDTOBuilder builder() {
        return new OpsDeliveryDTOBuilder();
    }

    public String getDeliveryId() { return deliveryId; }
    public void setDeliveryId(String deliveryId) { this.deliveryId = deliveryId; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getRiderId() { return riderId; }
    public void setRiderId(String riderId) { this.riderId = riderId; }
    public String getRiderName() { return riderName; }
    public void setRiderName(String riderName) { this.riderName = riderName; }
    public String getRiderPhone() { return riderPhone; }
    public void setRiderPhone(String riderPhone) { this.riderPhone = riderPhone; }
    public String getRiderVehicle() { return riderVehicle; }
    public void setRiderVehicle(String riderVehicle) { this.riderVehicle = riderVehicle; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getCustomerAddress() { return customerAddress; }
    public void setCustomerAddress(String customerAddress) { this.customerAddress = customerAddress; }
    public Double getCustomerLat() { return customerLat; }
    public void setCustomerLat(Double customerLat) { this.customerLat = customerLat; }
    public Double getCustomerLng() { return customerLng; }
    public void setCustomerLng(Double customerLng) { this.customerLng = customerLng; }
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
    public String getMerchantAddress() { return merchantAddress; }
    public void setMerchantAddress(String merchantAddress) { this.merchantAddress = merchantAddress; }
    public Double getMerchantLat() { return merchantLat; }
    public void setMerchantLat(Double merchantLat) { this.merchantLat = merchantLat; }
    public Double getMerchantLng() { return merchantLng; }
    public void setMerchantLng(Double merchantLng) { this.merchantLng = merchantLng; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Integer getOtpAttemptsLeft() { return otpAttemptsLeft; }
    public void setOtpAttemptsLeft(Integer otpAttemptsLeft) { this.otpAttemptsLeft = otpAttemptsLeft; }
    public Boolean getOtpVerified() { return otpVerified; }
    public void setOtpVerified(Boolean otpVerified) { this.otpVerified = otpVerified; }
    public Boolean getIsCod() { return isCod; }
    public void setIsCod(Boolean isCod) { this.isCod = isCod; }
    public BigDecimal getCodAmount() { return codAmount; }
    public void setCodAmount(BigDecimal codAmount) { this.codAmount = codAmount; }
    public BigDecimal getCodCollectedAmount() { return codCollectedAmount; }
    public void setCodCollectedAmount(BigDecimal codCollectedAmount) { this.codCollectedAmount = codCollectedAmount; }
    public Boolean getCodReconciled() { return codReconciled; }
    public void setCodReconciled(Boolean codReconciled) { this.codReconciled = codReconciled; }
    public OpsTelemetryDTO getTelemetry() { return telemetry; }
    public void setTelemetry(OpsTelemetryDTO telemetry) { this.telemetry = telemetry; }

    public static class OpsDeliveryDTOBuilder {
        private String deliveryId;
        private String orderId;
        private String riderId;
        private String riderName;
        private String riderPhone;
        private String riderVehicle;
        private String customerId;
        private String customerName;
        private String customerPhone;
        private String customerAddress;
        private Double customerLat;
        private Double customerLng;
        private String merchantName;
        private String merchantAddress;
        private Double merchantLat;
        private Double merchantLng;
        private String state;
        private Integer otpAttemptsLeft;
        private Boolean otpVerified;
        private Boolean isCod;
        private BigDecimal codAmount;
        private BigDecimal codCollectedAmount;
        private Boolean codReconciled;
        private OpsTelemetryDTO telemetry;

        public OpsDeliveryDTOBuilder deliveryId(String deliveryId) { this.deliveryId = deliveryId; return this; }
        public OpsDeliveryDTOBuilder orderId(String orderId) { this.orderId = orderId; return this; }
        public OpsDeliveryDTOBuilder riderId(String riderId) { this.riderId = riderId; return this; }
        public OpsDeliveryDTOBuilder riderName(String riderName) { this.riderName = riderName; return this; }
        public OpsDeliveryDTOBuilder riderPhone(String riderPhone) { this.riderPhone = riderPhone; return this; }
        public OpsDeliveryDTOBuilder riderVehicle(String riderVehicle) { this.riderVehicle = riderVehicle; return this; }
        public OpsDeliveryDTOBuilder customerId(String customerId) { this.customerId = customerId; return this; }
        public OpsDeliveryDTOBuilder customerName(String customerName) { this.customerName = customerName; return this; }
        public OpsDeliveryDTOBuilder customerPhone(String customerPhone) { this.customerPhone = customerPhone; return this; }
        public OpsDeliveryDTOBuilder customerAddress(String customerAddress) { this.customerAddress = customerAddress; return this; }
        public OpsDeliveryDTOBuilder customerLat(Double customerLat) { this.customerLat = customerLat; return this; }
        public OpsDeliveryDTOBuilder customerLng(Double customerLng) { this.customerLng = customerLng; return this; }
        public OpsDeliveryDTOBuilder merchantName(String merchantName) { this.merchantName = merchantName; return this; }
        public OpsDeliveryDTOBuilder merchantAddress(String merchantAddress) { this.merchantAddress = merchantAddress; return this; }
        public OpsDeliveryDTOBuilder merchantLat(Double merchantLat) { this.merchantLat = merchantLat; return this; }
        public OpsDeliveryDTOBuilder merchantLng(Double merchantLng) { this.merchantLng = merchantLng; return this; }
        public OpsDeliveryDTOBuilder state(String state) { this.state = state; return this; }
        public OpsDeliveryDTOBuilder otpAttemptsLeft(Integer otpAttemptsLeft) { this.otpAttemptsLeft = otpAttemptsLeft; return this; }
        public OpsDeliveryDTOBuilder otpVerified(Boolean otpVerified) { this.otpVerified = otpVerified; return this; }
        public OpsDeliveryDTOBuilder isCod(Boolean isCod) { this.isCod = isCod; return this; }
        public OpsDeliveryDTOBuilder codAmount(BigDecimal codAmount) { this.codAmount = codAmount; return this; }
        public OpsDeliveryDTOBuilder codCollectedAmount(BigDecimal codCollectedAmount) { this.codCollectedAmount = codCollectedAmount; return this; }
        public OpsDeliveryDTOBuilder codReconciled(Boolean codReconciled) { this.codReconciled = codReconciled; return this; }
        public OpsDeliveryDTOBuilder telemetry(OpsTelemetryDTO telemetry) { this.telemetry = telemetry; return this; }

        public OpsDeliveryDTO build() {
            return new OpsDeliveryDTO(deliveryId, orderId, riderId, riderName, riderPhone, riderVehicle, customerId, customerName, customerPhone, customerAddress, customerLat, customerLng, merchantName, merchantAddress, merchantLat, merchantLng, state, otpAttemptsLeft, otpVerified, isCod, codAmount, codCollectedAmount, codReconciled, telemetry);
        }
    }
}
