#!/usr/bin/env bash
export PATH="/Users/jigar/.nvm/versions/node/v20.20.2/bin:$PATH"
export NEXT_TELEMETRY_DISABLED=1
export NODE_OPTIONS="--max-old-space-size=4096"
cd /Users/jigar/Desktop/new-project/commerce-os/apps/seller
exec node ./node_modules/next/dist/bin/next dev -H 0.0.0.0 -p 3003
