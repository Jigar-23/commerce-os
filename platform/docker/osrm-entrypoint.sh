#!/bin/sh
set -e

DATA_DIR="/data"
OSRM_FILE="${DATA_DIR}/routing.osrm"

if [ ! -f "${OSRM_FILE}" ]; then
  echo "[OSRM Entrypoint] Initializing comprehensive operational road-network dataset..."
  mkdir -p "${DATA_DIR}"
  cat << 'EOF' > "${DATA_DIR}/network.osm"
<?xml version="1.0" encoding="UTF-8"?>
<osm version="0.6" generator="CommerceOS-Operational-Network-Generator">
  <!-- ======================================================== -->
  <!-- ZONE 1: REWARI SERVICE AREA & DARK STORE FULFILLMENT HUB -->
  <!-- ======================================================== -->
  <node id="101" lat="28.1900" lon="76.6050" version="1"/>
  <node id="102" lat="28.1950" lon="76.6100" version="1"/>
  <node id="103" lat="28.1985" lon="76.6120" version="1"/> <!-- Customer Delivery Point -->
  <node id="104" lat="28.2000" lon="76.6130" version="1"/> <!-- En-route Telemetry Point -->
  <node id="105" lat="28.2010" lon="76.6140" version="1"/> <!-- Store Entrance & Telemetry -->
  <node id="106" lat="28.2015" lon="76.6145" version="1"/> <!-- Rewari Central Dark Store Hub -->
  <node id="107" lat="28.2025" lon="76.6155" version="1"/>
  <node id="108" lat="28.2040" lon="76.6170" version="1"/>
  <node id="109" lat="28.2050" lon="76.6180" version="1"/>
  <node id="110" lat="28.2080" lon="76.6220" version="1"/>
  <node id="111" lat="28.2120" lon="76.6260" version="1"/>
  <node id="112" lat="28.2180" lon="76.6320" version="1"/>
  <node id="113" lat="28.2030" lon="76.6160" version="1"/> <!-- Model Town Junction -->
  <node id="114" lat="28.1980" lon="76.6180" version="1"/>
  <node id="115" lat="28.1940" lon="76.6200" version="1"/>
  <node id="116" lat="28.2070" lon="76.6140" version="1"/>
  <node id="117" lat="28.2110" lon="76.6120" version="1"/>

  <!-- ======================================================== -->
  <!-- ZONE 2: CONNECTING ARTERIAL ARTERY (NH-48 CORRIDOR)       -->
  <!-- ======================================================== -->
  <node id="201" lat="28.2250" lon="76.6500" version="1"/> <!-- Rewari Exit -->
  <node id="202" lat="28.2400" lon="76.7000" version="1"/>
  <node id="203" lat="28.2600" lon="76.7500" version="1"/>
  <node id="204" lat="28.2800" lon="76.8000" version="1"/> <!-- Dharuhera Junction -->
  <node id="205" lat="28.3100" lon="76.8500" version="1"/>
  <node id="206" lat="28.3400" lon="76.9000" version="1"/>
  <node id="207" lat="28.3700" lon="76.9500" version="1"/> <!-- Manesar Hub -->
  <node id="208" lat="28.4000" lon="76.9900" version="1"/>
  <node id="209" lat="28.4300" lon="77.0150" version="1"/> <!-- Gurugram South Entry -->

  <!-- ======================================================== -->
  <!-- ZONE 3: GURUGRAM / DELHI-NCR SERVICE AREA & CYBER HUB     -->
  <!-- ======================================================== -->
  <node id="301" lat="28.4350" lon="77.0200" version="1"/>
  <node id="302" lat="28.4450" lon="77.0220" version="1"/>
  <node id="303" lat="28.4550" lon="77.0250" version="1"/> <!-- Cyber City Hub -->
  <node id="304" lat="28.4595" lon="77.0266" version="1"/>
  <node id="305" lat="28.4680" lon="77.0350" version="1"/>
  <node id="306" lat="28.4750" lon="77.0420" version="1"/>
  <node id="307" lat="28.4820" lon="77.0500" version="1"/>
  <node id="308" lat="28.4900" lon="77.0580" version="1"/>
  <node id="309" lat="28.4600" lon="77.0400" version="1"/> <!-- Golf Course Road -->
  <node id="310" lat="28.4520" lon="77.0480" version="1"/>
  <node id="311" lat="28.4400" lon="77.0550" version="1"/>
  <node id="312" lat="28.4700" lon="77.0200" version="1"/> <!-- Udyog Vihar -->
  <node id="313" lat="28.4800" lon="77.0250" version="1"/>
  <node id="314" lat="28.4950" lon="77.0300" version="1"/>

  <!-- ======================================================== -->
  <!-- WAYS: INTERCONNECTED ROAD NETWORK                         -->
  <!-- ======================================================== -->
  <!-- Rewari Main Arterial (Railway Road to Hub) -->
  <way id="1001" version="1">
    <nd ref="101"/>
    <nd ref="102"/>
    <nd ref="103"/>
    <nd ref="104"/>
    <nd ref="105"/>
    <nd ref="106"/>
    <tag k="highway" v="primary"/>
    <tag k="oneway" v="no"/>
    <tag k="name" v="Rewari Railway Road"/>
  </way>

  <!-- Rewari Central Road (Hub to Delhi Road) -->
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
    <tag k="oneway" v="no"/>
    <tag k="name" v="Rewari Central Road"/>
  </way>

  <!-- Rewari Model Town Loop -->
  <way id="1003" version="1">
    <nd ref="113"/>
    <nd ref="114"/>
    <nd ref="115"/>
    <tag k="highway" v="residential"/>
    <tag k="oneway" v="no"/>
    <tag k="name" v="Model Town Main Road"/>
  </way>

  <!-- Rewari Circular Connector -->
  <way id="1004" version="1">
    <nd ref="107"/>
    <nd ref="116"/>
    <nd ref="117"/>
    <tag k="highway" v="secondary"/>
    <tag k="oneway" v="no"/>
    <tag k="name" v="Rewari Circular Road"/>
  </way>

  <!-- Rewari to Highway Connector -->
  <way id="1005" version="1">
    <nd ref="112"/>
    <nd ref="201"/>
    <tag k="highway" v="primary"/>
    <tag k="oneway" v="no"/>
    <tag k="name" v="Rewari Highway Link"/>
  </way>

  <!-- NH-48 Express Highway (Rewari to Gurugram) -->
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
    <tag k="oneway" v="no"/>
    <tag k="name" v="NH-48 Delhi-Jaipur Expressway"/>
  </way>

  <!-- Gurugram South Arterial -->
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
    <tag k="oneway" v="no"/>
    <tag k="name" v="Gurugram Cyber Road"/>
  </way>

  <!-- Gurugram Golf Course Extension -->
  <way id="3002" version="1">
    <nd ref="305"/>
    <nd ref="309"/>
    <nd ref="310"/>
    <nd ref="311"/>
    <tag k="highway" v="primary"/>
    <tag k="oneway" v="no"/>
    <tag k="name" v="Golf Course Road"/>
  </way>

  <!-- Gurugram Udyog Vihar Loop -->
  <way id="3003" version="1">
    <nd ref="304"/>
    <nd ref="312"/>
    <nd ref="313"/>
    <nd ref="314"/>
    <tag k="highway" v="secondary"/>
    <tag k="oneway" v="no"/>
    <tag k="name" v="Udyog Vihar Arterial"/>
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
  echo "[OSRM Entrypoint] Operational road-network dataset built successfully with full regional connectivity."
fi

exec "$@"
