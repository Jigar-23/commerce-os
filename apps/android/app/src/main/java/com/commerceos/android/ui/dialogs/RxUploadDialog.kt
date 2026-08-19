package com.commerceos.android.ui.dialogs

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.ui.theme.CommerceColors

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

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val (name, data) = context.readImageAsDataUri(uri)
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
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = patientName,
                    onValueChange = { patientName = it },
                    label = { Text("Patient Name") },
                    enabled = !busy
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (attachment == null) {
                    OutlinedButton(
                        onClick = { pickImage.launch("image/*") },
                        enabled = !busy
                    ) { Text("Attach Prescription Image") }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CommerceColors.Success)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(attachmentName ?: "Image attached", fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = { attachment = null; attachmentName = null },
                            enabled = !busy
                        ) { Text("Remove") }
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

/** Reads a picked image into a self-describing data URI; null on any failure. */
private fun Context.readImageAsDataUri(uri: Uri): Pair<String?, String?> {
    return try {
        val name = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
        val dataUri = bytes?.let {
            "data:image;base64,${Base64.encodeToString(it, Base64.NO_WRAP)}"
        }
        name to dataUri
    } catch (e: Exception) {
        null to null
    }
}
