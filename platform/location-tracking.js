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
  const rawTs = rawTelemetry ? (rawTelemetry.serverTimestamp || rawTelemetry.recordedAt || (rawTelemetry.recorded_at ? new Date(rawTelemetry.recorded_at).getTime() : 0)) : 0;
  const isStale = rawTelemetry ? ((now - rawTs) > 15000) : true;
  const telemetrySource = rawTelemetry ? 'LIVE_TELEMETRY' : (fallbackPresence ? 'LAST_KNOWN_LOCATION' : 'NONE');
  
  let telemetry = rawTelemetry;
  if (!telemetry && fallbackPresence) {
    const presenceTs = fallbackPresence.lastSeenTimestamp || fallbackPresence.recordedAt || (fallbackPresence.updated_at ? new Date(fallbackPresence.updated_at).getTime() : (now - 30000));
    telemetry = {
      latitude: Number(fallbackPresence.latitude || fallbackPresence.lastKnownLat || fallbackPresence.last_known_lat || 0),
      longitude: Number(fallbackPresence.longitude || fallbackPresence.lastKnownLng || fallbackPresence.last_known_lng || 0),
      speedKmh: Number(fallbackPresence.speedKmh || fallbackPresence.speed || 0),
      heading: Number(fallbackPresence.heading || 0),
      sequenceNumber: Number(fallbackPresence.sequenceNumber || fallbackPresence.sequence_number || 1),
      serverTimestamp: presenceTs,
      recordedAt: presenceTs,
      isStale: true,
      source: 'LAST_KNOWN_LOCATION'
    };
  } else if (telemetry) {
    telemetry = {
      ...telemetry,
      latitude: Number(telemetry.latitude || telemetry.lat || 0),
      longitude: Number(telemetry.longitude || telemetry.lng || 0),
      speedKmh: Number(telemetry.speedKmh || telemetry.speed || 0),
      heading: Number(telemetry.heading || telemetry.bearing || 0),
      sequenceNumber: Number(telemetry.sequenceNumber || telemetry.sequence_number || telemetry.seq || 1),
      serverTimestamp: rawTs || now,
      recordedAt: rawTs || now,
      isStale: isStale,
      source: 'LIVE_TELEMETRY'
    };
  }

  const riderId = session.riderId || session.rider_id;
  const isAssigned = Boolean(
    session.state && 
    !['PENDING', 'CREATED', 'DISPATCHED', 'PLACED', 'LOOKING_FOR_RIDER'].includes(session.state) && 
    riderId && 
    riderId !== 'unassigned'
  );

  const mLat = (session.merchantLat != null || session.merchant_lat != null) ? Number(session.merchantLat || session.merchant_lat) : null;
  const mLng = (session.merchantLng != null || session.merchant_lng != null) ? Number(session.merchantLng || session.merchant_lng) : null;
  const cLat = (session.customerLat != null || session.customer_lat != null) ? Number(session.customerLat || session.customer_lat) : null;
  const cLng = (session.customerLng != null || session.customer_lng != null) ? Number(session.customerLng || session.customer_lng) : null;

  let activeWaypoints = Array.isArray(waypoints) && waypoints.length >= 2 ? waypoints : [];

  let mapMatched = {
    snappedLat: telemetry?.latitude || null,
    snappedLng: telemetry?.longitude || null,
    remainingDistanceKm: null,
    routeProgressPct: 0,
    isSnapped: false
  };

  if (telemetry && telemetry.latitude && telemetry.longitude) {
    mapMatched = mapMatchRiderToRoute(telemetry.latitude, telemetry.longitude, activeWaypoints);
  }

  const riderCurrentLat = isAssigned ? mapMatched.snappedLat : null;
  const riderCurrentLng = isAssigned ? mapMatched.snappedLng : null;

  const distToCustomerKm = (riderCurrentLat != null && cLat != null) ? haversineDistanceKm(riderCurrentLat, riderCurrentLng, cLat, cLng) : null;
  const distToStoreKm = (riderCurrentLat != null && mLat != null) ? haversineDistanceKm(riderCurrentLat, riderCurrentLng, mLat, mLng) : null;

  const stage = resolveDeliveryStage(session.state, isAssigned, distToCustomerKm, distToStoreKm);
  const statusText = getTrackingStatusText(stage, isAssigned, session.riderName || session.rider_name);

  // Dynamic ETA Computation: Authoritative duration from route / live telemetry, no synthetic fake distances
  let etaMins = null;
  let etaMode = 'UNAVAILABLE';

  if (session.state === 'DELIVERED') {
    etaMins = 0;
    etaMode = 'DELIVERED';
  } else if (stage === 'AT_DOORSTEP') {
    etaMins = 1;
    etaMode = 'AUTHORITATIVE_STAGE';
  } else if (stage === 'NEARBY') {
    etaMins = 2;
    etaMode = 'AUTHORITATIVE_STAGE';
  } else if (['OUT_FOR_DELIVERY', 'PICKED_UP', 'EN_ROUTE_CUSTOMER'].includes(session.state)) {
    if (session.remainingDurationMins != null && session.remainingDurationMins > 0) {
      etaMins = Math.max(1, Math.ceil(Number(session.remainingDurationMins)));
      etaMode = 'AUTHORITATIVE_ROUTE';
    } else if (session.remaining_duration_mins != null && session.remaining_duration_mins > 0) {
      etaMins = Math.max(1, Math.ceil(Number(session.remaining_duration_mins)));
      etaMode = 'AUTHORITATIVE_ROUTE';
    } else if (mapMatched.remainingDistanceKm != null && mapMatched.remainingDistanceKm > 0) {
      etaMins = Math.max(1, Math.ceil(mapMatched.remainingDistanceKm * 2.2 + 1));
      etaMode = 'DEGRADED_DISTANCE';
    } else if (distToCustomerKm != null && distToCustomerKm > 0) {
      etaMins = Math.max(1, Math.ceil(distToCustomerKm * 2.2 + 1));
      etaMode = 'DEGRADED_DISTANCE';
    } else {
      etaMins = null;
      etaMode = 'UNAVAILABLE';
    }
  } else {
    // Pre-pickup (Rider -> Store -> Customer)
    const rToStoreMins = session.riderToStoreMins != null ? Number(session.riderToStoreMins) : (session.rider_to_store_mins != null ? Number(session.rider_to_store_mins) : (distToStoreKm != null ? Math.ceil(distToStoreKm * 2.5) : null));
    const storeToCustMins = session.storeToCustomerMins != null ? Number(session.storeToCustomerMins) : (session.store_to_customer_mins != null ? Number(session.store_to_customer_mins) : ((mLat && mLng && cLat && cLng) ? Math.ceil(haversineDistanceKm(mLat, mLng, cLat, cLng) * 2.5) : null));
    if (storeToCustMins != null) {
      const riderLeg = rToStoreMins != null ? rToStoreMins : 3;
      etaMins = Math.min(45, Math.max(3, riderLeg + 2 + storeToCustMins)); // +2 min store pickup
      etaMode = session.storeToCustomerMins ? 'AUTHORITATIVE_ROUTE' : 'DEGRADED_DISTANCE';
    } else {
      etaMins = null;
      etaMode = 'UNAVAILABLE';
    }
  }

  let traversedWaypoints = [];
  let remainingWaypoints = [];
  if (Array.isArray(activeWaypoints) && activeWaypoints.length >= 2) {
    const splitIdx = mapMatched.segmentIndex != null ? mapMatched.segmentIndex : 0;
    traversedWaypoints = activeWaypoints.slice(0, splitIdx + 1);
    if (mapMatched.snappedLat && mapMatched.snappedLng) {
      traversedWaypoints.push({ lat: mapMatched.snappedLat, lng: mapMatched.snappedLng });
      remainingWaypoints.push({ lat: mapMatched.snappedLat, lng: mapMatched.snappedLng });
    }
    remainingWaypoints = remainingWaypoints.concat(activeWaypoints.slice(splitIdx + 1));
  }

  const isPredictive = Boolean(isAssigned && telemetry && telemetrySource === 'LIVE_GPS' && !isStale && !telemetry.isStale);
  const staleDurationSec = Math.max(0, Math.floor((now - (telemetry?.serverTimestamp || telemetry?.recordedAt || now)) / 1000));

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
    riderLat: riderCurrentLat,
    riderLng: riderCurrentLng,
    riderHeading: telemetry ? Number(telemetry.heading || telemetry.bearing || 0) : 0,
    riderBearing: telemetry ? Number(telemetry.heading || telemetry.bearing || 0) : 0,
    speedKmh: telemetry ? Number(telemetry.speedKmh || telemetry.speed || 0) : 0,
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
      staleDurationSeconds: staleDurationSec,
      source: telemetrySource,
      isPredictiveMotionEnabled: isPredictive,
      predictionWindowMs: isPredictive ? 2500 : 0,
      deadReckoningAllowed: isPredictive
    } : null,
    trackingStatusText: statusText,
    estimatedArrivalMins: etaMins,
    etaMode,
    remainingDistanceKm: mapMatched.remainingDistanceKm,
    routeProgressPct: mapMatched.routeProgressPct,
    snappedSegmentIndex: mapMatched.segmentIndex,
    isStale: isStale,
    staleDurationSeconds: staleDurationSec,
    lastUpdatedTimestamp: telemetry?.serverTimestamp || telemetry?.recordedAt || now,
    waypoints: activeWaypoints || [],
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
