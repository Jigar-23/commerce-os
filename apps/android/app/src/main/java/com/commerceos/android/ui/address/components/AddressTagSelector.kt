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
fun AddressTagSelector(
    selectedTag: String,
    onTagSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("Save as:", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("Home", "Work", "Family", "Other")) { tag ->
                val isSelected = selectedTag.equals(tag, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = { onTagSelected(tag) },
                    label = { Text(tag, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CommerceColors.Primary,
                        selectedLabelColor = CommerceColors.OnPrimary,
                        containerColor = CommerceColors.SurfaceSubtle,
                        labelColor = CommerceColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(Radius.Chip)
                )
            }
        }
    }
}
