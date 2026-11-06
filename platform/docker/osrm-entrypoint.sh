#!/bin/sh
set -e

DATA_DIR="/data"
OSRM_FILE="${DATA_DIR}/routing.osrm"

if [ ! -f "${OSRM_FILE}" ]; then
  echo "[OSRM Entrypoint] Initializing pinned operational road-network dataset..."
  mkdir -p "${DATA_DIR}"
  cat << 'EOF' > "${DATA_DIR}/network.osm"
<?xml version="1.0" encoding="UTF-8"?>
<osm version="0.6" generator="CommerceOS">
  <node id="1" lat="28.2010" lon="76.6140" version="1"/>
  <node id="2" lat="28.2025" lon="76.6155" version="1"/>
  <node id="3" lat="28.2040" lon="76.6170" version="1"/>
  <node id="4" lat="28.2050" lon="76.6180" version="1"/>
  <node id="5" lat="28.4550" lon="77.0250" version="1"/>
  <node id="6" lat="28.4595" lon="77.0266" version="1"/>
  <node id="7" lat="28.4680" lon="77.0350" version="1"/>
  <way id="100" version="1">
    <nd ref="1"/>
    <nd ref="2"/>
    <nd ref="3"/>
    <nd ref="4"/>
    <tag k="highway" v="primary"/>
    <tag k="oneway" v="no"/>
    <tag k="name" v="Rewari Central Road"/>
  </way>
  <way id="101" version="1">
    <nd ref="5"/>
    <nd ref="6"/>
    <nd ref="7"/>
    <tag k="highway" v="primary"/>
    <tag k="oneway" v="no"/>
    <tag k="name" v="Gurugram Cyber Road"/>
  </way>
</osm>
EOF
  osrm-extract -p /opt/car.lua "${DATA_DIR}/network.osm"
  osrm-partition "${DATA_DIR}/network.osrm"
  osrm-customize "${DATA_DIR}/network.osrm"
  mv "${DATA_DIR}/network.osrm" "${OSRM_FILE}"
  for ext in cells ebg edges enw fileIndex geometry mldgr nbg nodes partition properties ramIndex restrictions timestamp tld tls; do
    if [ -f "${DATA_DIR}/network.osrm.${ext}" ]; then
      mv "${DATA_DIR}/network.osrm.${ext}" "${DATA_DIR}/routing.osrm.${ext}"
    fi
  done
  echo "[OSRM Entrypoint] Pinned road-network dataset built successfully."
fi

exec "$@"
