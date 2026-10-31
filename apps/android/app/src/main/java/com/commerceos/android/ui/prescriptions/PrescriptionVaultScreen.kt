package com.commerceos.android.ui.prescriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.commerceos.android.model.Prescription
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing

@Composable
fun PrescriptionVaultScreen(
    prescriptions: List<Prescription>,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onUpload: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Surface(
                    color = androidx.compose.ui.graphics.Color.White,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFE2E8F0)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.size(40.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = androidx.compose.ui.graphics.Color(0xFF0F172A),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Prescriptions",
                    style = CommerceTypography.Heading,
                    fontWeight = FontWeight.Bold,
                    color = CommerceColors.TextPrimary
                )
                Text(
                    text = "Upload, review and manage prescription slips",
                    style = CommerceTypography.Meta,
                    color = CommerceColors.TextMuted
                )
            }

            Surface(
                color = androidx.compose.ui.graphics.Color.White,
                shape = androidx.compose.foundation.shape.CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFE2E8F0)),
                shadowElevation = 0.5.dp,
                modifier = Modifier.size(38.dp)
            ) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = androidx.compose.ui.graphics.Color(0xFF059669),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        val isLimitReached = prescriptions.size >= 100
        Button(
            onClick = onUpload,
            enabled = !isLimitReached,
            shape = RoundedCornerShape(Radius.Button),
            colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Primary),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                if (isLimitReached) "Storage limit reached (100/100)" else "Upload prescription (${prescriptions.size}/100)",
                style = CommerceTypography.Label,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        when {
            isLoading -> Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CommerceColors.Primary)
            }
            errorMessage != null -> Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(errorMessage, style = CommerceTypography.BodySmall, color = CommerceColors.Danger, fontWeight = FontWeight.Bold)
            }
            prescriptions.isEmpty() -> EmptyVault()
            else -> LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                items(prescriptions) { rx -> PrescriptionCard(rx) }
            }
        }
    }
}

@Composable
private fun EmptyVault() {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.Card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No prescriptions uploaded", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text("Upload a clear photo when an item requires pharmacist review.", style = CommerceTypography.Caption, color = CommerceColors.TextMuted)
        }
    }
}

@Composable
private fun PrescriptionCard(rx: Prescription) {
    val presented = presentRxStatus(rx.status)
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(presented.icon, contentDescription = null, tint = presented.color, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(rx.patientName, style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                    Text(rx.createdAt ?: "Recently uploaded", style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
                }
                Surface(color = presented.background, shape = RoundedCornerShape(Radius.Chip)) {
                    Text(
                        presented.label,
                        style = CommerceTypography.Meta,
                        fontWeight = FontWeight.Bold,
                        color = presented.color,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                    )
                }
            }

            if (!rx.doctorName.isNullOrBlank()) {
                Text("Doctor: ${rx.doctorName}", style = CommerceTypography.Caption, color = CommerceColors.TextSecondary)
            }
            if (!rx.rejectionReason.isNullOrBlank()) {
                Text(rx.rejectionReason, style = CommerceTypography.Caption, color = CommerceColors.Danger, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (rx.attachments.isNotEmpty()) {
                val firstAttachment = rx.attachments.first()
                val isLocal = firstAttachment.startsWith("/") || firstAttachment.startsWith("file:") || firstAttachment.startsWith("local:")
                val modelToLoad: Any = if (firstAttachment.startsWith("/")) {
                    java.io.File(firstAttachment)
                } else if (firstAttachment.startsWith("file:")) {
                    java.io.File(firstAttachment.removePrefix("file://"))
                } else {
                    firstAttachment
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                        .padding(6.dp)
                ) {
                    AsyncImage(
                        model = modelToLoad,
                        contentDescription = "Prescription image",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isLocal) "Device Personal Storage" else if (firstAttachment.startsWith("http")) "Cloud CDN Hosted" else "Image attached",
                            style = CommerceTypography.Caption,
                            fontWeight = FontWeight.SemiBold,
                            color = CommerceColors.TextPrimary
                        )
                        Text(
                            text = if (isLocal) "Stored privately on device (zero cloud overhead)" else if (firstAttachment.startsWith("http")) "Stored in Cloudflare R2" else "Uploaded",
                            style = CommerceTypography.Meta,
                            color = CommerceColors.TextMuted
                        )
                    }
                }
            } else {
                Text(
                    "No attachments",
                    style = CommerceTypography.Meta,
                    color = CommerceColors.TextMuted
                )
            }
        }
    }
}

private data class RxStatusPresentation(
    val label: String,
    val color: androidx.compose.ui.graphics.Color,
    val background: androidx.compose.ui.graphics.Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@androidx.compose.runtime.Composable
private fun presentRxStatus(raw: String): RxStatusPresentation {
    return when (raw.uppercase()) {
        "VERIFIED", "APPROVED", "PHARMACIST_APPROVED" -> RxStatusPresentation(
            "Approved", CommerceColors.Savings, CommerceColors.SavingsSoft, Icons.Default.CheckCircle
        )
        "REJECTED" -> RxStatusPresentation(
            "Needs attention", CommerceColors.Danger, CommerceColors.DangerSoft, Icons.Default.Warning
        )
        else -> RxStatusPresentation(
            "Under review", CommerceColors.Verification, CommerceColors.VerificationSoft, Icons.Default.Warning
        )
    }
}
