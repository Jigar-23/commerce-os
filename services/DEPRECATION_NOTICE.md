# ARCHITECTURAL NOTICE: CONVERGENCE TO PRODUCTION MONOLITH

> **Notice Date:** November 2026  
> **Status:** ARCHIVED / HISTORICAL REFERENCE

## Overview

The Commerce OS runtime architecture has converged from the legacy multi-service Spring Boot prototype into the authoritative, consolidated high-performance Node.js production server:

- **Active Authoritative Entrypoint:** [`platform/server/production-server.js`](file:///Users/jigar/Desktop/new-project/commerce-os/platform/server/production-server.js)
- **Active Authoritative Repositories:** [`platform/repositories/index.js`](file:///Users/jigar/Desktop/new-project/commerce-os/platform/repositories/index.js)
- **Active Schema & Migrations:** [`platform/schema.sql`](file:///Users/jigar/Desktop/new-project/commerce-os/platform/schema.sql)

All HTTP APIs, SSE event streams, WebSocket telemetry, serializable ACID state transitions, and background transactional outbox workers run inside `platform/server/production-server.js` listening on port `3000` (or `PORT` environment variable).

The Java Maven services in this `services/` directory (`ai`, `cart`, `catalog`, `customer`, `identity`, `inventory`, `order`, `payment`, `return`) are preserved solely for historical and architectural reference. They are not active in deployment pipelines.
