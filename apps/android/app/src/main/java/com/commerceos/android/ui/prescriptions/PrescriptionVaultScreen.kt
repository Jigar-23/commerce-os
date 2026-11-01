package com.commerceos.android.ui.prescriptions

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.commerceos.android.R
import com.commerceos.android.model.Prescription
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing
import java.io.File

@Composable
fun PrescriptionVaultScreen(
    prescriptions: List<Prescription>,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onUpload: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    var previewPrescription by remember { mutableStateOf<Prescription?>(null) }

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
                    color = Color.White,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.size(40.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A),
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
                    text = "Saved prescriptions ready to share with pharmacy",
                    style = CommerceTypography.Meta,
                    color = CommerceColors.TextMuted
                )
            }

            Surface(
                color = Color.White,
                shape = CircleShape,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shadowElevation = 0.5.dp,
                modifier = Modifier.size(38.dp)
            ) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFF059669),
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
                items(prescriptions) { rx ->
                    PrescriptionCard(
                        rx = rx,
                        onPreview = { previewPrescription = rx }
                    )
                }
            }
        }
    }

    // Full-screen Prescription Preview Dialog
    previewPrescription?.let { rx ->
        PrescriptionPreviewDialog(
            prescription = rx,
            onDismiss = { previewPrescription = null }
        )
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
            Text("Upload a clear photo of your prescription to share with the vendor pharmacy during order checkout.", style = CommerceTypography.Caption, color = CommerceColors.TextMuted)
        }
    }
}

@Composable
private fun PrescriptionCard(
    rx: Prescription,
    onPreview: () -> Unit
) {
    val patient = rx.patientName.takeIf { it.isNotBlank() } ?: "Patient"
    val dateDisplay = formatRxDate(rx.createdAt)
    val attachments = rx.attachments
    val firstAttachment = attachments.firstOrNull()?.takeIf { it.isNotBlank() }

    val modelToLoad: Any? = when {
        firstAttachment == null -> null
        firstAttachment.startsWith("data:") -> {
            remember(firstAttachment) { decodeBase64ToBitmap(firstAttachment) }
        }
        firstAttachment.startsWith("/") -> {
            val f = File(firstAttachment)
            if (f.exists()) f else null
        }
        firstAttachment.startsWith("file:") -> {
            val f = File(firstAttachment.removePrefix("file://"))
            if (f.exists()) f else null
        }
        firstAttachment.startsWith("http://") || firstAttachment.startsWith("https://") -> firstAttachment
        else -> null
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPreview)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row: Icon, Patient Name, Date, Preview Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = Color(0xFFECFDF5),
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        patient,
                        style = CommerceTypography.BodySmall,
                        fontWeight = FontWeight.Bold,
                        color = CommerceColors.TextPrimary
                    )
                    Text(
                        dateDisplay,
                        style = CommerceTypography.Meta,
                        color = CommerceColors.TextMuted
                    )
                }

                // Explicit Preview Pill Button
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.clickable(onClick = onPreview)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_visibility),
                            contentDescription = "Preview prescription",
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Preview",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0284C7)
                        )
                    }
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
                        rx.doctorName.orEmpty(),
                        style = CommerceTypography.Caption,
                        color = CommerceColors.TextSecondary
                    )
                }
            }

            // Attachment Preview Strip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (modelToLoad != null) {
                    SubcomposeAsyncImage(
                        model = modelToLoad,
                        contentDescription = "Prescription thumbnail",
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE2E8F0)),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFF059669), modifier = Modifier.size(16.dp))
                            }
                        },
                        error = {
                            Surface(
                                color = Color(0xFFF0FDF4),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    )
                } else {
                    Surface(
                        color = Color(0xFFF0FDF4),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Prescription Slip Attached",
                        style = CommerceTypography.Caption,
                        fontWeight = FontWeight.SemiBold,
                        color = CommerceColors.TextPrimary
                    )
                    Text(
                        text = "Tap to open and preview full document",
                        style = CommerceTypography.Meta,
                        color = Color(0xFF0284C7)
                    )
                }
            }
        }
    }
}

@Composable
fun PrescriptionPreviewDialog(
    prescription: Prescription,
    onDismiss: () -> Unit
) {
    val attachments = prescription.attachments
    val firstAttachment = attachments.firstOrNull()?.takeIf { it.isNotBlank() }

    val modelToLoad: Any? = when {
        firstAttachment == null -> null
        firstAttachment.startsWith("data:") -> {
            remember(firstAttachment) { decodeBase64ToBitmap(firstAttachment) }
        }
        firstAttachment.startsWith("/") -> {
            val f = File(firstAttachment)
            if (f.exists()) f else null
        }
        firstAttachment.startsWith("file:") -> {
            val f = File(firstAttachment.removePrefix("file://"))
            if (f.exists()) f else null
        }
        firstAttachment.startsWith("http://") || firstAttachment.startsWith("https://") -> firstAttachment
        else -> null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Dialog Title Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PRESCRIPTION PREVIEW",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF059669),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = prescription.patientName.ifBlank { "Patient Prescription" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close preview",
                            tint = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // Prescription Image Container
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 380.dp)
                ) {
                    if (modelToLoad != null) {
                        SubcomposeAsyncImage(
                            model = modelToLoad,
                            contentDescription = "Full Prescription Slip",
                            contentScale = ContentScale.Fit,
                            loading = {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            },
                            error = {
                                PrescripionFallbackCard(prescription = prescription)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        PrescripionFallbackCard(prescription = prescription)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Metadata Details Strip
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Patient Name", fontSize = 12.sp, color = Color(0xFF64748B))
                            Text(
                                prescription.patientName.ifBlank { "Patient" },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }

                        if (!prescription.doctorName.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Prescribing Doctor", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text(
                                    "Dr. ${prescription.doctorName}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Uploaded Date", fontSize = 12.sp, color = Color(0xFF64748B))
                            Text(
                                formatRxDate(prescription.createdAt),
                                fontSize = 12.sp,
                                color = Color(0xFF334155)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Vendor Status", fontSize = 12.sp, color = Color(0xFF64748B))
                            Surface(
                                color = Color(0xFFECFDF5),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "READY TO SHARE WITH PHARMACY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "This prescription is saved to your account and will be transmitted directly to the fulfillment pharmacy vendor with your order.",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PrescripionFallbackCard(prescription: Prescription) {
    Surface(
        color = Color(0xFFF0FDF4),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF86EFAC)),
        modifier = Modifier.fillMaxSize().padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = Color(0xFF059669),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "PRESCRIPTION ON FILE",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF166534),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Document slip registered for ${prescription.patientName.ifBlank { "patient" }}",
                fontSize = 11.sp,
                color = Color(0xFF15803D)
            )
        }
    }
}

private fun decodeBase64ToBitmap(dataUriOrBase64: String): Bitmap? {
    return try {
        val base64Data = if (dataUriOrBase64.contains(",")) {
            dataUriOrBase64.substringAfter(",")
        } else {
            dataUriOrBase64
        }
        val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (_: Exception) {
        null
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
