export interface CloudEventEnvelope<T> {
  id: string;
  source: string;
  type: string;
  specversion: '1.0';
  datacontenttype: 'application/json';
  time: string;
  data: T;
}

export interface CanonicalCommerceEvent<T> {
  eventId: string;
  aggregateId: string;
  aggregateType: 'ORDER' | 'PAYMENT' | 'INVENTORY' | 'DELIVERY' | 'CUSTOMER';
  tenantId: string;
  version: number;
  occurredAt: string;
  producer: string;
  correlationId: string;
  causationId?: string;
  schemaVersion: string;
  payload: T;
}

// SAGA ORCHESTRATION & TRANSACTIONAL OUTBOX EVENT SCHEMAS
export interface OrderPlacedEvent {
  orderId: string;
  customerId: string;
  totalAmountCents: number;
  prescriptionRequired: boolean;
  prescriptionId?: string;
  orderType: 'QUICK_COMMERCE_10MIN' | 'STANDARD_DELIVERY' | 'REFILL_SUBSCRIPTION';
}

export interface OrderStatusChangedEvent {
  orderId: string;
  customerId: string;
  storeId: string;
  previousStatus: string;
  newStatus: string;
  actor: string;
  timestamp: string;
}

export interface PharmacistVerifiedEvent {
  orderId: string;
  prescriptionId: string;
  pharmacistLicenseNo: string;
  verifiedAt: string;
  status: 'APPROVED' | 'REJECTED';
}

export interface FEFOBatchAllocatedEvent {
  orderId: string;
  darkStoreId: string;
  allocatedBatchNo: string;
  expiryDate: string;
  coldChainRequired: boolean;
}

export interface StockReservationEvent {
  reservationId: string;
  orderId?: string;
  customerId: string;
  storeId: string;
  sku: string;
  quantity: number;
  status: 'RESERVED' | 'RELEASED' | 'CONSUMED';
  expiresAt: string;
}

export interface PaymentLifecycleEvent {
  paymentId: string;
  orderId: string;
  customerId: string;
  amount: number;
  currency: string;
  status: 'CREATED' | 'AUTHORIZED' | 'CAPTURED' | 'PARTIALLY_REFUNDED' | 'REFUNDED' | 'FAILED';
  gatewayTransactionId?: string;
}

export interface DeliveryAssignedEvent {
  orderId: string;
  deliveryId: string;
  riderId: string;
  riderName: string;
  otp: string;
  estimatedDeliverySlaMins: number;
}

export interface DeliveryTelemetryEvent {
  deliveryId: string;
  riderId: string;
  latitude: number;
  longitude: number;
  bearing?: number;
  speedKmh?: number;
  batteryLevel?: number;
  recordedAt: string;
}

export interface KafkaDeadLetterQueueMessage {
  originalTopic: string;
  partition: number;
  offset: number;
  exceptionMessage: string;
  failedPayload: string;
  timestamp: string;
}
