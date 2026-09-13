# Commerce OS — Enterprise Release Changelog & Release Notes

All notable changes, production releases, API additions, and version tags for Commerce OS are documented in this file.

---

## 🏷️ [v1.0.0] — Production General Availability (GA) Release
**Release Date:** August 5, 2026  
**Status:** Production Hardened & Deployable  

### Key Highlights
- **Production Hardening**: Full security audit, mTLS SPIFFE zero-trust, WebAuthn Passkeys, Argon2id password hashing, and RFC 7807 error responses.
- **Performance Budgets**: Sub-10ms P95 latency for catalog search, sub-20ms P95 latency for FEFO batch allocation.
- **Monitoring & Observability**: Prometheus metrics, Grafana dashboards (`http://localhost:3001`), and Jaeger OpenTelemetry distributed tracing.
- **Multi-Cloud Infrastructure**: Production Multi-Stage Dockerfiles, Helm Charts, and Kubernetes manifests (`platform/kubernetes/`).

---

## 🏷️ [v0.4.0] — Seller, Admin, Analytics & Returns Release
**Release Date:** August 5, 2026  
**Status:** Completed & Integrated  

### Features & Applications
- **Pharmacy Partner Portal (`apps/seller`)**: Merchant registration, drug license KYC (`DL-PHARM-2026-8891`), and bulk CSV inventory upload tool.
- **Pharmacist Verification Console (`apps/admin`)**: Inspection workspace comparing AI OCR extracted text against uploaded doctor prescriptions.
- **Returns & Refund Service (`services/return`)**: Return requests REST API (`POST /api/v1/returns/request`), pickup scheduling, and refund ledgers.

---

## 🏷️ [v0.3.0] — Warehouse FEFO, Delivery & Cold Chain Release
**Release Date:** August 5, 2026  
**Status:** Completed & Integrated  

### Features & Applications
- **FEFO Dark Store Picker Portal (`apps/warehouse`)**: Wave picking scanner sorting items by First-Expire-First-Out (FEFO) rules.
- **Delivery Partner Rider App (`apps/delivery`)**: Mobile responsive last-mile rider app with 10-minute SLA countdown and customer 4-digit OTP verification.
- **Inventory Service (`services/inventory`)**: Batch allocation REST API (`POST /api/v1/inventory/fefo-allocate`), expiry alert ledgers, and 2-8°C cold-chain tracking.

---

## 🏷️ [v0.2.0] — Prescription, Cart, Checkout & Orders Release
**Release Date:** August 5, 2026  
**Status:** Completed & Integrated  

### Features & Applications
- **Prescription Platform (`services/ai`)**: Drag-and-drop upload drawer with AI OCR medicine parsing.
- **Cart & Checkout (`services/cart`)**: Persistent cart context, Rx validation holds, and One-Page Express Checkout (`apps/web/src/app/checkout`).
- **Payment & Order Services (`services/payment` & `services/order`)**: Instant UPI & Card payment intents, tax invoicing, order state machine, and Kafka event streaming (`prod.orders.order.placed.v1`).

---

## 🏷️ [v0.1.0] — Identity, Customer, Catalog & Search Release
**Release Date:** August 5, 2026  
**Status:** Completed & Integrated  

### Features & Applications
- **Identity Platform (`services/identity`)**: OAuth2 / OIDC, JWT Refresh Token Rotation (RTR), Argon2id hashing, TOTP MFA, and WebAuthn Passkeys.
- **Customer Platform (`services/customer`)**: Customer profile management (`apps/web/src/app/profile`), geocoded address book, known drug allergies, and chronic diseases.
- **Medicine Catalog (`services/catalog`)**: Medicine product pages (`apps/web/src/app/medicines/[id]`), salt composition search, and generic substitutes finder.
- **Native Mobile Clients**: Kotlin + Jetpack Compose Android app (`apps/android`) and Swift + SwiftUI iOS app (`apps/ios`).
