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
    val safeStatus = rx.status?.takeIf { it.isNotBlank() } ?: "PENDING"
    val presented = presentRxStatus(safeStatus)
    val patient = rx.patientName?.takeIf { it.isNotBlank() } ?: "Patient"
    val dateDisplay = formatRxDate(rx.createdAt)
    val attachments = rx.attachments ?: emptyList()

    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = presented.background,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(presented.icon, contentDescription = null, tint = presented.color, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(patient, style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                    Text(dateDisplay, style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
                }
                Surface(color = presented.background, shape = RoundedCornerShape(Radius.Chip)) {
                    Text(
                        presented.label,
                        style = CommerceTypography.Meta,
                        fontWeight = FontWeight.Bold,
                        color = presented.color,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (!rx.doctorName.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Doctor: ",
                        style = CommerceTypography.Meta,
                        fontWeight = FontWeight.Bold,
                        color = CommerceColors.TextMuted
                    )
                    Text(
                        rx.doctorName ?: "",
                        style = CommerceTypography.Caption,
                        color = CommerceColors.TextSecondary
                    )
                }
            }
            if (!rx.rejectionReason.isNullOrBlank()) {
                Text(rx.rejectionReason ?: "", style = CommerceTypography.Caption, color = CommerceColors.Danger, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (attachments.isNotEmpty()) {
                val firstAttachment = attachments.firstOrNull() ?: ""
                val isDataUri = firstAttachment.startsWith("data:")
                val isHttp = firstAttachment.startsWith("http://") || firstAttachment.startsWith("https://")
                val isLocal = firstAttachment.startsWith("/") || firstAttachment.startsWith("file:")

                val modelToLoad: Any? = when {
                    isLocal -> {
                        val cleanPath = firstAttachment.removePrefix("file://")
                        val file = java.io.File(cleanPath)
                        if (file.exists()) file else null
                    }
                    isHttp -> firstAttachment
                    isDataUri -> firstAttachment
                    firstAttachment.isNotBlank() && !firstAttachment.contains("/") -> null // e.g. "local_device_storage" audit placeholder
                    else -> firstAttachment.takeIf { it.isNotBlank() }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    if (modelToLoad != null) {
                        AsyncImage(
                            model = modelToLoad,
                            contentDescription = "Prescription image",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFE2E8F0)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            color = Color(0xFFECFDF5),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isLocal) "Device Personal Storage" else if (isHttp) "Verified Pharmacy Upload" else "Prescription Attached",
                            style = CommerceTypography.Caption,
                            fontWeight = FontWeight.SemiBold,
                            color = CommerceColors.TextPrimary
                        )
                        Text(
                            text = if (isLocal) "Stored securely on device" else if (isHttp) "Stored in verified cloud vault" else "Valid prescription record",
                            style = CommerceTypography.Meta,
                            color = CommerceColors.TextMuted
                        )
                    }
                }
            } else {
                Text(
                    "Doctor's slip attached and on file",
                    style = CommerceTypography.Meta,
                    color = CommerceColors.TextMuted
                )
            }
        }
    }
}

private fun formatRxDate(rawDate: String?): String {
    if (rawDate.isNullOrBlank()) return "Recently uploaded"
    return try {
        if (rawDate.contains("T")) {
            val datePart = rawDate.substringBefore("T")
            val parts = datePart.split("-")
            if (parts.size == 3) {
                val year = parts[0]
                val month = when (parts[1]) {
                    "01" -> "Jan"; "02" -> "Feb"; "03" -> "Mar"; "04" -> "Apr"
                    "05" -> "May"; "06" -> "Jun"; "07" -> "Jul"; "08" -> "Aug"
                    "09" -> "Sep"; "10" -> "Oct"; "11" -> "Nov"; "12" -> "Dec"
                    else -> parts[1]
                }
                val day = parts[2]
                "$day $month $year"
            } else {
                datePart
            }
        } else {
            rawDate
        }
    } catch (_: Exception) {
        rawDate
    }
}

private data class RxStatusPresentation(
    val label: String,
    val color: androidx.compose.ui.graphics.Color,
    val background: androidx.compose.ui.graphics.Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@androidx.compose.runtime.Composable
private fun presentRxStatus(raw: String?): RxStatusPresentation {
    val statusClean = raw?.uppercase()?.trim() ?: "PENDING"
    return when (statusClean) {
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
