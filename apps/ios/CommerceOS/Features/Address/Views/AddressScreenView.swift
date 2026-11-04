import SwiftUI

/**
 * 1:1 Parity with Android AddressScreen.kt
 * Top-Level Production Address Screen Orchestrator.
 * Driven by AddressViewModel as the Single Source of Truth for state machine steps & address mutations.
 * Supports direct single-click back exit when entered directly from bottom sheet or home screen.
 */
public struct AddressScreenView: View {
    @ObservedObject var viewModel: AddressViewModel = .shared
    public var serviceability: ServiceabilityState = .idle
    public let onSelectAddress: (AddressDto) -> Void
    public var onSavedSuccessfully: (() -> Void)? = nil
    public var onBack: (() -> Void)? = nil
    public var fromProfile: Bool = false
    public var directAddFlow: Bool = false

    public init(
        viewModel: AddressViewModel = .shared,
        serviceability: ServiceabilityState = .idle,
        onSelectAddress: @escaping (AddressDto) -> Void,
        onSavedSuccessfully: (() -> Void)? = nil,
        onBack: (() -> Void)? = nil,
        fromProfile: Bool = false,
        directAddFlow: Bool = false
    ) {
        self.viewModel = viewModel
        self.serviceability = serviceability
        self.onSelectAddress = onSelectAddress
        self.onSavedSuccessfully = onSavedSuccessfully
        self.onBack = onBack
        self.fromProfile = fromProfile
        self.directAddFlow = directAddFlow
    }

    private var state: AddressPlatformUiState {
        viewModel.platformUiState
    }

    private func handleBackPress() {
        if case .editingDetails = state.currentStep, !state.isEditingExisting {
            // Stepping back from details form returns to pin on map
            viewModel.navigateBack()
        } else {
            // Direct single-click exit from Add Address / Pin on Map back to parent screen
            viewModel.discardDraft()
            onBack?()
        }
    }

    public var body: some View {
        ZStack {
            if state.isFlowActive || directAddFlow {
                AddAddressFlowView(onSaveAddress: { saved in
                    onSavedSuccessfully?()
                    onSelectAddress(saved)
                })
            } else {
                VStack(spacing: 0) {
                    AddressBookView(
                        viewModel: viewModel,
                        customerId: "current_user",
                        onSelectAddress: { addr in
                            viewModel.select(addr)
                            onSelectAddress(addr)
                        },
                        onAddNewAddress: {
                            viewModel.startAddAddressFlow()
                        },
                        onBack: onBack,
                        fromProfile: fromProfile
                    )

                    // Enterprise Multi-Vertical Serviceability Card
                    if !fromProfile {
                        AddressServiceabilityCard(
                            serviceabilityState: serviceability,
                            selectedAddressId: viewModel.selectedAddress?.id
                        )
                        .padding(.horizontal, 16)
                        .padding(.bottom, 8)
                    }
                }
            }
        }
        .navigationBarHidden(true)
    }
}
