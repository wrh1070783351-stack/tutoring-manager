package com.wrh.keshiguanjia.logic

import android.content.Context
import com.wrh.keshiguanjia.data.Enrollment
import com.wrh.keshiguanjia.data.EnrollmentWithDetails
import com.wrh.keshiguanjia.data.Payment
import java.time.LocalDate

/** 单条提醒（首页提醒中心）。 */
data class ReminderItem(
    val studentId: Long,
    val studentName: String,
    val classId: Long,
    val className: String,
    /** 0=课时不足 1=即将到期 2=欠费 */
    val kind: Int,
    val detail: String,
) {
    companion object {
        const val KIND_LOW_BALANCE = 0
        const val KIND_EXPIRING = 1
        const val KIND_UNPAID = 2
        val KIND_LABELS = listOf("课时不足", "即将到期", "欠费")
    }
}

object Reminders {

    /**
     * 三类提醒：
     *  - 次卡剩余 ≤ lowBalanceThreshold（默认 3）
     *  - 课时包有效期 / 学期结束日在 expiryWarnDays（默认 14）天内（含已过期；剩余 0 的包不再提醒到期）
     *  - 约定金额 − 已收 > 0（欠费）
     */
    fun build(
        enrollments: List<EnrollmentWithDetails>,
        consumedByEnrollment: Map<Long, Int>,
        payments: List<Payment>,
        today: LocalDate,
        lowBalanceThreshold: Int = 3,
        expiryWarnDays: Int = 14,
    ): List<ReminderItem> {
        val result = mutableListOf<ReminderItem>()
        val paymentsByEnrollment = payments.filter { it.enrollmentId != null }
            .groupBy { it.enrollmentId!! }
        val warnLine = today.plusDays(expiryWarnDays.toLong())

        for (e in enrollments) {
            val name = e.student.name
            val className = e.clazz.name

            if (e.enrollment.billingType == Enrollment.BILLING_SESSIONS) {
                val remaining =
                    Billing.remainingSessions(e.packages, consumedByEnrollment[e.enrollment.id] ?: 0)
                if (remaining <= lowBalanceThreshold) {
                    result += ReminderItem(
                        e.enrollment.studentId, name, e.enrollment.classId, className,
                        ReminderItem.KIND_LOW_BALANCE, "剩余 $remaining 次",
                    )
                }
                if (remaining > 0) {
                    val earliestExpiry = e.packages.mapNotNull { it.validUntil }
                        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
                        .filter { it <= warnLine }
                        .minOrNull()
                    if (earliestExpiry != null) {
                        result += ReminderItem(
                            e.enrollment.studentId, name, e.enrollment.classId, className,
                            ReminderItem.KIND_EXPIRING, "课时包 $earliestExpiry 到期",
                        )
                    }
                }
            } else {
                val latestEnd = e.termRecords.map { it.endDate }
                    .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
                    .filter { it <= warnLine }
                    .maxOrNull()
                if (latestEnd != null) {
                    result += ReminderItem(
                        e.enrollment.studentId, name, e.enrollment.classId, className,
                        ReminderItem.KIND_EXPIRING, "学期 $latestEnd 到期",
                    )
                }
            }

            val billed = e.packages.sumOf { it.amountCents } + e.termRecords.sumOf { it.amountCents }
            val paid = paymentsByEnrollment[e.enrollment.id].orEmpty().sumOf { it.amountCents }
            val unpaid = billed - paid
            if (unpaid > 0) {
                result += ReminderItem(
                    e.enrollment.studentId, name, e.enrollment.classId, className,
                    ReminderItem.KIND_UNPAID, "未收 ¥" + MoneyUtils.yuanText(unpaid),
                )
            }
        }
        return result.sortedBy { it.kind }
    }
}

/** 统计口径的日期区间工具。 */
object StatsLogic {

    /** 本周（周一 ~ 周日）。 */
    fun thisWeekRange(today: LocalDate): Pair<LocalDate, LocalDate> {
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return monday to monday.plusDays(6)
    }
}

/** 轻量设置存储（提醒阈值等）。 */
object Settings {
    private const val PREFS = "settings"
    const val KEY_LOW_BALANCE = "low_balance_threshold"
    const val KEY_EXPIRY_DAYS = "expiry_warn_days"
    const val DEFAULT_LOW_BALANCE = 3
    const val DEFAULT_EXPIRY_DAYS = 14

    fun lowBalanceThreshold(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_LOW_BALANCE, DEFAULT_LOW_BALANCE)

    fun expiryWarnDays(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_EXPIRY_DAYS, DEFAULT_EXPIRY_DAYS)

    fun save(context: Context, lowBalance: Int, expiryDays: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_LOW_BALANCE, lowBalance)
            .putInt(KEY_EXPIRY_DAYS, expiryDays)
            .apply()
    }
}
