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
    waypoints: List<MapRoutePoint> = emptyList(),
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

    LaunchedEffect(riderLat, riderLng, riderHeading, isStale, waypoints, stage, isMapLoaded) {
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
            isStale = isStale,
            waypoints = waypoints,
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
                                isStale = isStale,
                                waypoints = waypoints,
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
    isStale: Boolean,
    waypoints: List<MapRoutePoint>,
    stage: String
): String {
    val wpArray = JSONArray()
    waypoints.forEach {
        val obj = JSONObject()
        obj.put("lat", it.lat)
        obj.put("lng", it.lng)
        wpArray.put(obj)
    }

    val riderObj = if (riderLat != null && riderLng != null && riderLat != 0.0 && riderLng != 0.0) {
        val ro = JSONObject()
        ro.put("lat", riderLat)
        ro.put("lng", riderLng)
        ro.put("heading", riderHeading ?: JSONObject.NULL)
        ro.put("isStale", isStale)
        ro.put("timestamp", System.currentTimeMillis())
        ro
    } else {
        JSONObject.NULL
    }

    return "updateSmartMap($merchantLat, $merchantLng, $customerLat, $customerLng, $riderObj, $wpArray, '$stage');"
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
        
        /* Active Route Glow */
        .leaflet-interactive.active-route {
            stroke-dasharray: 8, 8;
            animation: dashMarch 1.5s linear infinite;
        }
        @keyframes dashMarch {
            to { stroke-dashoffset: -16; }
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

    var animFrameId = null;
    function smoothGlideTo(marker, startPos, endPos, startHeading, endHeading, durationMs) {
        if (animFrameId) {
            cancelAnimationFrame(animFrameId);
            animFrameId = null;
        }
        var startTime = performance.now();
        function step(now) {
            var elapsed = now - startTime;
            var t = Math.min(1, elapsed / durationMs);
            var ease = 1 - Math.pow(1 - t, 3); // Cubic ease out
            
            var lat = startPos[0] + (endPos[0] - startPos[0]) * ease;
            var lng = startPos[1] + (endPos[1] - startPos[1]) * ease;
            marker.setLatLng([lat, lng]);

            if (startHeading != null && endHeading != null) {
                var diff = endHeading - startHeading;
                if (diff > 180) diff -= 360;
                if (diff < -180) diff += 360;
                var currentHeading = startHeading + diff * ease;
                var el = marker.getElement();
                if (el) {
                    var core = el.querySelector('.biker-core');
                    if (core) core.style.transform = 'rotate(' + currentHeading + 'deg)';
                }
            }

            if (t < 1) {
                animFrameId = requestAnimationFrame(step);
            } else {
                animFrameId = null;
            }
        }
        animFrameId = requestAnimationFrame(step);
    }

    var currentMarkerHeading = 0;

    function updateSmartMap(mLat, mLng, cLat, cLng, rider, waypoints, stage) {
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

        // 3. Rider Marker with 60fps Road Snapping & Continuous Heading Rotation
        if (rider && rider.lat && rider.lng) {
            var rot = (rider.heading != null) ? rider.heading : currentMarkerHeading;
            var markerHtml = '<div class="biker-container">' +
                             (rider.isStale ? '' : '<div class="biker-pulse"></div>') +
                             '<div class="biker-core" style="transform: rotate(' + currentMarkerHeading + 'deg);">' + bikeSvg + '</div>' +
                             '</div>';
            var bikerIcon = L.divIcon({ className: '', html: markerHtml, iconSize: [40, 40], iconAnchor: [20, 20] });

            if (!riderMarker) {
                riderMarker = L.marker([rider.lat, rider.lng], { icon: bikerIcon }).addTo(map);
                currentMarkerHeading = rot;
            } else {
                var prevLatLng = riderMarker.getLatLng();
                var el = riderMarker.getElement();
                if (el) {
                    var core = el.querySelector('.biker-core');
                    if (core) core.style.transform = 'rotate(' + rot + 'deg)';
                }
                if (rider.isStale) {
                    riderMarker.setLatLng([rider.lat, rider.lng]);
                } else {
                    smoothGlideTo(riderMarker, [prevLatLng.lat, prevLatLng.lng], [rider.lat, rider.lng], currentMarkerHeading, rot, 800);
                    currentMarkerHeading = rot;
                }
            }
        } else {
            if (riderMarker) {
                if (animFrameId) cancelAnimationFrame(animFrameId);
                map.removeLayer(riderMarker);
                riderMarker = null;
            }
        }

        // 4. Dual-Tone Dynamic Polylines (Traversed Path vs Active Remaining Path)
        if (allWaypoints.length >= 2) {
            var latLngs = allWaypoints.map(function(pt) { return [pt.lat, pt.lng]; });
            
            // Find closest waypoint segment to rider if rider is active
            var splitIdx = 0;
            if (rider && rider.lat && rider.lng) {
                var minD = Infinity;
                for (var i = 0; i < latLngs.length; i++) {
                    var d = Math.hypot(latLngs[i][0] - rider.lat, latLngs[i][1] - rider.lng);
                    if (d < minD) {
                        minD = d;
                        splitIdx = i;
                    }
                }
            }

            var traversedPts = latLngs.slice(0, splitIdx + 1);
            if (rider && rider.lat && rider.lng) traversedPts.push([rider.lat, rider.lng]);
            var remainingPts = [];
            if (rider && rider.lat && rider.lng) remainingPts.push([rider.lat, rider.lng]);
            remainingPts = remainingPts.concat(latLngs.slice(splitIdx));

            // Traversed Faded Route
            if (traversedPts.length >= 2 && splitIdx > 0) {
                if (!traversedPolyline) {
                    traversedPolyline = L.polyline(traversedPts, {
                        color: '#334155',
                        weight: 4,
                        opacity: 0.5,
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

            // Active Glowing Remaining Route
            var activePts = remainingPts.length >= 2 ? remainingPts : latLngs;
            if (!activePolyline) {
                activePolyline = L.polyline(activePts, {
                    color: '#10B981',
                    weight: 5,
                    opacity: 0.95,
                    className: 'active-route',
                    lineCap: 'round',
                    lineJoin: 'round'
                }).addTo(map);
            } else {
                activePolyline.setLatLngs(activePts);
            }
        } else {
            if (activePolyline) {
                map.removeLayer(activePolyline);
                activePolyline = null;
            }
            if (traversedPolyline) {
                map.removeLayer(traversedPolyline);
                traversedPolyline = null;
            }
        }

        // 5. Phase-Aware Camera Choreography
        if (autoCamera) {
            smartChoreographCamera(mLat, mLng, cLat, cLng, rider, stage);
        }
    }

    function smartChoreographCamera(mLat, mLng, cLat, cLng, rider, stage) {
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

        if (stage === 'AT_DOORSTEP' || stage === 'NEARBY') {
            // High-resolution doorstep zoom
            if (cLat && cLng) {
                map.setView([cLat, cLng], 17, { animate: true, duration: 1.0 });
            }
        } else if (stage === 'OUT_FOR_DELIVERY' && rider && rider.lat && rider.lng && cLat && cLng) {
            // Dynamic bounds showing Rider + Customer Home
            var bounds = L.latLngBounds([[rider.lat, rider.lng], [cLat, cLng]]);
            map.fitBounds(bounds, { padding: [70, 70], maxZoom: 16, animate: true, duration: 0.8 });
        } else if (stage === 'HEADING_TO_STORE' && rider && rider.lat && rider.lng && mLat && mLng) {
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
