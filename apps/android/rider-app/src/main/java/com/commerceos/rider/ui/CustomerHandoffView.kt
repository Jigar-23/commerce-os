package com.commerceos.rider.ui

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
    val codAmountExpected = (session.codAmount ?: 0.0).toInt()

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Customer Header Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Home, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("DOORSTEP HANDOFF", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B), letterSpacing = 1.sp)
                        Text(session.customerName.ifBlank { "Customer" }, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }

                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = session.maskedCustomerPhone,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }
            }

            // Customer Delivery Address Box
            Surface(
                color = Color(0xFF1E293B).copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Delivery Address", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = session.customerAddress,
                        fontSize = 13.sp,
                        color = Color(0xFFCBD5E1),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

            // ==========================================
            // STEP 1: CASH COLLECTION (FOR COD ORDERS)
            // ==========================================
            if (session.isCod) {
                if (!session.codReconciled) {
                    Surface(
                        color = Color(0xFF271B0B),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
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
                                    Text("STEP 1: CASH TO COLLECT", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFFBBF24), letterSpacing = 0.5.sp)
                                    Text("Cash On Delivery (COD)", fontSize = 13.sp, color = Color(0xFFFDE68A))
                                }
                                Text(
                                    text = "₹$codAmountExpected",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFFBBF24)
                                )
                            }

                            // Quick Cash Amount Preset Chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = if (enteredCodAmount == codAmountExpected.toString()) Color(0xFFFBBF24) else Color(0xFF1E293B),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onCodAmountChange(codAmountExpected.toString()) }
                                ) {
                                    Text(
                                        text = "Exact ₹$codAmountExpected",
                                        color = if (enteredCodAmount == codAmountExpected.toString()) Color.Black else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    )
                                }

                                listOf(100, 200, 500).forEach { note ->
                                    if (note >= codAmountExpected) {
                                        Surface(
                                            color = if (enteredCodAmount == note.toString()) Color(0xFFFBBF24) else Color(0xFF1E293B),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onCodAmountChange(note.toString()) }
                                        ) {
                                            Text(
                                                text = "₹$note",
                                                color = if (enteredCodAmount == note.toString()) Color.Black else Color.White,
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
                                label = { Text("Amount Received from Customer (₹)", color = Color(0xFF94A3B8)) },
                                placeholder = { Text("e.g. $codAmountExpected", color = Color(0xFF64748B)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFFBBF24),
                                    unfocusedBorderColor = Color(0xFF475569),
                                    focusedLabelColor = Color(0xFFFBBF24),
                                    cursorColor = Color(0xFFFBBF24)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = onReconcileCod,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24)),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isLoading && enteredCodAmount.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        text = "CONFIRM CASH COLLECTED",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = Color.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // COD Collected & Reconciled Success Card
                    Surface(
                        color = Color(0xFF064E3B).copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(26.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("CASH COLLECTED & RECONCILED", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399), letterSpacing = 0.5.sp)
                                Text("₹${(session.codCollectedAmount ?: session.codAmount ?: 0.0).toInt()} received safely", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // STEP 2: DOORSTEP DELIVERY PIN VERIFICATION
            // ==========================================
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (!session.isCod || session.codReconciled) Color(0xFF38BDF8).copy(alpha = 0.5f) else Color(0xFF334155)),
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
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (session.isCod) "STEP 2: DELIVERY PIN" else "CUSTOMER DELIVERY PIN",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF38BDF8),
                                letterSpacing = 0.5.sp
                            )
                        }

                        if (resendCooldown > 0) {
                            Text(
                                text = "Resend PIN in ${resendCooldown}s",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            TextButton(
                                onClick = onResendOtp,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Resend PIN", fontSize = 12.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(
                        text = "Ask customer for the 4-digit Delivery PIN shown on their app screen to complete the handoff.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 16.sp
                    )

                    // Big, Bold Delivery PIN Input Field
                    OutlinedTextField(
                        value = enteredOtp,
                        onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) onOtpChange(it) },
                        placeholder = { Text("• • • •", fontSize = 24.sp, color = Color(0xFF475569), textAlign = TextAlign.Center) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            letterSpacing = 8.sp,
                            color = Color.White
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF475569),
                            cursorColor = Color(0xFF38BDF8)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!errorMessage.isNullOrBlank()) {
                        Surface(
                            color = Color(0xFF7F1D1D).copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ $errorMessage",
                                color = Color(0xFFFCA5A5),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    Button(
                        onClick = onVerifyOtp,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading && enteredOtp.trim().length >= 4 && (!session.isCod || session.codReconciled),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        } else {
                            Text(
                                text = "VERIFY PIN & COMPLETE DELIVERY",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = Color.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
