import SwiftUI

/**
 * 1:1 Parity with Android LocationSearchStep.kt
 * Location search step with Google Places / MapKit autocomplete and GPS pinpointing.
 */
public struct LocationSearchStepView: View {
    public let searchQuery: String
    public let searchResults: [PlaceSearchResult]
    public let isSearching: Bool
    public let isLocatingGps: Bool
    public let gpsErrorMessage: String?
    public let onQueryChanged: (String) -> Void
    public let onRequestGps: () -> Void
    public let onSelectResult: (PlaceSearchResult) -> Void

    public init(
        searchQuery: String,
        searchResults: [PlaceSearchResult],
        isSearching: Bool,
        isLocatingGps: Bool,
        gpsErrorMessage: String?,
        onQueryChanged: @escaping (String) -> Void,
        onRequestGps: @escaping () -> Void,
        onSelectResult: @escaping (PlaceSearchResult) -> Void
    ) {
        self.searchQuery = searchQuery
        self.searchResults = searchResults
        self.isSearching = isSearching
        self.isLocatingGps = isLocatingGps
        self.gpsErrorMessage = gpsErrorMessage
        self.onQueryChanged = onQueryChanged
        self.onRequestGps = onRequestGps
        self.onSelectResult = onSelectResult
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Search building, street, area, landmark or PIN code")
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(Color(hex: "64748B"))
                .padding(.horizontal, 16)

            // Search Bar Input
            HStack(spacing: 10) {
                CommerceVectorIcon(.search, size: 18, color: Color(hex: "0E675E"))

                TextField("Search location (e.g. DLF Cyber, Sector 45, 122018)...", text: Binding(
                    get: { searchQuery },
                    set: { onQueryChanged($0) }
                ))
                .font(.system(size: 14))
                .foregroundColor(Color(hex: "0F172A"))
                .accentColor(Color(hex: "0E675E"))
                .autocapitalization(.none)
                .disableAutocorrection(true)

                if isSearching {
                    ProgressView()
                        .scaleEffect(0.8)
                } else if !searchQuery.isEmpty {
                    Button(action: { onQueryChanged("") }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(Color(hex: "94A3B8"))
                            .font(.system(size: 16))
                    }
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(Color.white)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
            )
            .padding(.horizontal, 16)

            // Location Acquisition Button (Blinkit / Zomato Pattern)
            Button(action: onRequestGps) {
                HStack(spacing: 12) {
                    if isLocatingGps {
                        ProgressView()
                            .scaleEffect(0.8)
                            .frame(width: 20, height: 20)
                    } else {
                        Image(systemName: "location.fill")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Color(hex: "0E675E"))
                            .frame(width: 20, height: 20)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Use current location")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Color(hex: "0E675E"))

                        Text(gpsErrorMessage ?? (isLocatingGps ? "Getting your current location..." : "Pinpoint location using device sensors"))
                            .font(.system(size: 12))
                            .foregroundColor(gpsErrorMessage != nil ? Color(hex: "DC2626") : Color(hex: "64748B"))
                    }

                    Spacer()
                }
                .padding(14)
                .background(Color(hex: "F8FAFC"))
                .cornerRadius(12)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                )
            }
            .buttonStyle(PlainButtonStyle())
            .padding(.horizontal, 16)

            // Place Autocomplete Results List or Empty State
            if !isSearching && searchQuery.trimmingCharacters(in: .whitespaces).count >= 2 && searchResults.isEmpty {
                VStack(spacing: 8) {
                    Text("No locations found")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))

                    Text("Try searching for a nearby landmark, building, sector or 6-digit PIN code.")
                        .font(.system(size: 12))
                        .foregroundColor(Color(hex: "64748B"))
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(20)
                .background(Color.white)
                .cornerRadius(12)
                .padding(.horizontal, 16)
                .padding(.top, 16)
            } else {
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(searchResults) { result in
                            Button(action: { onSelectResult(result) }) {
                                HStack(spacing: 12) {
                                    Image(systemName: "mappin.circle.fill")
                                        .font(.system(size: 20))
                                        .foregroundColor(Color(hex: "64748B"))

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(result.primaryText)
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundColor(Color(hex: "0F172A"))

                                        Text(result.secondaryText)
                                            .font(.system(size: 12))
                                            .foregroundColor(Color(hex: "64748B"))
                                            .lineLimit(1)
                                    }

                                    Spacer()
                                }
                                .padding(12)
                                .background(Color.white)
                                .cornerRadius(12)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(Color(hex: "F1F5F9"), lineWidth: 1)
                                )
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 4)
                }
            }

            Spacer()
        }
    }
}
