import Foundation

/**
 * 1:1 Parity with Android GeneralizedVerticalEngine.kt (88 lines).
 * Definition of a custom domain vertical registered at runtime.
 */
public struct CustomVerticalDefinition: Codable, Identifiable, Equatable {
    public var id: String { verticalId }
    public let verticalId: String
    public let verticalName: String
    public let iconUrl: String?
    public let visualSymbol: String
    public let displayOrder: Int
    public let defaultCta: String
    public let catalogHeader: String

    public init(
        verticalId: String,
        verticalName: String,
        iconUrl: String? = nil,
        visualSymbol: String = "✨",
        displayOrder: Int = 10,
        defaultCta: String? = nil,
        catalogHeader: String? = nil
    ) {
        self.verticalId = verticalId
        self.verticalName = verticalName
        self.iconUrl = iconUrl
        self.visualSymbol = visualSymbol
        self.displayOrder = displayOrder
        self.defaultCta = defaultCta ?? "Explore \(verticalName)"
        self.catalogHeader = catalogHeader ?? "\(verticalName) Catalog"
    }
}

/**
 * Generalized Vertical Engine enabling white-label client apps to register,
 * resolve, and render arbitrary domain verticals dynamically.
 */
public final class GeneralizedVerticalEngine {
    public static let shared = GeneralizedVerticalEngine()

    private var registeredVerticals: [String: CustomVerticalDefinition] = [:]

    private init() {}

    public func registerVertical(definition: CustomVerticalDefinition) {
        registeredVerticals[definition.verticalId.lowercased()] = definition
    }

    public func getVertical(verticalId: String) -> CustomVerticalDefinition? {
        return registeredVerticals[verticalId.lowercased()]
    }

    public func resolveActiveVerticals() -> [CustomVerticalDefinition] {
        let builtIn: [CustomVerticalDefinition] = [
            CustomVerticalDefinition(verticalId: "general", verticalName: "All Products", visualSymbol: "🛍️", displayOrder: 1),
            CustomVerticalDefinition(verticalId: "food", verticalName: "Food & Dining", visualSymbol: "🍕", displayOrder: 2),
            CustomVerticalDefinition(verticalId: "fashion", verticalName: "Apparel & Style", visualSymbol: "👗", displayOrder: 3),
            CustomVerticalDefinition(verticalId: "electronics", verticalName: "Tech & Devices", visualSymbol: "⚡", displayOrder: 4),
            CustomVerticalDefinition(verticalId: "pharmacy", verticalName: "Medicines & Rx", visualSymbol: "💊", displayOrder: 5),
            CustomVerticalDefinition(verticalId: "services", verticalName: "Home Services", visualSymbol: "🛠️", displayOrder: 6)
        ]

        let customList = Array(registeredVerticals.values)
        return (builtIn + customList).sorted(by: { $0.displayOrder < $1.displayOrder })
    }
}
