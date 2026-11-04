import SwiftUI

/**
 * 1:1 Parity with Android CommerceOSApp.kt Navigation Architecture.
 * Central Orchestrator for Commerce OS Customer Application on iOS.
 * 
 * Features:
 * - 4 Primary Bottom Navigation Tabs: Home, Categories, Orders, Account (Material 3 styling).
 * - Full-Screen Navigation Stack (No modal popup sheets for Notifications, Search, Orders, Prescriptions, Cart, Tracking, or Addresses).
 * - Floating GlobalCartBar synced across all primary screens with animated entrance/exit.
 * - Live Category Taxonomy & Cart synchronization.
 */
public struct MainTabView: View {
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var cartStore: CartLocalStore
    @EnvironmentObject private var configProvider: ClientConfigProvider
    @EnvironmentObject private var addressRepository: AddressRepository
    @ObservedObject private var navigator = AppNavigator.shared
    
    public init() {}
    
    public var body: some View {
        ZStack {
            if let currentScreen = navigator.currentScreen {
                // MARK: - Full Page Navigation Destinations (No Bottom Sheets)
                Group {
                    switch currentScreen {
                    case .notifications:
                        NotificationsScreen(
                            onBack: { navigator.pop() },
                            onNavigateToOrders: {
                                navigator.switchTab(to: 2)
                            },
                            onNavigateToPrescriptions: {
                                navigator.pop()
                                navigator.push(.prescriptions)
                            }
                        )
                    case .search(let query):
                        UniversalSearchScreen(
                            initialQuery: query,
                            onBack: { navigator.pop() }
                        )
                    case .catalog(let query):
                        CatalogScreen(
                            onSelectProduct: { prod in
                                navigator.push(.productDetail(product: prod))
                            },
                            onAddToCart: { prod in
                                cartStore.add(product: prod)
                            },
                            onBack: { navigator.pop() }
                        )
                        .onAppear {
                            if !query.isEmpty {
                                CatalogViewModel.shared.onQueryChange(query)
                                CatalogViewModel.shared.submitQuery()
                            }
                        }
                    case .productDetail(let prod):
                        ProductDetailSheet(
                            product: prod,
                            onDismiss: { navigator.pop() }
                        )
                    case .cart:
                        CartScreen(
                            onCheckoutSuccess: { orderId in
                                navigator.pop()
                                navigator.push(.orderTracking(orderId: orderId))
                            },
                            onBack: { navigator.pop() }
                        )
                    case .orderTracking(let orderId):
                        OrderTrackingScreen(
                            orderId: orderId,
                            onBack: { navigator.pop() }
                        )
                    case .prescriptions:
                        PrescriptionVaultScreen(
                            onBack: { navigator.pop() }
                        )
                    case .addressSelection(let fromCheckout, let fromProfile):
                        AddressScreenView(
                            onSelectAddress: { addr in
                                addressRepository.selectAddress(addr)
                                navigator.pop()
                                if fromCheckout {
                                    navigator.push(.paymentGateway)
                                }
                            },
                            onSavedSuccessfully: {
                                navigator.pop()
                                if fromCheckout {
                                    navigator.push(.paymentGateway)
                                }
                            },
                            onBack: { navigator.pop() },
                            fromProfile: fromProfile,
                            directAddFlow: false
                        )
                    case .addAddressFlow:
                        AddAddressFlowView(
                            onSaveAddress: { addr in
                                addressRepository.selectAddress(addr)
                                navigator.pop()
                                navigator.push(.paymentGateway)
                            }
                        )
                    case .paymentGateway:
                        PaymentScreen(
                            address: addressRepository.selectedAddress,
                            onAuthorize: { orderId in
                                navigator.pop() // pop payment
                                navigator.pop() // pop cart
                                navigator.push(.orderTracking(orderId: orderId))
                            },
                            onChangeAddress: {
                                navigator.push(.addressSelection(fromCheckout: true, fromProfile: false))
                            },
                            onBack: { navigator.pop() }
                        )
                    }
                }
                .transition(.asymmetric(insertion: .move(edge: .trailing), removal: .move(edge: .trailing)))
            } else {
                // MARK: - 4 Primary Bottom Tabs (Home, Categories, Orders, Account)
                VStack(spacing: 0) {
                    ZStack {
                        switch navigator.selectedTab {
                        case 0:
                            HomeScreen(onOpenCatalog: { navigator.switchTab(to: 1) })
                        case 1:
                            CategoriesScreen(
                                selectedAddress: addressRepository.selectedAddress,
                                calculatedEtaMinutes: addressRepository.calculatedEtaMinutes,
                                locationHeaderLabel: addressRepository.locationHeaderTitle,
                                isLocating: addressRepository.isLoading,
                                onChangeAddress: {
                                    navigator.push(.addressSelection(fromCheckout: false, fromProfile: false))
                                },
                                onProfileClick: {
                                    navigator.switchTab(to: 3)
                                },
                                onNotificationsClick: {
                                    navigator.push(.notifications)
                                },
                                onSearchClick: { query in
                                    navigator.push(.search(query: query))
                                },
                                onUploadPrescription: {
                                    navigator.push(.prescriptions)
                                },
                                onSelectCategory: { category in
                                    navigator.push(.search(query: category.name))
                                }
                            )
                        case 2:
                            OrderHistoryScreen(onTrackOrder: { id in
                                navigator.push(.orderTracking(orderId: id))
                            })
                        case 3:
                            AccountScreen()
                        default:
                            HomeScreen(onOpenCatalog: { navigator.switchTab(to: 1) })
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    
                    // Global Sticky Cart Bar floating directly above bottom navigation
                    if cartStore.totalItemCount > 0 {
                        GlobalCartBar(onTap: {
                            navigator.push(.cart)
                        })
                        .padding(.horizontal, 14)
                        .padding(.bottom, 8)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                        .animation(.spring(response: 0.35, dampingFraction: 0.8), value: cartStore.totalItemCount)
                    }
                    
                    // Android-style Material 3 Bottom Navigation Bar
                    androidBottomNavBar
                }
                .background(Color(hex: "F4F5F7").ignoresSafeArea())
            }
        }
        .animation(.easeInOut(duration: 0.22), value: navigator.screenStack)
        .onChange(of: navigator.selectedTab) { _, newTab in
            if newTab == 2 {
                Task {
                    await OrderRepository.shared.fetchCustomerOrders()
                }
            }
        }
    }

    // MARK: - Android Material 3 Bottom Navigation Bar (1:1 with Android CommerceOSApp.kt lines 445-472)
    private var androidBottomNavBar: some View {
        HStack(spacing: 0) {
            navBarItem(
                index: 0,
                icon: "house.fill",
                label: "Home"
            )
            navBarItem(
                index: 1,
                icon: "square.grid.2x2.fill",
                label: "Categories"
            )
            navBarItem(
                index: 2,
                icon: "list.bullet.rectangle.portrait.fill",
                label: configProvider.currentConfig.terminology.orderLabel
            )
            navBarItem(
                index: 3,
                icon: "person.fill",
                label: "Account"
            )
        }
        .frame(maxWidth: .infinity)
        .frame(height: 60)
        .background(
            Color.white
                .shadow(color: Color.black.opacity(0.05), radius: 6, x: 0, y: -2)
                .ignoresSafeArea(edges: .bottom)
        )
        .overlay(
            Rectangle()
                .fill(Color(hex: "E2E8F0"))
                .frame(height: 1),
            alignment: .top
        )
    }

    private func navBarItem(index: Int, icon: String, label: String) -> some View {
        let isSelected = navigator.selectedTab == index

        return Button(action: {
            navigator.switchTab(to: index)
        }) {
            VStack(spacing: 3) {
                ZStack {
                    if isSelected {
                        Capsule()
                            .fill(Color(hex: "DCFCE7"))
                            .frame(width: 58, height: 28)
                            .transition(.scale.combined(with: .opacity))
                    }

                    Image(systemName: icon)
                        .font(.system(size: 17, weight: isSelected ? .bold : .regular))
                        .foregroundColor(isSelected ? Color(hex: "059669") : Color(hex: "64748B"))
                        .frame(width: 22, height: 22)
                }
                .frame(height: 28)

                Text(label)
                    .font(.system(size: 11, weight: isSelected ? .bold : .medium))
                    .foregroundColor(isSelected ? Color(hex: "059669") : Color(hex: "64748B"))
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }
}
