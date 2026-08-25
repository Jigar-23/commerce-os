package com.commerceos.android.ui.address.create

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.commerceos.android.model.AddressConflictWarning
import com.commerceos.android.model.AddressValidationResult
import com.commerceos.android.model.StructuredAddress
import com.commerceos.android.ui.address.components.AddressTagSelector
import com.commerceos.android.ui.address.components.DeliveryEntranceSelector
import com.commerceos.android.ui.address.components.DeliveryInstructionsField
import com.commerceos.android.ui.address.components.RecipientSelector
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing

@Composable
fun AddressDetailsStep(
    formAddress: StructuredAddress,
    conflictWarning: AddressConflictWarning?,
    validationResult: AddressValidationResult,
    isSaving: Boolean,
    onFormAddressChanged: (StructuredAddress) -> Unit,
    onApplyConflictSuggestion: () -> Unit,
    onSaveSubmitted: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // Address ↔ Geocode Conflict Alert Banner
        if (conflictWarning?.hasConflict == true) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CommerceColors.WarningSoft),
                shape = RoundedCornerShape(Radius.md),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = CommerceColors.Warning)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Address details conflict with selected map location.",
                            style = CommerceTypography.Caption,
                            fontWeight = FontWeight.Bold,
                            color = CommerceColors.TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Entered: ${formAddress.city} (${formAddress.postalCode})\nMap Pin: ${conflictWarning.suggestedCity} (${conflictWarning.suggestedPostalCode})",
                        style = CommerceTypography.Meta,
                        color = CommerceColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onApplyConflictSuggestion,
                            colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Warning),
                            shape = RoundedCornerShape(Radius.Chip)
                        ) {
                            Text("Use map location", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Tag Selector
        AddressTagSelector(
            selectedTag = formAddress.tag,
            onTagSelected = { onFormAddressChanged(formAddress.copy(tag = it)) }
        )

        // Structured Input Fields with Field Errors
        OutlinedTextField(
            value = formAddress.houseNumber,
            onValueChange = { onFormAddressChanged(formAddress.copy(houseNumber = it)) },
            label = { Text("Flat / House No. / Apartment *") },
            isError = validationResult.houseNumberError != null,
            supportingText = validationResult.houseNumberError?.let { { Text(it, color = CommerceColors.Danger) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = formAddress.building,
            onValueChange = { onFormAddressChanged(formAddress.copy(building = it)) },
            label = { Text("Building / Tower / Society Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = formAddress.floor,
                onValueChange = { onFormAddressChanged(formAddress.copy(floor = it)) },
                label = { Text("Floor No.") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = formAddress.street,
                onValueChange = { onFormAddressChanged(formAddress.copy(street = it)) },
                label = { Text("Street / Area / Sector *") },
                isError = validationResult.streetError != null,
                supportingText = validationResult.streetError?.let { { Text(it, color = CommerceColors.Danger) } },
                singleLine = true,
                modifier = Modifier.weight(2f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = formAddress.city,
                onValueChange = { onFormAddressChanged(formAddress.copy(city = it)) },
                label = { Text("City *") },
                isError = validationResult.cityError != null,
                supportingText = validationResult.cityError?.let { { Text(it, color = CommerceColors.Danger) } },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = formAddress.state,
                onValueChange = { onFormAddressChanged(formAddress.copy(state = it)) },
                label = { Text("State") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = formAddress.postalCode,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() }.take(6)
                    onFormAddressChanged(formAddress.copy(postalCode = filtered))
                },
                label = { Text("PIN code *") },
                isError = validationResult.postalCodeError != null,
                supportingText = validationResult.postalCodeError?.let { { Text(it, color = CommerceColors.Danger) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = formAddress.landmark,
            onValueChange = { onFormAddressChanged(formAddress.copy(landmark = it)) },
            label = { Text("Landmark (e.g. Near City Park)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Delivery Entrance Selector
        DeliveryEntranceSelector(
            selectedEntrance = formAddress.entrance,
            customEntranceDetails = formAddress.customEntranceDetails,
            customEntranceDetailsError = validationResult.customEntranceDetailsError,
            onEntranceSelected = { onFormAddressChanged(formAddress.copy(entrance = it)) },
            onCustomDetailsChanged = { onFormAddressChanged(formAddress.copy(customEntranceDetails = it)) }
        )

        // Delivery Instructions
        DeliveryInstructionsField(
            instructions = formAddress.deliveryInstructions,
            onInstructionsChanged = { onFormAddressChanged(formAddress.copy(deliveryInstructions = it)) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Recipient Details Selector
        RecipientSelector(
            recipientType = formAddress.recipientType,
            contactName = formAddress.contactName,
            contactPhone = formAddress.contactPhone,
            nameError = validationResult.contactNameError,
            phoneError = validationResult.contactPhoneError,
            onRecipientTypeChanged = { onFormAddressChanged(formAddress.copy(recipientType = it)) },
            onContactNameChanged = { onFormAddressChanged(formAddress.copy(contactName = it)) },
            onContactPhoneChanged = { onFormAddressChanged(formAddress.copy(contactPhone = it)) }
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = formAddress.isDefault,
                onCheckedChange = { onFormAddressChanged(formAddress.copy(isDefault = it)) }
            )
            Text("Make this my default delivery address", style = CommerceTypography.Caption, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // Save Button
        Button(
            onClick = onSaveSubmitted,
            enabled = !isSaving && validationResult.isValid,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Primary),
            shape = RoundedCornerShape(Radius.Button)
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CommerceColors.OnPrimary, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Saving address...", style = CommerceTypography.Label, fontWeight = FontWeight.Bold)
            } else {
                Text(if (formAddress.id.isBlank()) "Save address" else "Update address", style = CommerceTypography.Label, fontWeight = FontWeight.Bold)
            }
        }
    }
}
