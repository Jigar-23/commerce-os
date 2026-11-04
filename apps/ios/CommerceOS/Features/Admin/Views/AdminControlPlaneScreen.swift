import SwiftUI

/**
 * 1:1 Parity with Android AdminControlPlaneScreen.kt (120 lines) & TenantSuspendedScreen.kt (78 lines).
 * Enterprise Admin Control Plane Screen:
 * - Real-time license state controls
 * - One-Click Tenant Suspension & Reactivation
 * - Enterprise immutable audit trail logs
 * - Tenant Suspended Gate Screen
 */
public struct AdminControlPlaneScreen: View {
    @ObservedObject private var suspensionEngine = TenantSuspensionEngine.shared
    public let tenantId: String
    public let onBack: () -> Void

    @State private var isSuspended: Bool = false

    public init(tenantId: String = "tenant_vogue_01", onBack: @escaping () -> Void = {}) {
        self.tenantId = tenantId
        self.onBack = onBack
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Header Bar
            HStack {
                Button(action: onBack) {
                    Image(systemName: "arrow.left")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))
                }
                Text("Admin Control Plane")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(Color.white)
            .shadow(color: Color.black.opacity(0.04), radius: 2, y: 1)

            ScrollView {
                VStack(spacing: 16) {
                    // Tenant License & Status Card
                    VStack(alignment: .leading, spacing: 14) {
                        HStack {
                            Text("Tenant: \(tenantId)")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                            Spacer()
                            Text(isSuspended ? "SUSPENDED" : "ACTIVE")
                                .font(.system(size: 11, weight: .bold))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 4)
                                .background(isSuspended ? Color(hex: "FEE2E2") : Color(hex: "DCFCE7"))
                                .foregroundColor(isSuspended ? Color(hex: "DC2626") : Color(hex: "16A34A"))
                                .cornerRadius(12)
                        }

                        Button(action: {
                            if isSuspended {
                                suspensionEngine.reactivateTenant(tenantId: tenantId)
                                isSuspended = false
                            } else {
                                suspensionEngine.suspendTenant(tenantId: tenantId, reason: "Admin control plane toggle")
                                isSuspended = true
                            }
                        }) {
                            Text(isSuspended ? "One-Click Reactivate Tenant" : "One-Click Suspend Tenant")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(isSuspended ? Color(hex: "16A34A") : Color(hex: "DC2626"))
                                .cornerRadius(8)
                        }
                    }
                    .padding(16)
                    .background(Color.white)
                    .cornerRadius(12)
                    .shadow(color: Color.black.opacity(0.05), radius: 4, y: 2)

                    // Audit Trail Header
                    HStack {
                        Text("Enterprise Audit Logs")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                        Spacer()
                    }
                    .padding(.top, 8)

                    // Audit Logs List
                    let logs = suspensionEngine.getAuditLogs()
                    if logs.isEmpty {
                        Text("No audit mutations recorded yet.")
                            .font(.system(size: 13))
                            .foregroundColor(Color(hex: "64748B"))
                            .padding(.vertical, 24)
                    } else {
                        VStack(spacing: 8) {
                            ForEach(logs) { entry in
                                VStack(alignment: .leading, spacing: 4) {
                                    HStack {
                                        Text(entry.action)
                                            .font(.system(size: 13, weight: .bold))
                                            .foregroundColor(Color(hex: "0E675E"))
                                        Spacer()
                                        Text(DateFormatter.localizedString(from: Date(timeIntervalSince1970: Double(entry.timestamp) / 1000.0), dateStyle: .short, timeStyle: .medium))
                                            .font(.system(size: 11))
                                            .foregroundColor(Color(hex: "94A3B8"))
                                    }
                                    Text("Actor: \(entry.actor) • Tenant: \(entry.tenantId)")
                                        .font(.system(size: 12))
                                        .foregroundColor(Color(hex: "334155"))
                                    if let oldVal = entry.oldValue, let newVal = entry.newValue {
                                        Text("\(oldVal) → \(newVal)")
                                            .font(.system(size: 11, weight: .semibold))
                                            .foregroundColor(Color(hex: "64748B"))
                                    }
                                }
                                .padding(12)
                                .background(Color.white)
                                .cornerRadius(8)
                            }
                        }
                    }
                }
                .padding(16)
            }
            .background(Color(hex: "F8FAFC"))
        }
        .onAppear {
            self.isSuspended = suspensionEngine.isTenantSuspended(tenantId: tenantId)
        }
    }
}

/**
 * 1:1 Parity with Android TenantSuspendedScreen.kt (78 lines).
 * Controlled Tenant Suspension Screen presented when a tenant license is SUSPENDED.
 */
public struct TenantSuspendedScreen: View {
    public let reason: String?
    public let onContactSupport: () -> Void

    public init(
        reason: String? = nil,
        onContactSupport: @escaping () -> Void = {}
    ) {
        self.reason = reason
        self.onContactSupport = onContactSupport
    }

    public var body: some View {
        ZStack {
            Color(hex: "F8FAFC").edgesIgnoringSafeArea(.all)

            VStack(spacing: 20) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 56))
                    .foregroundColor(Color(hex: "DC2626"))

                VStack(spacing: 8) {
                    Text("Service Temporarily Suspended")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))
                        .multilineTextAlignment(.center)

                    Text(reason ?? "Your tenant account access has been paused. Please contact Commerce OS billing support to resolve account status.")
                        .font(.system(size: 14))
                        .foregroundColor(Color(hex: "64748B"))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                }

                Button(action: onContactSupport) {
                    Text("Contact Enterprise Support")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Color(hex: "0E675E"))
                        .cornerRadius(12)
                }
                .padding(.horizontal, 32)
                .padding(.top, 12)
            }
            .padding(24)
            .background(Color.white)
            .cornerRadius(16)
            .shadow(color: Color.black.opacity(0.08), radius: 10, y: 4)
            .padding(24)
        }
    }
}
