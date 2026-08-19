package com.commerceos.android.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.model.CancellationReason

@Composable
fun CancelOrderDialog(
    orderId: String,
    reasons: List<CancellationReason>,
    selectedReasonCode: String?,
    reasonNote: String,
    submitting: Boolean,
    onSelectReason: (String) -> Unit,
    onReasonNoteChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel Order $orderId") },
        text = {
            Column {
                if (reasons.isNotEmpty()) {
                    Text("Select a reason:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    reasons.forEach { reason ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            RadioButton(selected = selectedReasonCode == reason.code, onClick = { onSelectReason(reason.code) })
                            Text(reason.label, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = reasonNote,
                    onValueChange = onReasonNoteChange,
                    label = { Text("Additional notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !submitting && selectedReasonCode != null
            ) {
                Text(if (submitting) "Cancelling..." else "Confirm Cancel")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text("Keep Order") }
        }
    )
}
