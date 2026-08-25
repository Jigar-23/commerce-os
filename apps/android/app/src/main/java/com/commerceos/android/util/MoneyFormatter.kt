package com.commerceos.android.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Single source of truth for displaying Indian Rupee amounts. Accepts any
 * numeric money representation (BigDecimal, Double, Int) and renders a stable
 * ₹-prefixed string. Fixes ad-hoc `"₹$amount"` string interpolation, which
 * mis-rendered trailing zeros (e.g. ₹19.5 instead of ₹19.50).
 *
 * NOTE: amounts are currently serialized as Double/Decimal on the wire. The
 * long-term fix is minor-unit (paise) integers end-to-end; the formatter at
 * least guarantees consistent presentation today.
 */
object MoneyFormatter {
    private val twoDecimals: DecimalFormat = DecimalFormat("0.00").apply {
        decimalFormatSymbols = DecimalFormatSymbols(Locale.US)
    }
    private val integerFormat: DecimalFormat = DecimalFormat("#,##0").apply {
        decimalFormatSymbols = DecimalFormatSymbols(Locale.US)
    }

    fun format(amount: BigDecimal?): String = if (amount == null) "₹0.00" else "₹${twoDecimals.format(amount)}"

    fun format(amount: Double?): String = if (amount == null) "₹0.00" else format(BigDecimal.valueOf(amount))

    fun format(amount: Int?): String = if (amount == null) "₹0" else "₹${integerFormat.format(amount)}"

    /** Indian grouping (e.g. ₹12,34,567.00). */
    fun formatIndian(amount: BigDecimal): String {
        val fixed = amount.setScale(2, RoundingMode.HALF_UP)
        val rupees = fixed.toBigInteger()
        val paise = fixed.subtract(BigDecimal(rupees)).movePointRight(2).setScale(0, RoundingMode.HALF_UP)
        return "₹${groupIndian(rupees.toString())}.${"%02d".format(paise.toInt())}"
    }

    private fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val grouped = rest.toList().reversed().chunked(2)
            .map { it.reversed().joinToString("") }
            .reversed()
            .joinToString(",")
        return "$grouped,$last3"
    }
}
