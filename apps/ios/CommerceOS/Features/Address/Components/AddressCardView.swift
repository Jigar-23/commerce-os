import SwiftUI

/**
 * Production Address Card matching Blinkit's visual hierarchy.
 * Displays left badge with Home/Work icon and live proximity ("You're here" for <=100m or "X.XX km").
 * 1:1 Parity with Android AddressCard.kt
 */
public struct AddressCardView: View {
    public let address: AddressDto
    public let isSelected: Bool
    public let distanceMeters: Double?
    public let onSelect: () -> Void
    public let onEdit: () -> Void
    public let onDelete: () -> Void
    public let onSetDefault: () -> Void

    public init(
        address: AddressDto,
        isSelected: Bool,
        distanceMeters: Double? = nil,
        onSelect: @escaping () -> Void,
        onEdit: @escaping () -> Void,
        onDelete: @escaping () -> Void,
        onSetDefault: @escaping () -> Void
    ) {
        self.address = address
        self.isSelected = isSelected
        self.distanceMeters = distanceMeters
        self.onSelect = onSelect
        self.onEdit = onEdit
        self.onDelete = onDelete
        self.onSetDefault = onSetDefault
    }

    public var body: some View {
        HStack(alignment: .top, spacing: 12) {
            // Left Column: Soft cream container with Icon + "You're here" / Distance badge (Blinkit Pattern)
            VStack(alignment: .center, spacing: 3) {
                Image(systemName: iconForTag(address.displayTag))
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(Color(hex: "D97706"))
                    .frame(height: 22)

                let isAtLocation = distanceMeters != nil && (distanceMeters ?? 0) <= 100.0
                if isAtLocation {
                    Text("You're here")
                        .font(.system(size: 8, weight: .heavy))
                        .foregroundColor(Color(hex: "15803D"))
                        .padding(.horizontal, 4)
                        .padding(.vertical, 1.5)
                        .background(Color(hex: "DCFCE7"))
                        .cornerRadius(4)
                } else if let meters = distanceMeters {
                    Text(String(format: "%.2f km", meters / 1000.0))
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(Color(hex: "92400E"))
                        .lineLimit(1)
                } else {
                    Text(address.isDefault ? "Default" : "Saved")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(address.isDefault ? Color(hex: "16A34A") : Color(hex: "64748B"))
                }
            }
            .frame(width: 54)
            .padding(.vertical, 8)
            .background(Color(hex: "FFFBEB"))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color(hex: "FEF3C7"), lineWidth: 1)
            )

            // Right Column: Tag, Full Address, Phone & Bottom Action Icons
            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text(address.displayTag.isEmpty ? "Home" : address.displayTag)
                        .font(.system(size: 15, weight: .heavy))
                        .foregroundColor(Color(hex: "0F172A"))

                    Spacer()

                    if address.isDefault {
                        Text("✓ Selected")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(Color(hex: "166534"))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color(hex: "DCFCE7"))
                            .cornerRadius(6)
                    }
                }

                Text(address.addressLine)
                    .font(.system(size: 13))
                    .foregroundColor(Color(hex: "334155"))
                    .lineLimit(2)

                let locationLine = [address.city, address.state, address.postalCode].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: ", ")
                if !locationLine.isEmpty {
                    Text(locationLine)
                        .font(.system(size: 12))
                        .foregroundColor(Color(hex: "64748B"))
                        .lineLimit(1)
                }

                let phone = address.contactPhone ?? address.recipientPhone ?? ""
                if !phone.isEmpty {
                    Text("Phone number: \(phone)")
                        .font(.system(size: 12))
                        .foregroundColor(Color(hex: "64748B"))
                        .padding(.top, 1)
                }

                Spacer().frame(height: 6)

                // Bottom Circular Action Buttons (Zomato Pattern)
                HStack(spacing: 8) {
                    // Menu Options Button (•••)
                    Menu {
                        Button(action: onEdit) {
                            Label("Edit address", systemImage: "pencil")
                        }
                        if !address.isDefault {
                            Button(action: onSetDefault) {
                                Label("Set as default delivery address", systemImage: "star")
                            }
                        }
                        Divider()
                        Button(role: .destructive, action: onDelete) {
                            Label("Delete address", systemImage: "trash")
                        }
                    } label: {
                        ZStack {
                            Circle()
                                .fill(Color(hex: "F8FAFC"))
                                .frame(width: 32, height: 32)
                                .overlay(
                                    Circle()
                                        .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                                )
                            Image(systemName: "ellipsis")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(Color(hex: "64748B"))
                        }
                    }

                    // Edit Shortcut Button
                    Button(action: onEdit) {
                        ZStack {
                            Circle()
                                .fill(Color(hex: "F8FAFC"))
                                .frame(width: 32, height: 32)
                                .overlay(
                                    Circle()
                                        .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                                )
                            Image(systemName: "pencil")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(Color(hex: "64748B"))
                        }
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
        }
        .padding(14)
        .background(isSelected ? Color(hex: "F0FDF4") : Color.white)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(isSelected ? Color(hex: "16A34A") : Color(hex: "E2E8F0"), lineWidth: isSelected ? 1.5 : 1)
        )
        .contentShape(Rectangle())
        .onTapGesture(perform: onSelect)
    }

    private func iconForTag(_ tag: String) -> String {
        switch tag.lowercased() {
        case "work", "office": return "briefcase.fill"
        default: return "house.fill"
        }
    }
}
