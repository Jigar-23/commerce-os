package com.commerceos.android.ui.address.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.commerceos.android.model.RecipientType
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography

@Composable
fun RecipientSelector(
    recipientType: RecipientType,
    contactName: String,
    contactPhone: String,
    nameError: String?,
    phoneError: String?,
    onRecipientTypeChanged: (RecipientType) -> Unit,
    onContactNameChanged: (String) -> Unit,
    onContactPhoneChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("Deliver to:", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = recipientType == RecipientType.ME,
                onClick = { onRecipientTypeChanged(RecipientType.ME) },
                label = { Text("Me", fontWeight = FontWeight.Bold) }
            )
            FilterChip(
                selected = recipientType == RecipientType.SOMEONE_ELSE,
                onClick = { onRecipientTypeChanged(RecipientType.SOMEONE_ELSE) },
                label = { Text("Someone else", fontWeight = FontWeight.Bold) }
            )
        }

        if (recipientType == RecipientType.SOMEONE_ELSE) {
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = contactName,
                onValueChange = onContactNameChanged,
                label = { Text("Recipient Full Name *") },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it, color = CommerceColors.Danger) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = contactPhone,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() || it == '+' }.take(13)
                    onContactPhoneChanged(filtered)
                },
                label = { Text("Recipient Mobile Number (+91) *") },
                isError = phoneError != null,
                supportingText = phoneError?.let { { Text(it, color = CommerceColors.Danger) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
