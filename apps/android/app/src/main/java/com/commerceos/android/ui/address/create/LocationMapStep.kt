package com.commerceos.android.ui.address.create

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.commerceos.android.location.RealLocationMapViewport
import com.commerceos.android.model.StructuredAddress
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing

@Composable
fun LocationMapStep(
    formAddress: StructuredAddress,
    isGeocoding: Boolean,
    onMapCameraSettled: (lat: Double, lng: Double) -> Unit,
    onRecenterGps: () -> Unit,
    onConfirmLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isReadyToConfirm = !isGeocoding && formAddress.geoLocation != null

    Column(modifier = modifier.fillMaxSize()) {
        Text("Position map pin precisely over your delivery entrance", style = CommerceTypography.Caption, color = CommerceColors.TextMuted)
        Spacer(modifier = Modifier.height(Spacing.sm))

        RealLocationMapViewport(
            centerPoint = formAddress.geoLocation,
            isGeocoding = isGeocoding,
            onMapCameraSettled = onMapCameraSettled,
            onRecenterGps = onRecenterGps
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        // Location Verification Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CommerceColors.SurfaceSubtle),
            shape = RoundedCornerShape(Radius.lg),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Confirm delivery location", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.TextMuted)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (isGeocoding) "Updating address location..." else formAddress.formattedAddress.ifBlank { "Selected geographic pin" },
                    style = CommerceTypography.BodySmall,
                    fontWeight = FontWeight.Bold,
                    color = CommerceColors.TextPrimary
                )
                if (!isGeocoding && formAddress.city.isNotBlank()) {
                    Text("${formAddress.city}, ${formAddress.state} ${formAddress.postalCode}", style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onConfirmLocation,
            enabled = isReadyToConfirm,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CommerceColors.Primary,
                disabledContainerColor = CommerceColors.SurfaceSubtle
            ),
            shape = RoundedCornerShape(Radius.Button)
        ) {
            Text(
                if (isGeocoding) "Updating address location..." else "Confirm location & enter house details",
                style = CommerceTypography.Label,
                fontWeight = FontWeight.Bold,
                color = if (isReadyToConfirm) CommerceColors.OnPrimary else CommerceColors.TextMuted
            )
        }
    }
}
