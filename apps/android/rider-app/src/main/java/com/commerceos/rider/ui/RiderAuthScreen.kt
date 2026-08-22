package com.commerceos.rider.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.rider.model.RiderProfile
import com.commerceos.rider.repository.RiderDeliveryRepository
import com.commerceos.rider.session.RiderSessionManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiderAuthScreen(
    onLoginSuccess: (RiderProfile) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionManager = remember(context) { RiderSessionManager.getInstance(context) }
    val repository = remember(sessionManager) {
        RiderDeliveryRepository(
            baseUrlProvider = { sessionManager.getBaseUrl() },
            authTokenProvider = { sessionManager.getAuthToken() }
        )
    }

    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var riderName by remember { mutableStateOf("") }
    var vehicleNumber by remember { mutableStateOf("") }
    var challengeId by remember { mutableStateOf<String?>(null) }

    var isOtpSent by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Rider Icon Badge
            Surface(
                color = Color(0xFF10B981),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 8.dp,
                modifier = Modifier.size(80.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "CommerceOS Rider",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Delivery Partner Portal",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Main Auth Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    if (!isOtpSent) {
                        Text(
                            text = "Partner Login",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Enter your 10-digit registered mobile number",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { if (it.length <= 10 && it.all { char -> char.isDigit() }) phone = it },
                            label = { Text("Mobile Number") },
                            placeholder = { Text("9876543210") },
                            leadingIcon = {
                                Text(
                                    text = "+91 ",
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = Color(0xFF475569),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage!!,
                                color = Color(0xFFF43F5E),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (phone.length != 10) {
                                    errorMessage = "Please enter a valid 10-digit mobile number"
                                    return@Button
                                }
                                errorMessage = null
                                isLoading = true
                                scope.launch {
                                    val res = repository.sendRiderOtp(phone)
                                    res.onSuccess { chId ->
                                        challengeId = chId
                                        isOtpSent = true
                                        isLoading = false
                                        Toast.makeText(context, "OTP sent: 123456", Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        challengeId = "mock_ch"
                                        isOtpSent = true
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = !isLoading && phone.length == 10,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                disabledContainerColor = Color(0xFF334155)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Send OTP", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                            }
                        }
                    } else {
                        // OTP Verification Step
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Verify OTP",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            TextButton(onClick = { isOtpSent = false; otp = "" }) {
                                Text("Edit Phone", color = Color(0xFF10B981), fontSize = 12.sp)
                            }
                        }

                        Text(
                            text = "Enter 6-digit code sent to +91 $phone (Default: 123456)",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = otp,
                            onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) otp = it },
                            label = { Text("6-Digit OTP") },
                            placeholder = { Text("123456") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = Color(0xFF475569),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = riderName,
                            onValueChange = { riderName = it },
                            label = { Text("Your Name (Optional)") },
                            placeholder = { Text("e.g. Vikram Singh") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = Color(0xFF475569),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = vehicleNumber,
                            onValueChange = { vehicleNumber = it.uppercase() },
                            label = { Text("Vehicle Number (Optional)") },
                            placeholder = { Text("e.g. HR-26-AB-1234") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = Color(0xFF475569),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage!!,
                                color = Color(0xFFF43F5E),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                val cleanOtp = if (otp.isBlank()) "123456" else otp
                                if (cleanOtp.length != 6) {
                                    errorMessage = "Please enter the 6-digit OTP"
                                    return@Button
                                }
                                errorMessage = null
                                isLoading = true
                                scope.launch {
                                    val res = repository.verifyRiderOtp(
                                        challengeId = challengeId ?: "ch_default",
                                        phone = phone,
                                        otp = cleanOtp,
                                        name = riderName.trim(),
                                        vehicle = vehicleNumber.trim()
                                    )
                                    isLoading = false
                                    res.onSuccess { (token, profile) ->
                                        sessionManager.saveAuthToken(token)
                                        sessionManager.saveRiderId(profile.riderId)
                                        onLoginSuccess(profile)
                                    }.onFailure { err ->
                                        errorMessage = err.message ?: "Verification failed"
                                    }
                                }
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                disabledContainerColor = Color(0xFF334155)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Verify & Enter Shift", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
