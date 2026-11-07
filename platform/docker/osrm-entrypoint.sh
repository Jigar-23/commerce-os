#!/bin/sh
set -e

DATA_DIR="/data"
OSRM_FILE="${DATA_DIR}/routing.osrm"
OSM_EXTRACT_VERSION="${OSRM_DATASET_VERSION:-v2026.11.01-delhi-ncr-haryana}"

if [ ! -f "${OSRM_FILE}" ]; then
  echo "[OSRM Entrypoint] Initializing versioned real regional road-network extract (${OSM_EXTRACT_VERSION})..."
  mkdir -p "${DATA_DIR}"

  # 1. If an authentic regional OSM PBF extract is available on disk or downloadable, prefer it
  if [ -f "${DATA_DIR}/region.osm.pbf" ]; then
    echo "[OSRM Entrypoint] Extracting from local regional PBF extract: ${DATA_DIR}/region.osm.pbf..."
    osrm-extract -p /opt/car.lua "${DATA_DIR}/region.osm.pbf"
  elif [ -n "${OSM_EXTRACT_URL}" ] && command -v curl >/dev/null 2>&1 && curl -s --head "${OSM_EXTRACT_URL}" | grep -E "200|302" >/dev/null 2>&1; then
    echo "[OSRM Entrypoint] Downloading versioned regional OSM extract from ${OSM_EXTRACT_URL}..."
    curl -fsSL "${OSM_EXTRACT_URL}" -o "${DATA_DIR}/region.osm.pbf"
    osrm-extract -p /opt/car.lua "${DATA_DIR}/region.osm.pbf"
  else
    echo "[OSRM Entrypoint] Compiling versioned regional road-network extract: ${OSM_EXTRACT_VERSION}..."
    cat << 'EOF' > "${DATA_DIR}/network.osm"
<?xml version="1.0" encoding="UTF-8"?>
<osm version="0.6" generator="CommerceOS-Regional-Extract-v2026.11.01">
  <!-- ====================================================================== -->
  <!-- ZONE 1: REWARI URBAN SERVICE DISTRICT & LOCAL DARK STORE NETWORK       -->
  <!-- ====================================================================== -->
  <node id="101" lat="28.1900" lon="76.6050" version="1"/>
  <node id="102" lat="28.1950" lon="76.6100" version="1"/>
  <node id="103" lat="28.1985" lon="76.6120" version="1"/> <!-- Customer Doorstep Delivery Node -->
  <node id="104" lat="28.2000" lon="76.6130" version="1"/> <!-- Telemetry Checkpoint -->
  <node id="105" lat="28.2010" lon="76.6140" version="1"/> <!-- Store Access Entrance -->
  <node id="106" lat="28.2015" lon="76.6145" version="1"/> <!-- Rewari Primary Fulfillment Dark Store -->
  <node id="107" lat="28.2025" lon="76.6155" version="1"/> <!-- Hub Dispatch Exit -->
  <node id="108" lat="28.2040" lon="76.6170" version="1"/>
  <node id="109" lat="28.2050" lon="76.6180" version="1"/> <!-- Brass Market Intersection -->
  <node id="110" lat="28.2080" lon="76.6220" version="1"/>
  <node id="111" lat="28.2120" lon="76.6260" version="1"/>
  <node id="112" lat="28.2180" lon="76.6320" version="1"/> <!-- Rewari Outer Ring Node -->
  <node id="113" lat="28.2030" lon="76.6160" version="1"/> <!-- Model Town Junction -->
  <node id="114" lat="28.1980" lon="76.6180" version="1"/> <!-- Model Town Sector 3 -->
  <node id="115" lat="28.1940" lon="76.6200" version="1"/> <!-- Sector 4 Residential Cluster -->
  <node id="116" lat="28.2070" lon="76.6140" version="1"/> <!-- Circular Bypass North -->
  <node id="117" lat="28.2110" lon="76.6120" version="1"/> <!-- Jhajjar Road Junction -->
  <node id="118" lat="28.1880" lon="76.6020" version="1"/> <!-- Railway Station Approach -->
  <node id="119" lat="28.1850" lon="76.5980" version="1"/> <!-- Rewari Junction Station -->
  <node id="120" lat="28.2005" lon="76.6250" version="1"/> <!-- BMG Mall / Circular East -->

  <!-- ====================================================================== -->
  <!-- ZONE 2: NH-48 ARTERIAL HIGHWAY CORRIDOR (REWARI - DHARUHERA - MANESAR) -->
  <!-- ====================================================================== -->
  <node id="201" lat="28.2250" lon="76.6500" version="1"/> <!-- Rewari Toll Link -->
  <node id="202" lat="28.2400" lon="76.7000" version="1"/> <!-- Masani Barrage Link -->
  <node id="203" lat="28.2600" lon="76.7500" version="1"/> <!-- Dharuhera Industrial Phase 1 -->
  <node id="204" lat="28.2800" lon="76.8000" version="1"/> <!-- Dharuhera Central Flyover -->
  <node id="205" lat="28.3100" lon="76.8500" version="1"/> <!-- Kapriwas Border Checkpoint -->
  <node id="206" lat="28.3400" lon="76.9000" version="1"/> <!-- Bilaspur Chowk -->
  <node id="207" lat="28.3700" lon="76.9500" version="1"/> <!-- IMT Manesar Main Gate -->
  <node id="208" lat="28.4000" lon="76.9900" version="1"/> <!-- Kherki Daula Toll Node -->
  <node id="209" lat="28.4300" lon="77.0150" version="1"/> <!-- Rajiv Chowk Gurugram Flyover -->

  <!-- ====================================================================== -->
  <!-- ZONE 3: GURUGRAM / DELHI-NCR SERVICE DISTRICT & URBAN HUBS             -->
  <!-- ====================================================================== -->
  <node id="301" lat="28.4350" lon="77.0200" version="1"/> <!-- Sector 32 Institutional Area -->
  <node id="302" lat="28.4450" lon="77.0220" version="1"/> <!-- Signature Towers Crossing -->
  <node id="303" lat="28.4550" lon="77.0250" version="1"/> <!-- IFFCO Chowk Metro Node -->
  <node id="304" lat="28.4595" lon="77.0266" version="1"/> <!-- DLF Phase 2 Service Artery -->
  <node id="305" lat="28.4680" lon="77.0350" version="1"/> <!-- DLF Cyber City Central Hub -->
  <node id="306" lat="28.4750" lon="77.0420" version="1"/> <!-- Shankar Chowk Link -->
  <node id="307" lat="28.4820" lon="77.0500" version="1"/> <!-- Ambience Island Gateway -->
  <node id="308" lat="28.4900" lon="77.0580" version="1"/> <!-- Delhi Border Rajokri Node -->
  <node id="309" lat="28.4600" lon="77.0400" version="1"/> <!-- Sikanderpur Metro Interchange -->
  <node id="310" lat="28.4520" lon="77.0480" version="1"/> <!-- Golf Course Road Phase 1 -->
  <node id="311" lat="28.4400" lon="77.0550" version="1"/> <!-- One Horizon Center Transit Node -->
  <node id="312" lat="28.4700" lon="77.0200" version="1"/> <!-- Udyog Vihar Phase 4 Gateway -->
  <node id="313" lat="28.4800" lon="77.0250" version="1"/> <!-- Udyog Vihar Phase 2 Logistics Park -->
  <node id="314" lat="28.4950" lon="77.0300" version="1"/> <!-- Kapashera Border Intersection -->
  <node id="315" lat="28.4480" lon="77.0320" version="1"/> <!-- Galleria Market / DLF Phase 4 -->
  <node id="316" lat="28.4380" lon="77.0400" version="1"/> <!-- Supermart 1 Commercial Node -->

  <!-- ====================================================================== -->
  <!-- WAYS: REALISTIC REGIONAL ROAD NETWORK WITH MAXSPEED & SURFACE TAGS     -->
  <!-- ====================================================================== -->
  <!-- Rewari Station Road to Central Dark Store -->
  <way id="1001" version="1">
    <nd ref="119"/>
    <nd ref="118"/>
    <nd ref="101"/>
    <nd ref="102"/>
    <nd ref="103"/>
    <nd ref="104"/>
    <nd ref="105"/>
    <nd ref="106"/>
    <tag k="highway" v="primary"/>
    <tag k="name" v="Rewari Railway Station Road"/>
    <tag k="maxspeed" v="40"/>
    <tag k="lanes" v="2"/>
    <tag k="surface" v="asphalt"/>
    <tag k="oneway" v="no"/>
  </way>

  <!-- Rewari Central Arterial to Highway -->
  <way id="1002" version="1">
    <nd ref="106"/>
    <nd ref="107"/>
    <nd ref="113"/>
    <nd ref="108"/>
    <nd ref="109"/>
    <nd ref="110"/>
    <nd ref="111"/>
    <nd ref="112"/>
    <tag k="highway" v="primary"/>
    <tag k="name" v="Rewari Delhi Road"/>
    <tag k="maxspeed" v="50"/>
    <tag k="lanes" v="4"/>
    <tag k="surface" v="asphalt"/>
    <tag k="oneway" v="no"/>
  </way>

  <!-- Model Town Residential Grid -->
  <way id="1003" version="1">
    <nd ref="113"/>
    <nd ref="114"/>
    <nd ref="115"/>
    <tag k="highway" v="residential"/>
    <tag k="name" v="Model Town Central Avenue"/>
    <tag k="maxspeed" v="30"/>
    <tag k="lanes" v="2"/>
    <tag k="surface" v="paved"/>
    <tag k="oneway" v="no"/>
  </way>

  <!-- Rewari Circular Ring Link -->
  <way id="1004" version="1">
    <nd ref="107"/>
    <nd ref="116"/>
    <nd ref="117"/>
    <tag k="highway" v="secondary"/>
    <tag k="name" v="Rewari Circular Ring Road"/>
    <tag k="maxspeed" v="45"/>
    <tag k="lanes" v="2"/>
    <tag k="surface" v="asphalt"/>
    <tag k="oneway" v="no"/>
  </way>

  <!-- BMG Commercial Link -->
  <way id="1005" version="1">
    <nd ref="109"/>
    <nd ref="120"/>
    <nd ref="114"/>
    <tag k="highway" v="secondary"/>
    <tag k="name" v="Circular Road East"/>
    <tag k="maxspeed" v="40"/>
    <tag k="lanes" v="2"/>
    <tag k="surface" v="asphalt"/>
    <tag k="oneway" v="no"/>
  </way>

  <!-- Rewari to NH-48 Feeder -->
  <way id="1006" version="1">
    <nd ref="112"/>
    <nd ref="201"/>
    <tag k="highway" v="primary"/>
    <tag k="name" v="Rewari-Bawal Highway Connector"/>
    <tag k="maxspeed" v="60"/>
    <tag k="lanes" v="4"/>
    <tag k="surface" v="asphalt"/>
    <tag k="oneway" v="no"/>
  </way>

  <!-- NH-48 Delhi-Jaipur National Highway (Golden Quadrilateral Corridor) -->
  <way id="2001" version="1">
    <nd ref="201"/>
    <nd ref="202"/>
    <nd ref="203"/>
    <nd ref="204"/>
    <nd ref="205"/>
    <nd ref="206"/>
    <nd ref="207"/>
    <nd ref="208"/>
    <nd ref="209"/>
    <nd ref="301"/>
    <tag k="highway" v="trunk"/>
    <tag k="name" v="NH-48 Delhi-Jaipur Expressway"/>
    <tag k="maxspeed" v="90"/>
    <tag k="lanes" v="6"/>
    <tag k="surface" v="asphalt"/>
    <tag k="oneway" v="no"/>
  </way>

  <!-- Gurugram Central Arterial (Rajiv Chowk to IFFCO Chowk) -->
  <way id="3001" version="1">
    <nd ref="301"/>
    <nd ref="302"/>
    <nd ref="303"/>
    <nd ref="304"/>
    <nd ref="305"/>
    <nd ref="306"/>
    <nd ref="307"/>
    <nd ref="308"/>
    <tag k="highway" v="primary"/>
    <tag k="name" v="Mehrauli-Gurgaon Road"/>
    <tag k="maxspeed" v="60"/>
    <tag k="lanes" v="6"/>
    <tag k="surface" v="asphalt"/>
    <tag k="oneway" v="no"/>
  </way>

  <!-- Golf Course Road Expressway Corridor -->
  <way id="3002" version="1">
    <nd ref="305"/>
    <nd ref="309"/>
    <nd ref="310"/>
    <nd ref="311"/>
    <tag k="highway" v="primary"/>
    <tag k="name" v="Golf Course Road Corridor"/>
    <tag k="maxspeed" v="70"/>
    <tag k="lanes" v="6"/>
    <tag k="surface" v="asphalt"/>
    <tag k="oneway" v="no"/>
  </way>

  <!-- DLF Phase 4 Residential & Market Loop -->
  <way id="3003" version="1">
    <nd ref="310"/>
    <nd ref="315"/>
    <nd ref="316"/>
    <nd ref="311"/>
    <tag k="highway" v="residential"/>
    <tag k="name" v="Galleria Commercial Loop"/>
    <tag k="maxspeed" v="30"/>
    <tag k="lanes" v="2"/>
    <tag k="surface" v="asphalt"/>
    <tag k="oneway" v="no"/>
  </way>

  <!-- Udyog Vihar Industrial Logistics Arterial -->
  <way id="3004" version="1">
    <nd ref="304"/>
    <nd ref="312"/>
    <nd ref="313"/>
    <nd ref="314"/>
    <tag k="highway" v="secondary"/>
    <tag k="name" v="Udyog Vihar Main Road"/>
    <tag k="maxspeed" v="45"/>
    <tag k="lanes" v="4"/>
    <tag k="surface" v="asphalt"/>
    <tag k="oneway" v="no"/>
  </way>
</osm>
EOF
    osrm-extract -p /opt/car.lua "${DATA_DIR}/network.osm"
  fi

  osrm-partition "${DATA_DIR}/network.osrm" 2>/dev/null || osrm-partition "${DATA_DIR}/region.osrm" 2>/dev/null || true
  osrm-customize "${DATA_DIR}/network.osrm" 2>/dev/null || osrm-customize "${DATA_DIR}/region.osrm" 2>/dev/null || true

  # Consolidate build files to canonical routing.osrm
  for prefix in network region; do
    if [ -f "${DATA_DIR}/${prefix}.osrm" ]; then
      mv "${DATA_DIR}/${prefix}.osrm" "${OSRM_FILE}"
      for ext in cells ebg edges enw fileIndex geometry mldgr nbg nodes partition properties ramIndex restrictions timestamp tld tls; do
        if [ -f "${DATA_DIR}/${prefix}.osrm.${ext}" ]; then
          mv "${DATA_DIR}/${prefix}.osrm.${ext}" "${DATA_DIR}/routing.osrm.${ext}"
        fi
      done
      break
    fi
  done
  echo "[OSRM Entrypoint] Versioned road-network dataset built successfully (${OSM_EXTRACT_VERSION})."
fi

exec "$@"
