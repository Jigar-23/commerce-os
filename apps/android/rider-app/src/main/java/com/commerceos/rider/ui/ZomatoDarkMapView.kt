package com.commerceos.rider.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.commerceos.rider.model.RoutePoint
import org.json.JSONArray
import org.json.JSONObject

/**
 * Hardened Dark Cartography Map Engine with Monotonic GPS Smoothing,
 * Real Geographic Road Geometry, and Zero Artificial Coordinates.
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
    waypoints: List<RoutePoint> = emptyList(),
    isRouteLoading: Boolean = false,
    routeUnavailable: Boolean = false,
    isStale: Boolean = false,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isMapLoaded by remember { mutableStateOf(false) }

    val hasRiderLocation = riderLat != null && riderLng != null && riderLat != 0.0 && riderLng != 0.0

    val mapHtml = remember {
        generateHardenedDarkMapHtml(
            merchantLat = merchantLat,
            merchantLng = merchantLng,
            customerLat = customerLat,
            customerLng = customerLng
        )
    }

    LaunchedEffect(riderLat, riderLng, riderHeading, speedKmh, routeProgressPct, snappedSegmentIndex, isStale, waypoints, isMapLoaded) {
        val webView = webViewRef ?: return@LaunchedEffect
        if (!isMapLoaded) return@LaunchedEffect

        val jsCall = buildUpdateScript(
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
            waypoints = waypoints
        )
        webView.evaluateJavascript(jsCall, null)
    }

    // Lifecycle cleanup
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0D0F14))) {
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
                    setBackgroundColor(0xFF0D0F14.toInt())

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            return true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isMapLoaded = true
                            val initialSync = buildUpdateScript(
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
                                waypoints = waypoints
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

        // Route Status Overlay
        if (routeUnavailable) {
            Surface(
                color = Color(0xFF7F1D1D).copy(alpha = 0.9f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Route temporarily unavailable", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        } else if (isRouteLoading) {
            Surface(
                color = Color(0xFF16181F).copy(alpha = 0.9f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262933)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color(0xFF10B981), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Updating route…", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Rider GPS Status Pill
        if (!hasRiderLocation) {
            Surface(
                color = Color(0xFF16181F).copy(alpha = 0.9f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262933)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color(0xFF10B981), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Waiting for live GPS fix…", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF94A3B8))
                }
            }
        } else if (isStale) {
            Surface(
                color = Color(0xFF78350F).copy(alpha = 0.9f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = "Updating rider location…",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFBBF24),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        // Recenter Action FAB
        FloatingActionButton(
            onClick = {
                webViewRef?.evaluateJavascript("recenterMap();", null)
            },
            containerColor = Color(0xFF16181F),
            contentColor = Color(0xFF10B981),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(40.dp)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "Recenter", modifier = Modifier.size(20.dp))
        }
    }
}

private fun buildUpdateScript(
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
    waypoints: List<RoutePoint>
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
        ro.put("speedKmh", speedKmh ?: 0f)
        ro.put("routeProgressPct", routeProgressPct ?: JSONObject.NULL)
        ro.put("snappedSegmentIndex", snappedSegmentIndex ?: JSONObject.NULL)
        ro.put("isStale", isStale)
        ro.put("timestamp", System.currentTimeMillis())
        ro
    } else {
        JSONObject.NULL
    }

    return "updateMapData($merchantLat, $merchantLng, $customerLat, $customerLng, $riderObj, $wpArray);"
}

private fun generateHardenedDarkMapHtml(
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
        html, body, #map {
            margin: 0;
            padding: 0;
            width: 100%;
            height: 100%;
            background: #050811;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            overflow: hidden;
        }
        .leaflet-container {
            background: #050811;
        }
        
        /* Zomato-Grade Dark Obsidian Tile Contrast Filter */
        .leaflet-tile-pane {
            filter: brightness(0.85) contrast(1.25) saturate(1.2) hue-rotate(210deg);
        }

        /* 3D Elevated Store Marker */
        .store-marker-3d {
            position: relative;
            width: 44px;
            height: 44px;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .store-beacon {
            position: absolute;
            width: 44px;
            height: 44px;
            border-radius: 50%;
            background: rgba(2, 132, 199, 0.25);
            border: 1px solid rgba(2, 132, 199, 0.8);
            animation: pulseBeacon 2.5s infinite ease-out;
        }
        .store-pill {
            position: relative;
            width: 32px;
            height: 32px;
            background: linear-gradient(135deg, #0284C7 0%, #0369A1 100%);
            border: 2px solid #FFFFFF;
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 8px 20px rgba(0, 0, 0, 0.8), 0 0 12px rgba(2, 132, 199, 0.7);
        }
        .store-pill svg { width: 17px; height: 17px; fill: #FFFFFF; }

        /* 3D Elevated Customer Destination Marker */
        .customer-marker-3d {
            position: relative;
            width: 44px;
            height: 44px;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .customer-beacon {
            position: absolute;
            width: 44px;
            height: 44px;
            border-radius: 50%;
            background: rgba(249, 115, 22, 0.25);
            border: 1px solid rgba(249, 115, 22, 0.8);
            animation: pulseBeacon 2.5s infinite ease-out 0.5s;
        }
        .customer-pill {
            position: relative;
            width: 32px;
            height: 32px;
            background: linear-gradient(135deg, #F97316 0%, #C2410C 100%);
            border: 2px solid #FFFFFF;
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 8px 20px rgba(0, 0, 0, 0.8), 0 0 12px rgba(249, 115, 22, 0.7);
        }
        .customer-pill svg { width: 17px; height: 17px; fill: #FFFFFF; }

        /* Zomato 3D Isometric Scooter with Headlight Projection Beam */
        .biker-anchor {
            position: relative;
            width: 60px;
            height: 60px;
            display: flex;
            align-items: center;
            justify-content: center;
            pointer-events: none;
        }
        .biker-rotator {
            position: relative;
            width: 60px;
            height: 60px;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: transform 0.4s cubic-bezier(0.2, 0.8, 0.2, 1);
        }
        /* Dynamic Headlight Light Cone on Asphalt */
        .biker-headlight {
            position: absolute;
            top: -24px;
            left: 50%;
            transform: translateX(-50%);
            width: 44px;
            height: 34px;
            background: radial-gradient(ellipse at 50% 100%, rgba(56, 189, 248, 0.55) 0%, rgba(56, 189, 248, 0.2) 50%, rgba(56, 189, 248, 0) 80%);
            clip-path: polygon(35% 100%, 65% 100%, 100% 0%, 0% 0%);
            pointer-events: none;
        }
        .biker-pulse-primary {
            position: absolute;
            width: 48px;
            height: 48px;
            border-radius: 50%;
            background: rgba(0, 245, 212, 0.2);
            border: 1.5px solid rgba(0, 245, 212, 0.8);
            animation: radarWave 2s cubic-bezier(0.1, 0.7, 0.1, 1) infinite;
        }
        .biker-pulse-secondary {
            position: absolute;
            width: 48px;
            height: 48px;
            border-radius: 50%;
            background: rgba(0, 187, 249, 0.15);
            animation: radarWave 2s cubic-bezier(0.1, 0.7, 0.1, 1) infinite 0.7s;
        }
        .biker-core-puck {
            position: relative;
            width: 34px;
            height: 34px;
            background: linear-gradient(135deg, #00F5D4 0%, #00BBF9 100%);
            border: 2.5px solid #FFFFFF;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 6px 18px rgba(0, 245, 212, 0.7), 0 2px 6px rgba(0, 0, 0, 0.9);
        }
        .biker-core-puck svg {
            width: 19px;
            height: 19px;
            fill: #050811;
            filter: drop-shadow(0 1px 2px rgba(255,255,255,0.4));
        }

        @keyframes radarWave {
            0% { transform: scale(0.6); opacity: 0.9; }
            70% { transform: scale(1.4); opacity: 0.3; }
            100% { transform: scale(1.6); opacity: 0; }
        }
        @keyframes pulseBeacon {
            0% { transform: scale(0.8); opacity: 0.8; }
            100% { transform: scale(1.5); opacity: 0; }
        }

        /* SVG Multi-Layer Glowing Neon Polyline Shader */
        .neon-glow-outer {
            stroke: #00F5D4;
            stroke-opacity: 0.35;
            filter: drop-shadow(0 0 8px #00F5D4);
        }
        .neon-track-core {
            stroke: #00BBF9;
            stroke-opacity: 0.95;
        }
        .neon-spine-inner {
            stroke: #FFFFFF;
            stroke-opacity: 0.95;
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
        subdomains: 'abcd',
        attribution: ''
    }).addTo(map);

    var storeSvg = '<svg viewBox="0 0 24 24"><path d="M4 4h16v3H4zm0 5h16v11H4zm3 2v7h10v-7z"/></svg>';
    var customerSvg = '<svg viewBox="0 0 24 24"><path d="M12 3L2 12h3v8h14v-8h3L12 3zm0 4.7l4 3.6V18h-8v-6.7l4-3.6z"/></svg>';
    var scooterSvg = '<svg viewBox="0 0 24 24"><path d="M19 7c0-1.1-.9-2-2-2h-3v2h3v2.65L13.52 14H10V9H6c-2.21 0-4 1.79-4 4v3h2c0 1.66 1.34 3 3 3s3-1.34 3-3h4.18c.41 1.16 1.51 2 2.82 2 1.66 0 3-1.34 3-3h1v-4.5L19 7zM7 17c-.55 0-1-.45-1-1h2c0 .55-.45 1-1 1zm11 0c-.55 0-1-.45-1-1h2c0 .55-.45 1-1 1z"/></svg>';

    var storeHtml = '<div class="store-marker-3d"><div class="store-beacon"></div><div class="store-pill">' + storeSvg + '</div></div>';
    var customerHtml = '<div class="customer-marker-3d"><div class="customer-beacon"></div><div class="customer-pill">' + customerSvg + '</div></div>';

    var storeIcon = L.divIcon({ className: '', html: storeHtml, iconSize: [44, 44], iconAnchor: [22, 22] });
    var customerIcon = L.divIcon({ className: '', html: customerHtml, iconSize: [44, 44], iconAnchor: [22, 22] });

    var storeMarker = null;
    var customerMarker = null;
    if ($merchantLat && $merchantLng && $merchantLat !== 0) {
        storeMarker = L.marker([$merchantLat, $merchantLng], { icon: storeIcon }).addTo(map);
    }
    if ($customerLat && $customerLng && $customerLat !== 0) {
        customerMarker = L.marker([$customerLat, $customerLng], { icon: customerIcon }).addTo(map);
    }

    var riderMarker = null;
    var glowOuterPolyline = null;
    var corePolyline = null;
    var spinePolyline = null;
    var traversedPolyline = null;
    var boundsGroup = [];
    if ($merchantLat && $merchantLng && $merchantLat !== 0) boundsGroup.push([$merchantLat, $merchantLng]);
    if ($customerLat && $customerLng && $customerLat !== 0) boundsGroup.push([$customerLat, $customerLng]);
    if (boundsGroup.length > 0) {
        map.fitBounds(L.latLngBounds(boundsGroup), { padding: [35, 35], maxZoom: 16 });
    }
    var lastAcceptedTimestamp = 0;

    // Smooth Monotonic Interpolation Engine
    var animFrame = null;
    function interpolateMarker(marker, startPos, endPos, durationMs) {
        if (animFrame) {
            cancelAnimationFrame(animFrame);
            animFrame = null;
        }
        var startTime = performance.now();
        function step(now) {
            var elapsed = now - startTime;
            var t = Math.min(1, elapsed / durationMs);
            var ease = 1 - Math.pow(1 - t, 3);
            var curLat = startPos[0] + (endPos[0] - startPos[0]) * ease;
            var curLng = startPos[1] + (endPos[1] - startPos[1]) * ease;
            marker.setLatLng([curLat, curLng]);
            if (t < 1) {
                animFrame = requestAnimationFrame(step);
            } else {
                animFrame = null;
            }
        }
        animFrame = requestAnimationFrame(step);
    }

    var autoFollow = true;
    map.on('dragstart', function() { autoFollow = false; });
    map.on('zoomstart', function(e) { if (e && e.originalEvent) autoFollow = false; });

    function updateMapData(mLat, mLng, cLat, cLng, rider, waypoints) {
        boundsGroup = [];

        var effectiveMLat = (mLat && mLat !== 0) ? mLat : $merchantLat;
        var effectiveMLng = (mLng && mLng !== 0) ? mLng : $merchantLng;
        var effectiveCLat = (cLat && cLat !== 0) ? cLat : $customerLat;
        var effectiveCLng = (cLng && cLng !== 0) ? cLng : $customerLng;

        if (effectiveMLat && effectiveMLng) {
            if (!storeMarker) {
                storeMarker = L.marker([effectiveMLat, effectiveMLng], { icon: storeIcon }).addTo(map);
            } else {
                storeMarker.setLatLng([effectiveMLat, effectiveMLng]);
            }
            boundsGroup.push([effectiveMLat, effectiveMLng]);
        }

        if (effectiveCLat && effectiveCLng) {
            if (!customerMarker) {
                customerMarker = L.marker([effectiveCLat, effectiveCLng], { icon: customerIcon }).addTo(map);
            } else {
                customerMarker.setLatLng([effectiveCLat, effectiveCLng]);
            }
            boundsGroup.push([effectiveCLat, effectiveCLng]);
        }

        // Zomato 3D Isometric Rider Marker
        if (rider && rider.lat && rider.lng) {
            var ts = rider.timestamp || Date.now();
            if (ts >= lastAcceptedTimestamp) {
                lastAcceptedTimestamp = ts;
                var rot = (rider.heading != null) ? rider.heading : 0;
                var markerHtml = '<div class="biker-anchor">' +
                                 '<div class="biker-rotator" style="transform: rotate(' + rot + 'deg);">' +
                                 '<div class="biker-headlight"></div>' +
                                 (rider.isStale ? '' : '<div class="biker-pulse-primary"></div><div class="biker-pulse-secondary"></div>') +
                                 '<div class="biker-core-puck">' + scooterSvg + '</div>' +
                                 '</div></div>';
                var bikerIcon = L.divIcon({ className: '', html: markerHtml, iconSize: [60, 60], iconAnchor: [30, 30] });

                if (!riderMarker) {
                    riderMarker = L.marker([rider.lat, rider.lng], { icon: bikerIcon }).addTo(map);
                } else {
                    var prevLatLng = riderMarker.getLatLng();
                    riderMarker.setIcon(bikerIcon);
                    if (rider.isStale) {
                        if (animFrame) {
                            cancelAnimationFrame(animFrame);
                            animFrame = null;
                        }
                        riderMarker.setLatLng([rider.lat, rider.lng]);
                    } else {
                        interpolateMarker(riderMarker, [prevLatLng.lat, prevLatLng.lng], [rider.lat, rider.lng], 900);
                    }
                }
                boundsGroup.push([rider.lat, rider.lng]);

                if (autoFollow && !rider.isStale) {
                    map.panTo([rider.lat, rider.lng], { animate: true, duration: 0.8 });
                }
            }
        } else {
            if (riderMarker) {
                if (animFrame) {
                    cancelAnimationFrame(animFrame);
                    animFrame = null;
                }
                map.removeLayer(riderMarker);
                riderMarker = null;
            }
        }

        // Zomato-Grade Multi-Layer Glowing Neon Polyline Shader
        if (waypoints && waypoints.length > 1) {
            var latLngs = waypoints.map(function(pt) { return [pt.lat, pt.lng]; });
            
            // Layer 1: Outer Ambient Glow (14px)
            if (!glowOuterPolyline) {
                glowOuterPolyline = L.polyline(latLngs, {
                    color: '#00F5D4',
                    weight: 12,
                    opacity: 0.35,
                    lineCap: 'round',
                    lineJoin: 'round',
                    className: 'neon-glow-outer'
                }).addTo(map);
            } else {
                glowOuterPolyline.setLatLngs(latLngs);
            }

            // Layer 2: Core Neon Track (5px)
            if (!corePolyline) {
                corePolyline = L.polyline(latLngs, {
                    color: '#00BBF9',
                    weight: 5,
                    opacity: 0.95,
                    lineCap: 'round',
                    lineJoin: 'round',
                    className: 'neon-track-core'
                }).addTo(map);
            } else {
                corePolyline.setLatLngs(latLngs);
            }

            // Layer 3: Inner White Beam Spine (2px)
            if (!spinePolyline) {
                spinePolyline = L.polyline(latLngs, {
                    color: '#FFFFFF',
                    weight: 2,
                    opacity: 0.95,
                    lineCap: 'round',
                    lineJoin: 'round',
                    className: 'neon-spine-inner'
                }).addTo(map);
            } else {
                spinePolyline.setLatLngs(latLngs);
            }
        } else {
            if (glowOuterPolyline) { map.removeLayer(glowOuterPolyline); glowOuterPolyline = null; }
            if (corePolyline) { map.removeLayer(corePolyline); corePolyline = null; }
            if (spinePolyline) { map.removeLayer(spinePolyline); spinePolyline = null; }
        }
    }

    function recenterMap() {
        autoFollow = true;
        if (boundsGroup.length > 0) {
            map.fitBounds(L.latLngBounds(boundsGroup), { padding: [40, 40], maxZoom: 16 });
        }
    }
</script>
</body>
</html>
    """.trimIndent()
}
