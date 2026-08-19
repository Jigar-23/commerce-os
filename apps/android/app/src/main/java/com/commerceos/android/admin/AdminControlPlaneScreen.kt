package com.commerceos.android.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing

/**
 * Enterprise Admin Control Plane Screen.
 * Provides tenant management, license state control, one-click suspension, and audit logs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminControlPlaneScreen(
    tenantId: String = "tenant_vogue_01",
    onBack: () -> Unit = {}
) {
    var isSuspended by remember { mutableStateOf(TenantSuspensionEngine.isTenantSuspended(tenantId)) }
    val auditLogs = remember { TenantSuspensionEngine.getAuditLogs() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Commerce OS Admin Control Plane", style = CommerceTypography.Title) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CommerceColors.Surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(CommerceColors.Background)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Tenant License & Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.Card),
                    colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface)
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Tenant: $tenantId", style = CommerceTypography.Title, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.weight(1f))
                            Surface(
                                color = if (isSuspended) CommerceColors.DangerSoft else CommerceColors.SuccessSoft,
                                shape = RoundedCornerShape(Radius.Pill)
                            ) {
                                Text(
                                    if (isSuspended) "SUSPENDED" else "ACTIVE",
                                    style = CommerceTypography.Meta,
                                    color = if (isSuspended) CommerceColors.Danger else CommerceColors.Success,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))

                        Button(
                            onClick = {
                                if (isSuspended) {
                                    TenantSuspensionEngine.reactivateTenant(tenantId)
                                    isSuspended = false
                                } else {
                                    TenantSuspensionEngine.suspendTenant(tenantId, "Admin suspension test")
                                    isSuspended = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSuspended) CommerceColors.Success else CommerceColors.Danger
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isSuspended) "One-Click Reactivate Tenant" else "One-Click Suspend Tenant", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Audit Trail Header
            item {
                Text("Enterprise Audit Logs", style = CommerceTypography.Title, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
            }

            items(auditLogs) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.Card),
                    colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface)
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Row {
                            Text(log.action, style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.Primary)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(log.actor, style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
                        }
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text("Resource: ${log.resource} | ${log.oldValue ?: "N/A"} ➔ ${log.newValue ?: "N/A"}", style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
                    }
                }
            }
        }
    }
}
