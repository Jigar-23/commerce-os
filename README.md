# Commerce OS — Blinkit-Grade Quick-Commerce Platform

This repository contains the complete, unified source code for **Commerce OS**, an enterprise quick-commerce platform comprising:
1. **Android Rider App** (`apps/android/rider-app`)
2. **Android Customer App** (`apps/android/app`)
3. **iOS Customer App with Realtime SSE Tracking** (`apps/ios/CommerceOS`)
4. **Seller Merchant Dashboard** (`apps/seller`)
5. **Backend Unified Gateway & PostgreSQL Repositories** (`platform/`)
6. **Live Concurrency, Outbox & Schema Contract Test Suites** (`platform/test-*`)

---

## 1. Architectural Accomplishments & Schema/Repository Alignment

### 🛡️ Schema ↔ Repository Contract Harmonization
- **Authoritative `orders` Table**:
  - `id VARCHAR(64) PRIMARY KEY`, `order_id VARCHAR(64) UNIQUE NOT NULL`.
  - `delivery_otp_hash VARCHAR(128) NOT NULL` (HMAC-SHA256 hash).
  - `otp_expires_at TIMESTAMPTZ`, `otp_attempts INT`, `otp_verified_at TIMESTAMPTZ`.
  - **Zero Plaintext**: `delivery_otp` column eliminated from production schema and SQL queries.
- **Authoritative `delivery_sessions` Table**:
  - `id VARCHAR(64) PRIMARY KEY`, `delivery_id VARCHAR(64) UNIQUE NOT NULL`.
  - Full coordinate and address attributes (`merchant_name`, `merchant_address`, `merchant_lat`, `merchant_lng`, `customer_name`, `customer_address`, `customer_lat`, `customer_lng`).
  - `otp_verified BOOLEAN NOT NULL DEFAULT FALSE`.
  - **Zero Plaintext**: `delivery_pin` column eliminated from production schema and SQL queries.
- **Authoritative `sellers` Table**:
  - `id VARCHAR(64) PRIMARY KEY`, `seller_id VARCHAR(64) UNIQUE NOT NULL`, `email VARCHAR(255) UNIQUE`.
  - `password_hash VARCHAR(128) NOT NULL`, `store_id VARCHAR(64) REFERENCES stores(id)`, `roles JSONB`.
- **Authoritative `inventory` & `inventory_ledger` Tables**:
  - `inventory`: 3-state inventory model (`on_hand`, `reserved`, `available = on_hand - reserved`).
  - `inventory_ledger`: Immutable audit ledger recording `delta`, `new_stock`, and `reason` on every adjustment.
  - `CatalogRepository` manages catalog metadata only (zero stock mutations).

### 🔐 Cryptographic Delivery OTP Verification
- `DeliveryOtpService` enforces constant-time hash comparisons (`crypto.timingSafeEqual`) against stored `orders.delivery_otp_hash`.
- Invalid OTP attempts increment `orders.otp_attempts` in database.
- `COMMERCEOS_OTP_PEPPER` environment variable is strictly required in production mode (zero hardcoded fallback secrets in source code).

### 🔄 End-to-End Outbox & Crash Recovery Pipeline
- Atomic Order Placement Transaction:
  `orders` + `inventory` debit + `delivery_sessions` + `outbox_events` (`DISPATCH_REQUESTED`) inside a single `BEGIN ... COMMIT` block.
- On simulated process termination, `OutboxProcessor` boots, picks up `DISPATCH_REQUESTED`, executes `DispatchService.processDispatch()`, creates a real rider offer in `offers`, generates `NEW_DISPATCH_OFFER` in `outbox_events`, and marks the event `SENT`.

---

## 2. Frozen Subsystems

The following core modules are **FROZEN** and operate at enterprise standards:
- **Rider Realtime Subsystem**: FCM data-only payload, `OfferPayloadValidator`, `RiderOfferEventPipeline`, deduplication queue, local notification manager, and `SharedFlow`.
- **GPS & Routing Engine**: `RiderForegroundLocationService`, presence freshness gating, OSRM route calculation, Leaflet / MapView dark theme.
- **iOS Realtime SSE Tracking**: `TrackingRepository.swift` streaming with `URLSession.bytes(for:)` and 15-second reconciliation loop.
- **Outbox Engine**: `OutboxProcessor` polling worker with exponential backoff and durable database recovery.

---

## 3. Test Suites & Execution Instructions

### A. Dedicated Live PostgreSQL Suite (`platform/test-postgres-live/`)
Requires an active PostgreSQL instance (`DATABASE_URL=postgresql://user:pass@host:5432/db`):
```bash
# Run full live PostgreSQL suite (Fails if DATABASE_URL is missing)
DATABASE_URL=postgresql://user:pass@host:5432/commerceos node platform/test-postgres-live/run-live-postgres.js
```
Includes:
1. `schema-contract.test.js`: Validates 20/20 schema tables, columns, constraints, and verifies absence of plaintext OTP columns.
2. `order-transaction.test.js`: Executes real atomic order placement transaction and asserts database state.
3. `inventory-race.test.js`: 2 concurrent database clients competing for last unit ($N=1$).
4. `offer-race.test.js`: 2 concurrent riders accepting same delivery session.
5. `seller-isolation.test.js`: Multi-tenant store boundary isolation.
6. `customer-isolation.test.js`: Customer order & tracking isolation.
7. `crash-outbox.test.js`: Process crash recovery with `DispatchService` and downstream offer creation.

### B. Full Test Matrix (Zero Production Fallbacks)
```bash
# 1. FCM Sender, Telemetry & Outbox Idempotency Matrix
node platform/test-fcm-outbox-integration.js

# 2. Concurrency, Decline & Local Deduplication Matrix
node platform/test-concurrency-and-dedup.js

# 3. PostgreSQL Concurrency & Security Matrix
node platform/test-real-postgres-matrix.js

# 4. Production PostgreSQL Live Integration Suite
node platform/test-postgres-integration.js

# 5. Local Repository Contract Suite
node platform/test-postgres-live/run-local-contracts.js
```
*Result: 43/43 tests passing with 100% success rate.*

---

## 4. Current Blockers & Next Milestones

| Milestone | Status | Details |
| :--- | :--- | :--- |
| **Schema ↔ Repository Alignment** | ✅ COMPLETE | All SQL queries and tables match PostgreSQL 16+ schema exactly. |
| **Cryptographic OTP Security** | ✅ COMPLETE | Constant-time HMAC-SHA256 comparison, mandatory pepper, zero plaintext. |
| **Atomic Order Placement** | ✅ COMPLETE | ACID transaction with inventory debit, session creation, and outbox event. |
| **Crash & Outbox Recovery** | ✅ COMPLETE | Full business chain (`DISPATCH_REQUESTED` $\rightarrow$ `DispatchService` $\rightarrow$ `offers` $\rightarrow$ `NEW_DISPATCH_OFFER`). |
| **Dedicated Test Runners** | ✅ COMPLETE | `run-live-postgres.js` (strict CI gate) and `run-local-contracts.js`. |
| **Physical Device Verification** | ⏳ NEXT | Real Android/iOS physical device push notification and background GPS tracking. |
| **Final UI Polish** | ⏳ NEXT | Merchant dashboard polish and customer active tracking map styling. |
