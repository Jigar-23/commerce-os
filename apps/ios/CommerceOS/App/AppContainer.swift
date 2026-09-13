import Foundation
import Combine

@MainActor
public final class AppContainer: ObservableObject {
    public static let shared = AppContainer()
    
    let apiClient: APIClient
    let configProvider: ClientConfigProvider
    let catalogRepository: CatalogRepository
    let orderRepository: OrderRepository
    let trackingRepository: TrackingRepository
    let cartStore: CartLocalStore
    let biometricService: BiometricPasskeyService
    let offlineCache: OfflineCatalogCache
    
    public var customerSession: CustomerSessionDto? {
        guard let id = UserDefaults.standard.string(forKey: "customer_id") else { return nil }
        return CustomerSessionDto(
            customerId: id,
            phone: UserDefaults.standard.string(forKey: "customer_phone") ?? "",
            accessToken: UserDefaults.standard.string(forKey: "auth_token") ?? "",
            refreshToken: nil,
            name: UserDefaults.standard.string(forKey: "customer_name")
        )
    }
    
    init(
        apiClient: APIClient = APIClient.shared,
        configProvider: ClientConfigProvider = ClientConfigProvider.shared,
        catalogRepository: CatalogRepository = CatalogRepository.shared,
        orderRepository: OrderRepository = OrderRepository.shared,
        trackingRepository: TrackingRepository = TrackingRepository.shared,
        cartStore: CartLocalStore = CartLocalStore.shared,
        biometricService: BiometricPasskeyService = BiometricPasskeyService.shared,
        offlineCache: OfflineCatalogCache = OfflineCatalogCache.shared
    ) {
        self.apiClient = apiClient
        self.configProvider = configProvider
        self.catalogRepository = catalogRepository
        self.orderRepository = orderRepository
        self.trackingRepository = trackingRepository
        self.cartStore = cartStore
        self.biometricService = biometricService
        self.offlineCache = offlineCache
    }
}
