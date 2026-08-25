package com.commerceos.android.ui.orders

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject

data class MapRoutePoint(val lat: Double, val lng: Double)

/**
 * Commerce OS — Blinkit/Zomato-Grade Live Tracking Map View V2
 * 
 * Features:
 * 1. 60fps Sub-Pixel Road-Following Motion with Bearing Smoothing
 * 2. Dual-Tone Dynamic Polyline (Traversed Dark Grey vs Active Glowing Emerald)
 * 3. Phase-Aware Smart Camera Choreography (Overview -> Route Focus -> Doorstep Zoom)
 * 4. Touch Gesture Decoupling & Floating Recenter Pill
 * 5. High-Contrast Dark Cartography with 3D Elevated Pin Hierarchy
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ZomatoDarkMapView(
    merchantLat: Double,
    merchantLng: Double,
    customerLat: Double,
    customerLng: Double,
    riderLat: Double? = null,
    riderLng: Double? = null,
    riderHeading: Float? = null,
    speedKmh: Float? = null,
    routeProgressPct: Float? = null,
    snappedSegmentIndex: Int? = null,
    waypoints: List<MapRoutePoint> = emptyList(),
    traversedWaypoints: List<MapRoutePoint> = emptyList(),
    remainingWaypoints: List<MapRoutePoint> = emptyList(),
    stage: String = "ASSIGNING_PARTNER",
    isStale: Boolean = false,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isMapLoaded by remember { mutableStateOf(false) }
    var isUserInteracting by remember { mutableStateOf(false) }

    val hasRiderLocation = riderLat != null && riderLng != null && riderLat != 0.0 && riderLng != 0.0

    val mapHtml = remember {
        generateBlinkitGradeDarkMapHtml(
            merchantLat = merchantLat,
            merchantLng = merchantLng,
            customerLat = customerLat,
            customerLng = customerLng
        )
    }

    LaunchedEffect(riderLat, riderLng, riderHeading, speedKmh, routeProgressPct, snappedSegmentIndex, isStale, waypoints, traversedWaypoints, remainingWaypoints, stage, isMapLoaded) {
        val webView = webViewRef ?: return@LaunchedEffect
        if (!isMapLoaded) return@LaunchedEffect

        val jsCall = buildSmartMapUpdateScript(
            merchantLat = merchantLat,
            merchantLng = merchantLng,
            customerLat = customerLat,
            customerLng = customerLng,
            riderLat = riderLat,
            riderLng = riderLng,
            riderHeading = riderHeading,
            speedKmh = speedKmh,
            routeProgressPct = routeProgressPct,
            snappedSegmentIndex = snappedSegmentIndex,
            isStale = isStale,
            waypoints = waypoints,
            traversedWaypoints = traversedWaypoints,
            remainingWaypoints = remainingWaypoints,
            stage = stage
        )
        webView.evaluateJavascript(jsCall, null)
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF080C16))) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    setBackgroundColor(0xFF080C16.toInt())

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isMapLoaded = true
                            val initialSync = buildSmartMapUpdateScript(
                                merchantLat = merchantLat,
                                merchantLng = merchantLng,
                                customerLat = customerLat,
                                customerLng = customerLng,
                                riderLat = riderLat,
                                riderLng = riderLng,
                                riderHeading = riderHeading,
                                speedKmh = speedKmh,
                                routeProgressPct = routeProgressPct,
                                snappedSegmentIndex = snappedSegmentIndex,
                                isStale = isStale,
                                waypoints = waypoints,
                                traversedWaypoints = traversedWaypoints,
                                remainingWaypoints = remainingWaypoints,
                                stage = stage
                            )
                            view?.evaluateJavascript(initialSync, null)
                        }
                    }

                    loadDataWithBaseURL("https://commerceos.local", mapHtml, "text/html", "UTF-8", null)
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Floating Stage Indicator Pill
        Surface(
            color = Color(0xFF0F172A).copy(alpha = 0.92f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            when {
                                isStale -> Color(0xFFF59E0B)
                                hasRiderLocation -> Color(0xFF10B981)
                                else -> Color(0xFF38BDF8)
                            },
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (stage) {
                        "AT_DOORSTEP" -> "Rider at your doorstep"
                        "NEARBY" -> "Rider is nearby (< 200m)"
                        "OUT_FOR_DELIVERY" -> "Rider on the way"
                        "AT_STORE" -> "Order picking up at store"
                        "HEADING_TO_STORE" -> "Rider heading to store"
                        else -> "Finding nearest partner..."
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Smart Recenter FAB (Re-engages camera choreography)
        FloatingActionButton(
            onClick = {
                webViewRef?.evaluateJavascript("smartRecenter();", null)
            },
            containerColor = Color(0xFF1E293B).copy(alpha = 0.95f),
            contentColor = Color(0xFF10B981),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(40.dp)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "Recenter Map", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
        }
    }
}

private fun buildSmartMapUpdateScript(
    merchantLat: Double,
    merchantLng: Double,
    customerLat: Double,
    customerLng: Double,
    riderLat: Double?,
    riderLng: Double?,
    riderHeading: Float?,
    speedKmh: Float?,
    routeProgressPct: Float?,
    snappedSegmentIndex: Int?,
    isStale: Boolean,
    waypoints: List<MapRoutePoint>,
    traversedWaypoints: List<MapRoutePoint>,
    remainingWaypoints: List<MapRoutePoint>,
    stage: String
): String {
    val wpArray = JSONArray()
    waypoints.forEach {
        val obj = JSONObject()
        obj.put("lat", it.lat)
        obj.put("lng", it.lng)
        wpArray.put(obj)
    }

    val traversedArray = JSONArray()
    traversedWaypoints.forEach {
        val obj = JSONObject()
        obj.put("lat", it.lat)
        obj.put("lng", it.lng)
        traversedArray.put(obj)
    }

    val remainingArray = JSONArray()
    remainingWaypoints.forEach {
        val obj = JSONObject()
        obj.put("lat", it.lat)
        obj.put("lng", it.lng)
        remainingArray.put(obj)
    }

    val riderObj = if (riderLat != null && riderLng != null && riderLat != 0.0 && riderLng != 0.0) {
        val ro = JSONObject()
        ro.put("lat", riderLat)
        ro.put("lng", riderLng)
        ro.put("heading", riderHeading ?: JSONObject.NULL)
        ro.put("speedKmh", speedKmh ?: 0f)
        ro.put("routeProgressPct", routeProgressPct ?: JSONObject.NULL)
        ro.put("snappedSegmentIndex", snappedSegmentIndex ?: JSONObject.NULL)
        ro.put("isStale", isStale)
        ro.put("timestamp", System.currentTimeMillis())
        ro
    } else {
        JSONObject.NULL
    }

    return "updateSmartMap($merchantLat, $merchantLng, $customerLat, $customerLng, $riderObj, $wpArray, '$stage', $traversedArray, $remainingArray);"
}

private fun generateBlinkitGradeDarkMapHtml(
    merchantLat: Double,
    merchantLng: Double,
    customerLat: Double,
    customerLng: Double
): String {
    return """
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
    <style>
        html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #080C16; font-family: -apple-system, Roboto, sans-serif; }
        .leaflet-container { background: #080C16; }
        
        /* 3D Elevated Pin Hierarchy */
        .pin-wrapper {
            display: flex;
            flex-direction: column;
            align-items: center;
            width: 48px;
            height: 56px;
        }
        .pin-head {
            border-radius: 50%;
            width: 32px;
            height: 32px;
            display: flex;
            align-items: center;
            justify-content: center;
            position: relative;
            z-index: 2;
            box-shadow: 0 6px 16px rgba(0,0,0,0.6);
        }
        .store-head {
            background: linear-gradient(135deg, #0284c7, #0369a1);
            border: 2.5px solid #38bdf8;
        }
        .store-head svg { width: 16px; height: 16px; fill: white; }
        .customer-head {
            background: linear-gradient(135deg, #059669, #047857);
            border: 2.5px solid #34d399;
        }
        .customer-head svg { width: 16px; height: 16px; fill: white; }
        .pin-needle {
            width: 0;
            height: 0;
            border-left: 6px solid transparent;
            border-right: 6px solid transparent;
            margin-top: -3px;
            position: relative;
            z-index: 1;
        }
        .store-needle { border-top: 10px solid #0284c7; }
        .customer-needle { border-top: 10px solid #059669; }
        .pin-shadow {
            width: 12px;
            height: 4px;
            background: rgba(0,0,0,0.5);
            border-radius: 50%;
            margin-top: -2px;
        }
        
        /* 60fps Animated Rider Vehicle */
        .biker-container {
            width: 40px;
            height: 40px;
            position: relative;
            will-change: transform;
        }
        .biker-pulse {
            position: absolute;
            width: 40px;
            height: 40px;
            border-radius: 50%;
            background: rgba(16, 185, 129, 0.25);
            animation: radarPulse 2s infinite ease-out;
        }
        .biker-core {
            position: absolute;
            top: 4px;
            left: 4px;
            width: 32px;
            height: 32px;
            background: #10B981;
            border: 2.5px solid #064E3B;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 4px 14px rgba(16, 185, 129, 0.6);
            transition: transform 0.2s ease;
        }
        .biker-core svg {
            width: 18px;
            height: 18px;
            fill: #FFFFFF;
        }
        
        @keyframes radarPulse {
            0% { transform: scale(0.8); opacity: 0.9; }
            70% { transform: scale(1.8); opacity: 0.1; }
            100% { transform: scale(1.8); opacity: 0; }
        }
        
        /* Blinkit-Grade Smooth Solid Neon Route */
        .leaflet-interactive.active-route-core {
            stroke-linecap: round;
            stroke-linejoin: round;
        }
        .leaflet-interactive.active-route-halo {
            stroke-linecap: round;
            stroke-linejoin: round;
            filter: drop-shadow(0 0 4px rgba(16, 185, 129, 0.45));
        }
    </style>
</head>
<body>
<div id="map"></div>
<script>
    var map = L.map('map', {
        zoomControl: false,
        attributionControl: false
    }).setView([$merchantLat, $merchantLng], 14);

    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
        maxZoom: 19,
        subdomains: 'abcd'
    }).addTo(map);

    var storeMarker = null;
    var customerMarker = null;
    var riderMarker = null;
    var traversedPolyline = null;
    var activePolylineGlow = null;
    var activePolyline = null;
    var allWaypoints = [];
    var currentStage = 'ASSIGNING_PARTNER';
    var autoCamera = true;
    var initialFramed = false;

    map.on('dragstart', function() { autoCamera = false; });
    map.on('zoomstart', function(e) { if (e && e.originalEvent) autoCamera = false; });

    var storeHtml = '<div class="pin-wrapper"><div class="pin-head store-head"><svg viewBox="0 0 24 24"><path d="M20 4H4v2h16V4zm1 10v-2l-1-5H4l-1 5v2h1v6h10v-6h4v6h2v-6h1zm-9 4H6v-4h6v4z"/></svg></div><div class="pin-needle store-needle"></div><div class="pin-shadow"></div></div>';
    var customerHtml = '<div class="pin-wrapper"><div class="pin-head customer-head"><svg viewBox="0 0 24 24"><path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"/></svg></div><div class="pin-needle customer-needle"></div><div class="pin-shadow"></div></div>';
    var bikeSvg = '<svg viewBox="0 0 24 24"><path d="M15.5 5.5c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zM5 12c-2.8 0-5 2.2-5 5s2.2 5 5 5 5-2.2 5-5-2.2-5-5-5zm0 8.5c-1.9 0-3.5-1.6-3.5-3.5s1.6-3.5 3.5-3.5 3.5 1.6 3.5 3.5-1.6 3.5-3.5 3.5zm14-8.5c-2.8 0-5 2.2-5 5s2.2 5 5 5 5-2.2 5-5-2.2-5-5-5zm0 8.5c-1.9 0-3.5-1.6-3.5-3.5s1.6-3.5 3.5-3.5 3.5 1.6 3.5 3.5-1.6 3.5-3.5 3.5zm-8.2-7.5l-2.4-4H5v2h2.2l1.6 2.7c-.8.8-1.3 1.8-1.5 3h2.1c.2-.7.6-1.3 1.1-1.8l1.7 2.1h3.7v-2h-2.5l-1.9-2.4.9-2.6 1.8 1.4v2.6h2v-3.7l-2.8-2.2c-.3-.2-.7-.3-1.1-.3-.4 0-.8.2-1.1.5l-1.6 2.4z"/></svg>';

    var effectiveMLat = $merchantLat || 28.202224;
    var effectiveMLng = $merchantLng || 76.615418;
    var effectiveCLat = $customerLat || 28.202224;
    var effectiveCLng = $customerLng || 76.615418;

    var sIcon = L.divIcon({ className: '', html: storeHtml, iconSize: [48, 56], iconAnchor: [24, 56] });
    storeMarker = L.marker([effectiveMLat, effectiveMLng], { icon: sIcon }).addTo(map);

    var cIcon = L.divIcon({ className: '', html: customerHtml, iconSize: [48, 56], iconAnchor: [24, 56] });
    customerMarker = L.marker([effectiveCLat, effectiveCLng], { icon: cIcon }).addTo(map);

    var initBounds = [[effectiveMLat, effectiveMLng], [effectiveCLat, effectiveCLng]];
    map.fitBounds(initBounds, { padding: [50, 50], maxZoom: 16 });

    var motionFrameId = null;
    var currentMarkerHeading = 0;

    function applyPredictiveVehicleMotion(marker, targetLat, targetLng, heading, speedKmh, isPredictive) {
        if (motionFrameId) {
            cancelAnimationFrame(motionFrameId);
            motionFrameId = null;
        }

        var startLatLng = marker.getLatLng();
        var startPos = [startLatLng.lat, startLatLng.lng];
        var endPos = [targetLat, targetLng];
        var startHeading = currentMarkerHeading;
        var endHeading = (heading != null) ? heading : currentMarkerHeading;
        var blendDuration = 400; // 400ms correction blend to authoritative coordinate
        var startTime = performance.now();

        // Speed in degrees per millisecond for dead reckoning
        var speedMs = (speedKmh || 0) / 3.6; // m/s
        var headingRad = (endHeading * Math.PI) / 180.0;
        var vLat = (speedMs * Math.cos(headingRad)) / 111320.0 / 1000.0; // deg/ms
        var vLng = (speedMs * Math.sin(headingRad)) / (111320.0 * Math.cos((targetLat * Math.PI) / 180.0)) / 1000.0; // deg/ms
        var maxDeadReckoningWindowMs = 2500;

        function renderFrame(now) {
            var elapsed = now - startTime;

            if (elapsed <= blendDuration) {
                // Phase 1: Smooth Cubic Ease-Out Correction to Authoritative Coordinate
                var t = elapsed / blendDuration;
                var ease = 1 - Math.pow(1 - t, 3);
                var curLat = startPos[0] + (endPos[0] - startPos[0]) * ease;
                var curLng = startPos[1] + (endPos[1] - startPos[1]) * ease;
                marker.setLatLng([curLat, curLng]);

                // Heading rotation
                var diff = endHeading - startHeading;
                if (diff > 180) diff -= 360;
                if (diff < -180) diff += 360;
                var curHeading = startHeading + diff * ease;
                currentMarkerHeading = curHeading;
                var el = marker.getElement();
                if (el) {
                    var core = el.querySelector('.biker-core');
                    if (core) core.style.transform = 'rotate(' + curHeading + 'deg)';
                }
                motionFrameId = requestAnimationFrame(renderFrame);
            } else if (isPredictive && speedKmh > 3 && (elapsed - blendDuration) <= maxDeadReckoningWindowMs) {
                // Phase 2: Dead Reckoning Extrapolation along Heading Vector
                var extraMs = elapsed - blendDuration;
                var predLat = endPos[0] + vLat * extraMs;
                var predLng = endPos[1] + vLng * extraMs;
                marker.setLatLng([predLat, predLng]);
                motionFrameId = requestAnimationFrame(renderFrame);
            } else {
                marker.setLatLng(endPos);
                motionFrameId = null;
            }
        }

        motionFrameId = requestAnimationFrame(renderFrame);
    }

    function updateSmartMap(mLat, mLng, cLat, cLng, rider, waypoints, stage, serverTraversedPts, serverRemainingPts) {
        currentStage = stage || 'ASSIGNING_PARTNER';
        allWaypoints = waypoints || [];

        // 1. Store Marker
        if (mLat && mLng) {
            if (!storeMarker) {
                var icon = L.divIcon({ className: '', html: storeHtml, iconSize: [48, 56], iconAnchor: [24, 56] });
                storeMarker = L.marker([mLat, mLng], { icon: icon }).addTo(map);
            } else {
                storeMarker.setLatLng([mLat, mLng]);
            }
        }

        // 2. Customer Marker
        if (cLat && cLng) {
            if (!customerMarker) {
                var icon = L.divIcon({ className: '', html: customerHtml, iconSize: [48, 56], iconAnchor: [24, 56] });
                customerMarker = L.marker([cLat, cLng], { icon: icon }).addTo(map);
            } else {
                customerMarker.setLatLng([cLat, cLng]);
            }
        }

        // 3. Persistent Rider Marker with Predictive Road Snapping, Dead Reckoning & Heading Rotation
        if (rider && rider.lat && rider.lng) {
            var rot = (rider.heading != null) ? rider.heading : currentMarkerHeading;
            var isPredictive = Boolean(rider.isPredictiveMotionEnabled && !rider.isStale);
            if (!riderMarker) {
                var markerHtml = '<div class="biker-container">' +
                                 '<div class="biker-pulse"></div>' +
                                 '<div class="biker-core" style="transform: rotate(' + rot + 'deg);">' + bikeSvg + '</div>' +
                                 '</div>';
                var bikerIcon = L.divIcon({ className: '', html: markerHtml, iconSize: [40, 40], iconAnchor: [20, 20] });
                riderMarker = L.marker([rider.lat, rider.lng], { icon: bikerIcon }).addTo(map);
                currentMarkerHeading = rot;
            } else {
                var el = riderMarker.getElement();
                if (el) {
                    var core = el.querySelector('.biker-core');
                    if (core) core.style.transform = 'rotate(' + rot + 'deg)';
                    var pulse = el.querySelector('.biker-pulse');
                    if (pulse) pulse.style.display = rider.isStale ? 'none' : 'block';
                }
                if (rider.isStale) {
                    if (motionFrameId) cancelAnimationFrame(motionFrameId);
                    riderMarker.setLatLng([rider.lat, rider.lng]);
                } else {
                    applyPredictiveVehicleMotion(riderMarker, rider.lat, rider.lng, rot, rider.speedKmh || 0, isPredictive);
                }
            }
        } else {
            if (riderMarker) {
                if (motionFrameId) cancelAnimationFrame(motionFrameId);
                map.removeLayer(riderMarker);
                riderMarker = null;
            }
        }

        // 4. Exact Authoritative Dual-Tone Polylines (Server Sliced vs Dynamic Segments)
        var latLngs = (allWaypoints && allWaypoints.length >= 2) ? allWaypoints.map(function(pt) { return [pt.lat, pt.lng]; }) : [];
        if (latLngs.length < 2 && mLat && mLng && cLat && cLng) {
            latLngs = [[mLat, mLng], [cLat, cLng]];
        }

        if (latLngs.length >= 2) {
            var traversedPts = [];
            var remainingPts = [];

            if (serverTraversedPts && serverTraversedPts.length >= 2 && serverRemainingPts && serverRemainingPts.length >= 2) {
                traversedPts = serverTraversedPts.map(function(pt) { return [pt.lat, pt.lng]; });
                remainingPts = serverRemainingPts.map(function(pt) { return [pt.lat, pt.lng]; });
            } else if (rider && rider.snappedSegmentIndex != null && rider.snappedSegmentIndex >= 0) {
                var sIdx = Math.min(rider.snappedSegmentIndex, latLngs.length - 1);
                traversedPts = latLngs.slice(0, sIdx + 1);
                if (rider.lat && rider.lng) traversedPts.push([rider.lat, rider.lng]);
                if (rider.lat && rider.lng) remainingPts.push([rider.lat, rider.lng]);
                remainingPts = remainingPts.concat(latLngs.slice(sIdx + 1));
            } else if (rider && rider.lat && rider.lng) {
                var minD = Infinity;
                var splitIdx = 0;
                for (var i = 0; i < latLngs.length; i++) {
                    var d = Math.hypot(latLngs[i][0] - rider.lat, latLngs[i][1] - rider.lng);
                    if (d < minD) {
                        minD = d;
                        splitIdx = i;
                    }
                }
                traversedPts = latLngs.slice(0, splitIdx + 1);
                traversedPts.push([rider.lat, rider.lng]);
                remainingPts.push([rider.lat, rider.lng]);
                remainingPts = remainingPts.concat(latLngs.slice(splitIdx));
            } else {
                remainingPts = latLngs;
            }

            // Traversed Faded Route (Smooth solid dark slate)
            if (traversedPts.length >= 2) {
                if (!traversedPolyline) {
                    traversedPolyline = L.polyline(traversedPts, {
                        color: '#334155',
                        weight: 4.5,
                        opacity: 0.55,
                        lineCap: 'round',
                        lineJoin: 'round'
                    }).addTo(map);
                } else {
                    traversedPolyline.setLatLngs(traversedPts);
                }
            } else if (traversedPolyline) {
                map.removeLayer(traversedPolyline);
                traversedPolyline = null;
            }

            // Active Remaining Route (Blinkit-Grade Dual-Layer: Soft Ambient Glow + Solid Neon Emerald Core)
            var activePts = remainingPts.length >= 2 ? remainingPts : latLngs;
            
            // Outer Halo Layer
            if (!activePolylineGlow) {
                activePolylineGlow = L.polyline(activePts, {
                    color: '#10B981',
                    weight: 10,
                    opacity: 0.25,
                    className: 'active-route-halo',
                    lineCap: 'round',
                    lineJoin: 'round'
                }).addTo(map);
            } else {
                activePolylineGlow.setLatLngs(activePts);
            }

            // Inner Solid Core Layer
            if (!activePolyline) {
                activePolyline = L.polyline(activePts, {
                    color: '#10B981',
                    weight: 5.5,
                    opacity: 1.0,
                    className: 'active-route-core',
                    lineCap: 'round',
                    lineJoin: 'round'
                }).addTo(map);
            } else {
                activePolyline.setLatLngs(activePts);
            }
        } else {
            if (activePolylineGlow) {
                map.removeLayer(activePolylineGlow);
                activePolylineGlow = null;
            }
            if (activePolyline) {
                map.removeLayer(activePolyline);
                activePolyline = null;
            }
            if (traversedPolyline) {
                map.removeLayer(traversedPolyline);
                traversedPolyline = null;
            }
        }

        // 5. Forward-Corridor Aware Camera Choreography
        if (autoCamera) {
            smartChoreographCamera(mLat, mLng, cLat, cLng, rider, stage, allWaypoints);
        }
    }

    var lastCameraLat = null;
    var lastCameraLng = null;
    var lastCameraStage = null;

    function smartChoreographCamera(mLat, mLng, cLat, cLng, rider, stage, waypoints) {
        if (!initialFramed) {
            initialFramed = true;
            var initialBounds = [];
            if (mLat && mLng) initialBounds.push([mLat, mLng]);
            if (cLat && cLng) initialBounds.push([cLat, cLng]);
            if (initialBounds.length >= 2) {
                map.fitBounds(L.latLngBounds(initialBounds), { padding: [60, 60], maxZoom: 16 });
                return;
            }
        }

        // Camera Hysteresis: prevent jitter if rider moved < 35 meters within same stage
        if (rider && rider.lat && rider.lng && lastCameraLat != null && lastCameraStage === stage) {
            var movedKm = Math.hypot(rider.lat - lastCameraLat, rider.lng - lastCameraLng) * 111.0;
            if (movedKm < 0.035) return;
        }

        if (rider && rider.lat) {
            lastCameraLat = rider.lat;
            lastCameraLng = rider.lng;
        }
        lastCameraStage = stage;

        var isDoorstep = (stage === 'AT_DOORSTEP' || stage === 'NEARBY' || stage === 'ARRIVED_CUSTOMER' || stage === 'HANDOFF_STARTED');
        var isOutForDelivery = (stage === 'OUT_FOR_DELIVERY' || stage === 'EN_ROUTE_CUSTOMER' || stage === 'PICKED_UP');
        var isPrePickup = (stage === 'HEADING_TO_STORE' || stage === 'AT_STORE' || stage === 'ASSIGNED' || stage === 'ACCEPTED' || stage === 'EN_ROUTE_PICKUP' || stage === 'OUT_FOR_PICKUP' || stage === 'EN_ROUTE_STORE' || stage === 'ARRIVED_PICKUP' || stage === 'ARRIVED_STORE' || stage === 'ARRIVED_AT_STORE');

        if (isDoorstep) {
            // High-resolution doorstep zoom
            if (cLat && cLng) {
                map.setView([cLat, cLng], 17, { animate: true, duration: 1.0 });
            }
        } else if (isOutForDelivery && rider && rider.lat && rider.lng && cLat && cLng) {
            // Forward route corridor anticipation bounds (rider + next 3 waypoints + destination)
            var corridorPts = [[rider.lat, rider.lng], [cLat, cLng]];
            if (waypoints && waypoints.length > 0 && rider.snappedSegmentIndex != null) {
                var sIdx = rider.snappedSegmentIndex;
                for (var k = sIdx + 1; k < Math.min(sIdx + 4, waypoints.length); k++) {
                    corridorPts.push([waypoints[k].lat, waypoints[k].lng]);
                }
            }
            var bounds = L.latLngBounds(corridorPts);
            map.fitBounds(bounds, { padding: [70, 70], maxZoom: 16, animate: true, duration: 0.8 });
        } else if (isPrePickup && rider && rider.lat && rider.lng && mLat && mLng) {
            // Dynamic bounds showing Rider + Store
            var bounds = L.latLngBounds([[rider.lat, rider.lng], [mLat, mLng]]);
            map.fitBounds(bounds, { padding: [70, 70], maxZoom: 16, animate: true, duration: 0.8 });
        }
    }

    function smartRecenter() {
        autoCamera = true;
        if (storeMarker && customerMarker) {
            var b = [];
            if (riderMarker) b.push(riderMarker.getLatLng());
            b.push(customerMarker.getLatLng());
            if (b.length >= 2) {
                map.fitBounds(L.latLngBounds(b), { padding: [60, 60], maxZoom: 16, animate: true });
            } else {
                map.setView(customerMarker.getLatLng(), 16, { animate: true });
            }
        }
    }
</script>
</body>
</html>
    """.trimIndent()
}
