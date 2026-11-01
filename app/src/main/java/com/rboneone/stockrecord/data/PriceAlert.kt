package com.rboneone.stockrecord.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.abs

/** 通知條件：漲跌類以「今天相對昨日收盤」計算；股價類直接比較現價 */
enum class AlertType(val label: String, val isPercent: Boolean) {
    RISE_AMOUNT("股價漲幅大於等於設定值", false),
    FALL_AMOUNT("股價跌幅大於等於設定值", false),
    RISE_PCT("股價漲幅比率大於等於設定值", true),
    FALL_PCT("股價跌幅比率大於等於設定值", true),
    PRICE_ABOVE("股價高於等於設定值", false),
    PRICE_BELOW("股價低於等於設定值", false),
}

@Entity(tableName = "price_alerts")
data class PriceAlert(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val market: Market,
    val symbol: String,
    val name: String,
    val type: AlertType,
    /** 漲跌金額（元／美元）或百分比數字，例如 3 代表 3% */
    val threshold: Double,
    val enabled: Boolean = true,
    /** 上次通知的交易日（LocalDate.toEpochDay），同一交易日只通知一次 */
    val lastTriggeredDay: Long? = null,
) {
    /**
     * 漲幅：上漲幅度 ≥ 設定值。
     * 跌幅：下跌幅度 ≥ 設定值（設定值不論正負都視為幅度），即漲跌 ≤ −|設定值|。
     */
    fun isTriggered(price: Double, previousClose: Double): Boolean {
        val t = abs(threshold)
        // 股價類只看現價，不需要昨日收盤
        when (type) {
            AlertType.PRICE_ABOVE -> return price >= t
            AlertType.PRICE_BELOW -> return price > 0 && price <= t
            else -> Unit
        }
        if (previousClose <= 0) return false
        val change = price - previousClose
        val pct = change / previousClose * 100
        return when (type) {
            AlertType.RISE_AMOUNT -> change >= t
            AlertType.FALL_AMOUNT -> -change >= t
            AlertType.RISE_PCT -> pct >= t
            AlertType.FALL_PCT -> -pct >= t
            AlertType.PRICE_ABOVE, AlertType.PRICE_BELOW -> false
        }
    }

    /** 例如「漲幅 ≥ 50 元」、「跌幅比率 ≥ 3%」 */
    fun conditionText(): String {
        val t = abs(threshold)
        val unit = if (market == Market.TW) "元" else "美元"
        val v = if (t % 1.0 == 0.0) "%.0f".format(t) else "%.2f".format(t)
        return when (type) {
            AlertType.RISE_AMOUNT -> "漲幅 ≥ $v $unit"
            AlertType.FALL_AMOUNT -> "跌幅 ≥ $v $unit"
            AlertType.RISE_PCT -> "漲幅比率 ≥ $v%"
            AlertType.FALL_PCT -> "跌幅比率 ≥ $v%"
            AlertType.PRICE_ABOVE -> "股價 ≥ $v $unit"
            AlertType.PRICE_BELOW -> "股價 ≤ $v $unit"
        }
    }
}
