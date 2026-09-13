package com.commerceos.order.dto;

public class CustomerTrackingDTO {
    private String orderId;
    private String deliveryId;
    private String state;
    private String riderName;
    private String riderVehicle;
    private Double riderRating;
    private Double merchantLat;
    private Double merchantLng;
    private Double customerLat;
    private Double customerLng;
    private CustomerTelemetryDTO liveRiderTelemetry;
    private String trackingStatusText;
    private Integer estimatedArrivalMins;
    private Boolean isStale;
    private Long lastUpdatedTimestamp;

    public CustomerTrackingDTO() {}

    public CustomerTrackingDTO(String orderId, String deliveryId, String state, String riderName, String riderVehicle, Double riderRating, Double merchantLat, Double merchantLng, Double customerLat, Double customerLng, CustomerTelemetryDTO liveRiderTelemetry, String trackingStatusText, Integer estimatedArrivalMins, Boolean isStale, Long lastUpdatedTimestamp) {
        this.orderId = orderId;
        this.deliveryId = deliveryId;
        this.state = state;
        this.riderName = riderName;
        this.riderVehicle = riderVehicle;
        this.riderRating = riderRating;
        this.merchantLat = merchantLat;
        this.merchantLng = merchantLng;
        this.customerLat = customerLat;
        this.customerLng = customerLng;
        this.liveRiderTelemetry = liveRiderTelemetry;
        this.trackingStatusText = trackingStatusText;
        this.estimatedArrivalMins = estimatedArrivalMins;
        this.isStale = isStale;
        this.lastUpdatedTimestamp = lastUpdatedTimestamp;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getDeliveryId() { return deliveryId; }
    public void setDeliveryId(String deliveryId) { this.deliveryId = deliveryId; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getRiderName() { return riderName; }
    public void setRiderName(String riderName) { this.riderName = riderName; }

    public String getRiderVehicle() { return riderVehicle; }
    public void setRiderVehicle(String riderVehicle) { this.riderVehicle = riderVehicle; }

    public Double getRiderRating() { return riderRating; }
    public void setRiderRating(Double riderRating) { this.riderRating = riderRating; }

    public Double getMerchantLat() { return merchantLat; }
    public void setMerchantLat(Double merchantLat) { this.merchantLat = merchantLat; }

    public Double getMerchantLng() { return merchantLng; }
    public void setMerchantLng(Double merchantLng) { this.merchantLng = merchantLng; }

    public Double getCustomerLat() { return customerLat; }
    public void setCustomerLat(Double customerLat) { this.customerLat = customerLat; }

    public Double getCustomerLng() { return customerLng; }
    public void setCustomerLng(Double customerLng) { this.customerLng = customerLng; }

    public CustomerTelemetryDTO getLiveRiderTelemetry() { return liveRiderTelemetry; }
    public void setLiveRiderTelemetry(CustomerTelemetryDTO liveRiderTelemetry) { this.liveRiderTelemetry = liveRiderTelemetry; }

    public String getTrackingStatusText() { return trackingStatusText; }
    public void setTrackingStatusText(String trackingStatusText) { this.trackingStatusText = trackingStatusText; }

    public Integer getEstimatedArrivalMins() { return estimatedArrivalMins; }
    public void setEstimatedArrivalMins(Integer estimatedArrivalMins) { this.estimatedArrivalMins = estimatedArrivalMins; }

    public Boolean getIsStale() { return isStale; }
    public void setIsStale(Boolean isStale) { this.isStale = isStale; }

    public Long getLastUpdatedTimestamp() { return lastUpdatedTimestamp; }
    public void setLastUpdatedTimestamp(Long lastUpdatedTimestamp) { this.lastUpdatedTimestamp = lastUpdatedTimestamp; }

    public static CustomerTrackingDTOBuilder builder() {
        return new CustomerTrackingDTOBuilder();
    }

    public static class CustomerTrackingDTOBuilder {
        private String orderId;
        private String deliveryId;
        private String state;
        private String riderName;
        private String riderVehicle;
        private Double riderRating;
        private Double merchantLat;
        private Double merchantLng;
        private Double customerLat;
        private Double customerLng;
        private CustomerTelemetryDTO liveRiderTelemetry;
        private String trackingStatusText;
        private Integer estimatedArrivalMins;
        private Boolean isStale;
        private Long lastUpdatedTimestamp;

        CustomerTrackingDTOBuilder() {}

        public CustomerTrackingDTOBuilder orderId(String orderId) { this.orderId = orderId; return this; }
        public CustomerTrackingDTOBuilder deliveryId(String deliveryId) { this.deliveryId = deliveryId; return this; }
        public CustomerTrackingDTOBuilder state(String state) { this.state = state; return this; }
        public CustomerTrackingDTOBuilder riderName(String riderName) { this.riderName = riderName; return this; }
        public CustomerTrackingDTOBuilder riderVehicle(String riderVehicle) { this.riderVehicle = riderVehicle; return this; }
        public CustomerTrackingDTOBuilder riderRating(Double riderRating) { this.riderRating = riderRating; return this; }
        public CustomerTrackingDTOBuilder merchantLat(Double merchantLat) { this.merchantLat = merchantLat; return this; }
        public CustomerTrackingDTOBuilder merchantLng(Double merchantLng) { this.merchantLng = merchantLng; return this; }
        public CustomerTrackingDTOBuilder customerLat(Double customerLat) { this.customerLat = customerLat; return this; }
        public CustomerTrackingDTOBuilder customerLng(Double customerLng) { this.customerLng = customerLng; return this; }
        public CustomerTrackingDTOBuilder liveRiderTelemetry(CustomerTelemetryDTO liveRiderTelemetry) { this.liveRiderTelemetry = liveRiderTelemetry; return this; }
        public CustomerTrackingDTOBuilder trackingStatusText(String trackingStatusText) { this.trackingStatusText = trackingStatusText; return this; }
        public CustomerTrackingDTOBuilder estimatedArrivalMins(Integer estimatedArrivalMins) { this.estimatedArrivalMins = estimatedArrivalMins; return this; }
        public CustomerTrackingDTOBuilder isStale(Boolean isStale) { this.isStale = isStale; return this; }
        public CustomerTrackingDTOBuilder lastUpdatedTimestamp(Long lastUpdatedTimestamp) { this.lastUpdatedTimestamp = lastUpdatedTimestamp; return this; }

        public CustomerTrackingDTO build() {
            return new CustomerTrackingDTO(orderId, deliveryId, state, riderName, riderVehicle, riderRating, merchantLat, merchantLng, customerLat, customerLng, liveRiderTelemetry, trackingStatusText, estimatedArrivalMins, isStale, lastUpdatedTimestamp);
        }
    }

    public static class CustomerTelemetryDTO {
        private Double latitude;
        private Double longitude;
        private Float heading;
        private Float speedKmh;
        private Long sequenceNumber;
        private Long serverTimestamp;
        private Boolean isStale;

        public CustomerTelemetryDTO() {}

        public CustomerTelemetryDTO(Double latitude, Double longitude, Float heading, Float speedKmh, Long sequenceNumber, Long serverTimestamp, Boolean isStale) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.heading = heading;
            this.speedKmh = speedKmh;
            this.sequenceNumber = sequenceNumber;
            this.serverTimestamp = serverTimestamp;
            this.isStale = isStale;
        }

        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }

        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }

        public Float getHeading() { return heading; }
        public void setHeading(Float heading) { this.heading = heading; }

        public Float getSpeedKmh() { return speedKmh; }
        public void setSpeedKmh(Float speedKmh) { this.speedKmh = speedKmh; }

        public Long getSequenceNumber() { return sequenceNumber; }
        public void setSequenceNumber(Long sequenceNumber) { this.sequenceNumber = sequenceNumber; }

        public Long getServerTimestamp() { return serverTimestamp; }
        public void setServerTimestamp(Long serverTimestamp) { this.serverTimestamp = serverTimestamp; }

        public Boolean getIsStale() { return isStale; }
        public void setIsStale(Boolean isStale) { this.isStale = isStale; }

        public static CustomerTelemetryDTOBuilder builder() {
            return new CustomerTelemetryDTOBuilder();
        }

        public static class CustomerTelemetryDTOBuilder {
            private Double latitude;
            private Double longitude;
            private Float heading;
            private Float speedKmh;
            private Long sequenceNumber;
            private Long serverTimestamp;
            private Boolean isStale;

            CustomerTelemetryDTOBuilder() {}

            public CustomerTelemetryDTOBuilder latitude(Double latitude) { this.latitude = latitude; return this; }
            public CustomerTelemetryDTOBuilder longitude(Double longitude) { this.longitude = longitude; return this; }
            public CustomerTelemetryDTOBuilder heading(Float heading) { this.heading = heading; return this; }
            public CustomerTelemetryDTOBuilder speedKmh(Float speedKmh) { this.speedKmh = speedKmh; return this; }
            public CustomerTelemetryDTOBuilder sequenceNumber(Long sequenceNumber) { this.sequenceNumber = sequenceNumber; return this; }
            public CustomerTelemetryDTOBuilder serverTimestamp(Long serverTimestamp) { this.serverTimestamp = serverTimestamp; return this; }
            public CustomerTelemetryDTOBuilder isStale(Boolean isStale) { this.isStale = isStale; return this; }

            public CustomerTelemetryDTO build() {
                return new CustomerTelemetryDTO(latitude, longitude, heading, speedKmh, sequenceNumber, serverTimestamp, isStale);
            }
        }
    }
}
