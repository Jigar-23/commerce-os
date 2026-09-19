import Foundation
import CoreLocation
import Combine

public enum GeofenceZone: String {
    case enRoute = "EN_ROUTE"
    case nearMerchant = "NEAR_MERCHANT"
    case atMerchant = "AT_MERCHANT"
    case nearCustomer = "NEAR_CUSTOMER"
    case atCustomer = "AT_CUSTOMER"
}

/// Native CoreLocation 50m circular geofence boundary arrival detector
public final class RiderGeofenceDetector: NSObject, ObservableObject, CLLocationManagerDelegate {
    public static let shared = RiderGeofenceDetector()
    
    public static let defaultArrivalRadiusMeters: CLLocationDistance = 50.0
    public static let proximityWarningRadiusMeters: CLLocationDistance = 200.0
    
    @Published public private(set) var currentZone: GeofenceZone = .enRoute
    @Published public private(set) var distanceToTargetMeters: CLLocationDistance? = nil
    @Published public private(set) var isInsideArrivalZone: Bool = false
    @Published public private(set) var hasTriggeredMerchantArrival: Bool = false
    @Published public private(set) var hasTriggeredCustomerArrival: Bool = false
    
    public var onMerchantArrival: (() -> Void)?
    public var onCustomerArrival: (() -> Void)?
    
    private var merchantCoordinate: CLLocationCoordinate2D?
    private var customerCoordinate: CLLocationCoordinate2D?
    private var activeStage: String = "ASSIGNED"
    
    private var merchantRegion: CLCircularRegion?
    private var customerRegion: CLCircularRegion?
    private let locationManager = CLLocationManager()
    
    public override init() {
        super.init()
        locationManager.delegate = self
    }
    
    /// Starts circular geofence monitoring for both pickup merchant and delivery customer
    public func startMonitoring(
        merchantCoord: CLLocationCoordinate2D,
        customerCoord: CLLocationCoordinate2D,
        initialStage: String
    ) {
        self.merchantCoordinate = merchantCoord
        self.customerCoordinate = customerCoord
        self.activeStage = initialStage
        self.hasTriggeredMerchantArrival = false
        self.hasTriggeredCustomerArrival = false
        self.currentZone = .enRoute
        self.isInsideArrivalZone = false
        
        // Setup CoreLocation circular geofences if supported
        if CLLocationManager.isMonitoringAvailable(for: CLCircularRegion.self) {
            let mRegion = CLCircularRegion(
                center: merchantCoord,
                radius: Self.defaultArrivalRadiusMeters,
                identifier: "merchant_pickup_geofence"
            )
            mRegion.notifyOnEntry = true
            mRegion.notifyOnExit = true
            self.merchantRegion = mRegion
            locationManager.startMonitoring(for: mRegion)
            
            let cRegion = CLCircularRegion(
                center: customerCoord,
                radius: Self.defaultArrivalRadiusMeters,
                identifier: "customer_delivery_geofence"
            )
            cRegion.notifyOnEntry = true
            cRegion.notifyOnExit = true
            self.customerRegion = cRegion
            locationManager.startMonitoring(for: cRegion)
        }
    }
    
    public func updateStage(_ stage: String) {
        self.activeStage = stage
    }
    
    /// Evaluates current GPS location against 50m boundaries with high-precision distance checks
    public func updateLocation(_ location: CLLocation) {
        let isHeadingToStore = (activeStage == "ASSIGNED" || activeStage == "ARRIVED_MERCHANT")
        guard let targetCoord = (isHeadingToStore ? merchantCoordinate : customerCoordinate) else { return }
        
        let targetLocation = CLLocation(latitude: targetCoord.latitude, longitude: targetCoord.longitude)
        let distance = location.distance(from: targetLocation)
        self.distanceToTargetMeters = distance
        
        if isHeadingToStore {
            if distance <= Self.defaultArrivalRadiusMeters {
                self.currentZone = .atMerchant
                self.isInsideArrivalZone = true
                if !hasTriggeredMerchantArrival {
                    self.hasTriggeredMerchantArrival = true
                    TurnByTurnVoiceNotifier.shared.announceApproachingPickup(merchantName: "Dark Store Fulfillment Center")
                    onMerchantArrival?()
                }
            } else if distance <= Self.proximityWarningRadiusMeters {
                self.currentZone = .nearMerchant
                self.isInsideArrivalZone = false
            } else {
                self.currentZone = .enRoute
                self.isInsideArrivalZone = false
            }
        } else {
            if distance <= Self.defaultArrivalRadiusMeters {
                self.currentZone = .atCustomer
                self.isInsideArrivalZone = true
                if !hasTriggeredCustomerArrival {
                    self.hasTriggeredCustomerArrival = true
                    TurnByTurnVoiceNotifier.shared.announceArrivalPromptOtp()
                    onCustomerArrival?()
                }
            } else if distance <= Self.proximityWarningRadiusMeters {
                self.currentZone = .nearCustomer
                self.isInsideArrivalZone = false
            } else {
                self.currentZone = .enRoute
                self.isInsideArrivalZone = false
            }
        }
    }
    
    public func setStage(_ stage: String) {
        self.activeStage = stage
    }
    
    public func stopMonitoring() {
        if let m = merchantRegion {
            locationManager.stopMonitoring(for: m)
        }
        if let c = customerRegion {
            locationManager.stopMonitoring(for: c)
        }
        merchantRegion = nil
        customerRegion = nil
        merchantCoordinate = nil
        customerCoordinate = nil
        distanceToTargetMeters = nil
        isInsideArrivalZone = false
        currentZone = .enRoute
    }
    
    // MARK: - CLLocationManagerDelegate
    
    public func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        if region.identifier == "merchant_pickup_geofence" {
            DispatchQueue.main.async {
                self.currentZone = .atMerchant
                self.isInsideArrivalZone = true
                if !self.hasTriggeredMerchantArrival {
                    self.hasTriggeredMerchantArrival = true
                    self.onMerchantArrival?()
                }
            }
        } else if region.identifier == "customer_delivery_geofence" {
            DispatchQueue.main.async {
                self.currentZone = .atCustomer
                self.isInsideArrivalZone = true
                if !self.hasTriggeredCustomerArrival {
                    self.hasTriggeredCustomerArrival = true
                    self.onCustomerArrival?()
                }
            }
        }
    }
    
    public func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        DispatchQueue.main.async {
            self.currentZone = .enRoute
            self.isInsideArrivalZone = false
        }
    }
}
