package com.commerceos.rider.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.rider.model.ServerDeliverySession
import com.commerceos.rider.theme.RiderColors

@Composable
fun CustomerHandoffView(
    session: ServerDeliverySession,
    enteredOtp: String,
    onOtpChange: (String) -> Unit,
    onVerifyOtp: () -> Unit,
    onResendOtp: () -> Unit,
    resendCooldown: Int,
    enteredCodAmount: String,
    onCodAmountChange: (String) -> Unit,
    onReconcileCod: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val codAmountExpected = (session.codAmount ?: 0.0).toInt()

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
        border = BorderStroke(1.dp, RiderColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Customer Header Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = RiderColors.WarningSoft,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Home, contentDescription = null, tint = RiderColors.Warning, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("DOORSTEP HANDOFF", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RiderColors.Warning, letterSpacing = 0.8.sp)
                        Text(session.customerName.ifBlank { "Customer" }, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary)
                    }
                }

                Surface(
                    color = RiderColors.AccentBlueSoft,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RiderColors.AccentBlue.copy(alpha = 0.3f)),
                    modifier = Modifier.clickable {
                        val ph = session.customerPhone.ifBlank { session.maskedCustomerPhone }
                        com.commerceos.rider.util.RiderNavigationUtils.dialPhoneNumber(context, ph)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = "Call Customer", tint = RiderColors.AccentBlueText, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = session.maskedCustomerPhone,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = RiderColors.AccentBlueText
                        )
                    }
                }
            }

            // Customer Delivery Address Box
            Surface(
                color = RiderColors.SurfaceSubtle,
                border = BorderStroke(1.dp, RiderColors.Border),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Delivery Address", fontSize = 11.sp, color = RiderColors.TextMuted, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = session.customerAddress,
                        fontSize = 13.sp,
                        color = RiderColors.TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp)

            // STEP 1: CASH COLLECTION (FOR COD ORDERS)
            if (session.isCod) {
                if (!session.codReconciled) {
                    Surface(
                        color = RiderColors.WarningSoft.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, RiderColors.Warning),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("STEP 1: CASH TO COLLECT", fontSize = 11.sp, fontWeight = FontWeight.Black, color = RiderColors.WarningText, letterSpacing = 0.5.sp)
                                    Text("Cash On Delivery (COD)", fontSize = 13.sp, color = RiderColors.WarningText, fontWeight = FontWeight.Medium)
                                }
                                Text(
                                    text = "₹$codAmountExpected",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = RiderColors.WarningText
                                )
                            }

                            // Quick Cash Amount Preset Chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = if (enteredCodAmount == codAmountExpected.toString()) RiderColors.Warning else RiderColors.Surface,
                                    border = BorderStroke(1.dp, if (enteredCodAmount == codAmountExpected.toString()) RiderColors.Warning else RiderColors.Border),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onCodAmountChange(codAmountExpected.toString()) }
                                ) {
                                    Text(
                                        text = "Exact ₹$codAmountExpected",
                                        color = if (enteredCodAmount == codAmountExpected.toString()) Color.White else RiderColors.TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    )
                                }

                                listOf(100, 200, 500).forEach { note ->
                                    if (note >= codAmountExpected) {
                                        Surface(
                                            color = if (enteredCodAmount == note.toString()) RiderColors.Warning else RiderColors.Surface,
                                            border = BorderStroke(1.dp, if (enteredCodAmount == note.toString()) RiderColors.Warning else RiderColors.Border),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onCodAmountChange(note.toString()) }
                                        ) {
                                            Text(
                                                text = "₹$note",
                                                color = if (enteredCodAmount == note.toString()) Color.White else RiderColors.TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 10.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Manual Amount Input
                            OutlinedTextField(
                                value = enteredCodAmount,
                                onValueChange = onCodAmountChange,
                                label = { Text("Amount Received from Customer (₹)", color = RiderColors.TextMuted) },
                                placeholder = { Text("e.g. $codAmountExpected", color = RiderColors.TextMuted) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RiderColors.Warning,
                                    unfocusedBorderColor = RiderColors.Border,
                                    focusedLabelColor = RiderColors.Warning,
                                    cursorColor = RiderColors.Warning,
                                    focusedContainerColor = RiderColors.Surface,
                                    unfocusedContainerColor = RiderColors.Surface
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = onReconcileCod,
                                colors = ButtonDefaults.buttonColors(containerColor = RiderColors.Warning),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isLoading && enteredCodAmount.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        text = "CONFIRM CASH COLLECTED",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // COD Collected & Reconciled Success Card
                    Surface(
                        color = RiderColors.PrimarySoft,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, RiderColors.Primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RiderColors.Primary, modifier = Modifier.size(26.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("CASH COLLECTED & RECONCILED", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RiderColors.PrimarySoftText, letterSpacing = 0.5.sp)
                                Text("₹${(session.codCollectedAmount ?: session.codAmount ?: 0.0).toInt()} received safely", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RiderColors.PrimarySoftText)
                            }
                        }
                    }
                }
            }

            // STEP 2: DOORSTEP DELIVERY PIN VERIFICATION
            Surface(
                color = RiderColors.Surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (!session.isCod || session.codReconciled) RiderColors.Primary else RiderColors.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = RiderColors.AccentBlue, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (session.isCod) "STEP 2: DELIVERY PIN" else "CUSTOMER DELIVERY PIN",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = RiderColors.AccentBlue,
                                letterSpacing = 0.5.sp
                            )
                        }

                        if (resendCooldown > 0) {
                            Text(
                                text = "Resend PIN in ${resendCooldown}s",
                                fontSize = 11.sp,
                                color = RiderColors.TextMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            TextButton(
                                onClick = onResendOtp,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Resend PIN", fontSize = 12.sp, color = RiderColors.AccentBlueText, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(
                        text = "Ask customer for the 6-digit Delivery PIN shown on their app screen to complete the handoff.",
                        fontSize = 12.sp,
                        color = RiderColors.TextSecondary,
                        lineHeight = 16.sp
                    )

                    // Big, Bold Delivery PIN Input Field
                    OutlinedTextField(
                        value = enteredOtp,
                        onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) onOtpChange(it) },
                        placeholder = { Text("• • • • • •", fontSize = 24.sp, color = RiderColors.Border, textAlign = TextAlign.Center) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            letterSpacing = 8.sp,
                            color = RiderColors.TextPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RiderColors.Primary,
                            unfocusedBorderColor = RiderColors.Border,
                            cursorColor = RiderColors.Primary,
                            focusedContainerColor = RiderColors.SurfaceSubtle,
                            unfocusedContainerColor = RiderColors.SurfaceSubtle
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!errorMessage.isNullOrBlank()) {
                        Surface(
                            color = RiderColors.DangerSoft,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ $errorMessage",
                                color = RiderColors.DangerText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    Button(
                        onClick = onVerifyOtp,
                        colors = ButtonDefaults.buttonColors(containerColor = RiderColors.Primary),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading && enteredOtp.trim().length in 4..6 && (!session.isCod || session.codReconciled),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        } else {
                            Text(
                                text = "VERIFY PIN & COMPLETE DELIVERY",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
