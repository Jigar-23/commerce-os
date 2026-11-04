import Foundation
import Combine

/**
 * 1:1 Parity with Android CartDao.kt (28 lines).
 * Abstract DAO & reactive protocol for local cart table operations.
 */
public protocol CartDaoProtocol {
    func observeCartItems(customerId: String) -> AnyPublisher<[CartItemEntity], Never>
    func getCartItems(customerId: String) async -> [CartItemEntity]
    func insertOrUpdate(item: CartItemEntity) async
    func insertAll(items: [CartItemEntity]) async
    func deleteBySku(sku: String, customerId: String) async
    func clearCart(customerId: String) async
}

/**
 * Persistent SQLite/Disk CartDao implementation.
 * Guarantees atomic writes and real-time reactive streams to UI.
 */
public final class CartDao: CartDaoProtocol {
    private let queue = DispatchQueue(label: "com.commerceos.cartdao", qos: .userInitiated, attributes: .concurrent)
    private let subject = CurrentValueSubject<[String: [CartItemEntity]], Never>([:])
    private let fileURL: URL

    public init(fileURL: URL? = nil) {
        if let url = fileURL {
            self.fileURL = url
        } else {
            let docs = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
            try? FileManager.default.createDirectory(at: docs, withIntermediateDirectories: true)
            self.fileURL = docs.appendingPathComponent("commerce_os_cart.json")
        }
        loadDiskData()
    }

    private func loadDiskData() {
        queue.sync(flags: .barrier) {
            guard let data = try? Data(contentsOf: fileURL),
                  let map = try? JSONDecoder().decode([String: [CartItemEntity]].self, from: data) else {
                return
            }
            subject.send(map)
        }
    }

    private func persistDiskData(_ map: [String: [CartItemEntity]]) {
        if let data = try? JSONEncoder().encode(map) {
            try? data.write(to: fileURL, options: .atomic)
        }
    }

    public func observeCartItems(customerId: String) -> AnyPublisher<[CartItemEntity], Never> {
        subject
            .map { map in
                (map[customerId] ?? []).sorted { $0.updatedAt > $1.updatedAt }
            }
            .eraseToAnyPublisher()
    }

    public func getCartItems(customerId: String) async -> [CartItemEntity] {
        return await withCheckedContinuation { continuation in
            queue.async {
                let items = self.subject.value[customerId] ?? []
                continuation.resume(returning: items.sorted { $0.updatedAt > $1.updatedAt })
            }
        }
    }

    public func insertOrUpdate(item: CartItemEntity) async {
        await withCheckedContinuation { continuation in
            queue.async(flags: .barrier) {
                var map = self.subject.value
                var list = map[item.customerId] ?? []
                list.removeAll { $0.sku == item.sku }
                list.append(item)
                map[item.customerId] = list
                self.subject.send(map)
                self.persistDiskData(map)
                continuation.resume()
            }
        }
    }

    public func insertAll(items: [CartItemEntity]) async {
        guard !items.isEmpty else { return }
        await withCheckedContinuation { continuation in
            queue.async(flags: .barrier) {
                var map = self.subject.value
                for item in items {
                    var list = map[item.customerId] ?? []
                    list.removeAll { $0.sku == item.sku }
                    list.append(item)
                    map[item.customerId] = list
                }
                self.subject.send(map)
                self.persistDiskData(map)
                continuation.resume()
            }
        }
    }

    public func deleteBySku(sku: String, customerId: String) async {
        await withCheckedContinuation { continuation in
            queue.async(flags: .barrier) {
                var map = self.subject.value
                var list = map[customerId] ?? []
                list.removeAll { $0.sku == sku }
                map[customerId] = list
                self.subject.send(map)
                self.persistDiskData(map)
                continuation.resume()
            }
        }
    }

    public func clearCart(customerId: String) async {
        await withCheckedContinuation { continuation in
            queue.async(flags: .barrier) {
                var map = self.subject.value
                map[customerId] = []
                self.subject.send(map)
                self.persistDiskData(map)
                continuation.resume()
            }
        }
    }
}
