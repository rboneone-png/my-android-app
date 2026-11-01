package com.rboneone.stockrecord.ui

import com.rboneone.stockrecord.data.Market
import java.time.LocalDate

fun formatMoney(market: Market, value: Double): String = when (market) {
    Market.TW -> "NT$ %,.0f".format(value)
    Market.US -> "US$ %,.2f".format(value)
}

fun formatPrice(value: Double): String = "%,.2f".format(value)

fun formatShares(value: Double): String =
    if (value % 1.0 == 0.0) "%,.0f".format(value)
    else "%,.4f".format(value).trimEnd('0').trimEnd('.')

/** 給輸入框用的數字字串，不加千分位 */
fun plainNumber(value: Double): String =
    if (value % 1.0 == 0.0) "%.0f".format(value)
    else "%.6f".format(value).trimEnd('0').trimEnd('.')

fun formatDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()

fun String.toAmountOrNull(): Double? = replace(",", "").trim().toDoubleOrNull()

fun formatSignedPrice(value: Double): String = (if (value > 0.0001) "+" else "") + "%,.2f".format(value)

fun formatPct(value: Double): String = (if (value > 0.0001) "+" else "") + "%.2f%%".format(value)

private val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm")

fun formatTime(epochMillis: Long): String =
    java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()).format(timeFormatter)

/** 不加幣別符號的金額：台股整數、美股兩位小數 */
fun formatAmount(market: Market, value: Double): String = when (market) {
    Market.TW -> "%,.0f".format(value)
    Market.US -> "%,.2f".format(value)
}

fun formatSignedAmount(market: Market, value: Double): String =
    (if (value > 0.0001) "+" else "") + formatAmount(market, value)

/** 月報酬格子用的精簡金額，例如 +34.8萬、-4.86萬、+5,070 */
fun formatWan(value: Double): String {
    val sign = if (value > 0.0001) "+" else if (value < -0.0001) "-" else ""
    val abs = kotlin.math.abs(value)
    if (abs < 10_000) return sign + "%,.0f".format(abs)
    val w = abs / 10_000
    val text = when {
        w >= 100 -> "%.0f".format(w)
        w >= 10 -> "%.1f".format(w)
        else -> "%.2f".format(w)
    }
    return "$sign${text}萬"
}

private val clockFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")

fun formatClock(epochMillis: Long): String =
    java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()).format(clockFormatter)
