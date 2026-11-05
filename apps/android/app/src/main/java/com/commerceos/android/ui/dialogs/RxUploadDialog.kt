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
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun RxUploadDialog(
    customerId: String = "",
    onDismiss: () -> Unit,
    onUpload: (patientName: String, attachments: List<String>, onResult: (Boolean, String?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var patientName by rememberSaveable(customerId) { mutableStateOf("") }
    var attachment by rememberSaveable(customerId) { mutableStateOf<String?>(null) }
    var attachmentName by rememberSaveable(customerId) { mutableStateOf<String?>(null) }
    var cameraError by rememberSaveable(customerId) { mutableStateOf<String?>(null) }
    var uploadError by rememberSaveable(customerId) { mutableStateOf<String?>(null) }
    var draftError by rememberSaveable(customerId) { mutableStateOf<String?>(null) }
    var hasDraftStorageError by rememberSaveable(customerId) { mutableStateOf(false) }
    var isDraftLoading by rememberSaveable(customerId) { mutableStateOf(true) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    var tempCameraFile by remember { mutableStateOf<File?>(null) }
    var draftJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(customerId) {
        patientName = ""
        attachment = null
        attachmentName = null
        cameraError = null
        uploadError = null
        draftError = null
        hasDraftStorageError = false
        isDraftLoading = true
        try {
            val draftResult = kotlinx.coroutines.withContext(Dispatchers.IO) {
                LocalPrescriptionManager.getDraftWithResult(context, customerId)
            }
            when (draftResult) {
                is LocalPrescriptionManager.DraftResult.Found -> {
                    val draft = draftResult.draft
                    if (draft.patientName.isNotBlank()) {
                        patientName = draft.patientName
                    }
                    if (!draft.attachmentPath.isNullOrBlank()) {
                        val file = File(draft.attachmentPath)
                        if (file.exists() && file.length() > 0) {
                            attachment = draft.attachmentPath
                            attachmentName = draft.attachmentName ?: file.name
                        }
                    }
                }
                is LocalPrescriptionManager.DraftResult.StorageError -> {
                    hasDraftStorageError = true
                    draftError = "Secure storage error: saved draft could not be decrypted. Editing and upload are disabled to protect saved prescription data."
                }
                is LocalPrescriptionManager.DraftResult.None -> Unit
            }
        } finally {
            isDraftLoading = false
        }
    }

    fun persistDraftDebounced(name: String, path: String?, fileName: String?, debounceMs: Long = 300L) {
        if (hasDraftStorageError || isDraftLoading) {
            draftError = "Cannot save draft: existing encrypted draft is unreadable. Overwrite prevented."
            return
        }
        draftJob?.cancel()
        val appContext = context.applicationContext
        draftJob = coroutineScope.launch(Dispatchers.IO) {
            if (debounceMs > 0) {
                kotlinx.coroutines.delay(debounceMs)
            }
            val ok = LocalPrescriptionManager.saveDraft(
                appContext,
                customerId,
                LocalPrescriptionManager.PrescriptionDraft(
                    patientName = name,
                    attachmentPath = path,
                    attachmentName = fileName
                )
            )
            withContext(Dispatchers.Main) {
                draftError = if (!ok) "Failed to save draft securely" else null
            }
        }
    }

    val takeCameraPhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (isDraftLoading || hasDraftStorageError) {
            draftError = "Cannot attach prescription while draft is loading or unreadable."
            try { tempCameraFile?.delete() } catch (_: Exception) {}
            tempCameraFile = null
            return@rememberLauncherForActivityResult
        }
        if (success && tempCameraFile != null) {
            val permanentFile = LocalPrescriptionManager.saveCameraCaptureFile(context, tempCameraFile!!)
            if (permanentFile != null) {
                attachment = permanentFile.absolutePath
                attachmentName = permanentFile.name
                cameraError = null
                persistDraftDebounced(patientName, permanentFile.absolutePath, permanentFile.name, 0L)
            } else {
                cameraError = "Failed to securely save captured photo"
            }
        } else if (!success) {
            cameraError = "Photo capture cancelled"
        }
        try {
            tempCameraFile?.delete()
        } catch (_: Exception) {}
        tempCameraFile = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            try {
                val (file, uri) = LocalPrescriptionManager.createCameraImageFile(context)
                tempCameraFile = file
                cameraError = null
                takeCameraPhoto.launch(uri)
            } catch (e: Exception) {
                cameraError = "Could not initialize camera: ${e.localizedMessage ?: "Storage error"}"
            }
        } else {
            cameraError = "Camera permission is required to capture prescriptions"
        }
    }

    val pickGalleryImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (isDraftLoading || hasDraftStorageError) {
            draftError = "Cannot attach prescription while draft is loading or unreadable."
            return@rememberLauncherForActivityResult
        }
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val file = LocalPrescriptionManager.savePrescriptionUriAsync(context, uri)
                withContext(Dispatchers.Main) {
                    if (isDraftLoading || hasDraftStorageError) {
                        draftError = "Cannot attach prescription while draft is loading or unreadable."
                        return@withContext
                    }
                    if (file != null) {
                        attachment = file.absolutePath
                        attachmentName = file.name
                        cameraError = null
                        persistDraftDebounced(patientName, file.absolutePath, file.name, 0L)
                    } else {
                        cameraError = "Selected image exceeds 5 MiB or could not be encrypted"
                    }
                }
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
                cameraError = null
                takeCameraPhoto.launch(uri)
            } catch (e: Exception) {
                cameraError = "Could not initialize camera: ${e.localizedMessage ?: "Storage error"}"
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var isDismissing by remember { mutableStateOf(false) }

    fun handleDismiss() {
        if (busy || isDismissing) return
        isDismissing = true
        draftJob?.cancel()
        if (hasDraftStorageError) {
            try {
                tempCameraFile?.delete()
            } catch (_: Exception) {}
            onDismiss()
            return
        }
        val appContext = context.applicationContext
        coroutineScope.launch {
            val ok = withContext(Dispatchers.IO) {
                LocalPrescriptionManager.saveDraft(
                    appContext,
                    customerId,
                    LocalPrescriptionManager.PrescriptionDraft(
                        patientName = patientName,
                        attachmentPath = attachment,
                        attachmentName = attachmentName
                    )
                )
            }
            if (!ok) {
                draftError = "Failed to save draft securely. Please retry."
                isDismissing = false
            } else {
                try {
                    tempCameraFile?.delete()
                } catch (_: Exception) {}
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!busy) handleDismiss() },
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
                        onClick = { handleDismiss() },
                        enabled = !busy,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                val activeBannerErr = draftError ?: cameraError
                if (activeBannerErr != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = activeBannerErr,
                                fontSize = 11.5.sp,
                                fontFamily = PlusJakartaSans,
                                color = Color(0xFFB91C1C),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    if (!hasDraftStorageError) {
                                        draftError = null
                                    }
                                    cameraError = null
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear error", tint = Color(0xFFB91C1C), modifier = Modifier.size(16.dp))
                            }
                        }
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
                    onValueChange = {
                        if (!hasDraftStorageError && !isDraftLoading) {
                            patientName = it
                            persistDraftDebounced(it, attachment, attachmentName, 300L)
                        }
                    },
                    placeholder = { Text("e.g. Self, Family Member", fontSize = 13.sp, color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF0E675E),
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    ),
                    singleLine = true,
                    enabled = !busy && !isDraftLoading && !hasDraftStorageError
                )

                if (hasDraftStorageError) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { showDiscardConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2), contentColor = Color(0xFFB91C1C)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Discard Corrupted Draft & Start Fresh",
                            fontSize = 12.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

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
                                .clickable(enabled = !busy && !isDraftLoading && !hasDraftStorageError) { launchCameraFlow() }
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
                                .clickable(enabled = !busy && !isDraftLoading && !hasDraftStorageError) { pickGalleryImage.launch("image/*") }
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
                                onClick = {
                                    val oldPath = attachment
                                    attachment = null
                                    attachmentName = null
                                    persistDraftDebounced(patientName, null, null, 0L)
                                    if (oldPath != null) {
                                        try {
                                            File(oldPath).delete()
                                        } catch (_: Exception) {}
                                    }
                                },
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

                if (uploadError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = uploadError!!,
                        color = Color(0xFFDC2626),
                        fontSize = 12.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Button
                Button(
                    onClick = {
                        if (attachment == null || busy || isDraftLoading || hasDraftStorageError) return@Button
                        busy = true
                        uploadError = null
                        onUpload(patientName.trim(), listOfNotNull(attachment)) { success, errorMsg ->
                            busy = false
                            if (success) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    LocalPrescriptionManager.clearDraft(context, customerId, force = false)
                                }
                                onDismiss()
                            } else {
                                uploadError = errorMsg ?: "Upload failed. Please retry."
                            }
                        }
                    },
                    enabled = !busy && !isDraftLoading && !hasDraftStorageError && attachment != null,
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

        if (showDiscardConfirm) {
            AlertDialog(
                onDismissRequest = { showDiscardConfirm = false },
                title = { Text("Discard Corrupted Draft?", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold) },
                text = { Text("The saved prescription draft cannot be decrypted. Discarding it will permanently remove the corrupt draft so you can create a new prescription.", fontFamily = PlusJakartaSans, fontSize = 13.sp) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDiscardConfirm = false
                            coroutineScope.launch(Dispatchers.IO) {
                                val cleared = LocalPrescriptionManager.clearDraft(context, customerId, force = true)
                                withContext(Dispatchers.Main) {
                                    if (cleared) {
                                        hasDraftStorageError = false
                                        draftError = null
                                        patientName = ""
                                        attachment = null
                                        attachmentName = null
                                    } else {
                                        draftError = "Failed to clear corrupted draft."
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Discard", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
