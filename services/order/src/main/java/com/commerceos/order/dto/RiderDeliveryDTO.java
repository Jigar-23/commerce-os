package com.commerceos.order.dto;

import java.math.BigDecimal;

public class RiderDeliveryDTO {
    private String deliveryId;
    private String orderId;
    private String riderId;
    private String customerId;
    private String customerName;
    private String maskedCustomerPhone;
    private String customerAddress;
    private Double customerLat;
    private Double customerLng;
    private String merchantName;
    private String merchantAddress;
    private Double merchantLat;
    private Double merchantLng;
    private String payoutFormatted;
    private Double distanceKm;
    private Integer estimatedTimeMins;
    private String state;
    private Integer otpAttemptsLeft;
    private Boolean otpVerified;
    private Boolean isCod;
    private BigDecimal codAmount;
    private BigDecimal codCollectedAmount;
    private Boolean codReconciled;
    private RiderTelemetryDTO telemetry;

    public RiderDeliveryDTO() {}

    public RiderDeliveryDTO(String deliveryId, String orderId, String riderId, String customerId, String customerName, String maskedCustomerPhone, String customerAddress, Double customerLat, Double customerLng, String merchantName, String merchantAddress, Double merchantLat, Double merchantLng, String payoutFormatted, Double distanceKm, Integer estimatedTimeMins, String state, Integer otpAttemptsLeft, Boolean otpVerified, Boolean isCod, BigDecimal codAmount, BigDecimal codCollectedAmount, Boolean codReconciled, RiderTelemetryDTO telemetry) {
        this.deliveryId = deliveryId;
        this.orderId = orderId;
        this.riderId = riderId;
        this.customerId = customerId;
        this.customerName = customerName;
        this.maskedCustomerPhone = maskedCustomerPhone;
        this.customerAddress = customerAddress;
        this.customerLat = customerLat;
        this.customerLng = customerLng;
        this.merchantName = merchantName;
        this.merchantAddress = merchantAddress;
        this.merchantLat = merchantLat;
        this.merchantLng = merchantLng;
        this.payoutFormatted = payoutFormatted;
        this.distanceKm = distanceKm;
        this.estimatedTimeMins = estimatedTimeMins;
        this.state = state;
        this.otpAttemptsLeft = otpAttemptsLeft;
        this.otpVerified = otpVerified;
        this.isCod = isCod;
        this.codAmount = codAmount;
        this.codCollectedAmount = codCollectedAmount;
        this.codReconciled = codReconciled;
        this.telemetry = telemetry;
    }

    public String getDeliveryId() { return deliveryId; }
    public void setDeliveryId(String deliveryId) { this.deliveryId = deliveryId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getRiderId() { return riderId; }
    public void setRiderId(String riderId) { this.riderId = riderId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getMaskedCustomerPhone() { return maskedCustomerPhone; }
    public void setMaskedCustomerPhone(String maskedCustomerPhone) { this.maskedCustomerPhone = maskedCustomerPhone; }

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

    public String getPayoutFormatted() { return payoutFormatted; }
    public void setPayoutFormatted(String payoutFormatted) { this.payoutFormatted = payoutFormatted; }

    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }

    public Integer getEstimatedTimeMins() { return estimatedTimeMins; }
    public void setEstimatedTimeMins(Integer estimatedTimeMins) { this.estimatedTimeMins = estimatedTimeMins; }

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

    public RiderTelemetryDTO getTelemetry() { return telemetry; }
    public void setTelemetry(RiderTelemetryDTO telemetry) { this.telemetry = telemetry; }

    public static RiderDeliveryDTOBuilder builder() {
        return new RiderDeliveryDTOBuilder();
    }

    public static class RiderDeliveryDTOBuilder {
        private String deliveryId;
        private String orderId;
        private String riderId;
        private String customerId;
        private String customerName;
        private String maskedCustomerPhone;
        private String customerAddress;
        private Double customerLat;
        private Double customerLng;
        private String merchantName;
        private String merchantAddress;
        private Double merchantLat;
        private Double merchantLng;
        private String payoutFormatted;
        private Double distanceKm;
        private Integer estimatedTimeMins;
        private String state;
        private Integer otpAttemptsLeft;
        private Boolean otpVerified;
        private Boolean isCod;
        private BigDecimal codAmount;
        private BigDecimal codCollectedAmount;
        private Boolean codReconciled;
        private RiderTelemetryDTO telemetry;

        RiderDeliveryDTOBuilder() {}

        public RiderDeliveryDTOBuilder deliveryId(String deliveryId) { this.deliveryId = deliveryId; return this; }
        public RiderDeliveryDTOBuilder orderId(String orderId) { this.orderId = orderId; return this; }
        public RiderDeliveryDTOBuilder riderId(String riderId) { this.riderId = riderId; return this; }
        public RiderDeliveryDTOBuilder customerId(String customerId) { this.customerId = customerId; return this; }
        public RiderDeliveryDTOBuilder customerName(String customerName) { this.customerName = customerName; return this; }
        public RiderDeliveryDTOBuilder maskedCustomerPhone(String maskedCustomerPhone) { this.maskedCustomerPhone = maskedCustomerPhone; return this; }
        public RiderDeliveryDTOBuilder customerAddress(String customerAddress) { this.customerAddress = customerAddress; return this; }
        public RiderDeliveryDTOBuilder customerLat(Double customerLat) { this.customerLat = customerLat; return this; }
        public RiderDeliveryDTOBuilder customerLng(Double customerLng) { this.customerLng = customerLng; return this; }
        public RiderDeliveryDTOBuilder merchantName(String merchantName) { this.merchantName = merchantName; return this; }
        public RiderDeliveryDTOBuilder merchantAddress(String merchantAddress) { this.merchantAddress = merchantAddress; return this; }
        public RiderDeliveryDTOBuilder merchantLat(Double merchantLat) { this.merchantLat = merchantLat; return this; }
        public RiderDeliveryDTOBuilder merchantLng(Double merchantLng) { this.merchantLng = merchantLng; return this; }
        public RiderDeliveryDTOBuilder payoutFormatted(String payoutFormatted) { this.payoutFormatted = payoutFormatted; return this; }
        public RiderDeliveryDTOBuilder distanceKm(Double distanceKm) { this.distanceKm = distanceKm; return this; }
        public RiderDeliveryDTOBuilder estimatedTimeMins(Integer estimatedTimeMins) { this.estimatedTimeMins = estimatedTimeMins; return this; }
        public RiderDeliveryDTOBuilder state(String state) { this.state = state; return this; }
        public RiderDeliveryDTOBuilder otpAttemptsLeft(Integer otpAttemptsLeft) { this.otpAttemptsLeft = otpAttemptsLeft; return this; }
        public RiderDeliveryDTOBuilder otpVerified(Boolean otpVerified) { this.otpVerified = otpVerified; return this; }
        public RiderDeliveryDTOBuilder isCod(Boolean isCod) { this.isCod = isCod; return this; }
        public RiderDeliveryDTOBuilder codAmount(BigDecimal codAmount) { this.codAmount = codAmount; return this; }
        public RiderDeliveryDTOBuilder codCollectedAmount(BigDecimal codCollectedAmount) { this.codCollectedAmount = codCollectedAmount; return this; }
        public RiderDeliveryDTOBuilder codReconciled(Boolean codReconciled) { this.codReconciled = codReconciled; return this; }
        public RiderDeliveryDTOBuilder telemetry(RiderTelemetryDTO telemetry) { this.telemetry = telemetry; return this; }

        public RiderDeliveryDTO build() {
            return new RiderDeliveryDTO(deliveryId, orderId, riderId, customerId, customerName, maskedCustomerPhone, customerAddress, customerLat, customerLng, merchantName, merchantAddress, merchantLat, merchantLng, payoutFormatted, distanceKm, estimatedTimeMins, state, otpAttemptsLeft, otpVerified, isCod, codAmount, codCollectedAmount, codReconciled, telemetry);
        }
    }

    public static class RiderTelemetryDTO {
        private Double latitude;
        private Double longitude;
        private Float speedKmh;
        private Float heading;
        private Float accuracyMeters;
        private Long sequenceNumber;
        private Long serverTimestamp;
        private Boolean isStale;

        public RiderTelemetryDTO() {}

        public RiderTelemetryDTO(Double latitude, Double longitude, Float speedKmh, Float heading, Float accuracyMeters, Long sequenceNumber, Long serverTimestamp, Boolean isStale) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.speedKmh = speedKmh;
            this.heading = heading;
            this.accuracyMeters = accuracyMeters;
            this.sequenceNumber = sequenceNumber;
            this.serverTimestamp = serverTimestamp;
            this.isStale = isStale;
        }

        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }

        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }

        public Float getSpeedKmh() { return speedKmh; }
        public void setSpeedKmh(Float speedKmh) { this.speedKmh = speedKmh; }

        public Float getHeading() { return heading; }
        public void setHeading(Float heading) { this.heading = heading; }

        public Float getAccuracyMeters() { return accuracyMeters; }
        public void setAccuracyMeters(Float accuracyMeters) { this.accuracyMeters = accuracyMeters; }

        public Long getSequenceNumber() { return sequenceNumber; }
        public void setSequenceNumber(Long sequenceNumber) { this.sequenceNumber = sequenceNumber; }

        public Long getServerTimestamp() { return serverTimestamp; }
        public void setServerTimestamp(Long serverTimestamp) { this.serverTimestamp = serverTimestamp; }

        public Boolean getIsStale() { return isStale; }
        public void setIsStale(Boolean isStale) { this.isStale = isStale; }

        public static RiderTelemetryDTOBuilder builder() {
            return new RiderTelemetryDTOBuilder();
        }

        public static class RiderTelemetryDTOBuilder {
            private Double latitude;
            private Double longitude;
            private Float speedKmh;
            private Float heading;
            private Float accuracyMeters;
            private Long sequenceNumber;
            private Long serverTimestamp;
            private Boolean isStale;

            RiderTelemetryDTOBuilder() {}

            public RiderTelemetryDTOBuilder latitude(Double latitude) { this.latitude = latitude; return this; }
            public RiderTelemetryDTOBuilder longitude(Double longitude) { this.longitude = longitude; return this; }
            public RiderTelemetryDTOBuilder speedKmh(Float speedKmh) { this.speedKmh = speedKmh; return this; }
            public RiderTelemetryDTOBuilder heading(Float heading) { this.heading = heading; return this; }
            public RiderTelemetryDTOBuilder accuracyMeters(Float accuracyMeters) { this.accuracyMeters = accuracyMeters; return this; }
            public RiderTelemetryDTOBuilder sequenceNumber(Long sequenceNumber) { this.sequenceNumber = sequenceNumber; return this; }
            public RiderTelemetryDTOBuilder serverTimestamp(Long serverTimestamp) { this.serverTimestamp = serverTimestamp; return this; }
            public RiderTelemetryDTOBuilder isStale(Boolean isStale) { this.isStale = isStale; return this; }

            public RiderTelemetryDTO build() {
                return new RiderTelemetryDTO(latitude, longitude, speedKmh, heading, accuracyMeters, sequenceNumber, serverTimestamp, isStale);
            }
        }
    }
}
