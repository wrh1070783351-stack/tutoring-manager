package com.wrh.keshiguanjia.logic

import com.wrh.keshiguanjia.data.Enrollment
import kotlin.math.roundToLong

/** 报名/续费的表单草稿，校验通过后交给仓库落库。 */
data class BillingDraft(
    val classId: Long,
    val billingType: Int,
    val sessions: Int = 0,
    val bonusSessions: Int = 0,
    val startDate: String? = null,
    val endDate: String? = null,
    /** 金额，单位：分（约定价格；未收时也记录，便于期末对账；期末计次类型结算时才写回） */
    val amountCents: Long = 0,
    val payDate: String,
    val method: Int = 0,
    val note: String = "",
    /** 次卡可选有效期，ISO 日期，空为不限 */
    val validUntil: String? = null,
    /** false = 定课未缴费（期末一起结），不生成缴费记录 */
    val paymentReceived: Boolean = true,
    /** 期末计次单价（分/次） */
    val unitPriceCents: Long = 0,
)

object Billing {

    /** 已购总次数 = 各课时包（购买 + 赠送）之和。 */
    fun purchasedSessions(packages: List<com.wrh.keshiguanjia.data.ClassPackage>): Int =
        packages.sumOf { it.totalSessions + it.bonusSessions }

    /** 剩余次数 = 已购总次数 − 已消课次数（到课与缺勤扣次，请假不扣）。可为负，交由界面高亮提示。 */
    fun remainingSessions(packages: List<com.wrh.keshiguanjia.data.ClassPackage>, consumed: Int): Int =
        purchasedSessions(packages) - consumed

    /** 学期制的当前有效期终点（取最晚的结束日期）。 */
    fun termValidUntil(termEndDates: List<String>): String? =
        termEndDates.maxOrNull()

    fun validate(draft: BillingDraft): String? = when {
        draft.classId <= 0 -> "请选择班级"
        draft.billingType == Enrollment.BILLING_SESSIONS && draft.sessions <= 0 -> "购买次数必须大于 0"
        draft.billingType == Enrollment.BILLING_SESSIONS && draft.bonusSessions < 0 -> "赠送次数不能为负"
        draft.billingType != Enrollment.BILLING_SESSIONS && draft.startDate.isNullOrBlank() -> "请填写开始日期"
        draft.billingType != Enrollment.BILLING_SESSIONS &&
            (draft.endDate.isNullOrBlank() || dateOrNull(draft.endDate!!) == null) -> "结束日期格式应为 yyyy-MM-dd"
        draft.billingType != Enrollment.BILLING_SESSIONS &&
            (dateOrNull(draft.startDate!!) == null || draft.startDate > draft.endDate!!) -> "开始日期必须早于或等于结束日期"
        draft.billingType == Enrollment.BILLING_TERM_SESSIONS && draft.unitPriceCents <= 0 -> "单价必须大于 0"
        draft.amountCents < 0 -> "金额不能为负"
        dateOrNull(draft.payDate) == null -> "缴费日期格式应为 yyyy-MM-dd"
        else -> null
    }

    internal fun dateOrNull(iso: String): java.time.LocalDate? = try {
        java.time.LocalDate.parse(iso.trim())
    } catch (e: Exception) {
        null
    }
}

object MoneyUtils {

    /** 12050 -> "120.50"（元，去尾零可选） */
    fun yuanText(cents: Long): String {
        val negative = cents < 0
        val abs = if (negative) -cents else cents
        val text = "%d.%02d".format(abs / 100, abs % 100)
        return if (negative) "-$text" else text
    }

    /**
     * 用户输入的元字符串 -> 分；非法或负数返回 null。
     * 支持基础算式（+ - * / × ÷，先乘除后加减），如 "500+300"、"1200*2-100"，自动算出结果。
     */
    fun parseYuanToCents(input: String): Long? {
        val trimmed = input.trim().removePrefix("¥").trim()
        if (trimmed.isEmpty()) return null
        val yuan = evaluate(trimmed) ?: return null
        if (yuan < 0) return null
        return (yuan * 100).roundToLong()
    }

    /** 求值只含数字与 + - * / × ÷ 的算式；每个数字最多两位小数；除零/非法返回 null。 */
    internal fun evaluate(expr: String): Double? {
        val s = expr.replace("×", "*").replace("÷", "/").filterNot { it == ' ' }
        if (s.isEmpty()) return null
        var pos = 0

        fun number(): Double? {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            if (pos == start) return null
            val token = s.substring(start, pos)
            if (token.count { it == '.' } > 1) return null
            if (token.substringAfter('.', "").length > 2) return null
            return token.toDoubleOrNull()
        }

        fun factor(): Double? = number()

        fun term(): Double? {
            var acc = factor() ?: return null
            while (pos < s.length && (s[pos] == '*' || s[pos] == '/')) {
                val op = s[pos]
                pos++
                val rhs = factor() ?: return null
                if (op == '*') {
                    acc *= rhs
                } else {
                    if (rhs == 0.0) return null
                    acc /= rhs
                }
            }
            return acc
        }

        fun expr(): Double? {
            var acc = term() ?: return null
            while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                val op = s[pos]
                pos++
                val rhs = term() ?: return null
                acc = if (op == '+') acc + rhs else acc - rhs
            }
            return acc
        }

        val result = expr()
        return if (result != null && !result.isNaN() && pos == s.length) result else null
    }
}
