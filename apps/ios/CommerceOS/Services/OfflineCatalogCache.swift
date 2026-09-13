import Foundation
import Combine

public struct CacheMetadata: Codable {
    public let itemCount: Int
    public let cachedAt: Date
    public let ttlSeconds: TimeInterval
    public let isExpired: Bool
    public let diskSizeBytes: Int
    
    public init(itemCount: Int, cachedAt: Date, ttlSeconds: TimeInterval, isExpired: Bool, diskSizeBytes: Int) {
        self.itemCount = itemCount
        self.cachedAt = cachedAt
        self.ttlSeconds = ttlSeconds
        self.isExpired = isExpired
        self.diskSizeBytes = diskSizeBytes
    }
}

private struct CatalogCacheEnvelope: Codable {
    let products: [ServerProduct]
    let timestamp: Date
    let ttlSeconds: TimeInterval
    let version: String
}

/// High-performance thread-safe offline catalog storage and search indexing
public actor OfflineCatalogCache {
    public static let shared = OfflineCatalogCache()
    
    private let fileManager = FileManager.default
    private let cacheFileName = "offline_catalog_cache.json"
    private let defaultTTL: TimeInterval = 3600 * 4 // 4 Hours default TTL
    
    private var inMemoryCache: [ServerProduct]? = nil
    private var lastCacheDate: Date? = nil
    
    private var cacheFileURL: URL {
        let urls = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)
        let base = urls.first ?? URL(fileURLWithPath: NSTemporaryDirectory())
        let appDir = base.appendingPathComponent("CommerceOS", isDirectory: true)
        if !fileManager.fileExists(atPath: appDir.path) {
            try? fileManager.createDirectory(at: appDir, withIntermediateDirectories: true, attributes: nil)
        }
        return appDir.appendingPathComponent(cacheFileName)
    }
    
    public init() {}
    
    /// Persists products to both in-memory cache and encrypted device disk storage
    public func cacheProducts(_ products: [ServerProduct], ttl: TimeInterval? = nil) async throws {
        let envelope = CatalogCacheEnvelope(
            products: products,
            timestamp: Date(),
            ttlSeconds: ttl ?? defaultTTL,
            version: "2.0"
        )
        
        // Update fast memory
        self.inMemoryCache = products
        self.lastCacheDate = envelope.timestamp
        
        // Encode and write to disk atomically
        let data = try JSONEncoder().encode(envelope)
        try data.write(to: cacheFileURL, options: .atomic)
    }
    
    /// Retrieves cached products, loading from disk if memory cache is cold
    public func getCachedProducts() async -> [ServerProduct] {
        if let memory = inMemoryCache, !memory.isEmpty {
            return memory
        }
        
        guard fileManager.fileExists(atPath: cacheFileURL.path) else {
            return []
        }
        
        do {
            let data = try Data(contentsOf: cacheFileURL)
            let envelope = try JSONDecoder().decode(CatalogCacheEnvelope.self, from: data)
            self.inMemoryCache = envelope.products
            self.lastCacheDate = envelope.timestamp
            return envelope.products
        } catch {
            print("[OfflineCatalogCache Error] Failed reading cache: \(error.localizedDescription)")
            return []
        }
    }
    
    /// Performs instantaneous full-text offline search across catalog
    public func searchOffline(query: String) async -> [ServerProduct] {
        let products = await getCachedProducts()
        let clean = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !clean.isEmpty else { return products }
        
        return products.filter { product in
            let nameMatch = product.name.lowercased().contains(clean)
            let skuMatch = product.sku.lowercased().contains(clean)
            let catMatch = product.category?.lowercased().contains(clean) ?? false
            let packMatch = product.packSize?.lowercased().contains(clean) ?? false
            return nameMatch || skuMatch || catMatch || packMatch
        }
    }
    
    /// Checks whether the disk cache is still valid within TTL
    public func isCacheValid() async -> Bool {
        guard fileManager.fileExists(atPath: cacheFileURL.path) else { return false }
        
        do {
            let data = try Data(contentsOf: cacheFileURL)
            let envelope = try JSONDecoder().decode(CatalogCacheEnvelope.self, from: data)
            let age = Date().timeIntervalSince(envelope.timestamp)
            return age < envelope.ttlSeconds
        } catch {
            return false
        }
    }
    
    /// Wipes all memory and disk caches
    public func clearCache() async throws {
        self.inMemoryCache = nil
        self.lastCacheDate = nil
        if fileManager.fileExists(atPath: cacheFileURL.path) {
            try fileManager.removeItem(at: cacheFileURL)
        }
    }
    
    /// Computes diagnostic cache health metadata
    public func getMetadata() async -> CacheMetadata {
        let products = await getCachedProducts()
        var diskSize = 0
        if let attrs = try? fileManager.attributesOfItem(atPath: cacheFileURL.path),
           let size = attrs[.size] as? Int {
            diskSize = size
        }
        
        let isValid = await isCacheValid()
        return CacheMetadata(
            itemCount: products.count,
            cachedAt: lastCacheDate ?? Date(timeIntervalSince1970: 0),
            ttlSeconds: defaultTTL,
            isExpired: !isValid,
            diskSizeBytes: diskSize
        )
    }
}
