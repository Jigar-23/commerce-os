#!/bin/sh
set -eo pipefail

DATA_DIR="/data"
OSRM_FILE="${DATA_DIR}/routing.osrm"
OSM_EXTRACT_VERSION="${OSRM_DATASET_VERSION:-v2026.11.01-delhi-ncr-haryana}"

if [ ! -f "${OSRM_FILE}" ]; then
  echo "[OSRM Entrypoint] Initializing versioned real regional road-network extract (${OSM_EXTRACT_VERSION})..."
  mkdir -p "${DATA_DIR}"

  # 1. Ensure authentic regional OSM PBF extract is present; download if needed
  if [ ! -f "${DATA_DIR}/region.osm.pbf" ]; then
    if [ -n "${OSM_EXTRACT_URL}" ]; then
      echo "[OSRM Entrypoint] Downloading versioned regional OSM extract from ${OSM_EXTRACT_URL}..."
      curl -fsSL "${OSM_EXTRACT_URL}" -o "${DATA_DIR}/region.osm.pbf" || {
        echo "[OSRM Entrypoint] FATAL: Failed to download authentic regional OSM extract from ${OSM_EXTRACT_URL}" >&2
        exit 1
      }
    else
      echo "[OSRM Entrypoint] FATAL: Neither ${DATA_DIR}/region.osm.pbf nor OSM_EXTRACT_URL is configured!" >&2
      exit 1
    fi
  fi

  # 2. Cryptographic checksum validation
  if [ -n "${OSM_EXTRACT_SHA256}" ]; then
    echo "[OSRM Entrypoint] Verifying PBF extract integrity against pinned SHA256 checksum..."
    echo "${OSM_EXTRACT_SHA256}  ${DATA_DIR}/region.osm.pbf" | sha256sum -c - || {
      echo "[OSRM Entrypoint] FATAL: Checksum verification failed for ${DATA_DIR}/region.osm.pbf!" >&2
      exit 1
    }
  fi

  # 3. Extract road network using car profile (fail immediately on error)
  echo "[OSRM Entrypoint] Running osrm-extract on authentic regional dataset..."
  osrm-extract -p /opt/car.lua "${DATA_DIR}/region.osm.pbf" || {
    echo "[OSRM Entrypoint] FATAL: osrm-extract failed on regional dataset!" >&2
    exit 1
  }

  # 4. Partition road network into Multi-Level Dijkstra cells (fail immediately on error)
  echo "[OSRM Entrypoint] Running osrm-partition..."
  osrm-partition "${DATA_DIR}/region.osrm" || {
    echo "[OSRM Entrypoint] FATAL: osrm-partition failed!" >&2
    exit 1
  }

  # 5. Customize cell weights and turn penalties (fail immediately on error)
  echo "[OSRM Entrypoint] Running osrm-customize..."
  osrm-customize "${DATA_DIR}/region.osrm" || {
    echo "[OSRM Entrypoint] FATAL: osrm-customize failed!" >&2
    exit 1
  }

  # 6. Consolidate build files to canonical routing.osrm
  mv "${DATA_DIR}/region.osrm" "${OSRM_FILE}"
  for ext in cells ebg edges enw fileIndex geometry mldgr nbg nodes partition properties ramIndex restrictions timestamp tld tls; do
    if [ -f "${DATA_DIR}/region.osrm.${ext}" ]; then
      mv "${DATA_DIR}/region.osrm.${ext}" "${DATA_DIR}/routing.osrm.${ext}"
    fi
  done

  echo "[OSRM Entrypoint] Versioned road-network dataset built successfully (${OSM_EXTRACT_VERSION})."
fi

# If invoked during docker build as verification step
if [ "$1" = "true" ] || [ "$1" = "verify" ]; then
  echo "[OSRM Entrypoint] Build verification complete."
  exit 0
fi

exec "$@"
