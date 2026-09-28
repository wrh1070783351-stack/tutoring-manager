package com.wrh.keshiguanjia

import com.wrh.keshiguanjia.logic.ClassTimeDraft
import com.wrh.keshiguanjia.logic.ClassTimeExpansion
import com.wrh.keshiguanjia.logic.Validators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {

    @Test
    fun student_blankNameIsRejected() {
        assertNotNull(Validators.validateStudent(""))
        assertNotNull(Validators.validateStudent("   "))
    }

    @Test
    fun student_validNamePasses() {
        assertNull(Validators.validateStudent("张三"))
    }

    @Test
    fun class_blankNameIsRejected() {
        val err = Validators.validateClass("  ", listOf(ClassTimeDraft()))
        assertEquals("班级名不能为空", err)
    }

    @Test
    fun class_emptyTimesIsRejected() {
        val err = Validators.validateClass("数学班", emptyList())
        assertEquals("请至少设置一个上课时间段", err)
    }

    @Test
    fun class_noDaySelectedIsRejected() {
        val noDays = listOf(ClassTimeDraft(days = emptySet(), startMinute = 600, endMinute = 720))
        val err = Validators.validateClass("数学班", noDays)
        assertEquals("请为每个时间段选择上课星期", err)
    }

    @Test
    fun class_endBeforeStartIsRejected() {
        val bad = listOf(ClassTimeDraft(days = setOf(6), startMinute = 600, endMinute = 600))
        val err = Validators.validateClass("数学班", bad)
        assertEquals("下课时间必须晚于上课时间", err)
    }

    @Test
    fun class_validInputPasses() {
        val ok = listOf(ClassTimeDraft(days = setOf(6), startMinute = 600, endMinute = 720))
        assertNull(Validators.validateClass("数学班", ok))
    }
}

class ClassTimeExpansionTest {

    @Test
    fun multiDayDraft_expandsToPerDayLessons() {
        val expanded = ClassTimeExpansion.expand(
            listOf(
                ClassTimeDraft(days = setOf(6, 7), startMinute = 600, endMinute = 720, room = "B"),
            )
        )
        assertEquals(2, expanded.size)
        assertEquals(6, expanded[0].dayOfWeek)
        assertEquals(7, expanded[1].dayOfWeek)
        assertEquals(600, expanded[0].startMinute)
        assertEquals("B", expanded[1].room)
    }

    @Test
    fun collapse_groupsSameSlotAcrossDays() {
        val drafts = ClassTimeExpansion.collapse(
            listOf(
                com.wrh.keshiguanjia.data.ClassTime(classId = 1, dayOfWeek = 2, startMinute = 1140, endMinute = 1260, room = "C"),
                com.wrh.keshiguanjia.data.ClassTime(classId = 1, dayOfWeek = 4, startMinute = 1140, endMinute = 1260, room = "C"),
                com.wrh.keshiguanjia.data.ClassTime(classId = 1, dayOfWeek = 6, startMinute = 600, endMinute = 720, room = "B"),
            )
        )
        assertEquals(2, drafts.size)
        val evening = drafts.first { it.startMinute == 1140 }
        assertEquals(setOf(2, 4), evening.days)
        assertEquals("C", evening.room)
    }

    @Test
    fun collapseThenExpand_roundTrips() {
        val original = listOf(
            com.wrh.keshiguanjia.data.ClassTime(classId = 1, dayOfWeek = 1, startMinute = 1110, endMinute = 1230, room = "A"),
            com.wrh.keshiguanjia.data.ClassTime(classId = 1, dayOfWeek = 5, startMinute = 1110, endMinute = 1230, room = "A"),
        )
        val roundTripped = ClassTimeExpansion.expand(ClassTimeExpansion.collapse(original))
        assertEquals(
            original.sortedBy { it.dayOfWeek }.map { Triple(it.dayOfWeek, it.startMinute, it.room) },
            roundTripped.map { Triple(it.dayOfWeek, it.startMinute, it.room) },
        )
    }
}
