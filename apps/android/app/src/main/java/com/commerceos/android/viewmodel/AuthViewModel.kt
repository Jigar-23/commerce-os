package com.commerceos.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commerceos.android.network.ApiResult
import com.commerceos.android.network.AppError
import com.commerceos.android.usecase.AuthUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** One-shot auth outcome. The root observes this to log the user in — the UI never
 *  writes the session itself. */
sealed interface AuthEvent {
    data class Authenticated(
        val customerId: String,
        val phone: String,
        val accessToken: String,
        val refreshToken: String
    ) : AuthEvent
}

/** UI-facing auth state. All fields immutable; the ViewModel is the only writer. */
data class AuthUiState(
    val phone: String = "",
    val otpCode: String = "",
    val challengeId: String = "",
    val isOtpSent: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val resendCountdownSeconds: Int = 0,
    val verifyAttemptsLeft: Int = 5,
    val otpExpiresInSeconds: Int? = null,
    val otpExpiresCountdownSeconds: Int = 0
)

/**
 * OTP sign-in. The OTP value NEVER enters the client; the server returns a
 * challengeId plus its throttle contract (expiry + resend cooldown), and enforces
 * attempt limits. Client-side counters are UX-only mirrors of the server's truth.
 */
class AuthViewModel(private val authUseCase: AuthUseCase) : ViewModel() {

    var uiState by mutableStateOf(AuthUiState())
        private set

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var countdownJob: Job? = null
    private var expiryJob: Job? = null

    companion object {
        private const val DEFAULT_COOLDOWN_SECONDS = 30L
        private const val MAX_VERIFY_ATTEMPTS = 5
    }

    fun onPhoneChange(value: String) {
        uiState = uiState.copy(phone = value, errorMessage = null)
    }

    fun onOtpChange(value: String) {
        uiState = uiState.copy(otpCode = value, errorMessage = null)
    }

    fun useDifferentNumber() {
        countdownJob?.cancel()
        expiryJob?.cancel()
        uiState = uiState.copy(
            isOtpSent = false,
            otpCode = "",
            challengeId = "",
            errorMessage = null,
            resendCountdownSeconds = 0,
            verifyAttemptsLeft = MAX_VERIFY_ATTEMPTS,
            otpExpiresInSeconds = null,
            otpExpiresCountdownSeconds = 0
        )
    }

    fun sendOtp() {
        if (uiState.isLoading) return
        val digitsOnly = uiState.phone.replace(Regex("[^0-9]"), "")
        if (digitsOnly.length < 10 || digitsOnly.length > 15) {
            uiState = uiState.copy(errorMessage = "Please enter a valid 10-digit mobile phone number")
            return
        }
        val formatted = if (uiState.phone.startsWith("+")) uiState.phone else "+91$digitsOnly"
        uiState = uiState.copy(phone = formatted, isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = authUseCase.sendOtp(formatted)) {
                is ApiResult.Success -> {
                    uiState = uiState.copy(
                        isLoading = false,
                        isOtpSent = true,
                        challengeId = result.data.challengeId,
                        verifyAttemptsLeft = MAX_VERIFY_ATTEMPTS,
                        otpExpiresInSeconds = result.data.expiresInSeconds,
                        errorMessage = null
                    ).also {
                        startResendCountdown(result.data.resendAfterSeconds)
                        startExpiryCountdown(result.data.expiresInSeconds)
                    }
                }
                is ApiResult.Failure -> {
                    uiState = uiState.copy(isLoading = false, errorMessage = result.error.message)
                }
            }
        }
    }

    fun resendOtp() {
        if (uiState.isLoading || uiState.resendCountdownSeconds > 0) return
        // Server throttle governs resend; requesting again mints a fresh challenge.
        sendOtp()
    }

    private fun startResendCountdown(resendAfterSeconds: Int) {
        countdownJob?.cancel()
        val seconds = (resendAfterSeconds.toLong()).coerceAtLeast(0L).let {
            if (it == 0L) DEFAULT_COOLDOWN_SECONDS else it
        }
        countdownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                uiState = uiState.copy(resendCountdownSeconds = remaining.toInt())
                delay(1000)
                remaining--
            }
            uiState = uiState.copy(resendCountdownSeconds = 0)
        }
    }

    private fun startExpiryCountdown(expiresInSeconds: Int) {
        expiryJob?.cancel()
        expiryJob = viewModelScope.launch {
            var remaining = expiresInSeconds.toLong().coerceAtLeast(0L)
            while (remaining > 0) {
                uiState = uiState.copy(otpExpiresCountdownSeconds = remaining.toInt())
                delay(1000)
                remaining--
            }
            uiState = uiState.copy(otpExpiresCountdownSeconds = 0)
        }
    }

    fun verifyOtp() {
        if (uiState.isLoading) return
        if (uiState.verifyAttemptsLeft <= 0) {
            uiState = uiState.copy(errorMessage = "Too many attempts. Request a fresh OTP to continue.")
            return
        }
        val code = uiState.otpCode.trim()
        if (code.isBlank()) {
            uiState = uiState.copy(errorMessage = "Please enter the 6-digit SMS code received")
            return
        }
        if (uiState.challengeId.isBlank()) {
            uiState = uiState.copy(errorMessage = "OTP session expired. Request a new code.")
            return
        }
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = authUseCase.verifyOtp(uiState.challengeId, uiState.phone, code)) {
                is ApiResult.Success -> {
                    expiryJob?.cancel()
                    uiState = uiState.copy(isLoading = false)
                    _events.trySend(
                        AuthEvent.Authenticated(
                            customerId = result.data.userId,
                            phone = uiState.phone,
                            accessToken = result.data.accessToken,
                            refreshToken = result.data.refreshToken
                        )
                    )
                }
                is ApiResult.Failure -> {
                    val error = result.error
                    val server = error as? AppError.Server
                    val attemptsLeft = server?.attemptsLeft ?: (uiState.verifyAttemptsLeft - 1)
                    val retryAfter = server?.retryAfterSeconds
                    val message = when {
                        server?.errorCode == "OTP_LOCKED" || attemptsLeft <= 0 ->
                            "Invalid OTP. Attempts exhausted — request a fresh OTP."
                        server?.errorCode == "OTP_EXPIRED" ->
                            "OTP expired. Request a new code."
                        error is AppError.Network || error is AppError.Unknown ->
                            error.message
                        else ->
                            "Incorrect OTP code. $attemptsLeft attempt(s) remaining."
                    }
                    if (retryAfter != null && retryAfter > 0) startResendCountdown(retryAfter)
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = message,
                        verifyAttemptsLeft = attemptsLeft.coerceAtLeast(0)
                    )
                }
            }
        }
    }

    override fun onCleared() {
        countdownJob?.cancel()
        expiryJob?.cancel()
        super.onCleared()
    }
}
