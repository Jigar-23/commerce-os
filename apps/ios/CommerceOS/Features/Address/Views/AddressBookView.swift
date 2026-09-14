import SwiftUI

/**
 * 1:1 Parity with Android AddressBookContent.kt
 * Saved Address Book View component.
 * Displays saved address list, adaptive tag filters, search filter bar, and address management actions.
 */
public struct AddressBookView: View {
    @ObservedObject var viewModel: AddressViewModel = .shared
    let customerId: String
    let onSelectAddress: (AddressDto) -> Void
    let onAddNewAddress: () -> Void
    let onBack: (() -> Void)?
    var fromProfile: Bool = false

    @State private var addressSearchQuery: String = ""
    @State private var selectedTagFilter: String = "All"
    @State private var addrToDelete: AddressDto? = nil

    private let tagOptions = ["All", "Home", "Work", "Family", "Other"]

    public init(
        viewModel: AddressViewModel = .shared,
        customerId: String,
        onSelectAddress: @escaping (AddressDto) -> Void,
        onAddNewAddress: @escaping () -> Void,
        onBack: (() -> Void)? = nil,
        fromProfile: Bool = false
    ) {
        self.viewModel = viewModel
        self.customerId = customerId
        self.onSelectAddress = onSelectAddress
        self.onAddNewAddress = onAddNewAddress
        self.onBack = onBack
        self.fromProfile = fromProfile
    }

    private var filteredAddresses: [AddressDto] {
        viewModel.addresses.filter { addr in
            let q = addressSearchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
            let matchesQuery = q.isEmpty ||
                addr.addressLine.localizedCaseInsensitiveContains(q) ||
                (addr.city?.localizedCaseInsensitiveContains(q) ?? false) ||
                (addr.state?.localizedCaseInsensitiveContains(q) ?? false) ||
                (addr.postalCode?.localizedCaseInsensitiveContains(q) ?? false) ||
                (addr.recipientName?.localizedCaseInsensitiveContains(q) ?? false) ||
                addr.displayTag.localizedCaseInsensitiveContains(q)

            let matchesTag = selectedTagFilter == "All" || addr.displayTag.caseInsensitiveCompare(selectedTagFilter) == .orderedSame
            return matchesQuery && matchesTag
        }
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Header Row
            HStack(spacing: 12) {
                if let onBack = onBack {
                    Button(action: onBack) {
                        ZStack {
                            Circle()
                                .fill(Color.white)
                                .frame(width: 40, height: 40)
                                .overlay(
                                    Circle()
                                        .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                                )
                            Image(systemName: "arrow.backward")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                        }
                    }
                    .buttonStyle(PlainButtonStyle())
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(fromProfile ? "Saved Addresses" : "Delivery Addresses")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))

                    Text(fromProfile ? "\(viewModel.addresses.count) saved delivery locations" : "Confirm fulfillment availability & ETAs")
                        .font(.system(size: 12))
                        .foregroundColor(Color(hex: "64748B"))
                }

                Spacer()

                Button(action: {
                    viewModel.startAddAddressFlow()
                    onAddNewAddress()
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "plus")
                            .font(.system(size: 12, weight: .bold))
                        Text("Add")
                            .font(.system(size: 13, weight: .bold))
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(Color(hex: "059669"))
                    .cornerRadius(10)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)
            .padding(.bottom, 10)

            // Search Filter Bar
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(Color(hex: "94A3B8"))
                TextField("Search saved addresses...", text: $addressSearchQuery)
                    .font(.system(size: 14))
            }
            .padding(11)
            .background(Color.white)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
            )
            .padding(.horizontal, 16)
            .padding(.bottom, 8)

            // Adaptive Tag Filters
            if viewModel.addresses.count >= 2 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(tagOptions, id: \.self) { tag in
                            let isSelected = selectedTagFilter.caseInsensitiveCompare(tag) == .orderedSame
                            Button(action: { selectedTagFilter = tag }) {
                                Text(tag)
                                    .font(.system(size: 11, weight: .bold))
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(isSelected ? Color(hex: "16A34A") : Color(hex: "F1F5F9"))
                                    .foregroundColor(isSelected ? .white : Color(hex: "334155"))
                                    .cornerRadius(8)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 8)
                                            .stroke(isSelected ? Color(hex: "15803D") : Color(hex: "E2E8F0"), lineWidth: 1)
                                    )
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 10)
                }
            }

            // Address List
            if viewModel.isLoading && viewModel.addresses.isEmpty {
                Spacer()
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: Color(hex: "16A34A")))
                Spacer()
            } else if filteredAddresses.isEmpty {
                Spacer()
                VStack(spacing: 8) {
                    Image(systemName: "mappin.circle.fill")
                        .font(.system(size: 48))
                        .foregroundColor(Color(hex: "16A34A"))

                    Text(viewModel.addresses.isEmpty ? "No saved addresses yet" : "No addresses match your search")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))

                    Text(viewModel.addresses.isEmpty ?
                        "Add your delivery address to see live 10-minute delivery ETAs and place your order." :
                        "Try searching for a different street, area, tag, or city.")
                        .font(.system(size: 13))
                        .foregroundColor(Color(hex: "64748B"))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)

                    if viewModel.addresses.isEmpty {
                        Button(action: {
                            viewModel.startAddAddressFlow()
                            onAddNewAddress()
                        }) {
                            HStack(spacing: 6) {
                                Image(systemName: "plus")
                                Text("Add Delivery Address")
                            }
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 20)
                            .padding(.vertical, 12)
                            .background(Color(hex: "16A34A"))
                            .cornerRadius(10)
                        }
                        .padding(.top, 8)
                    }
                }
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(filteredAddresses) { address in
                            AddressCardView(
                                address: address,
                                isSelected: address.id == viewModel.selectedAddress?.id,
                                onSelect: {
                                    viewModel.select(address)
                                    onSelectAddress(address)
                                },
                                onEdit: {
                                    viewModel.startEditAddressFlow(address)
                                    onAddNewAddress()
                                },
                                onDelete: {
                                    addrToDelete = address
                                },
                                onSetDefault: {
                                    viewModel.setDefaultAddress(addressId: address.id)
                                }
                            )
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
            }
        }
        .background(Color(hex: "F8FAFC").ignoresSafeArea())
        .alert(item: $addrToDelete) { addr in
            Alert(
                title: Text("Delete Address"),
                message: Text("Are you sure you want to remove '\(addr.addressLine)'? This action cannot be undone."),
                primaryButton: .destructive(Text("Delete")) {
                    viewModel.deleteAddress(addressId: addr.id)
                },
                secondaryButton: .cancel()
            )
        }
        .onAppear {
            viewModel.initialize(customerId: customerId)
        }
    }
}
