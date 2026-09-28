package com.wrh.keshiguanjia.logic

import com.wrh.keshiguanjia.data.Enrollment

/** 报名/续费的表单草稿，校验通过后交给仓库落库。 */
data class BillingDraft(
    val classId: Long,
    val billingType: Int,
    val sessions: Int = 0,
    val bonusSessions: Int = 0,
    val startDate: String? = null,
    val endDate: String? = null,
    /** 金额，单位：分（约定价格；未收时也记录，便于期末对账） */
    val amountCents: Long = 0,
    val payDate: String,
    val method: Int = 0,
    val note: String = "",
    /** 次卡可选有效期，ISO 日期，空为不限 */
    val validUntil: String? = null,
    /** false = 定课未缴费（期末一起结），不生成缴费记录 */
    val paymentReceived: Boolean = true,
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
        draft.billingType == Enrollment.BILLING_TERM && draft.startDate.isNullOrBlank() -> "请填写开始日期"
        draft.billingType == Enrollment.BILLING_TERM && (draft.endDate.isNullOrBlank() ||
            dateOrNull(draft.endDate!!) == null) -> "结束日期格式应为 yyyy-MM-dd"
        draft.billingType == Enrollment.BILLING_TERM &&
            (dateOrNull(draft.startDate!!) == null || draft.startDate > draft.endDate!!) -> "开始日期必须早于或等于结束日期"
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

    /** 用户输入的元字符串 -> 分；非法或负数返回 null。"120" -> 12000，"120.5" -> 12050 */
    fun parseYuanToCents(input: String): Long? {
        val trimmed = input.trim().removePrefix("¥").trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(".")
        if (parts.size > 2) return null
        val yuan = parts[0].toLongOrNull() ?: return null
        val cents = when (parts.size) {
            1 -> 0L
            else -> {
                if (parts[1].length > 2) return null
                (parts[1].padEnd(2, '0')).toLongOrNull() ?: return null
            }
        }
        if (yuan < 0 || cents < 0) return null
        return yuan * 100 + cents
    }
}
