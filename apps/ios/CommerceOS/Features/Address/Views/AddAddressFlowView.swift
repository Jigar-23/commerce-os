import SwiftUI
import MapKit

/**
 * 1:1 Parity with Android AddAddressFlow.kt
 * Zomato-grade 3-step address creation & editing flow:
 * Step 1: LocationSearchStepView (Search Places autocomplete & GPS acquisition)
 * Step 2: LocationMapStepView (Pin on map precision alignment & reverse geocode)
 * Step 3: AddressDetailsStepView (Structured address form with conflict detection)
 */
public struct AddAddressFlowView: View {
    @Environment(\.presentationMode) private var presentationMode
    @ObservedObject var viewModel: AddressViewModel = .shared

    let onSaveAddress: (AddressDto) -> Void

    public init(onSaveAddress: @escaping (AddressDto) -> Void = { _ in }) {
        self.onSaveAddress = onSaveAddress
    }

    private var state: AddressPlatformUiState {
        viewModel.platformUiState
    }

    private var currentForm: StructuredAddress {
        state.activeFormAddress
    }

    private var validationResult: AddressValidationResult {
        currentForm.validate()
    }

    private var conflictWarning: AddressConflictWarning? {
        state.addressConflict
    }

    private var isSaving: Bool {
        if case .saving = state.saveState { return true }
        return false
    }

    private func handleBackPress() {
        switch state.currentStep {
        case .editingDetails:
            if !state.isEditingExisting {
                // Stepping back from details form returns to pin on map
                viewModel.navigateBack()
            } else {
                viewModel.discardDraft()
                presentationMode.wrappedValue.dismiss()
            }
        case .locationSelected, .reverseGeocoding, .confirmingPin:
            viewModel.navigateBack()
        case .searchingLocation, .addressBook:
            viewModel.discardDraft()
            presentationMode.wrappedValue.dismiss()
        }
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Header Bar
            HStack(spacing: 12) {
                Button(action: handleBackPress) {
                    Image(systemName: "arrow.backward")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))
                        .padding(8)
                        .background(Color(hex: "F8FAFC"))
                        .clipShape(Circle())
                }

                Text(stepTitle)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))

                Spacer()

                // Step indicator pill
                Text(stepIndicator)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(Color(hex: "0E675E"))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Color(hex: "E6F4F1"))
                    .cornerRadius(6)
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 8)

            Divider()

            // Step Content with Smooth Transition
            ZStack {
                switch state.currentStep {
                case .searchingLocation, .addressBook:
                    LocationSearchStepView(
                        searchQuery: state.locationSearchQuery,
                        searchResults: state.placeSearchResults,
                        isSearching: state.isSearchingPlaces,
                        isLocatingGps: isLocatingGps,
                        gpsErrorMessage: state.locationErrorMessage,
                        onQueryChanged: { query in
                            viewModel.searchLocationPlaces(query)
                        },
                        onRequestGps: {
                            viewModel.requestCurrentGpsLocation()
                        },
                        onSelectResult: { result in
                            viewModel.selectPlaceSearchResult(result)
                        }
                    )
                    .transition(.asymmetric(insertion: .opacity, removal: .move(edge: .leading)))

                case .locationSelected, .reverseGeocoding, .confirmingPin:
                    LocationMapStepView(
                        formAddress: currentForm,
                        isGeocoding: state.isReverseGeocoding,
                        onMapCameraSettled: { lat, lng in
                            viewModel.onMapCameraSettled(lat: lat, lng: lng)
                        },
                        onRecenterGps: {
                            viewModel.requestCurrentGpsLocation()
                        },
                        onConfirmLocation: {
                            viewModel.confirmPinLocation()
                        }
                    )
                    .transition(.asymmetric(insertion: .move(edge: .trailing), removal: .move(edge: .leading)))

                case .editingDetails:
                    AddressDetailsStepView(
                        formAddress: currentForm,
                        conflictWarning: conflictWarning,
                        validationResult: validationResult,
                        isSaving: isSaving,
                        onFormAddressChanged: { updated in
                            viewModel.updateFormAddress(updated)
                        },
                        onApplyConflictSuggestion: {
                            viewModel.applyConflictSuggestion()
                        },
                        onSaveSubmitted: {
                            viewModel.submitSaveFormAddress()
                        }
                    )
                    .transition(.asymmetric(insertion: .move(edge: .trailing), removal: .opacity))
                }
            }
            .animation(.easeInOut(duration: 0.25), value: state.currentStep)
        }
        .background(Color(hex: "F8FAFC").ignoresSafeArea())
        .onReceive(viewModel.$platformUiState) { newState in
            if case .success(let saved) = newState.saveState {
                viewModel.resetSaveState()
                onSaveAddress(saved)
                presentationMode.wrappedValue.dismiss()
            }
        }
    }

    private var stepTitle: String {
        switch state.currentStep {
        case .searchingLocation, .addressBook:
            return "Search Location"
        case .locationSelected, .reverseGeocoding, .confirmingPin:
            return "Set Delivery Pin"
        case .editingDetails:
            return state.isEditingExisting ? "Edit Address Details" : "Enter Address Details"
        }
    }

    private var stepIndicator: String {
        switch state.currentStep {
        case .searchingLocation, .addressBook:
            return "Step 1 of 3"
        case .locationSelected, .reverseGeocoding, .confirmingPin:
            return "Step 2 of 3"
        case .editingDetails:
            return "Step 3 of 3"
        }
    }

    private var isLocatingGps: Bool {
        if case .acquiringGps = state.locationAcquisitionState { return true }
        return false
    }
}
