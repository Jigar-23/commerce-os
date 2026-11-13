/**
 * Commerce OS — Node Topology & Semantic Contracts
 * ------------------------------------------------
 * Models the entire Commerce Platform as a Directed Acyclic Graph (DAG) of nodes.
 * Each node represents a modular functional component with explicit Input/Output contracts.
 */

const NODES = {
  AUTH: {
    id: 'AUTH',
    name: 'Authentication & Session Node',
    layer: 'Client/Gateway',
    description: 'Manages user credentials, JWT tokens, tenant identification, and role-based access.',
    inputs: ['phone_or_email', 'otp_or_password', 'tenant_id'],
    outputs: ['jwt_token', 'user_id', 'is_authenticated', 'role'],
    invariants: ['Token must not be null when authenticated is true', 'Expired tokens must fail gracefully']
  },
  ADDRESS: {
    id: 'ADDRESS',
    name: 'Address & Geolocation Node',
    layer: 'Customer Domain',
    inputs: ['user_id', 'raw_address', 'pincode', 'latitude', 'longitude'],
    outputs: ['address_id', 'normalized_pincode', 'geofence_hub_id', 'is_serviceable', 'coordinates'],
    invariants: ['Latitude must be between -90 and 90', 'Longitude must be between -180 and 180', 'Pincode must be normalized']
  },
  CATALOG: {
    id: 'CATALOG',
    name: 'Catalog & Medicine Schedule Node',
    layer: 'Core Domain',
    inputs: ['tenant_id', 'product_id', 'search_query'],
    outputs: ['item_id', 'name', 'unit_price', 'requires_prescription', 'schedule_type', 'is_active'],
    invariants: ['Unit price must be positive integer/cents', 'Scheduled drugs (H/X) must require prescription']
  },
  INVENTORY: {
    id: 'INVENTORY',
    name: 'Seller & Inventory Node',
    layer: 'Fulfillment Domain',
    inputs: ['product_id', 'hub_id', 'quantity_requested'],
    outputs: ['available_stock', 'is_in_stock', 'reservation_token', 'warehouse_id'],
    invariants: ['Available stock cannot drop below zero', 'Reservation must release upon timeout']
  },
  CART: {
    id: 'CART',
    name: 'Cart & Pricing Calculation Node',
    layer: 'Transaction Domain',
    inputs: ['user_id', 'items', 'coupon_code', 'address_id'],
    outputs: ['cart_items', 'subtotal', 'discount_amount', 'delivery_fee', 'tax_amount', 'final_total', 'pricing_hash'],
    invariants: [
      'FinalTotal == Subtotal - Discount + DeliveryFee + Tax',
      'FinalTotal must be non-negative',
      'Prices must freeze or warn if catalog updates during checkout'
    ]
  },
  PAYMENT: {
    id: 'PAYMENT',
    name: 'Payment Methods & Gateway Node',
    layer: 'Financial Domain',
    inputs: ['order_id', 'final_total', 'method (COD|UPI|CARD)', 'idempotency_key'],
    outputs: ['payment_id', 'status (AUTHORIZED|FAILED|PENDING)', 'gateway_ref', 'signed_hash'],
    invariants: [
      'Duplicate idempotency keys must return identical transaction status',
      'Charge amount must match Cart FinalTotal exactly',
      'Webhooks must verify cryptographic signature'
    ]
  },
  ORDER: {
    id: 'ORDER',
    name: 'Order Lifecycle State Machine Node',
    layer: 'Core Domain',
    inputs: ['user_id', 'cart_snapshot', 'payment_confirmation', 'delivery_address'],
    outputs: ['order_id', 'status (CREATED|PAID|ASSIGNED|PICKED_UP|DELIVERED|CANCELLED)', 'otp', 'timeline'],
    invariants: [
      'State transition must be unidirectional (no jumping from CREATED to DELIVERED)',
      'Order requires valid payment or COD authorization before dispatch'
    ]
  },
  RIDER: {
    id: 'RIDER',
    name: 'Rider Fleet & Telemetry Node',
    layer: 'Logistics Domain',
    inputs: ['order_id', 'rider_id', 'gps_coordinates', 'delivery_otp'],
    outputs: ['dispatch_status', 'eta_minutes', 'polyline', 'is_handed_over'],
    invariants: [
      'Delivery handoff cannot succeed with incorrect OTP',
      'GPS telemetry coordinates must be monotonic in timestamp sequence'
    ]
  },
  DATABASE: {
    id: 'DATABASE',
    name: 'Database & Storage Mesh Node',
    layer: 'Infrastructure',
    inputs: ['sql_query', 'transaction_scope'],
    outputs: ['record_set', 'rows_affected', 'latency_ms'],
    invariants: ['Multi-node transactional updates must be atomic or rollback cleanly']
  }
};

/**
 * Directed edges defining data flow between nodes.
 * Each edge defines source, target, and what contract is verified.
 */
const EDGES = [
  { source: 'AUTH', target: 'ADDRESS', contract: 'Authenticated user context propagated' },
  { source: 'ADDRESS', target: 'INVENTORY', contract: 'Address coordinates resolve fulfillment hub' },
  { source: 'CATALOG', target: 'CART', contract: 'Item pricing & prescription rules bound to cart' },
  { source: 'INVENTORY', target: 'CART', contract: 'Stock availability verified before bill calculation' },
  { source: 'ADDRESS', target: 'CART', contract: 'Pincode determines delivery fee & tax brackets' },
  { source: 'CART', target: 'PAYMENT', contract: 'Validated total amount passed with idempotency key' },
  { source: 'PAYMENT', target: 'ORDER', contract: 'Payment authorization unlocks order creation' },
  { source: 'ORDER', target: 'RIDER', contract: 'Confirmed order broadcasts dispatch offer to fleet' },
  { source: 'ALL', target: 'DATABASE', contract: 'All state transitions persist atomically' }
];

module.exports = {
  NODES,
  EDGES
};
