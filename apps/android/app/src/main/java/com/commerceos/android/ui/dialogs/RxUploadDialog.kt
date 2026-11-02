package com.commerceos.android.ui.dialogs

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.commerceos.android.data.local.LocalPrescriptionManager
import com.commerceos.android.ui.theme.PlusJakartaSans
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

    var tempCameraFile by remember { mutableStateOf<File?>(null) }

    val takeCameraPhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraFile != null) {
            val permanentFile = LocalPrescriptionManager.saveCameraCaptureFile(context, tempCameraFile!!)
            if (permanentFile != null) {
                attachment = permanentFile.absolutePath
                attachmentName = permanentFile.name
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            try {
                val (file, uri) = LocalPrescriptionManager.createCameraImageFile(context)
                tempCameraFile = file
                takeCameraPhoto.launch(uri)
            } catch (_: Exception) {}
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

    fun launchCameraFlow() {
        val hasCameraPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCameraPerm) {
            try {
                val (file, uri) = LocalPrescriptionManager.createCameraImageFile(context)
                tempCameraFile = file
                takeCameraPhoto.launch(uri)
            } catch (_: Exception) {}
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Upload Prescription",
                            fontSize = 18.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Licensed pharmacist will verify before dispatch",
                            fontSize = 11.5.sp,
                            fontFamily = PlusJakartaSans,
                            color = Color(0xFF64748B)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = !busy,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // Patient Name Input
                Text(
                    text = "Patient Name (Optional)",
                    fontSize = 12.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = patientName,
                    onValueChange = { patientName = it },
                    placeholder = { Text("e.g. Self, Family Member", fontSize = 13.sp, color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF0E675E),
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    ),
                    singleLine = true,
                    enabled = !busy
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Prescription Photo Selector Cards
                if (attachment == null) {
                    Text(
                        text = "Attach Doctor's Slip",
                        fontSize = 12.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Camera Card
                        Surface(
                            color = Color(0xFFF0FDF4),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = !busy) { launchCameraFlow() }
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    color = Color(0xFF0E675E),
                                    shape = CircleShape,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.PhotoCamera,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Camera",
                                    fontSize = 13.sp,
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Take a clear photo",
                                    fontSize = 10.5.sp,
                                    fontFamily = PlusJakartaSans,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        // Gallery Card
                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = !busy) { pickGalleryImage.launch("image/*") }
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    color = Color(0xFF2563EB),
                                    shape = CircleShape,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Image,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Gallery",
                                    fontSize = 13.sp,
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Pick photo or file",
                                    fontSize = 10.5.sp,
                                    fontFamily = PlusJakartaSans,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                } else {
                    // High-Definition Preview Card
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            AsyncImage(
                                model = File(attachment!!),
                                contentDescription = "Prescription Preview",
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFE2E8F0)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Prescription Slip Attached",
                                    fontSize = 13.sp,
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Full clarity preserved for pharmacy",
                                    fontSize = 11.sp,
                                    fontFamily = PlusJakartaSans,
                                    color = Color(0xFF059669),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(
                                onClick = { attachment = null; attachmentName = null },
                                enabled = !busy,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove",
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Button
                Button(
                    onClick = {
                        if (attachment == null || busy) return@Button
                        busy = true
                        onUpload(patientName.trim(), listOfNotNull(attachment))
                    },
                    enabled = !busy && attachment != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E675E)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (busy) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text(
                            text = "Save & Attach Prescription",
                            fontSize = 14.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
