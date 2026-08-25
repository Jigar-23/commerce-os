package com.commerceos.rider.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
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

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0B1120))) {
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
                    setBackgroundColor(0xFF0B1120.toInt())

                    webViewClient = object : WebViewClient() {
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
                color = Color(0xFF1E293B).copy(alpha = 0.9f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color(0xFF38BDF8), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Updating route…", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Rider GPS Status Pill
        if (!hasRiderLocation) {
            Surface(
                color = Color(0xFF1E293B).copy(alpha = 0.9f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color(0xFF38BDF8), strokeWidth = 2.dp)
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
            containerColor = Color(0xFF1E293B),
            contentColor = Color(0xFF38BDF8),
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
        html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #0B1120; font-family: -apple-system, Roboto, sans-serif; }
        .leaflet-container { background: #0B1120; }
        .store-icon {
            background: #0284C7;
            border: 2px solid #FFFFFF;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 2px 10px rgba(2, 132, 199, 0.8);
        }
        .store-icon svg { width: 16px; height: 16px; fill: white; }
        .customer-icon {
            background: #EA580C;
            border: 2px solid #FFFFFF;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 2px 10px rgba(234, 88, 12, 0.8);
        }
        .customer-icon svg { width: 16px; height: 16px; fill: white; }
        
        /* Clean Quick-Commerce Branded Rider Marker */
        .biker-container {
            width: 36px;
            height: 36px;
            position: relative;
            transition: transform 0.4s cubic-bezier(0.25, 1, 0.5, 1);
        }
        .biker-pulse {
            position: absolute;
            width: 36px;
            height: 36px;
            border-radius: 50%;
            background: rgba(16, 185, 129, 0.3);
            animation: pulse 2.5s infinite;
        }
        .biker-core {
            position: absolute;
            top: 3px;
            left: 3px;
            width: 30px;
            height: 30px;
            background: #10B981;
            border: 2px solid #FFFFFF;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 2px 8px rgba(0,0,0,0.6);
        }
        .biker-core svg {
            width: 18px;
            height: 18px;
            fill: #FFFFFF;
        }
        @keyframes pulse {
            0% { transform: scale(0.9); opacity: 0.7; }
            70% { transform: scale(1.5); opacity: 0; }
            100% { transform: scale(1.5); opacity: 0; }
        }
    </style>
</head>
<body>
<div id="map"></div>
<script>
    var map = L.map('map', {
        zoomControl: false,
        attributionControl: true
    }).setView([$merchantLat, $merchantLng], 14);

    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
        maxZoom: 19,
        subdomains: 'abcd',
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'
    }).addTo(map);

    var storeSvg = '<svg viewBox="0 0 24 24"><path d="M4 4h16v3H4zm0 5h16v11H4zm3 2v7h10v-7z"/></svg>';
    var customerSvg = '<svg viewBox="0 0 24 24"><path d="M12 3L2 12h3v8h14v-8h3L12 3zm0 4.7l4 3.6V18h-8v-6.7l4-3.6z"/></svg>';
    var bikeSvg = '<svg viewBox="0 0 24 24"><path d="M15.5 5.5c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zM5 12c-2.8 0-5 2.2-5 5s2.2 5 5 5 5-2.2 5-5-2.2-5-5-5zm0 8.5c-1.9 0-3.5-1.6-3.5-3.5s1.6-3.5 3.5-3.5 3.5 1.6 3.5 3.5-1.6 3.5-3.5 3.5zm14-8.5c-2.8 0-5 2.2-5 5s2.2 5 5 5 5-2.2 5-5-2.2-5-5-5zm0 8.5c-1.9 0-3.5-1.6-3.5-3.5s1.6-3.5 3.5-3.5 3.5 1.6 3.5 3.5-1.6 3.5-3.5 3.5zm-8.2-7.5l-2.4-4H5v2h2.2l1.6 2.7c-.8.8-1.3 1.8-1.5 3h2.1c.2-.7.6-1.3 1.1-1.8l1.7 2.1h3.7v-2h-2.5l-1.9-2.4.9-2.6 1.8 1.4v2.6h2v-3.7l-2.8-2.2c-.3-.2-.7-.3-1.1-.3-.4 0-.8.2-1.1.5l-1.6 2.4z"/></svg>';

    var storeIcon = L.divIcon({ className: 'store-icon', html: storeSvg, iconSize: [30, 30], iconAnchor: [15, 15] });
    var customerIcon = L.divIcon({ className: 'customer-icon', html: customerSvg, iconSize: [30, 30], iconAnchor: [15, 15] });

    var storeMarker = null;
    var customerMarker = null;
    if ($merchantLat && $merchantLng && $merchantLat !== 0) {
        storeMarker = L.marker([$merchantLat, $merchantLng], { icon: storeIcon }).addTo(map);
    }
    if ($customerLat && $customerLng && $customerLat !== 0) {
        customerMarker = L.marker([$customerLat, $customerLng], { icon: customerIcon }).addTo(map);
    }

    var riderMarker = null;
    var routePolyline = null;
    var boundsGroup = [];
    if ($merchantLat && $merchantLng && $merchantLat !== 0) boundsGroup.push([$merchantLat, $merchantLng]);
    if ($customerLat && $customerLng && $customerLat !== 0) boundsGroup.push([$customerLat, $customerLng]);
    if (boundsGroup.length > 0) {
        map.fitBounds(L.latLngBounds(boundsGroup), { padding: [30, 30], maxZoom: 15 });
    }
    var lastAcceptedTimestamp = 0;

    // Smooth Monotonic Interpolation Engine with Immediate Stale Freeze
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

        // Monotonic Filtered Rider Marker with Immediate Stale Freeze
        if (rider && rider.lat && rider.lng) {
            var ts = rider.timestamp || Date.now();
            if (ts >= lastAcceptedTimestamp) {
                lastAcceptedTimestamp = ts;
                var rot = (rider.heading != null) ? rider.heading : 0;
                var markerHtml = '<div class="biker-container" style="transform: rotate(' + rot + 'deg);">' +
                                 (rider.isStale ? '' : '<div class="biker-pulse"></div>') +
                                 '<div class="biker-core">' + bikeSvg + '</div>' +
                                 '</div>';
                var bikerIcon = L.divIcon({ className: '', html: markerHtml, iconSize: [32, 32], iconAnchor: [16, 16] });

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

        // Authoritative Road Geometry Polyline
        if (waypoints && waypoints.length > 0) {
            var latLngs = waypoints.map(function(pt) { return [pt.lat, pt.lng]; });
            if (!routePolyline) {
                routePolyline = L.polyline(latLngs, {
                    color: '#10B981',
                    weight: 5,
                    opacity: 0.85,
                    lineCap: 'round',
                    lineJoin: 'round'
                }).addTo(map);
            } else {
                routePolyline.setLatLngs(latLngs);
            }
        } else {
            if (routePolyline) {
                map.removeLayer(routePolyline);
                routePolyline = null;
            }
        }
    }

    function recenterMap() {
        if (boundsGroup.length > 0) {
            map.fitBounds(L.latLngBounds(boundsGroup), { padding: [40, 40], maxZoom: 16 });
        }
    }
</script>
</body>
</html>
    """.trimIndent()
}
