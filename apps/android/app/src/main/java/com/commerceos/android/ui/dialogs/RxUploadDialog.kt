package com.commerceos.android.ui.dialogs

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
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
import com.commerceos.android.ui.theme.CommerceColors
import java.io.ByteArrayOutputStream

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
            val (name, data) = context.processBitmapAsDataUri(bitmap)
            attachment = data
            attachmentName = name
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            takeCameraPhoto.launch(null)
        }
    }

    val pickGalleryImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val (name, data) = context.processUriAsDataUri(uri)
            attachment = data
            attachmentName = name
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Upload Prescription") },
        text = {
            Column {
                Text(
                    "Attach a photo of your prescription. It will be queued for review by a licensed pharmacist — Rx items cannot be ordered until approved.",
                    fontSize = 12.sp,
                    color = CommerceColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
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
                        "Attach prescription (single image):",
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
                            model = attachment,
                            contentDescription = "Prescription Preview",
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                attachmentName ?: "Prescription Attached",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A),
                                maxLines = 1
                            )
                            Text(
                                "Ready for review",
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
            ) { Text(if (busy) "Uploading..." else "Upload for Review") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        }
    )
}

/** Processes a captured camera Bitmap into a compressed data URI. */
private fun Context.processBitmapAsDataUri(bitmap: Bitmap): Pair<String, String> {
    val scaled = scaleBitmapToMax(bitmap, 1600)
    val stream = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
    val bytes = stream.toByteArray()
    val dataUri = "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
    val name = "Camera Photo (${(bytes.size / 1024).coerceAtLeast(1)} KB)"
    return name to dataUri
}

/** Reads and downsamples a picked gallery image into a self-describing data URI. */
private fun Context.processUriAsDataUri(uri: Uri): Pair<String?, String?> {
    return try {
        val name = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: "Gallery_Prescription.jpg"

        val inputStream = contentResolver.openInputStream(uri) ?: return null to null
        val original = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        if (original == null) return null to null

        val scaled = scaleBitmapToMax(original, 1600)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, stream)
        val bytes = stream.toByteArray()
        val dataUri = "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
        name to dataUri
    } catch (e: Exception) {
        null to null
    }
}

private fun scaleBitmapToMax(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= maxDimension && height <= maxDimension) return bitmap

    val ratio = width.toFloat() / height.toFloat()
    val newWidth: Int
    val newHeight: Int
    if (width > height) {
        newWidth = maxDimension
        newHeight = (maxDimension / ratio).toInt().coerceAtLeast(1)
    } else {
        newHeight = maxDimension
        newWidth = (maxDimension * ratio).toInt().coerceAtLeast(1)
    }
    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}
