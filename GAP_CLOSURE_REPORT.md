# Commerce OS — Enterprise Architecture & Environment Profiles Report

> **Architecture Overview:** Multi-Template Commerce & Quick-Commerce Operating System  
> **Authoritative Security Contract:** Unified Edge Gateway + PostgreSQL Transactional Outbox + Jackson Typed JWT Auth + Cryptographic OTP & Webhook Verification.

---

## 🏛️ 1. Formalized Runtime Profiles

Commerce OS explicitly defines three operational execution profiles:

| Profile | Target Environment | Runtime Architecture | Storage & Messaging | Security & Gateways |
|---|---|---|---|---|
| **`demo`** | Local Quick Demo | `platform/mock-server.js` + Web/Mobile Apps | `platform/db.json` disk store | Development JWT + Mock Gateway |
| **`staging`** | Pre-production Testing | `platform/server/production-server.js` | PostgreSQL 16 + Redis 7 | Live JWT + Sandbox Payment Provider + Test SMS |
| **`production`** | Live Production Deployment | Spring Domain Microservices & Node Gateway Edge | PostgreSQL Cluster + Redis + Kafka Outbox | Strict Fail-Fast JWT + Live Razorpay/Stripe + 2Factor SMS + FCM Data-Only Push |

---

## 🔍 2. Production Architectural Invariants

1. **Security & Cryptography**:
   - Mandatory fail-fast on missing `JWT_SECRET`, `PAYMENT_GATEWAY_WEBHOOK_SECRET`, `DATABASE_URL`.
   - Algorithm allowlist (`HS256` only) and constant-time HMAC-SHA256 signature verification.
   - Role-scoped tenant resolution (`COMMERCEOS_CUSTOMER_RETAIL`, `MERCHANT_<id>`, `FLEET_RIDER`, `PLATFORM_ROOT`).
   - WebAuthn endpoint returns `501 NOT_IMPLEMENTED` to prevent simulated credential binding.

2. **Payments & Financial Correctness**:
   - Provider abstraction (`StripePaymentProvider`, `RazorpayPaymentProvider`, `SandboxPaymentProvider`).
   - `PAYMENT_PROVIDER=SANDBOX` is forbidden in production environments.
   - Database-backed `payment_refunds` table with `idempotencyKey UNIQUE` and strict total-refund balance enforcement (`totalRefunded + requested <= capturedAmount`).
   - Atomic webhook deduplication via `payment_webhook_events` with unique event ID constraint.

3. **Cart & Catalog Authority**:
   - Cart accepts strictly `{ sku, quantity }` from clients.
   - All product metadata, prices, MRPs, Rx requirements, and cold-chain flags are resolved from the authoritative Catalog domain.
   - Web client persists only SKU and quantity in local state and refreshes from server.

4. **Inventory & Fulfillment**:
   - Database row-locking (`SELECT ... FOR UPDATE`) during batch ATP evaluation.
   - Public hold release strictly requires specific `reservationId` and customer ownership.
   - FEFO allocation consumes specific order reservations.

5. **Delivery & Plaintext OTP Protection**:
   - Delivery sessions require authoritative database records; zero synthetic fallback strings.
   - Plaintext delivery OTP is never returned in Ops/Admin/Rider DTOs.
   - Cryptographically random 6-digit OTP stored as SHA-256 hash at rest.
