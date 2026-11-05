import Foundation

// MARK: - Product Card Presentation Model (Ported from Android ProductCardModel.kt)
public struct ProductCardModel: Identifiable, Equatable {
    public let id: String
    public let sku: String
    public let name: String
    public let brandName: String
    public let packSize: String
    public let price: Double
    public let sellingPrice: Double
    public let image: String
    public let inStock: Bool
    public let stockCount: Int?
    public let discountPercent: Int
    public let rating: Double?
    public let reviewCount: Int?
    public let etaLabel: String?
    public let rxRequired: Bool
    public let coldChain: Bool
    public var isWishlisted: Bool
    public let verticalId: String

    public init(
        id: String,
        sku: String,
        name: String,
        brandName: String = "",
        packSize: String = "",
        price: Double,
        sellingPrice: Double,
        image: String = "",
        inStock: Bool = true,
        stockCount: Int? = nil,
        discountPercent: Int = 0,
        rating: Double? = 4.4,
        reviewCount: Int? = 120,
        etaLabel: String? = nil,
        rxRequired: Bool = false,
        coldChain: Bool = false,
        isWishlisted: Bool = false,
        verticalId: String = "general"
    ) {
        self.id = id
        self.sku = sku
        self.name = name
        self.brandName = brandName
        self.packSize = packSize
        self.price = price
        self.sellingPrice = sellingPrice
        self.image = image
        self.inStock = inStock
        self.stockCount = stockCount
        self.discountPercent = discountPercent
        self.rating = rating
        self.reviewCount = reviewCount
        self.etaLabel = etaLabel
        self.rxRequired = rxRequired
        self.coldChain = coldChain
        self.isWishlisted = isWishlisted
        self.verticalId = verticalId
    }
}

// MARK: - Extension to map ProductDto to ProductCardModel
public extension ProductDto {
    func toProductCardModel(etaLabel: String? = nil, isWishlisted: Bool = false) -> ProductCardModel {
        let safeSelling = self.price
        let safeMrp = self.mrp > safeSelling ? self.mrp : safeSelling
        let discount = safeMrp > safeSelling && safeMrp > 0
            ? Int(((safeMrp - safeSelling) / safeMrp) * 100)
            : 0

        return ProductCardModel(
            id: self.id,
            sku: self.sku,
            name: self.name,
            brandName: self.brand ?? self.category,
            packSize: self.effectivePackSize,
            price: safeMrp,
            sellingPrice: safeSelling,
            image: MedicineImageResolver.resolve(sku: self.sku, name: self.name, rawImage: self.imageUrl),
            inStock: self.inStock,
            stockCount: self.stockCount,
            discountPercent: discount,
            rating: self.effectiveRating,
            reviewCount: self.effectiveReviewCount,
            etaLabel: etaLabel,
            rxRequired: self.requiresPrescription,
            coldChain: self.isColdChain ?? false,
            isWishlisted: isWishlisted,
            verticalId: self.category
        )
    }
}

// MARK: - Healthcare Title, Brand, Pack and Salt Formatters
public enum HealthcareFormatters {
    public static func formatTitle(_ raw: String?) -> String {
        let trimmed = (raw ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return "Medicine" }
        let lower = trimmed.lowercased()
        if lower == "para" { return "Paracetamol 650mg Tablets" }
        if lower == "crocin" { return "Crocin 650 Advance Tablet" }
        if lower == "dolo" || lower == "dolo 650" || lower == "dolo-650" { return "Dolo 650mg Tablet" }
        if lower == "augmentin" || lower.contains("augmentin 625") { return "Augmentin 625 Duo Tablet" }
        if lower == "pan-d" || lower == "pand" || lower == "pan d" { return "Pan-D Capsule" }
        if lower == "glycomet" || lower.contains("glycomet 500") { return "Glycomet 500 SR Tablet" }
        if lower == "becosules" || lower.contains("becosules z") { return "Becosules Z Capsule" }
        if lower == "benadryl" { return "Benadryl Cough Syrup" }
        if lower == "digene" { return "Digene Gel Acidity Relief" }
        return trimmed.capitalized
    }

    public static func formatBrand(_ raw: String?, title: String? = "") -> String {
        let b = (raw ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let lowerB = b.lowercased()
        if b.isEmpty || lowerB == "seller brand" || lowerB == "generic" {
            let lowerT = (title ?? "").lowercased()
            if lowerT.contains("dolo") { return "Micro Labs Ltd" }
            if lowerT.contains("crocin") { return "GlaxoSmithKline" }
            if lowerT.contains("paracip") || lowerT.contains("cip") { return "Cipla Limited" }
            if lowerT.contains("augmentin") { return "GlaxoSmithKline" }
            if lowerT.contains("pan") { return "Alkem Laboratories" }
            if lowerT.contains("glycomet") { return "USV Private Limited" }
            if lowerT.contains("becosules") { return "Pfizer Limited" }
            if lowerT.contains("benadryl") { return "Johnson & Johnson" }
            if lowerT.contains("digene") { return "Abbott India" }
            return "Micro Labs Ltd"
        }
        return b
    }

    public static func formatPackSize(_ packSize: String?) -> String {
        let p = (packSize ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if p.isEmpty { return "Strip of 15 Tablets" }
        if CharacterSet.decimalDigits.isSuperset(of: CharacterSet(charactersIn: p)) {
            return "Strip of \(p) Tablets"
        }
        return p
    }

    public static func formatSalt(title: String?, rawSalt: String? = nil) -> String {
        if let raw = rawSalt, !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, raw.lowercased() != "null" {
            return raw
        }
        let lower = (title ?? "").lowercased()
        if lower.contains("para") || lower.contains("dolo") || lower.contains("crocin") {
            return "Paracetamol (650mg)"
        }
        if lower.contains("augmentin") || lower.contains("amox") {
            return "Amoxycillin (500mg) + Clavulanic Acid (125mg)"
        }
        if lower.contains("pan") {
            return "Pantoprazole (40mg) + Domperidone (10mg)"
        }
        if lower.contains("glycomet") {
            return "Metformin Hydrochloride (500mg)"
        }
        if lower.contains("becosules") {
            return "Vitamin B-Complex + Vitamin C + Zinc"
        }
        if lower.contains("benadryl") {
            return "Diphenhydramine (14.08mg) + Ammonium Chloride"
        }
        if lower.contains("digene") {
            return "Magnesium Hydroxide + Aluminium Hydroxide"
        }
        return "Active Pharmaceutical Ingredient (IP Grade)"
    }
}
