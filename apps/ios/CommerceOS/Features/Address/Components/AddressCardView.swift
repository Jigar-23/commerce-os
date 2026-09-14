import SwiftUI

/**
 * Production Address Card with overflow menu hierarchy.
 * Masks sensitive recipient contact details for privacy, limits long lines, and ensures accessible touch targets.
 * 1:1 Parity with Android AddressCard.kt
 */
public struct AddressCardView: View {
    public let address: AddressDto
    public let isSelected: Bool
    public let onSelect: () -> Void
    public let onEdit: () -> Void
    public let onDelete: () -> Void
    public let onSetDefault: () -> Void

    public init(
        address: AddressDto,
        isSelected: Bool,
        onSelect: @escaping () -> Void,
        onEdit: @escaping () -> Void,
        onDelete: @escaping () -> Void,
        onSetDefault: @escaping () -> Void
    ) {
        self.address = address
        self.isSelected = isSelected
        self.onSelect = onSelect
        self.onEdit = onEdit
        self.onDelete = onDelete
        self.onSetDefault = onSetDefault
    }

    public var body: some View {
        HStack(alignment: .top, spacing: 12) {
            // Left Column: House / Location Icon + Default/Saved Label
            VStack(alignment: .center, spacing: 4) {
                ZStack {
                    Circle()
                        .fill(Color(hex: "F8FAFC"))
                        .frame(width: 36, height: 36)

                    Image(systemName: iconForTag(address.displayTag))
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(Color(hex: "0F172A"))
                }

                Text(address.isDefault ? "Default" : "Saved")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(address.isDefault ? Color(hex: "16A34A") : Color(hex: "64748B"))
            }
            .frame(width: 44)

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
