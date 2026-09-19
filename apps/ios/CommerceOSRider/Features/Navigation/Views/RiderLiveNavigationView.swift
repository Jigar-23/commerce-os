import SwiftUI
import WebKit
import MapKit
import CoreLocation

public struct RiderLiveNavigationView: View {
    public let merchantCoordinate: CLLocationCoordinate2D?
    public let customerCoordinate: CLLocationCoordinate2D?
    public let riderCoordinate: CLLocationCoordinate2D?
    public let destinationCoordinate: CLLocationCoordinate2D?
    public let riderBearing: Double
    public let speedKmh: Double

    @State private var webViewCoordinator: RiderDarkMapCoordinator?

    public init(
        merchantCoordinate: CLLocationCoordinate2D? = nil,
        customerCoordinate: CLLocationCoordinate2D? = nil,
        riderCoordinate: CLLocationCoordinate2D? = nil,
        destinationCoordinate: CLLocationCoordinate2D? = nil,
        riderBearing: Double = 0.0,
        speedKmh: Double = 0.0
    ) {
        self.merchantCoordinate = merchantCoordinate
        self.customerCoordinate = customerCoordinate
        self.riderCoordinate = riderCoordinate
        self.destinationCoordinate = destinationCoordinate
        self.riderBearing = riderBearing
        self.speedKmh = speedKmh
    }

    public var body: some View {
        ZStack(alignment: .bottomTrailing) {
            RiderDarkMapWebView(
                merchantCoordinate: merchantCoordinate,
                customerCoordinate: customerCoordinate,
                riderCoordinate: riderCoordinate,
                destinationCoordinate: destinationCoordinate,
                riderBearing: riderBearing,
                speedKmh: speedKmh,
                onCoordinatorCreated: { coord in
                    self.webViewCoordinator = coord
                }
            )
            .background(Color(red: 5/255, green: 8/255, blue: 17/255))
            .edgesIgnoringSafeArea(.all)

            // Recenter FAB Button
            Button(action: {
                webViewCoordinator?.recenter()
            }) {
                Image(systemName: "location.fill")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(Color(red: 16/255, green: 185/255, blue: 129/255))
                    .frame(width: 44, height: 44)
                    .background(Color(red: 22/255, green: 24/255, blue: 31/255, opacity: 0.95))
                    .clipShape(Circle())
                    .overlay(
                        Circle().stroke(Color(red: 38/255, green: 41/255, blue: 51/255), lineWidth: 1)
                    )
                    .shadow(color: Color.black.opacity(0.6), radius: 6, x: 0, y: 3)
            }
            .padding(.trailing, 16)
            .padding(.bottom, 220) // Positioned comfortably above bottom action sheet
        }
    }
}

// MARK: - WKWebView Representable Bridge
struct RiderDarkMapWebView: UIViewRepresentable {
    let merchantCoordinate: CLLocationCoordinate2D?
    let customerCoordinate: CLLocationCoordinate2D?
    let riderCoordinate: CLLocationCoordinate2D?
    let destinationCoordinate: CLLocationCoordinate2D?
    let riderBearing: Double
    let speedKmh: Double
    let onCoordinatorCreated: (RiderDarkMapCoordinator) -> Void

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        let prefs = WKWebpagePreferences()
        prefs.allowsContentJavaScript = true
        config.defaultWebpagePreferences = prefs

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.scrollView.isScrollEnabled = false
        webView.scrollView.bounces = false
        webView.isOpaque = false
        webView.backgroundColor = UIColor(red: 5/255, green: 8/255, blue: 17/255, alpha: 1.0)

        context.coordinator.webView = webView
        onCoordinatorCreated(context.coordinator)

        let initialLat = riderCoordinate?.latitude ?? merchantCoordinate?.latitude ?? 28.2022
        let initialLng = riderCoordinate?.longitude ?? merchantCoordinate?.longitude ?? 76.6154
        let html = generateDarkMapHtml(initLat: initialLat, initLng: initialLng)
        webView.loadHTMLString(html, baseURL: URL(string: "https://commerceos.local"))

        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        context.coordinator.update(
            merchantCoordinate: merchantCoordinate,
            customerCoordinate: customerCoordinate,
            riderCoordinate: riderCoordinate,
            destinationCoordinate: destinationCoordinate,
            riderBearing: riderBearing,
            speedKmh: speedKmh
        )
    }

    func makeCoordinator() -> RiderDarkMapCoordinator {
        RiderDarkMapCoordinator()
    }
}

// MARK: - Dark Map Coordinator
final class RiderDarkMapCoordinator: NSObject, WKNavigationDelegate {
    weak var webView: WKWebView?
    private var isLoaded: Bool = false
    private var pendingJs: String? = nil

    private var lastRouteOrigin: CLLocationCoordinate2D? = nil
    private var lastRouteDest: CLLocationCoordinate2D? = nil
    private var cachedWaypointsJson: String = "[]"

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        isLoaded = true
        if let js = pendingJs {
            webView.evaluateJavaScript(js, completionHandler: nil)
            pendingJs = nil
        }
    }

    func recenter() {
        webView?.evaluateJavaScript("recenterMap();", completionHandler: nil)
    }

    func update(
        merchantCoordinate: CLLocationCoordinate2D?,
        customerCoordinate: CLLocationCoordinate2D?,
        riderCoordinate: CLLocationCoordinate2D?,
        destinationCoordinate: CLLocationCoordinate2D?,
        riderBearing: Double,
        speedKmh: Double
    ) {
        let origin = riderCoordinate ?? merchantCoordinate
        let dest = destinationCoordinate ?? customerCoordinate ?? merchantCoordinate

        // Check if road routing needs recalculation via MKDirections
        if let orig = origin, let dst = dest {
            let origDelta = lastRouteOrigin.map { abs($0.latitude - orig.latitude) + abs($0.longitude - orig.longitude) } ?? 1.0
            let destDelta = lastRouteDest.map { abs($0.latitude - dst.latitude) + abs($0.longitude - dst.longitude) } ?? 1.0

            if origDelta > 0.0008 || destDelta > 0.0001 {
                self.lastRouteOrigin = orig
                self.lastRouteDest = dst
                fetchRoadPolyline(from: orig, to: dst)
            }
        }

        let mLatStr = merchantCoordinate != nil ? "\(merchantCoordinate!.latitude)" : "null"
        let mLngStr = merchantCoordinate != nil ? "\(merchantCoordinate!.longitude)" : "null"
        let cLatStr = customerCoordinate != nil ? "\(customerCoordinate!.latitude)" : "null"
        let cLngStr = customerCoordinate != nil ? "\(customerCoordinate!.longitude)" : "null"

        let riderJson: String
        if let r = riderCoordinate {
            let ts = Int(Date().timeIntervalSince1970 * 1000)
            riderJson = "{\"lat\":\(r.latitude),\"lng\":\(r.longitude),\"heading\":\(riderBearing),\"speedKmh\":\(speedKmh),\"timestamp\":\(ts)}"
        } else {
            riderJson = "null"
        }

        let js = "updateMapData(\(mLatStr), \(mLngStr), \(cLatStr), \(cLngStr), \(riderJson), \(cachedWaypointsJson));"

        if isLoaded {
            webView?.evaluateJavaScript(js, completionHandler: nil)
        } else {
            pendingJs = js
        }
    }

    private func fetchRoadPolyline(from origin: CLLocationCoordinate2D, to dest: CLLocationCoordinate2D) {
        let req = MKDirections.Request()
        req.source = MKMapItem(placemark: MKPlacemark(coordinate: origin))
        req.destination = MKMapItem(placemark: MKPlacemark(coordinate: dest))
        req.transportType = .automobile

        let directions = MKDirections(request: req)
        directions.calculate { [weak self] response, _ in
            guard let self = self, let route = response?.routes.first else { return }
            let pointCount = route.polyline.pointCount
            var coords = [CLLocationCoordinate2D](repeating: kCLLocationCoordinate2DInvalid, count: pointCount)
            route.polyline.getCoordinates(&coords, range: NSRange(location: 0, length: pointCount))

            let waypointsArray = coords.map { "[\($0.latitude),\($0.longitude)]" }.joined(separator: ",")
            self.cachedWaypointsJson = "[\(waypointsArray)]"

            if self.isLoaded {
                let js = "if (window.updateWaypoints) { window.updateWaypoints(\(self.cachedWaypointsJson)); }"
                self.webView?.evaluateJavaScript(js, completionHandler: nil)
            }
        }
    }
}

// MARK: - Dark Obsidian HTML Template
private func generateDarkMapHtml(initLat: Double, initLng: Double) -> String {
    return #"""
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
            -webkit-user-select: none;
            user-select: none;
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
            position: absolute;
            top: 0;
            left: 0;
            width: 60px;
            height: 60px;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: transform 0.4s cubic-bezier(0.2, 0.8, 0.2, 1);
            pointer-events: none;
        }
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
            z-index: 2;
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
    }).setView([INIT_LAT, INIT_LNG], 14);

    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
        maxZoom: 19,
        subdomains: 'abcd',
        attribution: ''
    }).addTo(map);

    function buildStoreMarkerHtml() {
        return '<div class="store-marker-pin" style="width:50px; height:65px; pointer-events:none;">' +
            '<svg viewBox="0 0 100 130" width="50" height="65" fill="none" xmlns="http://www.w3.org/2000/svg">' +
            '<ellipse cx="50" cy="122" rx="18" ry="6" fill="rgba(0,0,0,0.22)" />' +
            '<path d="M 50 118 C 42 104 16 76 16 52 A 34 34 0 1 1 84 52 C 84 76 58 104 50 118 Z" fill="#0284C7" stroke="#FFFFFF" stroke-width="4" stroke-linejoin="round" />' +
            '<circle cx="50" cy="52" r="24" fill="#FFFFFF" />' +
            '<rect x="34" y="36" width="32" height="3" rx="1.5" fill="#0369A1" />' +
            '<path d="M 34 39 L 66 39 L 68 48 L 32 48 Z" fill="#0369A1" />' +
            '<line x1="41" y1="39" x2="41" y2="48" stroke="#FFFFFF" stroke-width="1.8" stroke-linecap="round" />' +
            '<line x1="50" y1="39" x2="50" y2="48" stroke="#FFFFFF" stroke-width="1.8" stroke-linecap="round" />' +
            '<line x1="59" y1="39" x2="59" y2="48" stroke="#FFFFFF" stroke-width="1.8" stroke-linecap="round" />' +
            '<rect x="35" y="51" width="8" height="8" rx="1.5" fill="#0369A1" />' +
            '<rect x="57" y="51" width="8" height="8" rx="1.5" fill="#0369A1" />' +
            '<rect x="46" y="51" width="8" height="13" rx="2.5" fill="#0369A1" />' +
            '<rect x="32" y="64" width="36" height="2.5" rx="1.2" fill="#0369A1" />' +
            '<rect x="24" y="2" width="52" height="18" rx="9" fill="#0F172A" stroke="#FFFFFF" stroke-width="1.5" />' +
            '<text x="50" y="14.5" fill="#38BDF8" font-size="10" font-family="-apple-system, BlinkMacSystemFont, Roboto, sans-serif" font-weight="900" text-anchor="middle" letter-spacing="0.5">SHOP</text>' +
            '</svg></div>';
    }

    function buildCustomerMarkerHtml() {
        return '<div class="customer-marker-pin" style="width:50px; height:65px; pointer-events:none;">' +
            '<svg viewBox="0 0 100 130" width="50" height="65" fill="none" xmlns="http://www.w3.org/2000/svg">' +
            '<ellipse cx="50" cy="122" rx="18" ry="6" fill="rgba(0,0,0,0.22)" />' +
            '<path d="M 50 118 C 42 104 16 76 16 52 A 34 34 0 1 1 84 52 C 84 76 58 104 50 118 Z" fill="#EA580C" stroke="#FFFFFF" stroke-width="4" stroke-linejoin="round" />' +
            '<circle cx="50" cy="52" r="24" fill="#FFFFFF" />' +
            '<rect x="57" y="36" width="4" height="8" rx="1" fill="#C2410C" />' +
            '<path d="M 50 34 L 33 47 L 36 50 L 50 39 L 64 50 L 67 47 Z" fill="#C2410C" />' +
            '<rect x="37" y="48" width="26" height="17" rx="2" fill="#C2410C" />' +
            '<rect x="47" y="55" width="6" height="10" rx="2.5" fill="#FFFFFF" />' +
            '<rect x="40" y="52" width="5" height="5" rx="1.5" fill="#FFFFFF" />' +
            '<rect x="24" y="2" width="52" height="18" rx="9" fill="#0F172A" stroke="#FFFFFF" stroke-width="1.5" />' +
            '<text x="50" y="14.5" fill="#F59E0B" font-size="10" font-family="-apple-system, BlinkMacSystemFont, Roboto, sans-serif" font-weight="900" text-anchor="middle" letter-spacing="0.5">HOME</text>' +
            '</svg></div>';
    }

    var storeIcon = L.divIcon({ className: '', html: buildStoreMarkerHtml(), iconSize: [50, 65], iconAnchor: [25, 59] });
    var customerIcon = L.divIcon({ className: '', html: buildCustomerMarkerHtml(), iconSize: [50, 65], iconAnchor: [25, 59] });

    var storeMarker = null;
    var customerMarker = null;
    var riderMarker = null;
    var glowOuterPolyline = null;
    var corePolyline = null;
    var spinePolyline = null;
    var boundsGroup = [];
    var lastAcceptedTimestamp = 0;
    var autoFollow = true;

    var animFrame = null;
    function interpolateMarker(marker, startPos, endPos, durationMs) {
        if (animFrame) { cancelAnimationFrame(animFrame); animFrame = null; }
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

    map.on('dragstart', function() { autoFollow = false; });
    map.on('zoomstart', function(e) { if (e && e.originalEvent) autoFollow = false; });

    function renderWaypoints(waypoints) {
        if (waypoints && waypoints.length > 1) {
            var latLngs = waypoints.map(function(pt) { return [pt[0] || pt.lat, pt[1] || pt.lng]; });
            if (!glowOuterPolyline) {
                glowOuterPolyline = L.polyline(latLngs, { color: '#00F5D4', weight: 12, opacity: 0.35, lineCap: 'round', lineJoin: 'round', className: 'neon-glow-outer' }).addTo(map);
            } else {
                glowOuterPolyline.setLatLngs(latLngs);
            }
            if (!corePolyline) {
                corePolyline = L.polyline(latLngs, { color: '#00BBF9', weight: 5, opacity: 0.95, lineCap: 'round', lineJoin: 'round', className: 'neon-track-core' }).addTo(map);
            } else {
                corePolyline.setLatLngs(latLngs);
            }
            if (!spinePolyline) {
                spinePolyline = L.polyline(latLngs, { color: '#FFFFFF', weight: 2, opacity: 0.95, lineCap: 'round', lineJoin: 'round', className: 'neon-spine-inner' }).addTo(map);
            } else {
                spinePolyline.setLatLngs(latLngs);
            }
        } else {
            if (glowOuterPolyline) { map.removeLayer(glowOuterPolyline); glowOuterPolyline = null; }
            if (corePolyline) { map.removeLayer(corePolyline); corePolyline = null; }
            if (spinePolyline) { map.removeLayer(spinePolyline); spinePolyline = null; }
        }
    }
    window.updateWaypoints = renderWaypoints;

    function updateMapData(mLat, mLng, cLat, cLng, rider, waypoints) {
        boundsGroup = [];

        if (mLat && mLng && mLat !== 0) {
            if (!storeMarker) {
                storeMarker = L.marker([mLat, mLng], { icon: storeIcon }).addTo(map);
            } else {
                storeMarker.setLatLng([mLat, mLng]);
            }
            boundsGroup.push([mLat, mLng]);
        }

        if (cLat && cLng && cLat !== 0) {
            if (!customerMarker) {
                customerMarker = L.marker([cLat, cLng], { icon: customerIcon }).addTo(map);
            } else {
                customerMarker.setLatLng([cLat, cLng]);
            }
            boundsGroup.push([cLat, cLng]);
        }

        if (rider && rider.lat && rider.lng) {
            var ts = rider.timestamp || Date.now();
            if (ts >= lastAcceptedTimestamp) {
                lastAcceptedTimestamp = ts;
                var rot = (rider.heading != null) ? rider.heading : 0;
                var markerHtml = '<div class="biker-anchor">' +
                                 '<div class="biker-rotator" style="transform: rotate(' + rot + 'deg);">' +
                                 '<div class="biker-headlight"></div>' +
                                 '</div>' +
                                 '<div class="biker-pulse-primary"></div><div class="biker-pulse-secondary"></div>' +
                                 '<div class="biker-core-puck">' + scooterSvg + '</div>' +
                                 '</div>';
                var bikerIcon = L.divIcon({ className: '', html: markerHtml, iconSize: [60, 60], iconAnchor: [30, 30] });

                if (!riderMarker) {
                    riderMarker = L.marker([rider.lat, rider.lng], { icon: bikerIcon }).addTo(map);
                } else {
                    var prevLatLng = riderMarker.getLatLng();
                    riderMarker.setIcon(bikerIcon);
                    interpolateMarker(riderMarker, [prevLatLng.lat, prevLatLng.lng], [rider.lat, rider.lng], 900);
                }
                boundsGroup.push([rider.lat, rider.lng]);

                if (autoFollow) {
                    map.panTo([rider.lat, rider.lng], { animate: true, duration: 0.8 });
                }
            }
        }

        renderWaypoints(waypoints);
    }

    function recenterMap() {
        autoFollow = true;
        if (riderMarker) {
            map.panTo(riderMarker.getLatLng(), { animate: true, duration: 0.8 });
        } else if (boundsGroup.length > 0) {
            map.fitBounds(L.latLngBounds(boundsGroup), { padding: [40, 40], maxZoom: 16 });
        }
    }
</script>
</body>
</html>
"""#.replacingOccurrences(of: "INIT_LAT", with: "\(initLat)")
   .replacingOccurrences(of: "INIT_LNG", with: "\(initLng)")
}
