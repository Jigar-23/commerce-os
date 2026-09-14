import SwiftUI

/**
 * 1:1 Parity with Android DeliveryEntranceSelector.kt
 */
public struct DeliveryEntranceSelector: View {
    public let selectedEntrance: EntranceType
    public let customEntranceDetails: String
    public let customEntranceDetailsError: String?
    public let onEntranceSelected: (EntranceType) -> Void
    public let onCustomDetailsChanged: (String) -> Void

    public init(
        selectedEntrance: EntranceType,
        customEntranceDetails: String,
        customEntranceDetailsError: String? = nil,
        onEntranceSelected: @escaping (EntranceType) -> Void,
        onCustomDetailsChanged: @escaping (String) -> Void
    ) {
        self.selectedEntrance = selectedEntrance
        self.customEntranceDetails = customEntranceDetails
        self.customEntranceDetailsError = customEntranceDetailsError
        self.onEntranceSelected = onEntranceSelected
        self.onCustomDetailsChanged = onCustomDetailsChanged
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Delivery entrance:")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Color(hex: "64748B"))

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(EntranceType.allCases) { entrance in
                        let isSelected = selectedEntrance == entrance
                        Button(action: { onEntranceSelected(entrance) }) {
                            Text(entrance.label)
                                .font(.system(size: 11, weight: .bold))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(isSelected ? Color(hex: "DCFCE7") : Color(hex: "F8FAFC"))
                                .foregroundColor(isSelected ? Color(hex: "166534") : Color(hex: "334155"))
                                .cornerRadius(8)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8)
                                        .stroke(isSelected ? Color(hex: "16A34A") : Color(hex: "E2E8F0"), lineWidth: 1)
                                )
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
            }

            if selectedEntrance == .other {
                VStack(alignment: .leading, spacing: 4) {
                    TextField("Specify entrance (e.g. Gate 3, Tower B)", text: Binding(
                        get: { customEntranceDetails },
                        set: { onCustomDetailsChanged($0) }
                    ))
                    .font(.system(size: 13))
                    .padding(10)
                    .background(Color.white)
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(customEntranceDetailsError != nil ? Color(hex: "EF4444") : Color(hex: "CBD5E1"), lineWidth: 1)
                    )

                    if let err = customEntranceDetailsError {
                        Text(err)
                            .font(.system(size: 11))
                            .foregroundColor(Color(hex: "EF4444"))
                    }
                }
                .padding(.top, 2)
            }
        }
    }
}
