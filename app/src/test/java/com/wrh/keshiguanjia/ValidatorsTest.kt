package com.wrh.keshiguanjia

import com.wrh.keshiguanjia.logic.ClassTimeDraft
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
    fun class_endBeforeStartIsRejected() {
        val bad = listOf(ClassTimeDraft(dayOfWeek = 6, startMinute = 600, endMinute = 600))
        val err = Validators.validateClass("数学班", bad)
        assertEquals("下课时间必须晚于上课时间", err)
    }

    @Test
    fun class_validInputPasses() {
        val ok = listOf(ClassTimeDraft(dayOfWeek = 6, startMinute = 600, endMinute = 720))
        assertNull(Validators.validateClass("数学班", ok))
    }
}
