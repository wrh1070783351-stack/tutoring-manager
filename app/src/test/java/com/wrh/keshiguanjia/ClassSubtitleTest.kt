package com.wrh.keshiguanjia

import com.wrh.keshiguanjia.ui.classes.classSubtitle
import org.junit.Assert.assertEquals
import org.junit.Test

/** 班级列表副标题的拼接规则（曾因 listOf 打印出字面量 "null" 而修复）。 */
class ClassSubtitleTest {

    @Test
    fun subjectAndTimes_bothPresent() {
        assertEquals("数学 · 周六 10:00 - 12:00", classSubtitle("数学", "周六 10:00 - 12:00"))
    }

    @Test
    fun blankSubject_isSkipped_notLiteralNull() {
        assertEquals("周日 09:00 - 11:00", classSubtitle("", "周日 09:00 - 11:00"))
        assertEquals("周日 09:00 - 11:00", classSubtitle("   ", "周日 09:00 - 11:00"))
    }

    @Test
    fun blankTimes_showsFallback() {
        assertEquals("数学 · 未设置上课时间", classSubtitle("数学", ""))
    }

    @Test
    fun bothBlank_showsFallbackOnly() {
        assertEquals("未设置上课时间", classSubtitle("", ""))
    }
}
