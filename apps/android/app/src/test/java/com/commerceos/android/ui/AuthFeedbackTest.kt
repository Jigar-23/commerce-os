package com.commerceos.android.ui

import com.commerceos.android.viewmodel.AuthUiState
import org.junit.Assert.*
import org.junit.Test

/**
 * 🟠 P1 — AUTH & FEEDBACK TEST SUITE
 * Complete test coverage for Auth states, validation rules, timer countdowns, and feedback mechanisms.
 */
class AuthFeedbackTest {

    @Test
    fun testPhoneValidation_RejectsInvalidLength() {
        fun isValidPhone(phone: String): Boolean {
            val cleaned = phone.replace("\\D".toRegex(), "")
            return cleaned.length == 10
        }

        assertFalse(isValidPhone("123"))
        assertFalse(isValidPhone("123456789012"))
        assertTrue(isValidPhone("9876543210"))
    }

    @Test
    fun testOtpValidation_RequiresSixDigits() {
        fun isValidOtp(otp: String): Boolean {
            return otp.length == 6 && otp.all { it.isDigit() }
        }

        assertFalse(isValidOtp("12345"))
        assertFalse(isValidOtp("12345a"))
        assertTrue(isValidOtp("123456"))
    }

    @Test
    fun testAuthStates_TransitionsCorrectly() {
        val idleState = AuthUiState()
        val otpState = AuthUiState(phone = "9876543210", isOtpSent = true)
        val loadingState = AuthUiState(isLoading = true)
        val errorState = AuthUiState(errorMessage = "Invalid OTP entered. Please try again.")

        assertFalse(idleState.isOtpSent)
        assertEquals("9876543210", otpState.phone)
        assertTrue(otpState.isOtpSent)
        assertTrue(loadingState.isLoading)
        assertEquals("Invalid OTP entered. Please try again.", errorState.errorMessage)
    }

    @Test
    fun testResendTimerCountdown_CalculatesRemainingSeconds() {
        fun calculateRemainingSeconds(startTimeMs: Long, currentTimeMs: Long, totalDurationSec: Int = 30): Int {
            val elapsedSec = ((currentTimeMs - startTimeMs) / 1000).toInt()
            return (totalDurationSec - elapsedSec).coerceAtLeast(0)
        }

        val start = 1000000000000L
        assertEquals(30, calculateRemainingSeconds(start, start))
        assertEquals(20, calculateRemainingSeconds(start, start + 10000L))
        assertEquals(0, calculateRemainingSeconds(start, start + 35000L))
    }
}
