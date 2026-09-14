import Foundation
import Combine

public struct CartPersistedItem: Codable, Equatable {
    public let product: ProductDto
    public var quantity: Int
    public var updatedAt: Date

    public init(product: ProductDto, quantity: Int, updatedAt: Date = Date()) {
        self.product = product
        self.quantity = quantity
        self.updatedAt = updatedAt
    }
}

public final class CartLocalStore: ObservableObject {
    public static let shared = CartLocalStore()
    
    @Published public private(set) var items: [String: (product: ProductDto, quantity: Int)] = [:]
    
    private let storageKey = "commerce_os_cart_items_v1"
    private let userDefaults: UserDefaults
    
    public init(userDefaults: UserDefaults = .standard, loadPersisted: Bool = true) {
        self.userDefaults = userDefaults
        if loadPersisted {
            loadFromDisk()
        }
    }
    
    public var totalItemCount: Int {
        items.values.reduce(0) { $0 + $1.quantity }
    }
    
    public var subtotal: Double {
        items.values.reduce(0.0) { $0 + ($1.product.price * Double($1.quantity)) }
    }
    
    public var hasPrescriptionItem: Bool {
        items.values.contains { $0.product.requiresPrescription }
    }
    
    public func add(product: ProductDto) {
        if let existing = items[product.sku] {
            items[product.sku] = (product, existing.quantity + 1)
        } else {
            items[product.sku] = (product, 1)
        }
        persistToDisk()
    }
    
    public func increment(sku: String) {
        guard let existing = items[sku] else { return }
        items[sku] = (existing.product, existing.quantity + 1)
        persistToDisk()
    }
    
    public func decrement(sku: String) {
        guard let existing = items[sku] else { return }
        if existing.quantity > 1 {
            items[sku] = (existing.product, existing.quantity - 1)
        } else {
            items.removeValue(forKey: sku)
        }
        persistToDisk()
    }
    
    public func clear() {
        items.removeAll()
        persistToDisk()
    }
    
    public func persistToDisk() {
        let serializable = items.map { CartPersistedItem(product: $0.value.product, quantity: $0.value.quantity) }
        if let encoded = try? JSONEncoder().encode(serializable) {
            userDefaults.set(encoded, forKey: storageKey)
        }
    }
    
    public func loadFromDisk() {
        guard let data = userDefaults.data(forKey: storageKey),
              let decoded = try? JSONDecoder().decode([CartPersistedItem].self, from: data) else {
            return
        }
        var loaded: [String: (product: ProductDto, quantity: Int)] = [:]
        for item in decoded {
            loaded[item.product.sku] = (item.product, item.quantity)
        }
        self.items = loaded
    }
    
    @discardableResult
    public func reconcileWithCatalog(liveProducts: [ProductDto]) -> (removedCount: Int, priceChangedCount: Int) {
        var removedCount = 0
        var priceChangedCount = 0
        let liveMap = Dictionary(uniqueKeysWithValues: liveProducts.map { ($0.sku, $0) })
        
        for (sku, current) in items {
            if let liveProduct = liveMap[sku] {
                if !liveProduct.inStock {
                    items.removeValue(forKey: sku)
                    removedCount += 1
                } else if liveProduct.price != current.product.price {
                    items[sku] = (liveProduct, current.quantity)
                    priceChangedCount += 1
                }
            }
        }
        if removedCount > 0 || priceChangedCount > 0 {
            persistToDisk()
        }
        return (removedCount, priceChangedCount)
    }
}
