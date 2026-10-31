package com.commerceos.android.ui.dialogs

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.commerceos.android.data.local.LocalPrescriptionManager
import com.commerceos.android.ui.theme.CommerceColors
import java.io.File

@Composable
fun RxUploadDialog(
    onDismiss: () -> Unit,
    onUpload: (patientName: String, attachments: List<String>) -> Unit
) {
    val context = LocalContext.current
    var patientName by remember { mutableStateOf("") }
    var attachment by remember { mutableStateOf<String?>(null) }
    var attachmentName by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val takeCameraPhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val file = LocalPrescriptionManager.savePrescriptionBitmap(context, bitmap)
            if (file != null) {
                attachment = file.absolutePath
                attachmentName = file.name
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            takeCameraPhoto.launch(null)
        }
    }

    val pickGalleryImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val file = LocalPrescriptionManager.savePrescriptionUri(context, uri)
            if (file != null) {
                attachment = file.absolutePath
                attachmentName = file.name
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Attach Prescription") },
        text = {
            Column {
                Text(
                    "Attach a photo of your prescription. Prescriptions are saved strictly to your device's personal app storage — incurring zero remote memory overhead.",
                    fontSize = 12.sp,
                    color = CommerceColors.TextSecondary
                )

                Surface(
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Stored privately on this device's storage",
                            fontSize = 11.sp,
                            color = Color(0xFF1E40AF),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = patientName,
                    onValueChange = { patientName = it },
                    label = { Text("Patient Name") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (attachment == null) {
                    Text(
                        "Attach prescription slip:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = CommerceColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val hasCameraPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasCameraPerm) {
                                    takeCameraPhoto.launch(null)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Camera", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { pickGalleryImage.launch("image/*") },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery", fontSize = 12.sp)
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        AsyncImage(
                            model = File(attachment!!),
                            contentDescription = "Prescription Preview",
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                attachmentName ?: "Prescription Slip Attached",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A),
                                maxLines = 1
                            )
                            Text(
                                "Saved on device storage",
                                fontSize = 11.sp,
                                color = CommerceColors.Success,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        TextButton(
                            onClick = { attachment = null; attachmentName = null },
                            enabled = !busy
                        ) { Text("Remove", color = CommerceColors.Danger, fontSize = 12.sp) }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (attachment == null || busy) return@Button
                    busy = true
                    onUpload(patientName.trim(), listOfNotNull(attachment))
                },
                enabled = !busy && attachment != null,
                shape = RoundedCornerShape(12.dp)
            ) { Text(if (busy) "Saving..." else "Save Prescription") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        }
    )
}
