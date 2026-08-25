package com.commerceos.android.ui.address.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius

@Composable
fun DeliveryInstructionsField(
    instructions: String,
    onInstructionsChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val presetChips = listOf(
        "Leave with security",
        "Call on arrival",
        "Don't ring bell",
        "Leave at door"
    )

    val activePresets = instructions.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()

    Column(modifier = modifier) {
        Text("Delivery instructions:", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(presetChips) { preset ->
                val isPresent = activePresets.any { it.equals(preset, ignoreCase = true) }
                FilterChip(
                    selected = isPresent,
                    onClick = {
                        val updatedSet = if (isPresent) {
                            activePresets.filter { !it.equals(preset, ignoreCase = true) }
                        } else {
                            activePresets + preset
                        }
                        onInstructionsChanged(updatedSet.joinToString(", "))
                    },
                    label = { Text(preset, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(Radius.Chip)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = instructions,
            onValueChange = onInstructionsChanged,
            label = { Text("Specific instructions (optional)") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
