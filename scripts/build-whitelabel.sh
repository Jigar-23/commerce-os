#!/usr/bin/env bash
set -e

# ==============================================================================
# Commerce OS — Turnkey White-Label Build & Verification Pipeline
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$ROOT_DIR"

CONFIG="${1:-clients/grocery-template.json}"

echo "=============================================================================="
echo "🚀 COMMERCE OS TURNKEY WHITE-LABEL COMPILATION & VERIFICATION PIPELINE"
echo "Target Config: $CONFIG"
echo "=============================================================================="

if [ ! -f "$CONFIG" ]; then
    echo "❌ Error: Config file not found at $CONFIG"
    echo "Usage: ./scripts/build-whitelabel.sh <path/to/client.json>"
    exit 1
fi

# 1. Apply White-label Branding & Configurations
echo -e "\n---> Phase 1: Applying Brand Configuration & Generating Profiles..."
node scripts/whitelabel-cli.js apply "$CONFIG"

# 2. Run Comprehensive Core Verification Suites
echo -e "\n---> Phase 2: Running 102 Production Static Guards..."
node platform/test-production-static-guards.test.js

echo -e "\n---> Phase 3: Running Route-Schema Contract Sync..."
node platform/test-route-inventory-sync.test.js

echo -e "\n---> Phase 4: Running Multi-Client Concurrency & Deduplication Matrix..."
node platform/test-concurrency-and-dedup.js

echo -e "\n---> Phase 5: Running Android Ecosystem Contract Audit..."
node apps/android/test-android-contract-audit.js

echo -e "\n---> Phase 6: Running Zero-Fee UPI & Address Concurrency Suite..."
node platform/test-upi-payment-and-address-concurrency.test.js

echo -e "\n---> Phase 7: Running Native iOS Swift 6.3 Toolchain & Target Verification..."
node apps/ios/verify-all-ios-targets.js

echo -e "\n=============================================================================="
echo "🏆 WHITE-LABEL GENERATION COMPLETE & 100% VERIFIED!"
echo "All static assertions, route contracts, mobile contracts, and iOS targets PASSED."
echo "=============================================================================="
