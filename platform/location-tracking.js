/**
 * Commerce OS — Blinkit/Zomato-Grade Location & Tracking Engine V2
 * 
 * Provides:
 * 1. Road Snapping & Map Matching onto OSRM Waypoint Polylines
 * 2. 2-Phase Dynamic ETA Computation
 * 3. Dynamic Traversed vs. Remaining Route Slicing
 * 4. Stage Detection (LOOKING_FOR_RIDER -> HEADING_TO_STORE -> AT_STORE -> OUT_FOR_DELIVERY -> NEARBY -> AT_DOORSTEP -> DELIVERED)
 */

function haversineDistanceKm(lat1, lon1, lat2, lon2) {
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2);
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function projectPointToSegment(pLat, pLng, aLat, aLng, bLat, bLng) {
  const x = pLng, y = pLat;
  const x1 = aLng, y1 = aLat;
  const x2 = bLng, y2 = bLat;
  const dx = x2 - x1, dy = y2 - y1;
  const lenSq = dx * dx + dy * dy;
  if (lenSq === 0) {
    return { lat: aLat, lng: aLng, t: 0, distKm: haversineDistanceKm(pLat, pLng, aLat, aLng) };
  }
  const t = Math.max(0, Math.min(1, ((x - x1) * dx + (y - y1) * dy) / lenSq));
  const projLat = y1 + t * dy;
  const projLng = x1 + t * dx;
  const distKm = haversineDistanceKm(pLat, pLng, projLat, projLng);
  return { lat: projLat, lng: projLng, t, distKm };
}

function mapMatchRiderToRoute(riderLat, riderLng, waypoints) {
  if (!waypoints || waypoints.length < 2) {
    return {
      snappedLat: riderLat,
      snappedLng: riderLng,
      segmentIndex: 0,
      remainingDistanceKm: 0,
      routeProgressPct: 0,
      isSnapped: false
    };
  }

  let bestDist = Infinity;
  let bestProj = { lat: riderLat, lng: riderLng, t: 0 };
  let bestIndex = 0;

  for (let i = 0; i < waypoints.length - 1; i++) {
    const a = waypoints[i];
    const b = waypoints[i + 1];
    const proj = projectPointToSegment(riderLat, riderLng, a.lat, a.lng, b.lat, b.lng);
    if (proj.distKm < bestDist) {
      bestDist = proj.distKm;
      bestProj = proj;
      bestIndex = i;
    }
  }

  // Snap to road if within 120 meters of corridor
  const isSnapped = bestDist < 0.12;
  const snappedLat = isSnapped ? bestProj.lat : riderLat;
  const snappedLng = isSnapped ? bestProj.lng : riderLng;

  // Remaining distance along road from snapped point to destination
  let remKm = haversineDistanceKm(snappedLat, snappedLng, waypoints[bestIndex + 1].lat, waypoints[bestIndex + 1].lng);
  for (let j = bestIndex + 1; j < waypoints.length - 1; j++) {
    remKm += haversineDistanceKm(waypoints[j].lat, waypoints[j].lng, waypoints[j + 1].lat, waypoints[j + 1].lng);
  }

  // Total route distance
  let totalKm = 0;
  for (let k = 0; k < waypoints.length - 1; k++) {
    totalKm += haversineDistanceKm(waypoints[k].lat, waypoints[k].lng, waypoints[k + 1].lat, waypoints[k + 1].lng);
  }
  const progressPct = totalKm > 0 ? Math.max(0, Math.min(1, (totalKm - remKm) / totalKm)) : 0;

  return {
    snappedLat: Math.round(snappedLat * 1e6) / 1e6,
    snappedLng: Math.round(snappedLng * 1e6) / 1e6,
    segmentIndex: bestIndex,
    remainingDistanceKm: Math.round(remKm * 10) / 10,
    routeProgressPct: Math.round(progressPct * 100) / 100,
    isSnapped
  };
}

function resolveDeliveryStage(state, isAssigned, distToCustomerKm, distToStoreKm) {
  if (state === 'DELIVERED') return 'DELIVERED';
  if (state === 'CANCELLED' || state === 'FAILED') return 'CANCELLED';

  const isPostPickup = ['PICKED_UP', 'OUT_FOR_DELIVERY', 'EN_ROUTE_CUSTOMER', 'ARRIVED_CUSTOMER'].includes(state);

  if (isPostPickup) {
    if (state === 'ARRIVED_CUSTOMER' || (distToCustomerKm != null && distToCustomerKm < 0.06)) return 'AT_DOORSTEP';
    if (distToCustomerKm != null && distToCustomerKm < 0.30) return 'NEARBY';
    return 'OUT_FOR_DELIVERY';
  }

  // Pre-pickup states (LOOKING_FOR_RIDER, ASSIGNED, ACCEPTED, EN_ROUTE_STORE, ARRIVED_AT_STORE, PACKED, SELLER_ACCEPTED)
  if (state === 'ARRIVED_AT_STORE' || (distToStoreKm != null && distToStoreKm < 0.08)) return 'AT_STORE';
  if (isAssigned || ['ACCEPTED', 'EN_ROUTE_STORE'].includes(state)) return 'HEADING_TO_STORE';
  return 'ASSIGNING_PARTNER';
}

function getTrackingStatusText(stage, isAssigned, riderName) {
  switch (stage) {
    case 'DELIVERED':
      return 'Order Delivered';
    case 'AT_DOORSTEP':
      return 'Delivery partner at your doorstep';
    case 'NEARBY':
      return `${riderName ? riderName + ' is ' : 'Delivery partner is '}nearby (arriving in < 2 mins)`;
    case 'OUT_FOR_DELIVERY':
      return `${riderName ? riderName + ' is ' : ''}out for delivery`;
    case 'AT_STORE':
      return 'Partner arrived at store & picking up order';
    case 'HEADING_TO_STORE':
      return `${riderName ? riderName + ' is ' : ''}heading to store`;
    default:
      return 'Assigning delivery partner...';
  }
}

function detectRouteDeviation(riderLat, riderLng, waypoints, thresholdMeters = 75) {
  if (!waypoints || waypoints.length < 2) return { isOffRoute: false, deviationMeters: 0 };
  const match = mapMatchRiderToRoute(riderLat, riderLng, waypoints);
  const deviationMeters = Math.round(haversineDistanceKm(riderLat, riderLng, match.snappedLat, match.snappedLng) * 1000);
  return {
    isOffRoute: deviationMeters > thresholdMeters,
    deviationMeters,
    match
  };
}

function buildEnrichedTrackingDTO(session, rawTelemetry, fallbackPresence = null, waypoints = []) {
  if (!session) return null;

  const now = Date.now();
  const isStale = (now - (rawTelemetry?.serverTimestamp || rawTelemetry?.recordedAt || 0)) > 15000;
  const telemetrySource = rawTelemetry ? 'LIVE_TELEMETRY' : (fallbackPresence ? 'LAST_KNOWN_LOCATION' : 'NONE');
  
  let telemetry = rawTelemetry;
  if (!telemetry && fallbackPresence) {
    telemetry = {
      latitude: fallbackPresence.latitude,
      longitude: fallbackPresence.longitude,
      speedKmh: fallbackPresence.speedKmh || 20,
      heading: fallbackPresence.heading || 0,
      sequenceNumber: 1,
      serverTimestamp: fallbackPresence.lastSeenTimestamp || now,
      isStale: false
    };
  }

  const isAssigned = Boolean(
    session.state && 
    !['PENDING', 'CREATED', 'DISPATCHED', 'PLACED', 'LOOKING_FOR_RIDER'].includes(session.state) && 
    session.riderId && 
    session.riderId !== 'unassigned'
  );

  const mLat = (session.merchantLat != null || session.merchant_lat != null) ? Number(session.merchantLat || session.merchant_lat) : null;
  const mLng = (session.merchantLng != null || session.merchant_lng != null) ? Number(session.merchantLng || session.merchant_lng) : null;
  const cLat = (session.customerLat != null || session.customer_lat != null) ? Number(session.customerLat || session.customer_lat) : null;
  const cLng = (session.customerLng != null || session.customer_lng != null) ? Number(session.customerLng || session.customer_lng) : null;

  let mapMatched = {
    snappedLat: telemetry?.latitude || null,
    snappedLng: telemetry?.longitude || null,
    remainingDistanceKm: null,
    routeProgressPct: 0,
    isSnapped: false
  };

  if (telemetry && telemetry.latitude && telemetry.longitude) {
    mapMatched = mapMatchRiderToRoute(telemetry.latitude, telemetry.longitude, waypoints);
  }

  const riderCurrentLat = isAssigned ? mapMatched.snappedLat : null;
  const riderCurrentLng = isAssigned ? mapMatched.snappedLng : null;

  const distToCustomerKm = (riderCurrentLat != null && cLat != null) ? haversineDistanceKm(riderCurrentLat, riderCurrentLng, cLat, cLng) : null;
  const distToStoreKm = (riderCurrentLat != null && mLat != null) ? haversineDistanceKm(riderCurrentLat, riderCurrentLng, mLat, mLng) : null;

  const stage = resolveDeliveryStage(session.state, isAssigned, distToCustomerKm, distToStoreKm);
  const statusText = getTrackingStatusText(stage, isAssigned, session.riderName || session.rider_name);

  // Dynamic ETA Computation: Prefer real OSRM duration, fallback to urban flow model
  let etaMins = 8;
  if (session.state === 'DELIVERED') {
    etaMins = 0;
  } else if (stage === 'AT_DOORSTEP') {
    etaMins = 1;
  } else if (stage === 'NEARBY') {
    etaMins = 2;
  } else if (['OUT_FOR_DELIVERY', 'PICKED_UP', 'EN_ROUTE_CUSTOMER'].includes(session.state)) {
    if (session.remainingDurationMins != null && session.remainingDurationMins > 0) {
      etaMins = Math.max(1, Math.ceil(session.remainingDurationMins + 1));
    } else {
      const activeKm = mapMatched.remainingDistanceKm != null ? mapMatched.remainingDistanceKm : (distToCustomerKm || 1.5);
      etaMins = Math.max(1, Math.ceil(activeKm * 2.5 + 1));
    }
  } else {
    // Pre-pickup (Rider -> Store -> Customer)
    const riderToStoreKm = distToStoreKm || 0.8;
    const storeToCustKm = (mLat && mLng && cLat && cLng) ? haversineDistanceKm(mLat, mLng, cLat, cLng) : 2.0;
    const rToStoreMins = session.riderToStoreMins != null ? session.riderToStoreMins : Math.ceil(riderToStoreKm * 2.5);
    const storeToCustMins = session.storeToCustomerMins != null ? session.storeToCustomerMins : Math.ceil(storeToCustKm * 2.5);
    etaMins = Math.min(45, Math.max(3, rToStoreMins + 2 + storeToCustMins)); // +2 min store pickup
  }

  let traversedWaypoints = [];
  let remainingWaypoints = [];
  if (Array.isArray(waypoints) && waypoints.length >= 2) {
    const splitIdx = mapMatched.segmentIndex != null ? mapMatched.segmentIndex : 0;
    traversedWaypoints = waypoints.slice(0, splitIdx + 1);
    if (mapMatched.snappedLat && mapMatched.snappedLng) {
      traversedWaypoints.push({ lat: mapMatched.snappedLat, lng: mapMatched.snappedLng });
      remainingWaypoints.push({ lat: mapMatched.snappedLat, lng: mapMatched.snappedLng });
    }
    remainingWaypoints = remainingWaypoints.concat(waypoints.slice(splitIdx + 1));
  }

  return {
    orderId: session.orderId || session.order_id,
    deliveryId: session.deliveryId || session.delivery_id,
    state: session.state,
    stage,
    riderName: isAssigned ? (session.riderName || session.rider_name) : null,
    riderPhone: isAssigned ? (session.riderPhone || session.rider_phone) : null,
    riderVehicle: isAssigned ? (session.riderVehicle || session.rider_vehicle) : null,
    merchantLat: mLat,
    merchantLng: mLng,
    customerLat: cLat,
    customerLng: cLng,
    telemetrySource,
    liveRiderTelemetry: (isAssigned && telemetry) ? {
      latitude: mapMatched.snappedLat, // Snapped road latitude
      longitude: mapMatched.snappedLng, // Snapped road longitude
      rawLatitude: telemetry.latitude,
      rawLongitude: telemetry.longitude,
      speedKmh: telemetry.speedKmh || telemetry.speed || 0,
      heading: telemetry.heading || telemetry.bearing || 0,
      sequenceNumber: telemetry.sequenceNumber || telemetry.sequence_number || 0,
      serverTimestamp: telemetry.serverTimestamp || telemetry.recordedAt || now,
      routeProgressPct: mapMatched.routeProgressPct,
      remainingDistanceKm: mapMatched.remainingDistanceKm,
      isSnapped: mapMatched.isSnapped,
      isStale: telemetry.isStale || isStale,
      source: telemetrySource
    } : null,
    trackingStatusText: statusText,
    estimatedArrivalMins: etaMins,
    remainingDistanceKm: mapMatched.remainingDistanceKm,
    routeProgressPct: mapMatched.routeProgressPct,
    snappedSegmentIndex: mapMatched.segmentIndex,
    isStale: isStale,
    lastUpdatedTimestamp: telemetry?.serverTimestamp || telemetry?.recordedAt || now,
    waypoints: waypoints || [],
    traversedWaypoints,
    remainingWaypoints
  };
}

module.exports = {
  haversineDistanceKm,
  projectPointToSegment,
  mapMatchRiderToRoute,
  detectRouteDeviation,
  resolveDeliveryStage,
  getTrackingStatusText,
  buildEnrichedTrackingDTO
};
