#!/usr/bin/env bash
set -e

echo "================================================================"
echo "🚀 COMMERCE OS — COMPREHENSIVE PLATFORM & SUITE RUNNER"
echo "================================================================"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "Node Version: $(node -v)"
echo "Working Directory: $ROOT_DIR"
echo ""

echo "----------------------------------------------------------------"
echo "1. Production Static Guards & Invariant Enforcement (102 Guards)"
echo "----------------------------------------------------------------"
node platform/test-production-static-guards.test.js

echo ""
echo "----------------------------------------------------------------"
echo "2. Redis Distributed Cross-Node GPS Telemetry Architecture"
echo "----------------------------------------------------------------"
node platform/test-redis-distributed-cross-node-gps.test.js

echo ""
echo "----------------------------------------------------------------"
echo "3. Device Runtime Handoff & Failure Matrix"
echo "----------------------------------------------------------------"
node platform/test-device-runtime-handoff.test.js

echo ""
echo "----------------------------------------------------------------"
echo "4. Concurrency, Dedup & Lock Invariants"
echo "----------------------------------------------------------------"
node platform/test-concurrency-and-dedup.js

echo ""
echo "----------------------------------------------------------------"
echo "5. Local Domain Authority & Contract Matrices"
echo "----------------------------------------------------------------"
node platform/test-postgres-live/run-local-contracts.js

echo ""
echo "================================================================"
echo "🏆 ALL COMMERCE OS TEST SUITES EXECUTED WITH 100% SUCCESS"
echo "================================================================"
