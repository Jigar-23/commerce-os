package com.commerceos.android.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.BuildConfig
import com.commerceos.android.config.LocalClientConfiguration
import com.commerceos.android.network.NetworkClient
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing
import com.commerceos.android.viewmodel.AuthViewModel
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val context = LocalContext.current
    val uiState = viewModel.uiState
    val scrollState = rememberScrollState()
    val appName = LocalClientConfiguration.current.identity.appName

    var showServerConfig by rememberSaveable { mutableStateOf(false) }
    var serverHost by rememberSaveable { mutableStateOf(NetworkClient.baseUrl) }

    val activity = context as? Activity
    var resendingToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }

    fun dispatchGooglePhoneAuth() {
        val currentPhone = viewModel.uiState.phone
        android.util.Log.i("CommerceOSAuth", "dispatchGooglePhoneAuth clicked! currentPhone='$currentPhone'")
        val cleanDigits = currentPhone.replace(Regex("[^0-9]"), "").let {
            if (it.length > 10) it.takeLast(10) else it
        }
        android.util.Log.i("CommerceOSAuth", "cleanDigits='$cleanDigits', length=${cleanDigits.length}")
        if (cleanDigits.length != 10) {
            viewModel.setError("Please enter a valid 10-digit mobile number")
            return
        }
        val formatted = "+91$cleanDigits"
        viewModel.setLoading(true)

        if (activity == null) {
            android.util.Log.w("CommerceOSAuth", "activity is null, calling viewModel.sendOtp()")
            viewModel.sendOtp()
            return
        }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                android.util.Log.i("CommerceOSAuth", "onVerificationCompleted: code=${credential.smsCode}")
                val code = credential.smsCode
                if (!code.isNullOrBlank()) {
                    viewModel.onOtpChange(code)
                }
                FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnSuccessListener { authRes ->
                        authRes.user?.getIdToken(true)?.addOnSuccessListener { tokenRes ->
                            tokenRes.token?.let { idToken ->
                                android.util.Log.i("CommerceOSAuth", "Google idToken obtained, verifying with backend...")
                                viewModel.verifyWithGoogleToken(idToken)
                            }
                        }
                    }
                    .addOnFailureListener { err ->
                        android.util.Log.e("CommerceOSAuth", "Google sign-in credential failure: ${err.message}", err)
                        viewModel.setError("Google verification error: ${err.message}")
                    }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                android.util.Log.e("CommerceOSAuth", "Firebase Phone Auth failed: [${e.javaClass.simpleName}] ${e.message}", e)
                Toast.makeText(context, "Google SMS issue (${e.localizedMessage}). Using server gateway...", Toast.LENGTH_LONG).show()
                viewModel.setError("Firebase SMS: ${e.localizedMessage ?: e.message}")
                viewModel.setLoading(false)
                viewModel.sendOtp()
            }

            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                android.util.Log.i("CommerceOSAuth", "onCodeSent: verificationId=$verificationId")
                resendingToken = token
                viewModel.setGoogleVerification(verificationId)
                Toast.makeText(context, "Google SMS dispatched to $formatted", Toast.LENGTH_SHORT).show()
            }
        }

        val optionsBuilder = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber(formatted)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)

        resendingToken?.let { optionsBuilder.setForceResendingToken(it) }

        try {
            android.util.Log.i("CommerceOSAuth", "Triggering PhoneAuthProvider.verifyPhoneNumber for $formatted...")
            PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
        } catch (e: Exception) {
            android.util.Log.e("CommerceOSAuth", "PhoneAuthProvider exception: ${e.message}", e)
            viewModel.sendOtp()
        }
    }

    fun submitOtpVerification() {
        val enteredCode = viewModel.uiState.otpCode.trim()
        val googleVerId = viewModel.uiState.googleVerificationId
        android.util.Log.i("CommerceOSAuth", "submitOtpVerification: code='$enteredCode', googleVerId=$googleVerId")

        if (googleVerId != null && enteredCode.length == 6) {
            viewModel.setLoading(true)
            val credential = PhoneAuthProvider.getCredential(googleVerId, enteredCode)
            FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnSuccessListener { authRes ->
                    authRes.user?.getIdToken(true)?.addOnSuccessListener { tokenRes ->
                        val idToken = tokenRes.token
                        if (!idToken.isNullOrBlank()) {
                            viewModel.verifyWithGoogleToken(idToken)
                        } else {
                            viewModel.setError("Could not retrieve Google Auth Token")
                        }
                    }?.addOnFailureListener { err ->
                        android.util.Log.e("CommerceOSAuth", "getIdToken failed: ${err.message}", err)
                        viewModel.setError("Google token error: ${err.localizedMessage ?: err.message}")
                    }
                }
                .addOnFailureListener { err ->
                    android.util.Log.w("CommerceOSAuth", "Firebase credential error: ${err.message}")
                    viewModel.setLoading(false)
                    viewModel.setError(err.localizedMessage ?: "Invalid verification code. Please check and try again.")
                }
        } else {
            viewModel.verifyOtp()
        }
    }



    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = CommerceColors.SpeedYellow,
            shape = RoundedCornerShape(Radius.xl),
            shadowElevation = 4.dp,
            modifier = Modifier.size(72.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = CommerceColors.SushiInk,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = appName,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = CommerceColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "India's 10-minute delivery app",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = CommerceColors.PrimaryDark
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
            shape = RoundedCornerShape(Radius.xl),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CommerceColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Login or Sign up",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CommerceColors.TextPrimary
                )
                Text(
                    text = "Enter your 10-digit mobile number to continue",
                    fontSize = 12.sp,
                    color = CommerceColors.TextSecondary
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = CommerceColors.SurfaceSubtle,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CommerceColors.Border),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                            Text("+91", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CommerceColors.TextPrimary)
                        }
                    }

                    OutlinedTextField(
                        value = uiState.phone,
                        onValueChange = viewModel::onPhoneChange,
                        placeholder = { Text("Phone number", color = CommerceColors.TextMuted, fontSize = 14.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !uiState.isOtpSent && !uiState.isLoading,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CommerceColors.PrimaryDark,
                            unfocusedBorderColor = CommerceColors.Border,
                            focusedContainerColor = CommerceColors.Surface,
                            unfocusedContainerColor = CommerceColors.Surface
                        ),
                        modifier = Modifier.weight(1f).height(52.dp)
                    )
                }

                if (uiState.isOtpSent) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = CommerceColors.SuccessSoft,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CommerceColors.PrimaryDark, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "OTP sent to +91 ${uiState.phone}",
                                fontSize = 12.sp,
                                color = CommerceColors.PrimaryDark,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.otpCode,
                        onValueChange = viewModel::onOtpChange,
                        label = { Text("6-Digit OTP") },
                        placeholder = { Text("Enter OTP (e.g. 123456)") },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CommerceColors.PrimaryDark) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !uiState.isLoading,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CommerceColors.PrimaryDark,
                            unfocusedBorderColor = CommerceColors.Border,
                            focusedContainerColor = CommerceColors.Surface,
                            unfocusedContainerColor = CommerceColors.Surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "💡 Direct dev code 123456 verifies immediately",
                        fontSize = 11.sp,
                        color = CommerceColors.TextMuted,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }

                uiState.errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, fontSize = 12.sp, color = CommerceColors.Danger, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (!uiState.isOtpSent) {
                    Button(
                        onClick = { viewModel.sendOtp() },
                        enabled = !uiState.isLoading && uiState.phone.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CommerceColors.PrimaryDark,
                            disabledContainerColor = CommerceColors.Placeholder,
                            disabledContentColor = CommerceColors.TextMuted
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(color = CommerceColors.OnPrimary, modifier = Modifier.size(22.dp))
                        } else {
                            Text("Continue", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CommerceColors.OnPrimary)
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.verifyOtp() },
                        enabled = !uiState.isLoading && uiState.verifyAttemptsLeft > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Success),
                        shape = RoundedCornerShape(Radius.Button),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(color = CommerceColors.OnPrimary, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                if (uiState.verifyAttemptsLeft > 0) "Verify & Sign In" else "Attempts Exhausted",
                                style = CommerceTypography.Body,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        if (uiState.resendCountdownSeconds > 0) {
                            Text(
                                "Resend code in ${uiState.resendCountdownSeconds}s",
                                color = CommerceColors.TextMuted,
                                style = CommerceTypography.Label,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            TextButton(
                                onClick = { viewModel.resendOtp() },
                                enabled = !uiState.isLoading
                            ) {
                                Text("Resend Code", color = CommerceColors.Primary, style = CommerceTypography.Label, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(Spacing.md))
                        TextButton(onClick = viewModel::useDifferentNumber) {
                            Text("Change Number", color = CommerceColors.TextMuted, style = CommerceTypography.Label)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Server Host Config Section — visible in debug builds only.
        if (BuildConfig.DEBUG) {
            TextButton(onClick = { showServerConfig = !showServerConfig }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = CommerceColors.TextMuted, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = if (showServerConfig) "Hide Server Settings" else "Server Settings (${NetworkClient.baseUrl})",
                        color = CommerceColors.TextMuted,
                        style = CommerceTypography.Meta
                    )
                }
            }

            if (showServerConfig) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                
                // Quick preset buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            serverHost = "http://192.168.1.2:8083"
                            val prefs = context.getSharedPreferences("commerce_os_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit().putString("custom_api_base_url", serverHost).apply()
                            NetworkClient.baseUrl = serverHost
                            Toast.makeText(context, "Connected to Local Mac (192.168.1.2:8083)", Toast.LENGTH_SHORT).show()
                        },
                        label = { Text("Local Mac (192.168.1.2)", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold) }
                    )
                    SuggestionChip(
                        onClick = {
                            serverHost = "https://commerce-os-api.onrender.com"
                            val prefs = context.getSharedPreferences("commerce_os_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit().putString("custom_api_base_url", serverHost).apply()
                            NetworkClient.baseUrl = serverHost
                            Toast.makeText(context, "Connected to Cloud API", Toast.LENGTH_SHORT).show()
                        },
                        label = { Text("Cloud API", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold) }
                    )
                }
                
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = serverHost,
                        onValueChange = { newValue: String -> serverHost = newValue },
                        label = { Text("Backend Server Host / IP") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Button(
                        onClick = {
                            val cleanUrl = serverHost.trim().trimEnd('/')
                            val prefs = context.getSharedPreferences("commerce_os_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit().putString("custom_api_base_url", cleanUrl).apply()
                            NetworkClient.baseUrl = cleanUrl
                            Toast.makeText(context, "Server updated to ${NetworkClient.baseUrl}", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
