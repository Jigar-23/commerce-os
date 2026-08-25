package com.commerceos.android.usecase

import com.commerceos.android.model.AuthResponse
import com.commerceos.android.model.SendOtpResponse
import com.commerceos.android.network.ApiResult
import com.commerceos.android.repository.AppRepository

/**
 * Auth domain operations. ViewModels depend on use cases, never on the raw
 * repository — UI screens in particular must not receive a repository.
 * Errors travel as [ApiResult.Failure] so the UI can distinguish "wrong code",
 * "attempts exhausted", "resend cooldown" and "network down".
 */
class AuthUseCase(private val repository: AppRepository) {
    suspend fun sendOtp(phone: String): ApiResult<SendOtpResponse> = repository.sendOtp(phone)

    suspend fun verifyOtp(challengeId: String, phone: String, code: String): ApiResult<AuthResponse> =
        repository.verifyOtp(challengeId, phone, code)
}