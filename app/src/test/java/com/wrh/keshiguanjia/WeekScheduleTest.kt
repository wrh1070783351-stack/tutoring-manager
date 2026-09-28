package com.wrh.keshiguanjia

import com.wrh.keshiguanjia.logic.LessonSlot
import com.wrh.keshiguanjia.logic.WeekSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekScheduleTest {

    @Test
    fun emptyInput_givesEmptyMap() {
        assertTrue(WeekSchedule.groupByDay(emptyList()).isEmpty())
    }

    @Test
    fun groupsByDayOfWeek() {
        val slots = listOf(
            LessonSlot(6, 600, 720, "数学班", "数学"),
            LessonSlot(1, 1110, 1230, "英语班", "英语"),
            LessonSlot(6, 840, 960, "物理班", "物理"),
        )
        val week = WeekSchedule.groupByDay(slots)
        assertEquals(setOf(1, 6), week.keys)
        assertEquals(listOf("英语班"), week[1]!!.map { it.className })
    }

    @Test
    fun sortsWithinDayByStartTime() {
        val slots = listOf(
            LessonSlot(6, 840, 960, "物理班", "物理"),
            LessonSlot(6, 600, 720, "数学班", "数学"),
        )
        val saturday = WeekSchedule.groupByDay(slots)[6]!!
        assertEquals(listOf("数学班", "物理班"), saturday.map { it.className })
    }
}
