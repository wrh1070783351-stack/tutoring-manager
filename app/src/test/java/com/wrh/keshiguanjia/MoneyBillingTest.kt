package com.wrh.keshiguanjia

import com.wrh.keshiguanjia.logic.Billing
import com.wrh.keshiguanjia.logic.BillingDraft
import com.wrh.keshiguanjia.logic.MoneyUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyUtilsTest {

    @Test
    fun yuanText_formatsCents() {
        assertEquals("120.50", MoneyUtils.yuanText(12050))
        assertEquals("0.00", MoneyUtils.yuanText(0))
        assertEquals("5.05", MoneyUtils.yuanText(505))
        assertEquals("-3.20", MoneyUtils.yuanText(-320))
    }

    @Test
    fun parseYuan_variousInputs() {
        assertEquals(12000L, MoneyUtils.parseYuanToCents("120"))
        assertEquals(12050L, MoneyUtils.parseYuanToCents("120.5"))
        assertEquals(12050L, MoneyUtils.parseYuanToCents("120.50"))
        assertEquals(505L, MoneyUtils.parseYuanToCents("5.05"))
        assertEquals(0L, MoneyUtils.parseYuanToCents("0"))
        assertNull(MoneyUtils.parseYuanToCents("abc"))
        assertNull(MoneyUtils.parseYuanToCents("1.234"))
        assertNull(MoneyUtils.parseYuanToCents("-5"))
        assertNull(MoneyUtils.parseYuanToCents(""))
        assertEquals(12050L, MoneyUtils.parseYuanToCents("¥120.5"))
    }
}

class BillingValidateTest {

    private fun draft(type: Int, modify: BillingDraft.() -> BillingDraft = { this }) =
        BillingDraft(
            classId = 1, billingType = type,
            sessions = if (type == 0) 20 else 0,
            startDate = if (type == 1) "2026-09-01" else null,
            endDate = if (type == 1) "2027-01-31" else null,
            amountCents = 200000, payDate = "2026-09-29",
        ).modify()

    @Test
    fun validSessionAndTermPass() {
        assertNull(Billing.validate(draft(0)))
        assertNull(Billing.validate(draft(1)))
    }

    @Test
    fun sessionNeedsPositiveCount() {
        assertNotNull(Billing.validate(draft(0) { copy(sessions = 0) }))
        assertNotNull(Billing.validate(draft(0) { copy(bonusSessions = -1) }))
    }

    @Test
    fun termNeedsValidRange() {
        assertNotNull(Billing.validate(draft(1) { copy(endDate = "bad") }))
        assertNotNull(Billing.validate(draft(1) { copy(startDate = "2027-02-01") })) // 晚于结束
        assertNotNull(Billing.validate(draft(1) { copy(endDate = null) }))
    }

    @Test
    fun needsClassAndValidPayDate() {
        assertNotNull(Billing.validate(draft(0) { copy(classId = 0) }))
        assertNotNull(Billing.validate(draft(0) { copy(payDate = "2026/09/29") }))
    }
}
