import Foundation

/// Single Canonical Offer Payload Validator and Deserializer for iOS APNs & In-App payloads.
/// Enforces parity with Android OfferPayloadValidator with support for camelCase and snake_case fields.
public struct OfferPayloadValidator {
    public static func validate(payload: [AnyHashable: Any]) -> DispatchOfferDto? {
        guard let offerId = stringValue(payload, keys: ["offerId", "offer_id", "id"]),
              let orderId = stringValue(payload, keys: ["orderId", "order_id"]) else {
            return nil
        }

        let payout = doubleValue(payload, keys: [
            "earningsAmount", "earnings_amount", "totalEarnings",
            "payout", "payout_amount", "earnings", "estimatedEarnings", "estimated_earnings"
        ]) ?? 35.0

        let merchantName = stringValue(payload, keys: [
            "merchantName", "merchant_name", "storeName", "store_name", "merchant"
        ]) ?? "Merchant Partner"

        let merchantAddr = stringValue(payload, keys: [
            "merchantAddress", "merchant_address", "storeAddress", "store_address"
        ]) ?? "Merchant Hub"

        let mLat = doubleValue(payload, keys: ["merchantLat", "merchant_lat", "storeLat"]) ?? 28.202224
        let mLng = doubleValue(payload, keys: ["merchantLng", "merchant_lng", "storeLng"]) ?? 76.615418

        let customerName = stringValue(payload, keys: ["customerName", "customer_name"]) ?? "Customer"
        let customerAddr = stringValue(payload, keys: [
            "customerAddress", "customer_address", "deliveryAddress", "delivery_address"
        ]) ?? "Delivery Address"

        let cLat = doubleValue(payload, keys: ["customerLat", "customer_lat", "destLat"]) ?? 28.202224
        let cLng = doubleValue(payload, keys: ["customerLng", "customer_lng", "destLng"]) ?? 76.615418

        let distance = doubleValue(payload, keys: [
            "totalDistanceKm", "total_distance_km", "distance_km", "deliveryDistanceKm", "delivery_distance_km"
        ]) ?? 2.0

        let expiry: Date = {
            if let expMs = doubleValue(payload, keys: ["offerExpiresAt", "expiresAt", "offer_expires_at"]) {
                if expMs > 1_000_000_000_000 {
                    return Date(timeIntervalSince1970: expMs / 1000.0)
                } else if expMs > 1_000_000_000 {
                    return Date(timeIntervalSince1970: expMs)
                }
            }
            return Date().addingTimeInterval(30)
        }()

        return DispatchOfferDto(
            offerId: offerId,
            orderId: orderId,
            payoutAmount: payout,
            merchantName: merchantName,
            merchantAddress: merchantAddr,
            merchantLat: mLat,
            merchantLng: mLng,
            customerName: customerName,
            customerAddress: customerAddr,
            customerLat: cLat,
            customerLng: cLng,
            totalDistanceKm: distance,
            expiresAt: expiry
        )
    }

    private static func stringValue(_ payload: [AnyHashable: Any], keys: [String]) -> String? {
        for key in keys {
            if let val = payload[key] as? String, !val.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                return val
            }
        }
        return nil
    }

    private static func doubleValue(_ payload: [AnyHashable: Any], keys: [String]) -> Double? {
        for key in keys {
            if let num = payload[key] as? NSNumber {
                return num.doubleValue
            }
            if let str = payload[key] as? String, let d = Double(str) {
                return d
            }
        }
        return nil
    }
}
