import Foundation
import Combine

@MainActor
public final class AppContainer: ObservableObject {
    public static let shared = AppContainer()
    
    public let apiClient: APIClient
    let configProvider: ClientConfigProvider
    public let catalogRepository: CatalogRepository
    public let orderRepository: OrderRepository
    public let trackingRepository: TrackingRepository
    public let cartStore: CartLocalStore
    public let addressRepository: AddressRepository
    public let biometricService: BiometricPasskeyService
    public let offlineCache: OfflineCatalogCache
    
    @Published public var isAuthenticated: Bool = false
    @Published public var customerSession: CustomerSessionDto? = nil
    
    init(
        apiClient: APIClient = APIClient.shared,
        configProvider: ClientConfigProvider = ClientConfigProvider.shared,
        catalogRepository: CatalogRepository = CatalogRepository.shared,
        orderRepository: OrderRepository = OrderRepository.shared,
        trackingRepository: TrackingRepository = TrackingRepository.shared,
        cartStore: CartLocalStore = CartLocalStore.shared,
        addressRepository: AddressRepository = AddressRepository.shared,
        biometricService: BiometricPasskeyService = BiometricPasskeyService.shared,
        offlineCache: OfflineCatalogCache = OfflineCatalogCache.shared
    ) {
        self.apiClient = apiClient
        self.configProvider = configProvider
        self.catalogRepository = catalogRepository
        self.orderRepository = orderRepository
        self.trackingRepository = trackingRepository
        self.cartStore = cartStore
        self.addressRepository = addressRepository
        self.biometricService = biometricService
        self.offlineCache = offlineCache
        
        self.loadSession()
        
        NotificationCenter.default.addObserver(
            forName: APIClient.sessionExpiredNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.logout()
        }
    }
    
    public func loadSession() {
        // Securely retrieve authentication token from Keychain only (never UserDefaults)
        let token = KeychainHelper.shared.get(key: "auth_token")
        // Clean up legacy insecure storage if present
        UserDefaults.standard.removeObject(forKey: "auth_token")

        if let id = UserDefaults.standard.string(forKey: "customer_id"),
           let validToken = token,
           let phone = UserDefaults.standard.string(forKey: "customer_phone"),
           !validToken.isEmpty && !phone.isEmpty {
            let name = UserDefaults.standard.string(forKey: "customer_name")
            self.customerSession = CustomerSessionDto(
                customerId: id,
                phone: phone,
                accessToken: validToken,
                refreshToken: nil,
                name: name
            )
            self.isAuthenticated = true
            self.apiClient.setAuth(token: validToken, customerId: id)
            AddressViewModel.shared.initialize(customerId: id)
            Task {
                await self.addressRepository.loadAddresses(customerId: id)
            }
        } else {
            self.customerSession = nil
            self.isAuthenticated = false
            self.apiClient.clearAuth()
            AddressViewModel.shared.reset()
        }
    }
    
    public func login(customerId: String, phone: String, name: String? = nil, token: String) {
        UserDefaults.standard.set(customerId, forKey: "customer_id")
        UserDefaults.standard.set(phone, forKey: "customer_phone")
        UserDefaults.standard.set(phone, forKey: "last_login_phone")
        // Store auth token strictly in secure Keychain
        KeychainHelper.shared.save(key: "auth_token", data: token)
        UserDefaults.standard.removeObject(forKey: "auth_token")

        if let name = name {
            UserDefaults.standard.set(name, forKey: "customer_name")
        } else {
            UserDefaults.standard.removeObject(forKey: "customer_name")
        }
        
        apiClient.setAuth(token: token, customerId: customerId)
        CustomerPushNotificationManager.shared.syncPendingToken()
        AddressViewModel.shared.initialize(customerId: customerId)
        self.customerSession = CustomerSessionDto(
            customerId: customerId,
            phone: phone,
            accessToken: token,
            refreshToken: nil,
            name: name
        )
        self.isAuthenticated = true
        Task {
            await self.addressRepository.loadAddresses(customerId: customerId)
        }
    }
    
    public func logout() {
        let currentAuthToken = apiClient.authToken ?? KeychainHelper.shared.get(key: "auth_token")
        Task {
            await CustomerPushNotificationManager.shared.unregisterTokenWithBackend(authToken: currentAuthToken)
        }
        UserDefaults.standard.removeObject(forKey: "customer_id")
        UserDefaults.standard.removeObject(forKey: "customer_phone")
        UserDefaults.standard.removeObject(forKey: "auth_token")
        UserDefaults.standard.removeObject(forKey: "customer_name")
        KeychainHelper.shared.delete(key: "auth_token")
        apiClient.clearAuth()
        AddressViewModel.shared.reset()
        self.customerSession = nil
        self.isAuthenticated = false
    }

    public func logoutAsync() async {
        let currentAuthToken = apiClient.authToken ?? KeychainHelper.shared.get(key: "auth_token")
        await CustomerPushNotificationManager.shared.unregisterTokenWithBackend(authToken: currentAuthToken)
        UserDefaults.standard.removeObject(forKey: "customer_id")
        UserDefaults.standard.removeObject(forKey: "customer_phone")
        UserDefaults.standard.removeObject(forKey: "auth_token")
        UserDefaults.standard.removeObject(forKey: "customer_name")
        KeychainHelper.shared.delete(key: "auth_token")
        apiClient.clearAuth()
        AddressViewModel.shared.reset()
        self.customerSession = nil
        self.isAuthenticated = false
    }
}
