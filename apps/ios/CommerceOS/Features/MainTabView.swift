import SwiftUI

public struct MainTabView: View {
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var cartStore: CartLocalStore
    @EnvironmentObject private var configProvider: ClientConfigProvider
    @State private var selectedTab: Int = 0
    @State private var trackingOrderId: String? = nil
    
    public init() {}
    
    public var body: some View {
        VStack(spacing: 0) {
            // Main Content Area
            ZStack {
                switch selectedTab {
                case 0:
                    HomeScreen(onOpenCatalog: { selectedTab = 1 })
                case 1:
                    CategoriesScreen()
                case 2:
                    OrderHistoryScreen()
                case 3:
                    CartScreen(onCheckoutSuccess: { orderId in
                        trackingOrderId = orderId
                    })
                default:
                    HomeScreen(onOpenCatalog: { selectedTab = 1 })
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            
            // Global Sticky Cart Bar floating directly above bottom navigation
            if cartStore.totalItemCount > 0 && selectedTab != 3 {
                GlobalCartBar(onTap: {
                    selectedTab = 3
                })
                .padding(.horizontal, 14)
                .padding(.bottom, 8)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .animation(.spring(response: 0.35, dampingFraction: 0.8), value: cartStore.totalItemCount)
            }
            
            // Android-style Material 3 Bottom Navigation Bar pinned strictly to the bottom
            androidBottomNavBar
        }
        .background(Color(hex: "F4F5F7").ignoresSafeArea())
        .sheet(item: Binding<IdentifiableOrderWrapper?>(
            get: { trackingOrderId.map { IdentifiableOrderWrapper(value: $0) } },
            set: { trackingOrderId = $0?.value }
        )) { wrapper in
            NavigationView {
                OrderTrackingScreen(orderId: wrapper.value)
                    .navigationTitle("Order Status")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarLeading) {
                            Button("Done") {
                                trackingOrderId = nil
                                selectedTab = 2
                            }
                        }
                    }
            }
            .navigationViewStyle(.stack)
        }
    }

    // MARK: - Android Material 3 Bottom Navigation Bar (Matches Android NavigationBar verbatim)
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
                icon: "cart.fill",
                label: configProvider.currentConfig.terminology.cartLabel,
                badgeCount: cartStore.totalItemCount
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

    private func navBarItem(index: Int, icon: String, label: String, badgeCount: Int = 0) -> some View {
        let isSelected = selectedTab == index

        return Button(action: {
            withAnimation(.easeInOut(duration: 0.16)) {
                selectedTab = index
            }
        }) {
            VStack(spacing: 3) {
                ZStack {
                    if isSelected {
                        Capsule()
                            .fill(Color(hex: "DCFCE7"))
                            .frame(width: 58, height: 28)
                            .transition(.scale.combined(with: .opacity))
                    }

                    ZStack(alignment: .topTrailing) {
                        Image(systemName: icon)
                            .font(.system(size: 17, weight: isSelected ? .bold : .regular))
                            .foregroundColor(isSelected ? Color(hex: "059669") : Color(hex: "64748B"))
                            .frame(width: 22, height: 22)

                        if badgeCount > 0 {
                            Text(badgeCount > 99 ? "99+" : "\(badgeCount)")
                                .font(.system(size: 10, weight: .black))
                                .foregroundColor(.white)
                                .padding(.horizontal, 4)
                                .padding(.vertical, 1)
                                .background(Color(hex: "059669"))
                                .clipShape(Capsule())
                                .offset(x: 14, y: -6)
                        }
                    }
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
