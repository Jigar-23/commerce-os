# Architecture Decision Record (ADR-001)
## Authoritative Domain Ownership & System Topology

**Status:** Accepted  
**Date:** 2026-08-17  
**Context:** Commerce OS Architecture Standardization across Node Edge BFF Gateway, Spring Microservices, and Local Development Fixtures.

---

### 1. Domain Ownership Matrix

To prevent dual-authority ambiguity and eliminate competing implementations, Commerce OS enforces single-authority domain boundaries:

| Domain | Authoritative Production Engine | Edge Gateway / BFF Role | Persistent Store |
|---|---|---|---|
| **Identity & Access Management** | Spring Identity Microservice (`services/identity`) | Bearer JWT verification & Cookie translation | PostgreSQL (`user_accounts`, `user_sessions`, `otp_challenges`) |
| **Catalog & Products** | Spring Catalog Microservice (`services/catalog`) | Catalog caching & public route proxy | PostgreSQL (`products`, `categories`, `seller_inventory`) |
| **Pricing & Promotions Engine** | Pricing Domain Service (`platform/pricing-engine.js` / Spring) | Authoritative checkout quote preview & lock | Dynamic Rule Engine & PostgreSQL |
| **Cart & Session State** | Spring Cart Microservice (`services/cart`) | Ingestion of SKU + quantity; server price resolution | PostgreSQL (`cart_items`) |
| **Order Orchestration** | Spring Order Microservice (`services/order`) | State machine dispatch & event outbox | PostgreSQL (`customer_orders`, `order_items`) |
| **Inventory & ATP / FEFO** | Spring Inventory Microservice (`services/inventory`) | Real-time ATP calculation & reservation consumption | PostgreSQL (`inventory_batches`, `inventory_reservations` with row locks) |
| **Payments & Financial Settlement** | Spring Payment Microservice (`services/payment`) | Webhook routing & Provider dispatch (Stripe/Razorpay) | PostgreSQL (`payment_intents`, `payment_refunds`, `payment_webhook_events`) |
| **Rider Fleet & Dispatch Intelligence** | Node Edge Gateway (`platform/server/production-server.js`) | Geo-spatial dispatch, SSE clustering & FCM push | Redis + PostgreSQL (`delivery_sessions`, `delivery_events`) |
| **Prescription & Medicine OCR** | Spring Prescription Microservice (`services/prescription`) | Secure upload verification & Pharmacist review | PostgreSQL (`prescriptions`, `rx_verifications`) |

---

### 2. Runtime Profile Separation

1. **`demo` Profile**:
   - `platform/mock-server.js` + `platform/db.json`.
   - Used for zero-dependency local frontend development and UI rapid prototyping.
   - **Never used as production release security authority**.

2. **`staging` Profile**:
   - Node Edge Gateway + PostgreSQL 16 + Redis 7 + Sandbox Provider Adapters.
   - Used for end-to-end integration and load testing.

3. **`production` Profile**:
   - Spring Microservices Core + Node Edge Gateway BFF + PostgreSQL 16 Cluster + Redis Cluster.
   - Mandatory fail-fast security (live Razorpay/Stripe, 2Factor SMS, FCM Data-Only).
