package com.wrh.keshiguanjia.logic

import com.wrh.keshiguanjia.data.Student

/** 学生列表排序（学生页）。 */
object StudentSorting {
    const val MODE_RECENT = 0
    const val MODE_GRADE = 1
    const val MODE_NAME = 2

    fun apply(students: List<Student>, mode: Int): List<Student> = when (mode) {
        MODE_GRADE -> students.sortedWith(
            // 有年级的在前，按年级排，同年级按姓名
            compareByDescending<Student> { it.grade.isNotBlank() }
                .thenBy { it.grade }
                .thenBy { it.name })
        MODE_NAME -> students.sortedBy { it.name }
        else -> students // 入参已按加入时间倒序
    }
}
