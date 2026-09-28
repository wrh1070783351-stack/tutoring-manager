package com.wrh.keshiguanjia

import com.wrh.keshiguanjia.data.ClassPackage
import com.wrh.keshiguanjia.data.ClassRoom
import com.wrh.keshiguanjia.data.Enrollment
import com.wrh.keshiguanjia.data.EnrollmentWithDetails
import com.wrh.keshiguanjia.data.Payment
import com.wrh.keshiguanjia.data.Student
import com.wrh.keshiguanjia.data.TermRecord
import com.wrh.keshiguanjia.logic.ReminderItem
import com.wrh.keshiguanjia.logic.Reminders
import com.wrh.keshiguanjia.logic.StatsLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RemindersTest {

    private val today = LocalDate.parse("2026-09-29")
    private val student = Student(id = 1, name = "Tom")
    private val clazz = ClassRoom(id = 2, name = "Math A")

    private fun enrollment(
        billingType: Int = Enrollment.BILLING_SESSIONS,
        packages: List<ClassPackage> = emptyList(),
        terms: List<TermRecord> = emptyList(),
    ) = EnrollmentWithDetails(
        enrollment = Enrollment(id = 10, studentId = 1, classId = 2, billingType = billingType),
        clazz = clazz,
        student = student,
        packages = packages,
        termRecords = terms,
    )

    @Test
    fun lowBalance_isFlaggedAtOrBelowThreshold() {
        val e = enrollment(packages = listOf(ClassPackage(enrollmentId = 10, totalSessions = 20, amountCents = 200000)))
        // 剩 20 - 18 = 2 ≤ 3
        val reminders = Reminders.build(listOf(e), mapOf(10L to 18), emptyList(), today)
        assertTrue(reminders.any { it.kind == ReminderItem.KIND_LOW_BALANCE && it.detail.contains("2 次") })
    }

    @Test
    fun enoughBalance_noLowReminder() {
        val e = enrollment(packages = listOf(ClassPackage(enrollmentId = 10, totalSessions = 20, amountCents = 200000)))
        val reminders = Reminders.build(listOf(e), mapOf(10L to 5), emptyList(), today)
        assertTrue(reminders.none { it.kind == ReminderItem.KIND_LOW_BALANCE })
    }

    @Test
    fun expiringPackage_warnsWithinWindow() {
        val e = enrollment(
            packages = listOf(
                ClassPackage(
                    enrollmentId = 10, totalSessions = 20, amountCents = 0,
                    validUntil = today.plusDays(7).toString(), // 14 天内
                )
            )
        )
        val reminders = Reminders.build(listOf(e), emptyMap(), emptyList(), today)
        assertTrue(reminders.any { it.kind == ReminderItem.KIND_EXPIRING })
    }

    @Test
    fun farExpiry_noReminder_andZeroRemainingSkipsExpiryReminder() {
        val far = enrollment(
            packages = listOf(
                ClassPackage(enrollmentId = 10, totalSessions = 20, amountCents = 0, validUntil = today.plusMonths(3).toString())
            )
        )
        assertTrue(Reminders.build(listOf(far), emptyMap(), emptyList(), today).none { it.kind == ReminderItem.KIND_EXPIRING })

        // 已耗尽的包（剩余 0）即使快到期也不再提示到期
        val drained = enrollment(
            packages = listOf(
                ClassPackage(enrollmentId = 10, totalSessions = 5, amountCents = 0, validUntil = today.plusDays(3).toString())
            )
        )
        assertTrue(Reminders.build(listOf(drained), mapOf(10L to 5), emptyList(), today).none { it.kind == ReminderItem.KIND_EXPIRING })
    }

    @Test
    fun unpaidAmount_isFlagged() {
        val e = enrollment(packages = listOf(ClassPackage(enrollmentId = 10, totalSessions = 20, amountCents = 120000)))
        val payments = listOf(Payment(studentId = 1, enrollmentId = 10, amountCents = 20000, date = "2026-09-01"))
        val reminders = Reminders.build(listOf(e), emptyMap(), payments, today)
        val unpaid = reminders.first { it.kind == ReminderItem.KIND_UNPAID }
        assertTrue(unpaid.detail.contains("1000.00"))
    }

    @Test
    fun fullyPaid_noUnpaidReminder() {
        val e = enrollment(packages = listOf(ClassPackage(enrollmentId = 10, totalSessions = 20, amountCents = 120000)))
        val payments = listOf(Payment(studentId = 1, enrollmentId = 10, amountCents = 120000, date = "2026-09-01"))
        assertTrue(Reminders.build(listOf(e), emptyMap(), payments, today).none { it.kind == ReminderItem.KIND_UNPAID })
    }

    @Test
    fun termEndingSoon_warns() {
        val e = enrollment(
            billingType = Enrollment.BILLING_TERM,
            terms = listOf(TermRecord(enrollmentId = 10, startDate = "2026-09-01", endDate = today.plusDays(10).toString(), amountCents = 500000)),
        )
        val reminders = Reminders.build(listOf(e), emptyMap(), emptyList(), today)
        assertTrue(reminders.any { it.kind == ReminderItem.KIND_EXPIRING })
    }
}

class StatsLogicTest {

    @Test
    fun thisWeekRange_startsMondayEndsSunday() {
        // 2026-09-29 是周二
        val (start, end) = StatsLogic.thisWeekRange(LocalDate.parse("2026-09-29"))
        assertEquals("2026-09-28", start.toString()) // 周一
        assertEquals("2026-10-04", end.toString())   // 周日
        assertEquals(1, start.dayOfWeek.value)
        assertEquals(7, end.dayOfWeek.value)
    }

    @Test
    fun thisWeekRange_onMonday() {
        val (start, _) = StatsLogic.thisWeekRange(LocalDate.parse("2026-09-28"))
        assertEquals("2026-09-28", start.toString())
    }
}
