package com.wrh.keshiguanjia

import com.wrh.keshiguanjia.data.Student
import com.wrh.keshiguanjia.logic.StudentSorting
import org.junit.Assert.assertEquals
import org.junit.Test

class StudentSortingTest {

    private val s = { id: Long, name: String, grade: String ->
        Student(id = id, name = name, grade = grade)
    }

    @Test
    fun gradeMode_gradesFirstThenName() {
        val input = listOf(
            s(1, "张三", ""),
            s(2, "王五", "初二"),
            s(3, "李四", "初一"),
            s(4, "赵六", "初一"),
        )
        val sorted = StudentSorting.apply(input, StudentSorting.MODE_GRADE)
        assertEquals(listOf("李四", "赵六", "王五", "张三"), sorted.map { it.name })
    }

    @Test
    fun nameMode_sortsByName() {
        val input = listOf(s(1, "王五", ""), s(2, "张三", ""), s(3, "李四", ""))
        val sorted = StudentSorting.apply(input, StudentSorting.MODE_NAME)
        assertEquals(listOf("张三", "李四", "王五"), sorted.map { it.name })
    }

    @Test
    fun recentMode_keepsInputOrder() {
        val input = listOf(s(2, "后来", ""), s(1, "先来", ""))
        val sorted = StudentSorting.apply(input, StudentSorting.MODE_RECENT)
        assertEquals(listOf("后来", "先来"), sorted.map { it.name })
    }
}
