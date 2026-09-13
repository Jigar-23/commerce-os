# 🗺️ Commerce OS — Authoritative Production Route Inventory Matrix

This document provides the authoritative platform route inventory, classifying every route into one of four execution tiers:
- **`REAL PRODUCTION`**: Backed directly by PostgreSQL 16+, standalone application server (`platform/server/production-server.js`), fail-fast configuration, data-only FCM push adapter, and active outbox processing.
- **`PARTIAL`**: Domain repository and PostgreSQL persistence contracts complete; exposed via domain repository interfaces.
- **`NOT IMPLEMENTED`**: Planned for future milestones.
- **`LOCAL_TEST ONLY`**: Designated strictly for offline unit testing (`platform/mock-server.js`).

---

## 1. Gateway & Infrastructure Endpoints

| Method | Endpoint | Classification | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/orders/health` | **REAL PRODUCTION** | Lightweight process liveness probe. |
| `GET` | `/api/v1/orders/ready` | **REAL PRODUCTION** | Deep readiness probe: executes `SELECT 1` on PostgreSQL pool, verifies active OutboxProcessor worker, checks OSRM configuration & reachability, checks FCM server key & endpoint, and checks SSE broadcaster. |
| `GET` | `/api/v1/realtime/stream` | **REAL PRODUCTION** | Authenticated Server-Sent Events (SSE) stream subscription per user channel (`JWT.sub`). |

---

## 2. Customer Domain

| Method | Endpoint | Classification | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/customer/otp/send` | **REAL PRODUCTION** | Customer cryptographic OTP generation, rate limiting, and outbox SMS event dispatch. |
| `POST` | `/api/v1/auth/customer/otp/verify` | **REAL PRODUCTION** | Customer OTP verification against PostgreSQL challenge, customer account provisioning, and signed JWT issuance. |
| `GET` | `/api/v1/catalog/home-feed` | **REAL PRODUCTION** | Curated catalog home feed, banners, categories, and active products. |
| `GET` | `/api/v1/catalog/categories` | **REAL PRODUCTION** | Catalog taxonomy category listings. |
| `GET` | `/api/v1/catalog/destinations` | **REAL PRODUCTION** | Top shopping destination verticals. |
| `GET` | `/api/v1/catalog/medicines/search` | **REAL PRODUCTION** | Medicine catalog full-text and category search. |
| `GET` | `/api/v1/search` | **REAL PRODUCTION** | Catalog product keyword search. |
| `GET` | `/api/v1/search/autocomplete` | **REAL PRODUCTION** | Fast product autocomplete prefix matching. |
| `GET` | `/api/v1/cart/:customerId` | **REAL PRODUCTION** | Server-authoritative cart query with dynamic pricing totals breakdown. |
| `POST` | `/api/v1/cart/:customerId/items` | **REAL PRODUCTION** | Server-authoritative transactional cart item addition. |
| `PATCH` | `/api/v1/cart/:customerId/items/:sku` | **REAL PRODUCTION** | Server-authoritative cart quantity adjustment. |
| `DELETE` | `/api/v1/cart/:customerId/items/:sku` | **REAL PRODUCTION** | Server-authoritative cart item removal. |
| `GET` | `/api/v1/customers/:customerId/addresses` | **REAL PRODUCTION** | Customer address book lookup. |
| `POST` | `/api/v1/customers/:customerId/addresses` | **REAL PRODUCTION** | Customer address book entry creation with latitude/longitude. |
| `POST` | `/api/v1/orders/serviceability` | **REAL PRODUCTION** | Geodesic dark store serviceability and delivery promise evaluation. |
| `POST` | `/api/v1/pricing/quote` | **REAL PRODUCTION** | Order financial quote and fee computation. |
| `POST` | `/api/v1/orders/checkout-from-cart/:customerId` | **REAL PRODUCTION** | Direct checkout from server cart with transactional order placement and cart cleanup. |
| `GET` | `/api/v1/orders/customer/:customerId` | **REAL PRODUCTION** | Customer order history query. |
| `GET` | `/api/v1/orders/:orderId` | **REAL PRODUCTION** | Detailed customer order snapshot. |
| `POST` | `/api/v1/orders` | **REAL PRODUCTION** | Server-authoritative atomic order placement, store serviceability evaluation via `ServiceabilityService`, store-scoped stock reservation, server-generated OTP, payment initialization, and outbox event publishing (`ORDER_PLACED`). |
| `POST` | `/api/v1/orders/:id/cancel` | **REAL PRODUCTION** | Actor-authorized order cancellation (`Customer`, `Seller`, `Admin`), restoring store-scoped inventory reservation and publishing `ORDER_CANCELLED` outbox event. |
| `GET` | `/api/v1/orders/active-delivery` | **REAL PRODUCTION** | Authenticated customer live order tracking query. Returns explicit sanitized DTO (zero `delivery_otp_hash` or plaintext OTP leaks). |

---

## 3. Seller Domain

| Method | Endpoint | Classification | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/seller/login` | **REAL PRODUCTION** | Seller authentication verifying credentials via Scrypt KDF against PostgreSQL and issuing signed JWT. |
| `GET` | `/api/v1/orders/seller` | **REAL PRODUCTION** | Database-authorized seller order queue for the seller's authoritative `store_id`. Returns sanitized seller order DTO (excludes delivery OTP hashes). |
| `GET` | `/api/v1/orders/cod-ledger` | **REAL PRODUCTION** | Retrieves COD transaction ledger records for merchant store reconciliation. |
| `POST` | `/api/v1/orders/:id/accept-by-seller` | **REAL PRODUCTION** | Seller atomic order acceptance, updating order status to `SELLER_ACCEPTED` and enqueuing outbox event. |
| `POST` | `/api/v1/orders/:id/pack` | **REAL PRODUCTION** | Seller marking order as `PACKED` with state validation (`SELLER_ACCEPTED` required). |
| `POST` | `/api/v1/orders/:id/ready-for-pickup` | **REAL PRODUCTION** | Seller marking order as `READY_FOR_PICKUP`, enqueuing exactly one idempotent `DISPATCH_REQUESTED` outbox event. |
| `GET` | `/api/v1/seller/store/settings` | **REAL PRODUCTION** | Retrieves current operational settings, auto-accept status, and service hours for seller store. |
| `PATCH` | `/api/v1/seller/store/settings` | **REAL PRODUCTION** | Updates operational settings for authenticated merchant dark store. |
| `GET` | `/api/v1/catalog/seller/inventory` | **REAL PRODUCTION** | Database-scoped seller inventory listing for the seller's authoritative `store_id`. Rows are derived from store-scoped `inventory` joined to the global `products` catalog (zero `products.store_id` dependence), returning global product metadata (name/brand/MRP/price/category) plus 3-state store availability. Explicit sanitized DTO. |
| `GET` | `/api/v1/catalog/seller/inventory-history` | **REAL PRODUCTION** | Store-scoped immutable `inventory_ledger` trail for the seller's authoritative `store_id`. Explicit sanitized DTO. |
| `POST` | `/api/v1/catalog/inventory/adjust` | **REAL PRODUCTION** | Store-scoped inventory stock adjustment resolving the canonical `(store_id, product_id, sku)` triple against the global catalog, with `inventory_ledger` audit trail. Server-side seller store authority only (JWT.sub -> sellers.store_id). |
| `POST` | `/api/v1/catalog/inventory/adjust/undo` | **REAL PRODUCTION** | Reverses a previous store inventory adjustment entry transactionally via compensatory ledger entry. |
| `GET` | `/api/v1/catalog/products` | **REAL PRODUCTION** | Catalog product listing. If seller JWT is presented, returns store-scoped products derived from store inventory joined to global catalog. If unauthenticated/customer, returns global catalog products. |
| `POST` | `/api/v1/catalog/products` | **REAL PRODUCTION** | Seller attaches store inventory to an EXISTING global product (`200` link). Creating a NEW global catalog identity is restricted to DB-backed `GLOBAL_CATALOG_WRITE` operators (`201`); unknown SKU for a normal seller is rejected `403 GLOBAL_CATALOG_WRITE_REQUIRED`. Store availability is written as a store-scoped `inventory` row. |
| `PATCH` | `/api/v1/catalog/products/:id` | **REAL PRODUCTION** | GLOBAL CATALOG WRITE GATE: any global catalog field (`name`, `mrp`, `price`, `sku`, `rxRequirement`, etc.) is read-only for sellers (`403 GLOBAL_CATALOG_WRITE_REQUIRED`); only `GLOBAL_CATALOG_WRITE` operators may mutate global metadata. Seller stock is updated via the `/stock` and `/adjust` endpoints. |
| `DELETE` | `/api/v1/catalog/products/:id` | **REAL PRODUCTION** | Global product deactivation (soft delete) restricted to DB-backed `GLOBAL_CATALOG_WRITE` operators. Sellers cannot globally deactivate products; sellers disable own store inventory availability. |
| `PATCH` | `/api/v1/catalog/products/:id/stock` | **REAL PRODUCTION** | Seller store-scoped stock adjustment for a listed global product, resolved via the canonical triple and `inventory_ledger`. |

---

## 4. Rider Domain

| Method | Endpoint | Classification | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/rider/otp/send` | **REAL PRODUCTION** | Rider cryptographic OTP challenge generation and verification dispatch. |
| `POST` | `/api/v1/auth/rider/otp/verify` | **REAL PRODUCTION** | Rider OTP verification against PostgreSQL, active rider profile lookup, and signed JWT issuance. |
| `GET` | `/api/v1/delivery/rider/profile` | **REAL PRODUCTION** | Authentic rider account profile with presence status and operational metrics. |
| `POST` | `/api/v1/delivery/rider/shift-status` | **REAL PRODUCTION** | Updates rider operational online/offline shift status. |
| `POST` | `/api/v1/delivery/rider/device-token` | **REAL PRODUCTION** | Registers FCM data push notification device token for active rider. |
| `POST` | `/api/v1/delivery/rider/device-token/logout` | **REAL PRODUCTION** | De-registers FCM device token on session termination. |
| `GET` | `/api/v1/delivery/rider/notifications` | **REAL PRODUCTION** | Retrieves persisted rider notifications queue. |
| `POST` | `/api/v1/delivery/rider/notifications/:id/read` | **REAL PRODUCTION** | Marks specific notification as read. |
| `POST` | `/api/v1/delivery/rider/notifications/read-all` | **REAL PRODUCTION** | Marks all rider notifications as read. |
| `GET` | `/api/v1/delivery/rider/active-session` | **REAL PRODUCTION** | Queries active in-flight delivery session for authenticated rider. |
| `GET` | `/api/v1/delivery/offers/active` | **REAL PRODUCTION** | Active delivery offers query for authenticated rider. |
| `POST` | `/api/v1/delivery/offers/:id/ack` | **REAL PRODUCTION** | Acknowledges receipt/display of active delivery offer. |
| `POST` | `/api/v1/rider/offers/:id/accept` | **REAL PRODUCTION** | Transactional rider offer acceptance with offer ownership validation and DB partial unique constraint protecting against double-assignment. |
| `POST` | `/api/v1/delivery/offers/:id/decline` | **REAL PRODUCTION** | Declines active dispatch offer. |
| `POST` | `/api/v1/delivery/session/:deliveryId/arrive-merchant` | **REAL PRODUCTION** | Rider arrival at merchant dark store checkpoint. |
| `POST` | `/api/v1/delivery/session/:deliveryId/pickup` | **REAL PRODUCTION** | Order pickup confirmation transitioning delivery session to `PICKED_UP`. |
| `POST` | `/api/v1/delivery/:deliveryId/telemetry` | **REAL PRODUCTION** | High-frequency GPS telemetry ingestion with sequence enforcement, duplicate idempotency, and dual-plane fanout. |
| `POST` | `/api/v1/delivery/session/:deliveryId/arrive-customer` | **REAL PRODUCTION** | Rider arrival at customer destination. |
| `POST` | `/api/v1/delivery/session/:deliveryId/resend-otp` | **REAL PRODUCTION** | Triggers resending delivery verification OTP to customer. |
| `POST` | `/api/v1/delivery/session/:deliveryId/report-issue` | **REAL PRODUCTION** | Reports operational delivery exception/issue with audit log persistence. |
| `POST` | `/api/v1/delivery/session/:deliveryId/cancel` | **REAL PRODUCTION** | Authoritative emergency cancellation of active delivery session. |
| `POST` | `/api/v1/orders/:deliveryId/deliver-with-otp` | **REAL PRODUCTION** | Rider delivery completion via secure OTP verification with SHA-256 hash comparison and COD collection settlement. |

---

## 5. Admin & Pharmacist Domain

| Method | Endpoint | Classification | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/orders/audit` | **REAL PRODUCTION** | Role-based access controlled immutable audit logs query. Admin receives global logs; seller receives store-scoped logs. |
| `POST` | `/api/v1/prescriptions/:id/verify` | **PARTIAL** | Licensed pharmacist prescription verification and approval. |

---

## 6. Offline Mock Testing Gateway

| Service | File | Classification | Description |
| :--- | :--- | :--- | :--- |
| `MockServer` | `platform/mock-server.js` | **LOCAL_TEST ONLY** | Offline simulation server for local headless testing. Strictly forbidden in production environments. |
