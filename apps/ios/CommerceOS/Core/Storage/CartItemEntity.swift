import Foundation

/**
 * 1:1 Parity with Android CartItemEntity.kt (70 lines).
 * Authoritative local persistence entity representing an item in the cart.
 */
public struct CartItemEntity: Codable, Identifiable, Equatable {
    public var id: String { sku }
    public let sku: String
    public let customerId: String
    public let productId: String
    public let name: String
    public let unitPrice: Double
    public var quantity: Int
    public let verticalId: String
    public let merchantId: String?
    public let mrp: Double
    public let brand: String?
    public let packSize: String?
    public let image: String?
    public let prescriptionRequired: Bool
    public let coldChain: Bool
    public let updatedAt: Int64

    public init(
        sku: String,
        customerId: String,
        productId: String,
        name: String,
        unitPrice: Double,
        quantity: Int,
        verticalId: String = "general",
        merchantId: String? = nil,
        mrp: Double? = nil,
        brand: String? = nil,
        packSize: String? = nil,
        image: String? = nil,
        prescriptionRequired: Bool = false,
        coldChain: Bool = false,
        updatedAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.sku = sku
        self.customerId = customerId
        self.productId = productId.isEmpty ? sku : productId
        self.name = name.isEmpty ? sku : name
        self.unitPrice = unitPrice
        self.quantity = max(1, quantity)
        self.verticalId = verticalId.isEmpty ? "general" : verticalId
        self.merchantId = merchantId
        self.mrp = mrp ?? unitPrice
        self.brand = brand
        self.packSize = packSize
        self.image = image
        self.prescriptionRequired = prescriptionRequired
        self.coldChain = coldChain
        self.updatedAt = updatedAt
    }

    public func toProductDto() -> ProductDto {
        return ProductDto(
            id: productId,
            sku: sku,
            name: name,
            brand: brand,
            mrp: mrp > 0 ? mrp : unitPrice,
            price: unitPrice,
            category: verticalId,
            saltComposition: nil,
            requiresPrescription: prescriptionRequired,
            isColdChain: coldChain,
            inStock: true,
            packSize: packSize,
            imageUrl: image
        )
    }

    public static func fromProductDto(product: ProductDto, customerId: String, quantity: Int = 1) -> CartItemEntity {
        return CartItemEntity(
            sku: product.sku,
            customerId: customerId,
            productId: product.id.isEmpty ? product.sku : product.id,
            name: product.name.isEmpty ? product.sku : product.name,
            unitPrice: product.price,
            quantity: max(1, quantity),
            verticalId: product.category.isEmpty ? "general" : product.category,
            merchantId: nil,
            mrp: product.mrp > 0 ? product.mrp : product.price,
            brand: product.brand,
            packSize: product.packSize,
            image: product.imageUrl,
            prescriptionRequired: product.requiresPrescription,
            coldChain: product.isColdChain ?? false,
            updatedAt: Int64(Date().timeIntervalSince1970 * 1000)
        )
    }
}
