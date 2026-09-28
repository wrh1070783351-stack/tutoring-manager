package com.wrh.keshiguanjia

import com.wrh.keshiguanjia.logic.ScheduleLogic
import com.wrh.keshiguanjia.data.ClassRoom
import com.wrh.keshiguanjia.data.ClassTime
import com.wrh.keshiguanjia.data.ClassWithTimes
import com.wrh.keshiguanjia.data.LessonOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ScheduleLogicTest {

    private val math = ClassWithTimes(
        clazz = ClassRoom(id = 1, name = "Math A", subject = "Math"),
        times = listOf(
            ClassTime(classId = 1, dayOfWeek = 1, startMinute = 1110, endMinute = 1230, room = "A"), // 周一 18:30
            ClassTime(classId = 1, dayOfWeek = 6, startMinute = 600, endMinute = 720, room = "B"),   // 周六 10:00
        ),
    )
    private val english = ClassWithTimes(
        clazz = ClassRoom(id = 2, name = "English B", subject = "English"),
        times = listOf(
            ClassTime(classId = 2, dayOfWeek = 2, startMinute = 1140, endMinute = 1260), // 周二 19:00
        ),
    )
    private val classes = listOf(math, english)

    @Test
    fun expandsWeeklyTimesOnMatchingDates() {
        // 2026-09-28 是周一
        val monday = LocalDate.parse("2026-09-28")
        val lessons = ScheduleLogic.lessonsForDate(classes, emptyList(), monday)
        assertEquals(1, lessons.size)
        assertEquals("Math A", lessons[0].className)
        assertEquals(1110, lessons[0].startMinute)
        assertFalse(lessons[0].isCancelled)
    }

    @Test
    fun cancelOverride_marksLessonCancelled() {
        val monday = LocalDate.parse("2026-09-28")
        val overrides = listOf(
            LessonOverride(classId = 1, date = "2026-09-28", type = LessonOverride.TYPE_CANCEL)
        )
        val lessons = ScheduleLogic.lessonsForDate(classes, overrides, monday)
        assertEquals(1, lessons.size)
        assertTrue(lessons[0].isCancelled)
        assertEquals(1, lessons[0].classId)
        assertTrue(lessons[0].overrideId != null)
    }

    @Test
    fun addOverride_addsExtraSession() {
        val monday = LocalDate.parse("2026-09-28")
        val overrides = listOf(
            LessonOverride(
                classId = 2, date = "2026-09-28", type = LessonOverride.TYPE_ADD,
                startMinute = 540, endMinute = 660,
            )
        )
        val lessons = ScheduleLogic.lessonsForDate(classes, overrides, monday)
        assertEquals(2, lessons.size)
        val extra = lessons.first { it.isExtra }
        assertEquals("English B", extra.className)
        assertEquals(540, extra.startMinute)
        assertTrue(extra.overrideId != null)
    }

    @Test
    fun cancelOnlyAffectsGivenDate() {
        val nextMonday = LocalDate.parse("2026-10-05")
        val overrides = listOf(
            LessonOverride(classId = 1, date = "2026-09-28", type = LessonOverride.TYPE_CANCEL)
        )
        val lessons = ScheduleLogic.lessonsForDate(classes, overrides, nextMonday)
        assertEquals(1, lessons.size)
        assertFalse(lessons[0].isCancelled)
    }

    @Test
    fun monthLessons_coverWholeMonth() {
        val map = ScheduleLogic.lessonsForMonth(classes, emptyList(), YearMonth.of(2026, 9))
        assertEquals(30, map.size) // 九月 30 天
        assertEquals(1, map[LocalDate.parse("2026-09-28")]!!.size)
    }

    @Test
    fun monthGrid_startsMondayAndPadsToFullWeeks() {
        // 2026-10-01 是周四，前置 3 个空位（周一二三），10 月 31 天 → 3+31=34 → 补到 35
        val cells = ScheduleLogic.monthGridCells(YearMonth.of(2026, 10))
        assertEquals(35, cells.size)
        assertEquals(3, cells.takeWhile { it == null }.size)
        assertEquals(LocalDate.parse("2026-10-01"), cells.first { it != null })
    }
}
