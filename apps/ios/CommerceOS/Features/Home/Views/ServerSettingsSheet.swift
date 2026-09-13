import SwiftUI

public struct ServerSettingsSheet: View {
    @ObservedObject var serverConfig = ServerEnvironmentConfig.shared
    @Environment(\.dismiss) private var dismiss
    @State private var customURLText: String = ""

    public init() {
        _customURLText = State(initialValue: ServerEnvironmentConfig.shared.activeURLString)
    }

    public var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Active Server Connection")) {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Status: \(serverConfig.lastHealthStatus)")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(serverConfig.isServerConnected ? .green : .red)
                            Text(serverConfig.activeBaseURL.absoluteString)
                                .font(.system(size: 12, design: .monospaced))
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        if serverConfig.isCheckingHealth {
                            ProgressView()
                        } else {
                            Circle()
                                .fill(serverConfig.isServerConnected ? Color.green : Color.red)
                                .frame(width: 12, height: 12)
                        }
                    }

                    Button(action: {
                        serverConfig.checkHealth()
                    }) {
                        HStack {
                            Image(systemName: "arrow.clockwise")
                            Text("Test Health Probe (/health)")
                        }
                    }
                }

                Section(header: Text("Preset Environments")) {
                    ForEach(ServerEnvironmentPreset.allCases) { preset in
                        if preset != .custom {
                            Button(action: {
                                serverConfig.selectPreset(preset)
                                customURLText = preset.defaultURLString
                            }) {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(preset.rawValue)
                                            .font(.system(size: 14, weight: .medium))
                                            .foregroundColor(.primary)
                                        Text(preset.defaultURLString)
                                            .font(.system(size: 11, design: .monospaced))
                                            .foregroundColor(.secondary)
                                    }
                                    Spacer()
                                    if serverConfig.activePreset == preset {
                                        Image(systemName: "checkmark")
                                            .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                                    }
                                }
                            }
                        }
                    }
                }

                Section(header: Text("Custom Backend Gateway URL")) {
                    TextField("http://192.168.x.x:3000", text: $customURLText)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .keyboardType(.URL)
                        .font(.system(size: 13, design: .monospaced))

                    Button("Apply Custom URL") {
                        serverConfig.setCustomURL(customURLText)
                    }
                    .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                    .disabled(customURLText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }

                Section(footer: Text("Commerce OS Production Gateway routes catalog, auth, orders, and SSE telemetry over this address. Local LAN allows instant zero-latency development.")) {
                    EmptyView()
                }
            }
            .navigationTitle("Network Configuration")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
            .onAppear {
                customURLText = serverConfig.activeURLString
                serverConfig.checkHealth()
            }
        }
    }
}
