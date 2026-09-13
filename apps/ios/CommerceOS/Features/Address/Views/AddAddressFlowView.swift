import SwiftUI
import MapKit

public struct AddAddressFlowView: View {
    @Environment(\.presentationMode) private var presentationMode
    @State private var currentStep: Int = 1 // 1: Search, 2: Pin Drop, 3: Form Details
    
    @State private var searchQuery: String = ""
    @State private var searchResults: [MKMapItem] = []
    @State private var selectedCoordinate: CLLocationCoordinate2D = CLLocationManager().location?.coordinate ?? CLLocationCoordinate2D(latitude: 0, longitude: 0)
    @State private var detectedAddress: String = ""
    @State private var flatNumber: String = ""
    @State private var landmark: String = ""
    @State private var addressTag: String = "Home"
    @State private var recipientName: String = UserDefaults.standard.string(forKey: "customer_name") ?? ""
    @State private var recipientPhone: String = UserDefaults.standard.string(forKey: "customer_phone") ?? ""
    
    let onSaveAddress: (AddressDto) -> Void
    
    public init(onSaveAddress: @escaping (AddressDto) -> Void = { _ in }) {
        self.onSaveAddress = onSaveAddress
    }
    
    public var body: some View {
        NavigationView {
            VStack {
                if currentStep == 1 {
                    // Step 1: Search Location
                    VStack(spacing: 16) {
                        HStack {
                            Image(systemName: "magnifyingglass")
                                .foregroundColor(.gray)
                            TextField("Search area, apartment, street name...", text: $searchQuery)
                                .onChange(of: searchQuery) { newValue in
                                    performSearch(query: newValue)
                                }
                        }
                        .padding(12)
                        .background(Color(.systemGray6))
                        .cornerRadius(10)
                        .padding(.horizontal)
                        
                        Button(action: {
                            let mgr = CLLocationManager()
                            if let loc = mgr.location {
                                selectedCoordinate = loc.coordinate
                                CLGeocoder().reverseGeocodeLocation(loc) { placemarks, _ in
                                    if let p = placemarks?.first {
                                        let name = [p.name, p.subLocality, p.locality, p.postalCode].compactMap { $0 }.joined(separator: ", ")
                                        DispatchQueue.main.async {
                                            self.detectedAddress = name.isEmpty ? "Current GPS Location" : name
                                            if let city = p.locality {
                                                UserDefaults.standard.set(city, forKey: "customer_city")
                                            }
                                            if let pin = p.postalCode {
                                                UserDefaults.standard.set(pin, forKey: "customer_pincode")
                                            }
                                        }
                                    }
                                }
                            }
                            currentStep = 2
                        }) {
                            HStack {
                                Image(systemName: "location.fill")
                                    .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                                Text("Use Current GPS Location")
                                    .font(.system(size: 14, weight: .semibold))
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .foregroundColor(.secondary)
                            }
                            .padding()
                            .background(CommerceOSTheme.Colors.brandPrimarySoft)
                            .cornerRadius(10)
                            .padding(.horizontal)
                        }
                        
                        if !searchResults.isEmpty {
                            List(searchResults, id: \.self) { item in
                                Button(action: {
                                    if let loc = item.placemark.location {
                                        selectedCoordinate = loc.coordinate
                                        detectedAddress = item.placemark.title ?? item.name ?? "Selected Location"
                                        if let city = item.placemark.locality {
                                            UserDefaults.standard.set(city, forKey: "customer_city")
                                        }
                                        if let pin = item.placemark.postalCode {
                                            UserDefaults.standard.set(pin, forKey: "customer_pincode")
                                        }
                                        currentStep = 2
                                    }
                                }) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(item.name ?? "Location")
                                            .font(.system(size: 14, weight: .bold))
                                        if let title = item.placemark.title {
                                            Text(title)
                                                .font(.system(size: 12))
                                                .foregroundColor(.secondary)
                                        }
                                    }
                                }
                            }
                            .listStyle(InsetGroupedListStyle())
                        } else {
                            Spacer()
                        }
                    }
                    .navigationTitle("Select Delivery Area")
                } else if currentStep == 2 {
                    // Step 2: Interactive Dark Map Pin Drop
                    ZStack(alignment: .bottom) {
                        ZomatoDarkMapView(
                            customerCoordinate: selectedCoordinate
                        )
                        .edgesIgnoringSafeArea(.all)
                        
                        // Center Pin Overlay
                        VStack {
                            Spacer()
                            Image(systemName: "mappin")
                                .font(.system(size: 38))
                                .foregroundColor(.red)
                                .shadow(radius: 4)
                            Spacer()
                        }
                        
                        // Bottom Confirmation Sheet
                        VStack(spacing: 12) {
                            HStack {
                                Image(systemName: "mappin.circle.fill")
                                    .foregroundColor(.red)
                                    .font(.system(size: 20))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Order Will Be Delivered Here")
                                        .font(.system(size: 14, weight: .bold))
                                    Text(detectedAddress)
                                        .font(.system(size: 12))
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                            }
                            
                            Button(action: { currentStep = 3 }) {
                                Text("Confirm Location & Proceed")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 14)
                                    .background(Color.green)
                                    .cornerRadius(10)
                            }
                        }
                        .padding()
                        .background(Color(.systemBackground))
                        .cornerRadius(16, corners: [.topLeft, .topRight])
                    }
                    .navigationBarTitle("Pin Exact Location", displayMode: .inline)
                } else {
                    // Step 3: Complete Address Form
                    Form {
                        Section(header: Text("House / Apartment Details")) {
                            TextField("House / Flat / Block No. *", text: $flatNumber)
                            TextField("Apartment / Building / Street", text: $detectedAddress)
                            TextField("Nearby Landmark (Optional)", text: $landmark)
                        }
                        
                        Section(header: Text("Save Address As")) {
                            HStack {
                                ForEach(["Home", "Work", "Other"], id: \.self) { tag in
                                    Button(action: { addressTag = tag }) {
                                        Text(tag)
                                            .font(.system(size: 13, weight: .bold))
                                            .padding(.horizontal, 16)
                                            .padding(.vertical, 8)
                                            .background(addressTag == tag ? CommerceOSTheme.Colors.brandPrimaryDark : Color(.systemGray5))
                                            .foregroundColor(addressTag == tag ? .white : .primary)
                                            .cornerRadius(8)
                                    }
                                    .buttonStyle(PlainButtonStyle())
                                }
                            }
                        }
                        
                        Section(header: Text("Recipient Info")) {
                            TextField("Receiver's Name", text: $recipientName)
                            TextField("Receiver's Phone", text: $recipientPhone)
                        }
                        
                        Section {
                            Button(action: saveAddress) {
                                Text("Save Delivery Address")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(CommerceOSTheme.Colors.brandPrimaryDark)
                                    .cornerRadius(8)
                            }
                        }
                    }
                    .navigationTitle("Address Details")
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { presentationMode.wrappedValue.dismiss() }
                }
            }
        }
    }
    
    private func performSearch(query: String) {
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else {
            searchResults = []
            return
        }
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = query
        let search = MKLocalSearch(request: request)
        search.start { response, _ in
            if let items = response?.mapItems {
                DispatchQueue.main.async {
                    self.searchResults = items
                }
            }
        }
    }
    
    private func saveAddress() {
        let addressId = "ADDR-\(UUID().uuidString.prefix(8))"
        let custId = UserDefaults.standard.string(forKey: "customer_id") ?? "CUST-\(UUID().uuidString.prefix(6))"
        let newAddress = AddressDto(
            id: addressId,
            customerId: custId,
            label: addressTag,
            addressLine: detectedAddress.isEmpty ? "Current Map Coordinate" : detectedAddress,
            flatNumber: flatNumber.isEmpty ? nil : flatNumber,
            landmark: landmark.isEmpty ? nil : landmark,
            latitude: selectedCoordinate.latitude,
            longitude: selectedCoordinate.longitude,
            recipientName: recipientName.isEmpty ? nil : recipientName,
            recipientPhone: recipientPhone.isEmpty ? nil : recipientPhone,
            isDefault: true
        )
        onSaveAddress(newAddress)
        presentationMode.wrappedValue.dismiss()
    }
}
