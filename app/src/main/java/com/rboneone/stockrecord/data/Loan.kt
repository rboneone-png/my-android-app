package com.rboneone.stockrecord.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LoanType(val label: String) {
    HOUSING("房貸"),
    PERSONAL("信貸"),
    CAR("車貸"),
    OTHER("其他"),
}

/**
 * 帳務管理的貸款（台幣）。每月繳款日自動從帳戶可用餘額扣款，採本息平均攤還（每月繳款固定）。
 *
 * [paidPeriods] 是建立貸款時已經繳過的期數；從 [startDate]（建立日）之後的每個繳款日開始自動扣款，
 * 直到繳滿 [totalPeriods] 期。
 */
@Entity(tableName = "loans")
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: LoanType,
    /** 貸款金額（元） */
    val principal: Double,
    /** 年利率（%） */
    val annualRatePct: Double,
    /** 貸款期數（每月一期） */
    val totalPeriods: Int,
    /** 建立貸款時已繳的期數，預設 0 */
    val paidPeriods: Int = 0,
    /** 每月繳款日（1～31；該月沒有那一天時，改在當月最後一天扣款） */
    val payDay: Int,
    /** 建立日 LocalDate.toEpochDay()：自動扣款從這天之後的繳款日開始 */
    val startDate: Long,
    val note: String = "",
)
