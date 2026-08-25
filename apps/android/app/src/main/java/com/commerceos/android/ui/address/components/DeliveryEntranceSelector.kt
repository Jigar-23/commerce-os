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
import com.commerceos.android.model.EntranceType
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius

@Composable
fun DeliveryEntranceSelector(
    selectedEntrance: EntranceType,
    customEntranceDetails: String,
    customEntranceDetailsError: String? = null,
    onEntranceSelected: (EntranceType) -> Unit,
    onCustomDetailsChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("Delivery entrance:", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(EntranceType.values()) { entrance ->
                val isSelected = selectedEntrance == entrance
                FilterChip(
                    selected = isSelected,
                    onClick = { onEntranceSelected(entrance) },
                    label = { Text(entrance.label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(Radius.Chip)
                )
            }
        }
        if (selectedEntrance == EntranceType.OTHER) {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = customEntranceDetails,
                onValueChange = onCustomDetailsChanged,
                label = { Text("Specify entrance (e.g. Gate 3, Tower B)") },
                isError = customEntranceDetailsError != null,
                supportingText = customEntranceDetailsError?.let { { Text(it, color = CommerceColors.Danger) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
